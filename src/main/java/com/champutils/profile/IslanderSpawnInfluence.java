package com.champutils.profile;

import com.champutils.badge.BadgeManager;
import com.champutils.badge.BadgeType;
import com.champutils.emblem.EmblemManager;
import com.champutils.gym.GymConfig;
import com.champutils.profession.ProfessionTrinketManager;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.SpawnBucket;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePositionType;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.evolution.PassiveEvolution;
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import kotlin.ranges.IntRange;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class IslanderSpawnInfluence implements SpawningInfluence {
    private static final Map<String, List<SpawnDetail>> CACHE = new LinkedHashMap<>();

    private final UUID playerUuid;
    private final String playerName;

    public IslanderSpawnInfluence(ServerPlayer player) {
        this.playerUuid = player == null ? null : player.getUUID();
        this.playerName = player == null ? "" : player.getGameProfile().getName();
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    @Override
    public List<SpawnDetail> injectSpawns(SpawnBucket bucket, SpawnablePosition spawnablePosition) {
        if (!IslanderSpawningConfig.CONFIG.enabled || bucket == null || spawnablePosition == null) return null;

        ServerLevel level = spawnablePosition.getWorld();
        if (IslanderMineManager.isMineWorld(level)) return java.util.Collections.emptyList();
        if (!IslanderProfileManager.isIslanderWorld(level)) return null;

        SpawnablePositionType<?> type = SpawnablePosition.Companion.getByClass(spawnablePosition);
        if (type == null) return null;

        String typeName = type.getName();
        if (!IslanderSpawningConfig.CONFIG.spawnablePositionTypes.contains(typeName)) return null;

        ServerPlayer activePlayer = activePlayer(level);
        int badgeCount = activePlayer == null ? 0 : BadgeManager.getBadgeCount(activePlayer);
        IslanderSpawningConfig.Tier tier = IslanderSpawningConfig.tierForBadgeCount(badgeCount);

        String bucketKey = bucket.getName();
        String cacheKey = typeName + "|" + bucketKey + "|" + tier.id;

        synchronized (CACHE) {
            List<SpawnDetail> cached = CACHE.get(cacheKey);
            if (cached != null) return cached;

            List<SpawnDetail> built = buildDetails(typeName, type, bucketKey, bucket, tier);
            CACHE.put(cacheKey, built);
            System.out.println("[ChampUtils] Islander spawn pool cached " + built.size() + " entries for " + cacheKey + " levels " + tier.minLevel + "-" + tier.maxLevel + ".");
            return built;
        }
    }

    @Override
    public float affectWeight(SpawnDetail detail, SpawnablePosition spawnablePosition, float weight) {
        ServerLevel level = spawnablePosition == null ? null : spawnablePosition.getWorld();
        ServerPlayer player = activePlayer(level);
        if (player == null || detail == null || weight <= 0.0F) return weight;
        double bonus = ProfessionTrinketManager.rarePokemonSpawnBonus(player);
        if (bonus <= 0.0D || !isRareNonSpecialPokemon(detail)) return weight;
        return (float)Math.max(0.0D, weight * (1.0D + bonus));
    }

    @Override
    public void affectSpawn(com.cobblemon.mod.common.api.spawning.detail.SpawnAction<?> action, Entity entity) {
        if (!(entity instanceof PokemonEntity pokemonEntity) || entity.level() == null || !(entity.level() instanceof ServerLevel level)) return;
        ServerPlayer player = activePlayer(level);
        if (player == null) return;
        var pokemon = pokemonEntity.getPokemon();
        if (pokemon == null || isSpecialSpecies(pokemon.getSpecies().getResourceIdentifier() == null ? pokemon.getSpecies().getName() : pokemon.getSpecies().getResourceIdentifier().toString())) return;

        int cap = currentGymCap(player);
        if (cap > 0 && pokemon.getLevel() > cap) pokemon.setLevel(cap);

        double minPercent = ProfessionTrinketManager.levelCharmGymCapPercent(player);
        if (minPercent > 0.0D && cap > 0) {
            int minimum = Math.max(1, Math.min(cap, (int)Math.floor(cap * minPercent)));
            int targetLevel = ThreadLocalRandom.current().nextInt(minimum, cap + 1);
            targetLevel = Math.max(pokemon.getLevel(), targetLevel);
            int speciesMinimum = minimumSpawnLevelForPokemon(pokemon);
            if (speciesMinimum <= cap) targetLevel = Math.max(targetLevel, speciesMinimum);
            pokemon.setLevel(targetLevel);
            // Level Charm level corrections are intentionally quiet to avoid chat spam.
        }

        ProfessionTrinketManager.tryApplyWildSpawnShiny(player, pokemon);
        entity.addTag("champutils_spawn_boost_checked");
    }

    private ServerPlayer activePlayer(ServerLevel level) {
        return playerUuid == null || level == null || level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(playerUuid);
    }

    private static boolean isRareNonSpecialPokemon(SpawnDetail detail) {
        String bucket = detail.getBucket() == null || detail.getBucket().getName() == null ? "" : detail.getBucket().getName().toLowerCase(java.util.Locale.ROOT);
        if (!(bucket.contains("rare") || bucket.contains("uncommon") || bucket.contains("ultra"))) return false;
        if (detail instanceof PokemonSpawnDetail pokemonDetail) {
            String species = pokemonDetail.getPokemon().getSpecies();
            return species != null && !isSpecialSpecies(species);
        }
        return false;
    }

    private static boolean isSpecialSpecies(String species) {
        String normalized = IslanderSpawningConfig.normalize(species);
        return EmblemManager.isLegendary(normalized) || EmblemManager.isUltraBeast(normalized) || EmblemManager.isParadox(normalized) || isMythical(normalized);
    }

    private static boolean isMythical(String species) {
        return switch (species) {
            case "mew", "celebi", "jirachi", "deoxys", "phione", "manaphy", "darkrai", "shaymin", "arceus",
                    "victini", "keldeo", "meloetta", "genesect", "diancie", "hoopa", "volcanion", "magearna",
                    "marshadow", "zeraora", "meltan", "melmetal", "zarude", "pecharunt" -> true;
            default -> false;
        };
    }

    private static int currentGymCap(ServerPlayer player) {
        try {
            java.util.Set<BadgeType> earned = BadgeManager.getBadges(player);
            int bestEarnedCap = 0;
            int nextCap = 0;
            for (BadgeType badge : BadgeType.values()) {
                GymConfig.GymDefinition gym = GymConfig.getGym(badge);
                if (gym == null || gym.levelCap <= 0) continue;
                if (earned.contains(badge)) bestEarnedCap = Math.max(bestEarnedCap, gym.levelCap);
                else if (nextCap == 0 || gym.levelCap < nextCap) nextCap = gym.levelCap;
            }
            return nextCap > 0 ? Math.max(bestEarnedCap, nextCap) : Math.max(bestEarnedCap, 100);
        } catch (Throwable ignored) {
            return 50;
        }
    }

    private static List<SpawnDetail> buildDetails(String typeName, SpawnablePositionType<?> type, String bucketKey, SpawnBucket bucket, IslanderSpawningConfig.Tier tier) {
        Collection<Species> speciesList = PokemonSpecies.getImplemented();
        List<SpawnDetail> details = new ArrayList<>();

        for (Species species : speciesList) {
            ResourceLocation id = species.getResourceIdentifier();
            if (id == null) continue;

            String key = id.toString();
            if (IslanderSpawningConfig.isExcludedSpecies(key)) continue;

            int evolutionStage = evolutionStage(species);
            if (!IslanderSpawningConfig.isAllowedByTier(key, evolutionStage, tier)) continue;

            int minLevel = Math.max(Math.max(1, tier.minLevel), minimumSpawnLevel(species));
            int maxLevel = Math.min(Math.max(tier.minLevel, tier.maxLevel), maximumSensibleSpawnLevel(species));
            if (minLevel > maxLevel) continue;

            PokemonSpawnDetail detail = new PokemonSpawnDetail();
            detail.setId("champutils_islander_" + IslanderSpawningConfig.normalize(tier.id) + "_" + typeName + "_" + IslanderSpawningConfig.normalize(bucketKey) + "_" + IslanderSpawningConfig.normalize(key));
            detail.setSpawnablePositionType(type);
            detail.setBucket(bucket);
            detail.setWeight(IslanderSpawningConfig.CONFIG.weight * tier.weightMultiplier);
            detail.setLevelRange(new IntRange(minLevel, maxLevel));
            detail.getPokemon().setSpecies(key);
            detail.autoLabel();
            details.add(detail);
        }

        return List.copyOf(details);
    }

    /**
     * Keeps boosted wild spawns evolution-correct. The Level Charm should choose a random level inside
     * the player's unlocked range, not force every spawn to the same floor level. If the chosen level
     * would make the current species under-evolved, the Pokemon is advanced through normal passive
     * level-up evolutions until the species makes sense for that level.
     */
    public static void normalizeEvolutionForLevel(Pokemon pokemon, int targetLevel) {
        if (pokemon == null) return;
        int level = Math.max(1, Math.min(100, targetLevel));
        pokemon.setLevel(level);
        for (int guard = 0; guard < 8; guard++) {
            Species species = pokemon.getSpecies();
            if (species == null || species.getEvolutions() == null || species.getEvolutions().isEmpty()) return;

            Evolution chosen = null;
            for (Evolution evolution : species.getEvolutions()) {
                if (!(evolution instanceof PassiveEvolution)) continue;
                try {
                    if (passesPassiveEvolution(evolution, pokemon)) {
                        chosen = evolution;
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            if (chosen == null) return;
            try {
                chosen.getResult().apply(pokemon);
                pokemon.setLevel(level);
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static boolean passesPassiveEvolution(Evolution evolution, Pokemon pokemon) {
        if (evolution == null || pokemon == null) return false;
        try {
            for (Requirement requirement : evolution.getRequirements()) {
                if (requirement == null || !requirement.check(pokemon)) return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int minimumSpawnLevelForPokemon(Pokemon pokemon) {
        if (pokemon == null) return 1;
        return minimumSpawnLevel(pokemon.getSpecies());
    }

    public static int minimumSpawnLevelForSpecies(Species species) {
        return minimumSpawnLevel(species);
    }

    private static int minimumSpawnLevel(Species species) {
        if (species == null || species.getPreEvolution() == null) return 1;
        String key = IslanderSpawningConfig.normalize(species.getResourceIdentifier() == null ? species.getName() : species.getResourceIdentifier().toString());
        Integer fallback = fallbackMinimumEvolutionLevel(key);
        if (fallback != null) return fallback;
        int stage = evolutionStage(species);
        return stage <= 0 ? 1 : stage == 1 ? 16 : 36;
    }

    private static int maximumSensibleSpawnLevel(Species species) {
        if (species == null || species.getEvolutions() == null || species.getEvolutions().isEmpty()) return 100;
        int firstEvolutionLevel = firstPassiveEvolutionLevel(species);
        if (firstEvolutionLevel > 1) return Math.max(1, firstEvolutionLevel - 1);

        String key = IslanderSpawningConfig.normalize(species.getResourceIdentifier() == null ? species.getName() : species.getResourceIdentifier().toString());
        Integer fallback = fallbackMaximumUnevolvedLevel(key);
        if (fallback != null) return fallback;

        return 100;
    }

    private static int firstPassiveEvolutionLevel(Species species) {
        if (species == null || species.getEvolutions() == null || species.getEvolutions().isEmpty()) return -1;
        for (int level = 1; level <= 100; level++) {
            Pokemon probe;
            try {
                probe = species.create(level);
                probe.setLevel(level);
            } catch (Throwable ignored) {
                return -1;
            }
            for (Evolution evolution : species.getEvolutions()) {
                if (!(evolution instanceof PassiveEvolution)) continue;
                try {
                    if (passesPassiveEvolution(evolution, probe)) return level;
                } catch (Throwable ignored) {}
            }
        }
        return -1;
    }

    private static Integer fallbackMaximumUnevolvedLevel(String key) {
        return switch (key) {
            case "charmander", "bulbasaur", "squirtle", "chikorita", "cyndaquil", "totodile",
                    "treecko", "torchic", "mudkip", "turtwig", "chimchar", "piplup", "snivy",
                    "tepig", "oshawott", "chespin", "fennekin", "froakie", "rowlet", "litten",
                    "popplio", "grookey", "scorbunny", "sobble", "sprigatito", "fuecoco", "quaxly" -> 15;
            case "charmeleon", "ivysaur", "wartortle", "bayleef", "quilava", "croconaw",
                    "grovyle", "combusken", "marshtomp", "grotle", "monferno", "prinplup",
                    "servine", "pignite", "dewott", "quilladin", "braixen", "frogadier",
                    "dartrix", "torracat", "brionne", "thwackey", "raboot", "drizzile",
                    "floragato", "crocalor", "quaxwell" -> 35;
            default -> null;
        };
    }

    private static Integer fallbackMinimumEvolutionLevel(String key) {
        return switch (key) {
            case "goodra" -> 50;
            case "sliggoo" -> 40;
            case "dragonite", "tyranitar", "hydreigon", "volcarona" -> 55;
            case "salamence", "metagross" -> 50;
            case "garchomp" -> 48;
            case "kommo_o", "dragapult", "baxcalibur" -> 60;
            case "haxorus" -> 48;
            case "noivern" -> 48;
            case "kingambit" -> 52;
            case "annihilape" -> 35;
            default -> null;
        };
    }

    private static int evolutionStage(Species species) {
        int stage = 0;
        Species current = species;
        for (int guard = 0; guard < 6; guard++) {
            if (current == null || current.getPreEvolution() == null) return stage;
            stage++;
            current = current.getPreEvolution().getSpecies();
        }
        return stage;
    }
}
