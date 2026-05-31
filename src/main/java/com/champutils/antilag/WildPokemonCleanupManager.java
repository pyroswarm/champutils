package com.champutils.antilag;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;
import com.cobblemon.mod.common.pokemon.activestate.ShoulderedState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Cobblemon-aware cleanup that uses real Cobblemon classes instead of reflection guesses.
 *
 * This removes only ordinary natural wild PokemonEntity instances. It intentionally protects:
 * player/NPC/profile-owned Pokemon, pasture/tethered Pokemon, sent-out/shouldered Pokemon,
 * battle Pokemon, fresh spawns, shiny/special/boss/event-tagged Pokemon, and custom-named Pokemon.
 */
public final class WildPokemonCleanupManager {
    private static final Set<String> PROTECTED_MARKERS = Set.of(
            "champutils_mega_boss", "champutils_guild_boss", "champutils_world_boss", "champutils_special_spawn",
            "champutils_roaming_trainer", "boss", "special", "legendary", "mythical", "ultra_beast",
            "ultrabeast", "roaming", "event", "titan", "totem", "raid", "mega_boss", "world_boss", "guild_boss"
    );

    private WildPokemonCleanupManager() {}

    public static CleanupResult cleanup(MinecraftServer server, Options options) {
        CleanupResult result = new CleanupResult();
        if (server == null || options == null || options.maxRemovals <= 0) return result;

        for (ServerLevel level : server.getAllLevels()) {
            if (result.totalRemoved() >= options.maxRemovals) break;
            ResourceLocation dimension = level.dimension().location();
            if (options.disabledDimensions.contains(dimension.toString())) continue;

            List<Entity> snapshot = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) snapshot.add(entity);

            for (Entity entity : snapshot) {
                if (result.totalRemoved() >= options.maxRemovals) break;
                if (entity == null || !entity.isAlive()) continue;

                if (options.clearDroppedItems && entity instanceof ItemEntity) {
                    entity.remove(RemovalReason.DISCARDED);
                    result.droppedItems++;
                    continue;
                }

                if (options.clearWildPokemon && entity instanceof PokemonEntity pokemonEntity) {
                    Safety safety = classify(pokemonEntity, options.minWildPokemonAgeTicks, options.protectCustomNames);
                    result.checkedWildPokemon++;
                    if (safety.safe()) {
                        pokemonEntity.remove(RemovalReason.DISCARDED);
                        result.wildPokemon++;
                    } else {
                        result.protectedWildPokemon++;
                        result.protectedReasons.merge(safety.reason(), 1, Integer::sum);
                    }
                }
            }
        }

        return result;
    }

    public static boolean isSafeNaturalWildPokemon(Entity entity, int minAgeTicks, boolean protectCustomNames) {
        return entity instanceof PokemonEntity pokemonEntity && classify(pokemonEntity, minAgeTicks, protectCustomNames).safe();
    }

    public static Safety classify(PokemonEntity entity, int minAgeTicks, boolean protectCustomNames) {
        if (entity == null || !entity.isAlive()) return Safety.protectedBecause("not alive");
        if (entity.tickCount < Math.max(0, minAgeTicks)) return Safety.protectedBecause("fresh spawn");
        if (protectCustomNames && entity.hasCustomName()) return Safety.protectedBecause("custom name");
        if (hasProtectedEntityTag(entity)) return Safety.protectedBecause("protected entity tag");
        if (entity.isBattling() || entity.getBattleId() != null || entity.getBattle() != null) return Safety.protectedBecause("battle");
        if (entity.getTethering() != null) return Safety.protectedBecause("pasture tether");
        if (entity.getOwnerUUID() != null) return Safety.protectedBecause("entity owner");

        Pokemon pokemon = entity.getPokemon();
        if (pokemon == null) return Safety.protectedBecause("missing pokemon data");
        if (!pokemon.isWild()) return Safety.protectedBecause("not wild");
        if (pokemon.isPlayerOwned() || pokemon.isNPCOwned() || pokemon.getOwnerUUID() != null) return Safety.protectedBecause("owned pokemon");
        if (pokemon.getState() instanceof SentOutState || pokemon.getState() instanceof ShoulderedState) return Safety.protectedBecause("active pokemon state");
        if (pokemon.getShiny()) return Safety.protectedBecause("shiny");
        if (isSpecialPokemon(entity, pokemon)) return Safety.protectedBecause("special pokemon");

        return Safety.SAFE;
    }

    private static boolean hasProtectedEntityTag(Entity entity) {
        for (String tag : entity.getTags()) {
            String lower = tag.toLowerCase(Locale.ROOT);
            for (String marker : PROTECTED_MARKERS) if (lower.contains(marker)) return true;
        }
        return false;
    }

    private static boolean isSpecialPokemon(PokemonEntity entity, Pokemon pokemon) {
        StringBuilder text = new StringBuilder();
        appendAll(text, entity.getAspects());
        appendAll(text, pokemon.getAspects());
        // Do NOT use species/form labels here. Cobblemon species labels can include broad metadata
        // and this cleanup only needs to protect ChampUtils-marked/event-tagged entities.
        if (pokemon.getSpecies() != null) text.append(' ').append(pokemon.getSpecies().getName());

        String lower = text.toString().toLowerCase(Locale.ROOT);
        for (String marker : PROTECTED_MARKERS) if (lower.contains(marker)) return true;
        return false;
    }

    private static void appendAll(StringBuilder text, Iterable<?> values) {
        if (values == null) return;
        for (Object value : values) {
            if (value != null) text.append(' ').append(value);
        }
    }

    public record Safety(boolean safe, String reason) {
        static final Safety SAFE = new Safety(true, "ordinary natural wild pokemon");
        static Safety protectedBecause(String reason) { return new Safety(false, reason); }
    }

    public static final class Options {
        public boolean clearDroppedItems;
        public boolean clearWildPokemon = true;
        public boolean protectCustomNames = true;
        public int minWildPokemonAgeTicks = 20 * 60;
        public int maxRemovals = 500;
        public Set<String> disabledDimensions = Set.of();
    }

    public static final class CleanupResult {
        public int droppedItems;
        public int wildPokemon;
        public int checkedWildPokemon;
        public int protectedWildPokemon;
        public final Map<String, Integer> protectedReasons = new TreeMap<>();
        public int totalRemoved() { return droppedItems + wildPokemon; }
        public String protectedReasonSummary() {
            if (protectedReasons.isEmpty()) return "none";
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, Integer> entry : protectedReasons.entrySet()) {
                if (!first) builder.append(", ");
                builder.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
            return builder.toString();
        }
    }
}
