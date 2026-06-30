package com.champutils.specialspawn;

import com.champutils.wondertrade.WonderTradePokemonUtil;
import com.champutils.emblem.EmblemManager;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;

import java.util.Locale;

/**
 * Hard safety net: legendary/mythical/ultra-beast Pokemon should only exist as ChampUtils special spawns.
 * This removes natural/datapack spawns, including End spawns, while keeping player-owned, battle, boss,
 * crate/command-created, and ChampUtils special-spawn entities safe.
 */
public final class NaturalSpecialSpawnBlocker {
    private static final String SPECIAL_TAG = "champutils_special_spawn";
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static int ticks;

    private NaturalSpecialSpawnBlocker() {}

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        ticks++;
        if (ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof PokemonEntity pokemonEntity) || !pokemonEntity.isAlive()) continue;
                if (!shouldRemove(pokemonEntity)) continue;
                pokemonEntity.remove(RemovalReason.DISCARDED);
            }
        }
    }

    private static boolean shouldRemove(PokemonEntity entity) {
        if (entity.getTags().contains(SPECIAL_TAG)) return false;
        if (hasAllowedChampUtilsTag(entity)) return false;
        if (entity.isBattling() || entity.getBattleId() != null || entity.getBattle() != null) return false;
        if (entity.getOwnerUUID() != null || entity.getTethering() != null) return false;

        Pokemon pokemon = entity.getPokemon();
        if (pokemon == null) return false;
        if (!pokemon.isWild()) return false;
        if (pokemon.isPlayerOwned() || pokemon.isNPCOwned() || pokemon.getOwnerUUID() != null) return false;

        String species = speciesId(pokemon);
        String normalized = species == null ? "" : species.toLowerCase(Locale.ROOT).replace("cobblemon:", "");
        return WonderTradePokemonUtil.isLegendarySpecies(species)
                || EmblemManager.isUltraBeast(normalized)
                || EmblemManager.isParadox(normalized)
                || isMythical(normalized);
    }

    private static boolean isMythical(String species) {
        return switch (species) {
            case "mew", "celebi", "jirachi", "deoxys", "phione", "manaphy", "darkrai", "shaymin", "arceus",
                    "victini", "keldeo", "meloetta", "genesect", "diancie", "hoopa", "volcanion", "magearna",
                    "marshadow", "zeraora", "meltan", "melmetal", "zarude", "pecharunt" -> true;
            default -> false;
        };
    }

    private static boolean hasAllowedChampUtilsTag(Entity entity) {
        for (String tag : entity.getTags()) {
            String lower = tag.toLowerCase(Locale.ROOT);
            if (lower.contains("champutils_mega_boss")
                    || lower.contains("champutils_guild_boss")
                    || lower.contains("champutils_world_boss")) {
                return true;
            }
        }
        return false;
    }

    private static String speciesId(Pokemon pokemon) {
        try {
            return String.valueOf(pokemon.getSpecies().getResourceIdentifier());
        } catch (Exception ignored) {}
        try {
            return String.valueOf(pokemon.getSpecies().getName());
        } catch (Exception ignored) {}
        return "unknown";
    }
}
