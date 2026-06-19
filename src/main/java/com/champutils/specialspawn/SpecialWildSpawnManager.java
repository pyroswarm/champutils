package com.champutils.specialspawn;

import com.champutils.profile.IslanderProfileManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfilePlaytimeManager;
import com.champutils.territory.TerritoryRepository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class SpecialWildSpawnManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File STATE_FILE = new File("config/champutils/special_wild_spawn_state.json");
    private static final Random RANDOM = new Random();
    private static int ticksUntilCheck = 200;
    private static int ticksUntilCleanup = 200;
    private static final Map<UUID, Long> tracked = new ConcurrentHashMap<>();
    private static final long SPECIAL_DESPAWN_MILLIS = 15L * 60L * 1000L;
    private static final String SPECIAL_TAG = "champutils_special_spawn";
    private static final String SPECIAL_EXPIRES_TAG_PREFIX = "champutils_special_expires_";
    private static State state = new State();
    private static boolean stateLoaded = false;
    private static double cashShopChanceBoost = 0.0D;
    private static long cashShopChanceBoostExpiresAt = 0L;

    private SpecialWildSpawnManager() {}

    public static void tick(MinecraftServer server) {
        if (!SpecialWildSpawnConfig.DATA.enabled) return;
        ensureStateLoaded();

        ticksUntilCleanup--;
        if (ticksUntilCleanup <= 0) {
            ticksUntilCleanup = 200; // cleanup every 10 seconds, independent from the spawn roll interval
            cleanupTracked(server);
        }

        ticksUntilCheck--;
        if (ticksUntilCheck > 0) return;
        int intervalTicks = Math.max(20, SpecialWildSpawnConfig.DATA.checkIntervalTicks);
        ticksUntilCheck = intervalTicks;

        cleanupTracked(server);
        if (tracked.size() >= Math.max(1, SpecialWildSpawnConfig.DATA.maxAliveSpecialWildPokemon)) return;

        // Normal and Islander special spawns roll independently. Islander spawns use their
        // own player list, chance settings, species pool, and last-spawn pity timer.
        runSpawnRoll(server, intervalTicks, false);
        runSpawnRoll(server, intervalTicks, true);
    }


    private static void runSpawnRoll(MinecraftServer server, int intervalTicks, boolean islanderRoll) {
        if (tracked.size() >= Math.max(1, SpecialWildSpawnConfig.DATA.maxAliveSpecialWildPokemon)) return;

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.removeIf(p -> p == null || p.isSpectator() || !isEligibleSpecialSpawnPlayer(p, islanderRoll));
        if (players.isEmpty()) return;

        double chance = currentGlobalChancePerCheck(intervalTicks, islanderRoll);
        if (RANDOM.nextDouble() >= chance) return;

        Collections.shuffle(players, RANDOM);
        boolean rareTripleEvent = SpecialWildSpawnConfig.DATA.rareTripleSpawnEventEnabled
                && RANDOM.nextDouble() < Math.max(0.0D, Math.min(1.0D, SpecialWildSpawnConfig.DATA.rareTripleSpawnEventChance));

        if (rareTripleEvent) {
            runRareTripleSpawnEvent(server, players, islanderRoll);
            return;
        }

        ServerPlayer player = players.get(0);
        SpawnBucket bucket = pickBucket(islanderRoll);
        if (bucket == null) return;

        SpawnResult result = trySpawnFor(player, bucket, false);
        if (result == null) {
            for (ServerPlayer fallback : players) {
                if (fallback == player) continue;
                result = trySpawnFor(fallback, bucket, false);
                if (result != null) break;
            }
        }
        if (result != null) {
            markSpawned(result.type, result.species, false, islanderRoll);
            announce(server, result.type, result.species, result.level, result.pos);
        }
    }



    public static boolean forceSpawnFor(ServerPlayer player) {
        return forceSpawnForResult(player).success;
    }

    public static ForceSpawnResult forceSpawnForResult(ServerPlayer player) {
        if (player == null) return ForceSpawnResult.fail("No player was found.");
        if (!SpecialWildSpawnConfig.DATA.enabled) return ForceSpawnResult.fail("Special wild spawns are disabled in the config.");
        if (player.serverLevel() == null) return ForceSpawnResult.fail("Player has no loaded world.");

        boolean islanderRoll = isIslanderSpecialSpawnLevel(player.serverLevel());
        String eligibilityReason = eligibilityFailureReason(player, islanderRoll);
        if (eligibilityReason != null) return ForceSpawnResult.fail(eligibilityReason);

        SpawnBucket bucket = pickBucket(islanderRoll);
        if (bucket == null) return ForceSpawnResult.fail("No valid special spawn bucket exists for this world/profile type.");

        SpawnResult result = trySpawnFor(player, bucket, true);
        if (result == null) {
            return ForceSpawnResult.fail("No safe spawn position was found nearby, or the selected Pokémon failed to spawn. I tried expanded islander-safe fallback placement, loaded chunks, surface scans, and direct Cobblemon spawning. Check the server log for the exact direct spawn error.");
        }

        markSpawned(result.type, result.species, false, islanderRoll);
        announce(player.getServer(), result.type, result.species, result.level, result.pos);
        return ForceSpawnResult.ok(result.type, result.species, result.pos);
    }

    private static void runRareTripleSpawnEvent(MinecraftServer server, List<ServerPlayer> players, boolean islanderRoll) {
        int availableSlots = Math.max(0, SpecialWildSpawnConfig.DATA.maxAliveSpecialWildPokemon - tracked.size());
        int wanted = Math.max(1, SpecialWildSpawnConfig.DATA.rareTripleSpawnEventSpawnCount);
        int targetCount = Math.min(wanted, Math.min(players.size(), availableSlots));
        if (targetCount <= 0) return;

        List<SpawnResult> results = new ArrayList<>();
        Set<UUID> usedPlayers = new HashSet<>();
        for (ServerPlayer player : players) {
            if (results.size() >= targetCount) break;
            if (usedPlayers.contains(player.getUUID())) continue;

            SpawnBucket bucket = pickBucket(islanderRoll);
            if (bucket == null) continue;

            SpawnResult result = trySpawnFor(player, bucket, false);
            if (result != null) {
                results.add(result);
                usedPlayers.add(player.getUUID());
            }
        }

        if (results.isEmpty()) return;

        SpawnResult last = results.get(results.size() - 1);
        markSpawned(last.type, last.species, true, islanderRoll);
        announceRareTripleEvent(server, results);
    }

    public static void cleanupTracked(MinecraftServer server) {
        long now = System.currentTimeMillis();

        // Rebuild tracking from persistent entity scoreboard tags. This fixes special spawns
        // surviving restarts or any case where the in-memory map missed the spawned entity.
        recoverTaggedSpecialSpawns(server);

        tracked.entrySet().removeIf(entry -> {
            UUID id = entry.getKey();
            long expiresAt = entry.getValue();

            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(id);

                if (e != null && e.isAlive()) {
                    if (now >= expiresAt) {
                        removeSpecialSpawnEntity(e);
                        return true;
                    }
                    return false;
                }
            }

            return true;
        });

        // Safety net: if a tagged entity was not in the map for any reason, still remove it.
        removeExpiredTaggedSpecialSpawns(server, now);
    }

    private static void recoverTaggedSpecialSpawns(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!isTaggedSpecialSpawn(entity)) continue;
                long expiresAt = readSpecialExpiresAt(entity);
                if (expiresAt > 0L) {
                    tracked.put(entity.getUUID(), expiresAt);
                }
            }
        }
    }

    private static void removeExpiredTaggedSpecialSpawns(MinecraftServer server, long now) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!isTaggedSpecialSpawn(entity)) continue;
                long expiresAt = readSpecialExpiresAt(entity);
                if (expiresAt > 0L && now >= expiresAt) {
                    removeSpecialSpawnEntity(entity);
                    tracked.remove(entity.getUUID());
                }
            }
        }
    }

    private static boolean isTaggedSpecialSpawn(Entity entity) {
        return entity != null && entity.getTags().contains(SPECIAL_TAG);
    }

    private static long readSpecialExpiresAt(Entity entity) {
        if (entity == null) return 0L;
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(SPECIAL_EXPIRES_TAG_PREFIX)) {
                try {
                    return Long.parseLong(tag.substring(SPECIAL_EXPIRES_TAG_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private static void markSpecialSpawnEntity(Entity entity, long expiresAt) {
        if (entity == null) return;
        entity.addTag(SPECIAL_TAG);
        entity.getTags().stream()
                .filter(tag -> tag != null && tag.startsWith(SPECIAL_EXPIRES_TAG_PREFIX))
                .toList()
                .forEach(entity::removeTag);
        entity.addTag(SPECIAL_EXPIRES_TAG_PREFIX + expiresAt);

        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        tracked.put(entity.getUUID(), expiresAt);
    }

    private static void removeSpecialSpawnEntity(Entity entity) {
        if (entity == null) return;
        entity.discard();
    }

    public static long getLastSpawnEpochMillis() {
        return getLastNormalSpawnEpochMillis();
    }

    public static long getLastNormalSpawnEpochMillis() {
        ensureStateLoaded();
        return Math.max(0L, state.lastSpawnEpochMillis);
    }

    public static long getLastIslanderSpawnEpochMillis() {
        ensureStateLoaded();
        return Math.max(0L, state.islanderLastSpawnEpochMillis);
    }

    public static String formatLastSpawnAgo() {
        return formatLastNormalSpawnAgo();
    }

    public static String formatLastNormalSpawnAgo() {
        return formatAgo(getLastNormalSpawnEpochMillis());
    }

    public static String formatLastIslanderSpawnAgo() {
        return formatAgo(getLastIslanderSpawnEpochMillis());
    }

    private static String formatAgo(long last) {
        if (last <= 0L) return "Never";
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - last);
        long totalMinutes = elapsedMillis / 60000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours + "h " + minutes + "m ago";
    }

    public static void activateCashShopBoost(double amount, long durationMillis) {
        cashShopChanceBoost = Math.max(cashShopChanceBoost, Math.max(0.0D, amount));
        cashShopChanceBoostExpiresAt = Math.max(cashShopChanceBoostExpiresAt, System.currentTimeMillis() + Math.max(1L, durationMillis));
    }

    public static void deactivateCashShopBoost() {
        cashShopChanceBoost = 0.0D;
        cashShopChanceBoostExpiresAt = 0L;
    }

    private static double activeCashShopChanceBoost() {
        if (cashShopChanceBoostExpiresAt <= System.currentTimeMillis()) {
            cashShopChanceBoost = 0.0D;
            cashShopChanceBoostExpiresAt = 0L;
        }
        return Math.max(0.0D, cashShopChanceBoost);
    }

    private static double currentGlobalChancePerCheck(int intervalTicks, boolean islanderRoll) {
        double targetMinutes = Math.max(1.0D, islanderRoll ? SpecialWildSpawnConfig.DATA.islanderTargetAverageSpawnMinutes : SpecialWildSpawnConfig.DATA.targetAverageSpawnMinutes);
        double targetTicks = targetMinutes * 60.0D * 20.0D;
        double base = Math.max(0.000001D, Math.min(1.0D, intervalTicks / targetTicks));

        long last = islanderRoll ? state.islanderLastSpawnEpochMillis : state.lastSpawnEpochMillis;
        double elapsedTargetWindows;
        if (last <= 0L) {
            elapsedTargetWindows = 0.0D;
        } else {
            double elapsedMillis = Math.max(0L, System.currentTimeMillis() - last);
            elapsedTargetWindows = elapsedMillis / (targetMinutes * 60_000.0D);
        }

        double multiplier = SpecialWildSpawnConfig.DATA.baseChanceMultiplier
                + (elapsedTargetWindows * SpecialWildSpawnConfig.DATA.pityChanceIncreasePerTargetWindow);
        multiplier = Math.max(0.01D, Math.min(Math.max(0.01D, SpecialWildSpawnConfig.DATA.maxPityMultiplier), multiplier));
        return Math.max(0.0D, Math.min(1.0D, base * multiplier * (1.0D + activeCashShopChanceBoost())));
    }

    private static SpawnBucket pickBucket(boolean islanderRoll) {
        List<SpawnBucket> buckets = new ArrayList<>();
        addBucket(buckets, islanderRoll ? "islander legendary" : "legendary", islanderRoll ? SpecialWildSpawnConfig.DATA.islanderLegendaryChancePerCheck : SpecialWildSpawnConfig.DATA.legendaryChancePerCheck, islanderRoll ? SpecialWildSpawnConfig.DATA.islanderLegendarySpawns : SpecialWildSpawnConfig.DATA.legendarySpawns, SpecialWildSpawnConfig.DATA.levelRangeLegendary);
        addBucket(buckets, islanderRoll ? "islander paradox" : "paradox", islanderRoll ? SpecialWildSpawnConfig.DATA.islanderParadoxChancePerCheck : SpecialWildSpawnConfig.DATA.paradoxChancePerCheck, islanderRoll ? SpecialWildSpawnConfig.DATA.islanderParadoxSpawns : SpecialWildSpawnConfig.DATA.paradoxSpawns, SpecialWildSpawnConfig.DATA.levelRangeParadox);
        addBucket(buckets, islanderRoll ? "islander ultra beast" : "ultra beast", islanderRoll ? SpecialWildSpawnConfig.DATA.islanderUltraBeastChancePerCheck : SpecialWildSpawnConfig.DATA.ultraBeastChancePerCheck, islanderRoll ? SpecialWildSpawnConfig.DATA.islanderUltraBeastSpawns : SpecialWildSpawnConfig.DATA.ultraBeastSpawns, SpecialWildSpawnConfig.DATA.levelRangeUltraBeast);
        if (buckets.isEmpty()) return null;

        double total = 0.0D;
        for (SpawnBucket bucket : buckets) total += bucket.weight;
        double roll = RANDOM.nextDouble() * total;
        for (SpawnBucket bucket : buckets) {
            roll -= bucket.weight;
            if (roll <= 0.0D) return bucket;
        }
        return buckets.get(0);
    }

    private static void addBucket(List<SpawnBucket> buckets, String type, double weight, List<SpecialWildSpawnConfig.SpawnEntry> entries, String levelRange) {
        if (entries == null || entries.isEmpty()) return;
        buckets.add(new SpawnBucket(type, Math.max(0.01D, weight), entries, levelRange));
    }

    private static SpawnResult trySpawnFor(ServerPlayer player, SpawnBucket bucket, boolean forced) {
        ServerLevel level = player.serverLevel();
        boolean islanderLevel = isIslanderSpecialSpawnLevel(level);
        int attempts = forced ? 160 : (islanderLevel ? 80 : 35);

        List<SpecialWildSpawnConfig.SpawnEntry> valid = matchingEntries(level, bucket.entries);
        if (valid.isEmpty() && (forced || islanderLevel)) {
            valid = matchingEntriesIgnoringTime(bucket.entries);
        }
        if (valid.isEmpty()) return null;

        for (int attempt = 0; attempt < attempts; attempt++) {
            BlockPos pos = randomSpawnPos(level, player.blockPosition(), islanderLevel, forced, attempt);
            if (pos == null) continue;
            forceLoadSpawnChunk(level, pos);
            if (!isAllowedSpawnPosition(player, level, pos)) {
                BlockPos fallback = findAllowedPositionNearPlayer(player, level, pos, forced);
                if (fallback == null) continue;
                pos = fallback;
                forceLoadSpawnChunk(level, pos);
            }
            if ((forced || islanderLevel) && !isSafeSpawnSpace(level, pos)) {
                pos = prepareRobustSpawnSpace(level, pos, islanderLevel);
            }
            if (pos == null || !isSafeSpawnSpace(level, pos)) continue;

            SpecialWildSpawnConfig.SpawnEntry picked = pickWeighted(valid);
            if (picked == null) continue;

            int pokemonLevel = pickLevel(bucket.levelRange);

            // Use a direct Cobblemon entity spawn first instead of going through the normal spawn action/pool.
            // This keeps special spawns independent from the player's nearby Cobblemon spawn cap.
            boolean spawned = spawnDirectlyIgnoringNearbyLimit(level, pos, picked.species, pokemonLevel);
            if (!spawned) {
                // Fallback for API changes: command spawning is still forced, but some Cobblemon versions/addons
                // can route command spawns through extra checks. Direct spawn above is the preferred path.
                spawned = spawnViaCobblemonCommand(player.getServer(), level, pos, picked.species, pokemonLevel);
            }

            if (spawned) {
                return new SpawnResult(bucket.type, picked.species, level, pos);
            }
        }
        return null;
    }

    private static void markSpawned(String type, String species, boolean rareEvent, boolean islanderRoll) {
        long now = System.currentTimeMillis();
        if (islanderRoll) {
            state.islanderLastSpawnEpochMillis = now;
            state.islanderLastSpawnType = type == null ? "" : type;
            state.islanderLastSpawnSpecies = species == null ? "" : species;
            state.islanderLastSpawnWasRareTripleEvent = rareEvent;
        } else {
            state.lastSpawnEpochMillis = now;
            state.lastSpawnType = type == null ? "" : type;
            state.lastSpawnSpecies = species == null ? "" : species;
            state.lastSpawnWasRareTripleEvent = rareEvent;
        }
        saveState();
    }

    private static BlockPos randomSpawnPos(ServerLevel level, BlockPos origin, boolean islanderLevel, boolean forced, int attempt) {
        int configuredMin = Math.max(1, SpecialWildSpawnConfig.DATA.minDistanceFromPlayer);
        int configuredMax = Math.max(configuredMin + 1, SpecialWildSpawnConfig.DATA.maxDistanceFromPlayer);
        int min = (forced || islanderLevel) ? 1 : Math.max(8, configuredMin);
        int max = Math.max(min + 1, Math.max(configuredMax, (forced || islanderLevel) ? 32 : configuredMax));

        // Deterministic first attempts: directly around the player. This makes force-spawn
        // reliable in small island territories instead of only trying random far positions.
        if (attempt < 16) {
            int[][] offsets = {
                    {2,0},{-2,0},{0,2},{0,-2},{3,3},{3,-3},{-3,3},{-3,-3},
                    {5,0},{-5,0},{0,5},{0,-5},{8,0},{-8,0},{0,8},{0,-8}
            };
            int[] off = offsets[attempt];
            BlockPos base = new BlockPos(origin.getX() + off[0], origin.getY(), origin.getZ() + off[1]);
            BlockPos found = bestSurfaceAt(level, base, forced || islanderLevel);
            if (found != null) return found;
        }

        // Random attempts with several Y strategies because forests/snowy forests often fail
        // when the first top position is leaves, snow layers, or an awkward tree canopy.
        double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
        int dist = min + RANDOM.nextInt(Math.max(1, max - min + 1));
        int x = origin.getX() + (int)Math.round(Math.cos(angle) * dist);
        int z = origin.getZ() + (int)Math.round(Math.sin(angle) * dist);
        BlockPos base = new BlockPos(x, origin.getY(), z);
        return bestSurfaceAt(level, base, forced || islanderLevel);
    }

    private static BlockPos bestSurfaceAt(ServerLevel level, BlockPos base, boolean allowWorldBorderBypass) {
        for (int scan = 0; scan < 5; scan++) {
            BlockPos top = switch (scan) {
                case 0 -> level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
                case 1 -> level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, base);
                case 2 -> level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, base);
                default -> findOpenGroundNearY(level, base, scan == 3 ? 48 : 96);
            };
            if (top == null) continue;
            forceLoadSpawnChunk(level, top);
            if (!allowWorldBorderBypass && !level.getWorldBorder().isWithinBounds(top)) continue;
            if (isSafeSpawnSpace(level, top)) return top;
            if (allowWorldBorderBypass) {
                BlockPos prepared = prepareRobustSpawnSpace(level, top, true);
                if (prepared != null) return prepared;
            }
        }
        return null;
    }

    private static BlockPos findOpenGroundNearY(ServerLevel level, BlockPos base, int radiusY) {
        int minY = Math.max(level.getMinBuildHeight() + 1, base.getY() - radiusY);
        int maxY = Math.min(level.getMaxBuildHeight() - 3, base.getY() + radiusY);
        for (int y = maxY; y >= minY; y--) {
            BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
            if (isSafeSpawnSpace(level, pos)) return pos;
        }
        return null;
    }

    private static boolean isSafeSpawnSpace(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (!Level.isInSpawnableBounds(pos)) return false;
        if (!level.getBlockState(pos.below()).isSolid()) return false;
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) return false;
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private static void forceLoadSpawnChunk(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        try {
            level.getChunk(pos);
        } catch (Throwable ignored) {
        }
    }

    private static BlockPos prepareRobustSpawnSpace(ServerLevel level, BlockPos pos, boolean canEdit) {
        if (level == null || pos == null || !Level.isInSpawnableBounds(pos)) return null;
        if (!canEdit) return isSafeSpawnSpace(level, pos) ? pos : null;

        try {
            // Never replace containers/valuable blocks. This is only meant to fix grass, snow,
            // leaves, tall grass, bad territory spawn columns, and other harmless obstructions.
            if (!level.getBlockState(pos).isAir() && level.getBlockState(pos).getDestroySpeed(level, pos) < 0) return null;
            if (!level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above()).getDestroySpeed(level, pos.above()) < 0) return null;

            if (!level.getBlockState(pos.below()).isSolid() || !level.getFluidState(pos.below()).isEmpty()) {
                level.setBlock(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
            if (!level.getFluidState(pos).isEmpty() || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            if (!level.getFluidState(pos.above()).isEmpty() || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        } catch (Throwable ignored) {
        }

        return isSafeSpawnSpace(level, pos) ? pos : null;
    }

    private static BlockPos findAllowedPositionNearPlayer(ServerPlayer player, ServerLevel level, BlockPos original, boolean forced) {
        if (!forced && !isIslanderSpecialSpawnLevel(level)) return null;
        if (isAllowedSpawnPosition(player, level, original)) return original;
        BlockPos origin = player.blockPosition();
        for (int r = 1; r <= 32; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos candidate = bestSurfaceAt(level, new BlockPos(origin.getX() + dx, origin.getY(), origin.getZ() + dz), true);
                    if (candidate == null) continue;
                    if (isAllowedSpawnPosition(player, level, candidate)) return candidate;
                }
            }
        }
        return null;
    }

    private static List<SpecialWildSpawnConfig.SpawnEntry> matchingEntries(ServerLevel level, List<SpecialWildSpawnConfig.SpawnEntry> entries) {
        List<SpecialWildSpawnConfig.SpawnEntry> valid = new ArrayList<>();
        String time = timeName(level);
        for (SpecialWildSpawnConfig.SpawnEntry entry : entries) {
            if (entry == null || entry.species == null || entry.species.isBlank()) continue;
            if (entry.times != null && !entry.times.isEmpty() && entry.times.stream().noneMatch(t -> t != null && t.equalsIgnoreCase(time))) continue;
            valid.add(entry);
        }
        return valid;
    }

    private static List<SpecialWildSpawnConfig.SpawnEntry> matchingEntriesIgnoringTime(List<SpecialWildSpawnConfig.SpawnEntry> entries) {
        List<SpecialWildSpawnConfig.SpawnEntry> valid = new ArrayList<>();
        if (entries == null) return valid;
        for (SpecialWildSpawnConfig.SpawnEntry entry : entries) {
            if (entry == null || entry.species == null || entry.species.isBlank()) continue;
            valid.add(entry);
        }
        return valid;
    }

    private static String timeName(ServerLevel level) {
        long t = level.getDayTime() % 24000L;
        if (t >= 23000 || t < 1000) return "dawn";
        if (t >= 1000 && t < 12000) return "day";
        if (t >= 12000 && t < 14000) return "dusk";
        return "night";
    }

    private static SpecialWildSpawnConfig.SpawnEntry pickWeighted(List<SpecialWildSpawnConfig.SpawnEntry> entries) {
        double total = 0.0D;
        for (SpecialWildSpawnConfig.SpawnEntry e : entries) total += Math.max(0.01D, e.weight);
        double roll = RANDOM.nextDouble() * total;
        for (SpecialWildSpawnConfig.SpawnEntry e : entries) {
            roll -= Math.max(0.01D, e.weight);
            if (roll <= 0) return e;
        }
        return entries.isEmpty() ? null : entries.get(0);
    }

    private static int pickLevel(String range) {
        try {
            String[] p = (range == null ? "50-70" : range).replace(" ", "").split("-");
            int min = Integer.parseInt(p[0]);
            int max = p.length > 1 ? Integer.parseInt(p[1]) : min;
            if (max < min) { int tmp = min; min = max; max = tmp; }
            return Math.max(1, Math.min(100, min + RANDOM.nextInt(max - min + 1)));
        } catch (Exception ignored) { return 60; }
    }

    private static boolean spawnDirectlyIgnoringNearbyLimit(ServerLevel level, BlockPos pos, String species, int pokemonLevel) {
        try {
            if (level == null || pos == null || species == null || species.isBlank()) return false;
            if (!Level.isInSpawnableBounds(pos)) return false;

            String clean = sanitize(species);
            String propertiesText = clean + " lvl=" + Math.max(1, Math.min(100, pokemonLevel));

            PokemonProperties properties = PokemonProperties.Companion.parse(propertiesText);
            if (properties.getSpecies() == null) {
                propertiesText = "species=cobblemon:" + clean + " lvl=" + Math.max(1, Math.min(100, pokemonLevel));
                properties = PokemonProperties.Companion.parse(propertiesText);
            }
            if (properties.getSpecies() == null) return false;

            PokemonEntity entity = properties.createEntity(level, null);
            entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, entity.getYRot(), entity.getXRot());
            entity.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);

            if (!level.addFreshEntity(entity)) return false;

            long expiresAt = System.currentTimeMillis() + SPECIAL_DESPAWN_MILLIS;
            markSpecialSpawnEntity(entity, expiresAt);
            return true;
        } catch (Exception e) {
            System.err.println("[ChampUtils] Direct special wild spawn failed for " + species + " level " + pokemonLevel + ". Falling back to command spawn.");
            e.printStackTrace();
            return false;
        }
    }

    @Deprecated
    private static Object invokePokemonSendOut(Object pokemon, ServerLevel level, Vec3 spawnVec) throws Exception {
        for (Method method : pokemon.getClass().getMethods()) {
            if (!method.getName().equals("sendOut")) continue;

            Class<?>[] params = method.getParameterTypes();
            if (params.length < 2) continue;
            if (!params[0].isAssignableFrom(level.getClass())) continue;
            if (!params[1].isAssignableFrom(spawnVec.getClass())) continue;

            Object[] args = new Object[params.length];
            args[0] = level;
            args[1] = spawnVec;

            for (int i = 2; i < params.length; i++) {
                args[i] = defaultSendOutArgument(params[i]);
            }

            return method.invoke(pokemon, args);
        }

        return null;
    }

    private static Object defaultSendOutArgument(Class<?> paramType) {
        if (paramType == boolean.class) return false;
        if (paramType == int.class) return 0;
        if (paramType == long.class) return 0L;
        if (paramType == float.class) return 0F;
        if (paramType == double.class) return 0D;

        if (paramType.isInterface()) {
            return Proxy.newProxyInstance(
                    paramType.getClassLoader(),
                    new Class<?>[]{paramType},
                    (proxy, method, args) -> {
                        if ("toString".equals(method.getName())) return "ChampUtilsSpecialSpawnCallback";
                        try {
                            Class<?> unit = Class.forName("kotlin.Unit");
                            return unit.getField("INSTANCE").get(null);
                        } catch (Exception ignored) {
                            return null;
                        }
                    }
            );
        }

        return null;
    }

    private static boolean spawnViaCobblemonCommand(MinecraftServer server, ServerLevel level, BlockPos pos, String species, int pokemonLevel) {
        try {
            Set<UUID> before = new java.util.HashSet<>();
            for (Entity entity : level.getEntities(null, new net.minecraft.world.phys.AABB(pos).inflate(24.0D))) {
                before.add(entity.getUUID());
            }

            String clean = sanitize(species);
            CommandSourceStack source = server.createCommandSourceStack()
                    .withLevel(level)
                    .withPosition(new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D))
                    .withRotation(Vec2.ZERO)
                    .withPermission(4)
                    .withSuppressedOutput();

            String command = "spawnpokemonat " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + clean + " lvl=" + pokemonLevel;
            server.getCommands().performPrefixedCommand(source, command);

            long expiresAt = System.currentTimeMillis() + SPECIAL_DESPAWN_MILLIS;

            boolean foundNewSpawn = false;
            for (Entity entity : level.getEntities(null, new net.minecraft.world.phys.AABB(pos).inflate(24.0D))) {
                if (before.contains(entity.getUUID())) continue;

                markSpecialSpawnEntity(entity, expiresAt);
                foundNewSpawn = true;
            }

            return foundNewSpawn;
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to spawn special wild Pokémon: " + species);
            e.printStackTrace();
            return false;
        }
    }

    private static void announce(MinecraftServer server, String type, String species, ServerLevel level, BlockPos pos) {
        boolean broadcast = type.equals("legendary") ? SpecialWildSpawnConfig.DATA.broadcastLegendarySpawns : SpecialWildSpawnConfig.DATA.broadcastParadoxAndUltraBeastSpawns;
        if (!broadcast) return;
        String name = pretty(species);
        String biome = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(level.getBiome(pos).value()).toString();
        Component msg = Component.literal("§6A wild " + name + " has appeared! §7(" + biome + ") §e[X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ() + "] §cDespawns in 15 minutes!");

        if (isIslanderSpecialSpawnLevel(level) && SpecialWildSpawnConfig.DATA.islanderOnlyNotifyIslanders) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (PlayerProfileManager.isIslander(player)) player.sendSystemMessage(msg);
            }
            return;
        }

        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    private static void announceRareTripleEvent(MinecraftServer server, List<SpawnResult> results) {
        String message = SpecialWildSpawnConfig.DATA.rareTripleSpawnEventMessage;
        if (message == null || message.isBlank()) {
            message = "§5§lA COSMIC RIFT HAS OPENED! §dThree special Pokémon have appeared across the world!";
        }

        boolean islanderOnly = results.stream().allMatch(r -> isIslanderSpecialSpawnLevel(r.level))
                && SpecialWildSpawnConfig.DATA.islanderOnlyNotifyIslanders;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(message));
        lines.add(Component.literal("§d§l★ §fThis is a §51% super rare event§f! Hunt them down before someone else does! §d§l★"));

        for (SpawnResult result : results) {
            String name = pretty(result.species);
            String biome = result.level.registryAccess().registryOrThrow(Registries.BIOME).getKey(result.level.getBiome(result.pos).value()).toString();
            lines.add(Component.literal("§7 - §6" + name + " §7appeared in §f" + biome + " §e[X: " + result.pos.getX() + ", Y: " + result.pos.getY() + ", Z: " + result.pos.getZ() + "] §c(15 minute despawn)"));
        }

        if (islanderOnly) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (PlayerProfileManager.isIslander(player)) {
                    for (Component line : lines) player.sendSystemMessage(line);
                }
            }
            return;
        }

        for (Component line : lines) server.getPlayerList().broadcastSystemMessage(line, false);
    }

    private static boolean isDisabledDimension(ServerLevel level) {
        String id = level.dimension().location().toString();
        for (String d : SpecialWildSpawnConfig.DATA.disabledDimensions) if (id.equalsIgnoreCase(d)) return true;
        return false;
    }

    private static boolean isEligibleSpecialSpawnPlayer(ServerPlayer player, boolean islanderRoll) {
        return eligibilityFailureReason(player, islanderRoll) == null;
    }

    private static String eligibilityFailureReason(ServerPlayer player, boolean islanderRoll) {
        if (player == null) return "No player was found.";
        if (player.serverLevel() == null) return "Player has no loaded world.";
        ServerLevel level = player.serverLevel();
        String dimensionId = level.dimension().location().toString();
        if (isDisabledDimension(level)) return "This dimension is disabled for special spawns: " + dimensionId;

        boolean islanderLevel = isIslanderSpecialSpawnLevel(level);
        boolean playerIsIslander = PlayerProfileManager.isIslander(player);

        if (islanderRoll) {
            if (!islanderLevel) return "Islander special spawns can only roll in islander worlds.";
            if (!SpecialWildSpawnConfig.DATA.islanderSpecialSpawnsEnabled) return "Islander special spawns are disabled in special_wild_spawns.json.";
            if (!playerIsIslander) return "Only Islander profiles can force Islander special spawns.";
            long required = Math.max(0L, SpecialWildSpawnConfig.DATA.islanderMinimumProfilePlaytimeSeconds);
            if (!ProfilePlaytimeManager.hasAtLeastPlaytime(player, required)) return "This Islander profile needs more profile playtime before Islander special spawns unlock.";
            return null;
        }

        if (islanderLevel) return "Normal special spawns cannot spawn in Islander worlds.";
        if (playerIsIslander) return "Islander profiles use the separate Islander special spawn timer/pool.";

        // Do not require the world to be registered as an ExplorationWorldManager world. Some normal
        // gameplay worlds are Multiworld dimensions that are not active RTP entries. Territory checks
        // still prevent normal special spawns from landing inside player/guild territories.
        return null;
    }

    private static boolean isAllowedSpawnPosition(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return false;

        TerritoryRepository.Territory territory = TerritoryRepository.findAt(level, pos);
        boolean islanderLevel = isIslanderSpecialSpawnLevel(level);

        if (islanderLevel) {
            // Islander special spawns are the only special spawns allowed in Islander worlds.
            // Prefer Islander-owned territories, but do not hard-fail if the repository lookup is
            // temporarily stale after teleport/world creation. A real Islander player standing in
            // an Islander world is enough for force/natural special spawns to choose a nearby spot.
            if (!PlayerProfileManager.isIslander(player)) return false;
            return territory == null || IslanderProfileManager.isIslanderTerritory(territory);
        }

        // Normal special spawns are allowed in normal non-Islander gameplay worlds, but never
        // inside any territory. Islander territory spawning is handled by the islander branch above.
        return territory == null;
    }

    private static boolean isIslanderSpecialSpawnLevel(ServerLevel level) {
        if (level == null) return false;
        String prefix = SpecialWildSpawnConfig.DATA.islanderWorldPrefix;
        if (prefix == null || prefix.isBlank()) prefix = "islander_";
        String path = level.dimension().location().getPath().toLowerCase(Locale.ROOT);
        return IslanderProfileManager.isIslanderWorld(level) || path.startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private static void ensureStateLoaded() {
        if (stateLoaded) return;
        stateLoaded = true;
        try {
            if (!STATE_FILE.getParentFile().exists()) STATE_FILE.getParentFile().mkdirs();
            if (!STATE_FILE.exists()) {
                state = new State();
                saveState();
                return;
            }
            try (FileReader reader = new FileReader(STATE_FILE)) {
                State loaded = GSON.fromJson(reader, State.class);
                state = loaded == null ? new State() : loaded;
            }
        } catch (Exception e) {
            state = new State();
            e.printStackTrace();
        }
    }

    private static void saveState() {
        try {
            if (!STATE_FILE.getParentFile().exists()) STATE_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(STATE_FILE)) {
                GSON.toJson(state, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String sanitize(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int c = v.lastIndexOf(':');
        if (c >= 0 && c + 1 < v.length()) v = v.substring(c + 1);
        return v.replace(' ', '_').replace('-', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static String pretty(String raw) {
        String[] parts = sanitize(raw).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.length() > 1 ? p.substring(1) : "");
        }
        return sb.toString();
    }

    public static final class ForceSpawnResult {
        public final boolean success;
        public final String message;

        private ForceSpawnResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ForceSpawnResult ok(String type, String species, BlockPos pos) {
            return new ForceSpawnResult(true, "Forced a " + (type == null ? "special" : type) + " wild spawn: " + pretty(species) + " near X " + pos.getX() + ", Y " + pos.getY() + ", Z " + pos.getZ() + ".");
        }

        public static ForceSpawnResult fail(String reason) {
            return new ForceSpawnResult(false, "Could not force a special wild spawn here: " + reason);
        }
    }

    private record SpawnBucket(String type, double weight, List<SpecialWildSpawnConfig.SpawnEntry> entries, String levelRange) {}
    private record SpawnResult(String type, String species, ServerLevel level, BlockPos pos) {}

    private static final class State {
        long lastSpawnEpochMillis = 0L;
        String lastSpawnType = "";
        String lastSpawnSpecies = "";
        boolean lastSpawnWasRareTripleEvent = false;
        long islanderLastSpawnEpochMillis = 0L;
        String islanderLastSpawnType = "";
        String islanderLastSpawnSpecies = "";
        boolean islanderLastSpawnWasRareTripleEvent = false;
    }
}
