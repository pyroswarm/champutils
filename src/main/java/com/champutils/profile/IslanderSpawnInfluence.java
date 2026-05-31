package com.champutils.profile;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.SpawnBucket;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePositionType;
import com.cobblemon.mod.common.pokemon.Species;
import kotlin.ranges.IntRange;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        long playtimeSeconds = playerUuid == null ? 0L : PlayerDataManager.getPlaytimeSeconds(playerUuid, playerName);
        IslanderSpawningConfig.Tier tier = IslanderSpawningConfig.tierForPlaytime(playtimeSeconds);

        String bucketKey = String.valueOf(bucket);
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

            PokemonSpawnDetail detail = new PokemonSpawnDetail();
            detail.setId("champutils_islander_" + IslanderSpawningConfig.normalize(tier.id) + "_" + typeName + "_" + IslanderSpawningConfig.normalize(bucketKey) + "_" + IslanderSpawningConfig.normalize(key));
            detail.setSpawnablePositionType(type);
            detail.setBucket(bucket);
            detail.setWeight(IslanderSpawningConfig.CONFIG.weight * tier.weightMultiplier);
            detail.setLevelRange(new IntRange(Math.max(1, tier.minLevel), Math.max(tier.minLevel, tier.maxLevel)));
            detail.getPokemon().setSpecies(key);
            detail.autoLabel();
            details.add(detail);
        }

        return List.copyOf(details);
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
