package com.champutils.gym;

import com.champutils.commands.WildSpawnCapCommand;
import com.champutils.emblem.EmblemManager;
import com.champutils.profile.IslanderSpawningConfig;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/**
 * Belt-and-suspenders wild level cap enforcement.
 *
 * Spawn events cap new wild Pokemon immediately. This periodic pass catches anything that slipped
 * through because of delayed spawn setup, other spawn systems, chunk reloads, or event ordering.
 */
public final class WildGymLevelCapManager {
    private static final int CHECK_INTERVAL_TICKS = 20 * 5;
    private static final double PLAYER_RADIUS_SQ = 160.0D * 160.0D;
    private static boolean registered = false;
    private static int ticks = 0;

    private WildGymLevelCapManager() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(WildGymLevelCapManager::tick);
        System.out.println("[ChampUtils] Wild gym level cap safety manager registered.");
    }

    private static void tick(MinecraftServer server) {
        if (server == null) return;
        ticks++;
        if (ticks < CHECK_INTERVAL_TICKS) return;
        ticks = 0;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) continue;
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof PokemonEntity pokemonEntity) || !pokemonEntity.isAlive()) continue;
                if (!shouldCap(pokemonEntity)) continue;

                ServerPlayer player = nearestPlayer(level, pokemonEntity);
                if (player == null) continue;

                Pokemon pokemon = pokemonEntity.getPokemon();
                int before = Math.max(1, pokemon.getLevel());
                WildSpawnCapCommand.applyToWildSpawn(player, pokemon);
                if (pokemon.getLevel() != before) {
                    pokemonEntity.addTag("champutils_gym_cap_corrected");
                }
            }
        }
    }

    private static boolean shouldCap(PokemonEntity entity) {
        Pokemon pokemon = entity.getPokemon();
        if (pokemon == null || !pokemon.isWild()) return false;
        if (hasProtectedTag(entity)) return false;
        String species = normalizeSpecies(pokemon);
        return !isSpecialSpecies(species);
    }

    private static boolean hasProtectedTag(Entity entity) {
        for (String tag : entity.getTags()) {
            String lower = tag.toLowerCase(Locale.ROOT);
            if (lower.contains("champutils_special_spawn")
                    || lower.contains("champutils_mega_boss")
                    || lower.contains("champutils_world_boss")
                    || lower.contains("champutils_guild_boss")
                    || lower.contains("champutils_expedition")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSpecialSpecies(String species) {
        return EmblemManager.isLegendary(species)
                || EmblemManager.isUltraBeast(species)
                || EmblemManager.isParadox(species)
                || isMythical(species);
    }

    private static boolean isMythical(String species) {
        return switch (species) {
            case "mew", "celebi", "jirachi", "deoxys", "phione", "manaphy", "darkrai", "shaymin", "arceus",
                    "victini", "keldeo", "meloetta", "genesect", "diancie", "hoopa", "volcanion", "magearna",
                    "marshadow", "zeraora", "meltan", "melmetal", "zarude", "pecharunt" -> true;
            default -> false;
        };
    }

    private static String normalizeSpecies(Pokemon pokemon) {
        try {
            if (pokemon.getSpecies() != null && pokemon.getSpecies().getResourceIdentifier() != null) {
                return IslanderSpawningConfig.normalize(pokemon.getSpecies().getResourceIdentifier().toString());
            }
            if (pokemon.getSpecies() != null) return IslanderSpawningConfig.normalize(pokemon.getSpecies().getName());
        } catch (Throwable ignored) {}
        return "";
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, Entity entity) {
        ServerPlayer best = null;
        double bestDistance = PLAYER_RADIUS_SQ;
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(entity);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }
}
