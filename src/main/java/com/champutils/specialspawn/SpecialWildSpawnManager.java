package com.champutils.specialspawn;

import com.champutils.exploration.ExplorationWorldManager;

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

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.removeIf(p -> p == null || p.isSpectator() || isDisabledDimension(p.serverLevel()) || !ExplorationWorldManager.isOverworldGameplayLevel(p.serverLevel()));
        if (players.isEmpty()) return;

        double chance = currentGlobalChancePerCheck(intervalTicks);
        if (RANDOM.nextDouble() >= chance) return;

        Collections.shuffle(players, RANDOM);
        boolean rareTripleEvent = SpecialWildSpawnConfig.DATA.rareTripleSpawnEventEnabled
                && RANDOM.nextDouble() < Math.max(0.0D, Math.min(1.0D, SpecialWildSpawnConfig.DATA.rareTripleSpawnEventChance));

        if (rareTripleEvent) {
            runRareTripleSpawnEvent(server, players);
            return;
        }

        ServerPlayer player = players.get(0);
        SpawnBucket bucket = pickBucket();
        if (bucket == null) return;

        SpawnResult result = trySpawnFor(player, bucket);
        if (result == null) {
            for (ServerPlayer fallback : players) {
                if (fallback == player) continue;
                result = trySpawnFor(fallback, bucket);
                if (result != null) break;
            }
        }
        if (result != null) {
            markSpawned(result.type, result.species, false);
            announce(server, result.type, result.species, result.level, result.pos);
        }
    }


    private static void runRareTripleSpawnEvent(MinecraftServer server, List<ServerPlayer> players) {
        int availableSlots = Math.max(0, SpecialWildSpawnConfig.DATA.maxAliveSpecialWildPokemon - tracked.size());
        int wanted = Math.max(1, SpecialWildSpawnConfig.DATA.rareTripleSpawnEventSpawnCount);
        int targetCount = Math.min(wanted, Math.min(players.size(), availableSlots));
        if (targetCount <= 0) return;

        List<SpawnResult> results = new ArrayList<>();
        Set<UUID> usedPlayers = new HashSet<>();
        for (ServerPlayer player : players) {
            if (results.size() >= targetCount) break;
            if (usedPlayers.contains(player.getUUID())) continue;

            SpawnBucket bucket = pickBucket();
            if (bucket == null) continue;

            SpawnResult result = trySpawnFor(player, bucket);
            if (result != null) {
                results.add(result);
                usedPlayers.add(player.getUUID());
            }
        }

        if (results.isEmpty()) return;

        SpawnResult last = results.get(results.size() - 1);
        markSpawned(last.type, last.species, true);
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
        ensureStateLoaded();
        return Math.max(0L, state.lastSpawnEpochMillis);
    }

    public static String formatLastSpawnAgo() {
        long last = getLastSpawnEpochMillis();
        if (last <= 0L) return "Never";
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - last);
        long totalMinutes = elapsedMillis / 60000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours + "h " + minutes + "m ago";
    }

    private static double currentGlobalChancePerCheck(int intervalTicks) {
        double targetMinutes = Math.max(1.0D, SpecialWildSpawnConfig.DATA.targetAverageSpawnMinutes);
        double targetTicks = targetMinutes * 60.0D * 20.0D;
        double base = Math.max(0.000001D, Math.min(1.0D, intervalTicks / targetTicks));

        long last = state.lastSpawnEpochMillis;
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
        return Math.max(0.0D, Math.min(1.0D, base * multiplier));
    }

    private static SpawnBucket pickBucket() {
        List<SpawnBucket> buckets = new ArrayList<>();
        addBucket(buckets, "legendary", SpecialWildSpawnConfig.DATA.legendaryChancePerCheck, SpecialWildSpawnConfig.DATA.legendarySpawns, SpecialWildSpawnConfig.DATA.levelRangeLegendary);
        addBucket(buckets, "paradox", SpecialWildSpawnConfig.DATA.paradoxChancePerCheck, SpecialWildSpawnConfig.DATA.paradoxSpawns, SpecialWildSpawnConfig.DATA.levelRangeParadox);
        addBucket(buckets, "ultra beast", SpecialWildSpawnConfig.DATA.ultraBeastChancePerCheck, SpecialWildSpawnConfig.DATA.ultraBeastSpawns, SpecialWildSpawnConfig.DATA.levelRangeUltraBeast);
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

    private static SpawnResult trySpawnFor(ServerPlayer player, SpawnBucket bucket) {
        ServerLevel level = player.serverLevel();
        for (int attempt = 0; attempt < 20; attempt++) {
            BlockPos pos = randomSpawnPos(level, player.blockPosition());
            if (pos == null) continue;

            List<SpecialWildSpawnConfig.SpawnEntry> valid = matchingEntries(level, bucket.entries);
            if (valid.isEmpty()) continue;

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

    private static void markSpawned(String type, String species, boolean rareEvent) {
        state.lastSpawnEpochMillis = System.currentTimeMillis();
        state.lastSpawnType = type == null ? "" : type;
        state.lastSpawnSpecies = species == null ? "" : species;
        state.lastSpawnWasRareTripleEvent = rareEvent;
        saveState();
    }

    private static BlockPos randomSpawnPos(ServerLevel level, BlockPos origin) {
        int min = Math.max(8, SpecialWildSpawnConfig.DATA.minDistanceFromPlayer);
        int max = Math.max(min + 1, SpecialWildSpawnConfig.DATA.maxDistanceFromPlayer);
        double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
        int dist = min + RANDOM.nextInt(Math.max(1, max - min));
        int x = origin.getX() + (int)Math.round(Math.cos(angle) * dist);
        int z = origin.getZ() + (int)Math.round(Math.sin(angle) * dist);
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, origin.getY(), z));
        if (!level.hasChunk(new ChunkPos(top).x, new ChunkPos(top).z)) return null;
        if (!level.getWorldBorder().isWithinBounds(top)) return null;
        if (!level.getBlockState(top.below()).isSolid()) return null;
        if (!level.getBlockState(top).isAir() || !level.getBlockState(top.above()).isAir()) return null;
        return top;
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
            String clean = sanitize(species);
            String properties = "species=\"cobblemon:" + clean + "\" level=" + pokemonLevel;

            Class<?> propertiesClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
            Object companion = propertiesClass.getField("Companion").get(null);
            Object parsed = companion.getClass().getMethod("parse", String.class).invoke(companion, properties);
            Object pokemon = parsed.getClass().getMethod("create").invoke(parsed);

            Vec3 spawnVec = new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            Object spawnedEntity = invokePokemonSendOut(pokemon, level, spawnVec);

            if (spawnedEntity instanceof Entity entity) {
                long expiresAt = System.currentTimeMillis() + SPECIAL_DESPAWN_MILLIS;
                markSpecialSpawnEntity(entity, expiresAt);
                return true;
            }

            return false;
        } catch (Exception ignored) {
            // Keep this silent because command fallback below is expected to cover minor Cobblemon API differences.
            return false;
        }
    }

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
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    private static void announceRareTripleEvent(MinecraftServer server, List<SpawnResult> results) {
        String message = SpecialWildSpawnConfig.DATA.rareTripleSpawnEventMessage;
        if (message == null || message.isBlank()) {
            message = "§5§lA COSMIC RIFT HAS OPENED! §dThree special Pokémon have appeared across the world!";
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
        server.getPlayerList().broadcastSystemMessage(Component.literal("§d§l★ §fThis is a §51% super rare event§f! Hunt them down before someone else does! §d§l★"), false);

        for (SpawnResult result : results) {
            String name = pretty(result.species);
            String biome = result.level.registryAccess().registryOrThrow(Registries.BIOME).getKey(result.level.getBiome(result.pos).value()).toString();
            server.getPlayerList().broadcastSystemMessage(Component.literal("§7 - §6" + name + " §7appeared in §f" + biome + " §e[X: " + result.pos.getX() + ", Y: " + result.pos.getY() + ", Z: " + result.pos.getZ() + "] §c(15 minute despawn)"), false);
        }
    }

    private static boolean isDisabledDimension(ServerLevel level) {
        String id = level.dimension().location().toString();
        for (String d : SpecialWildSpawnConfig.DATA.disabledDimensions) if (id.equalsIgnoreCase(d)) return true;
        return false;
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

    private record SpawnBucket(String type, double weight, List<SpecialWildSpawnConfig.SpawnEntry> entries, String levelRange) {}
    private record SpawnResult(String type, String species, ServerLevel level, BlockPos pos) {}

    private static final class State {
        long lastSpawnEpochMillis = 0L;
        String lastSpawnType = "";
        String lastSpawnSpecies = "";
        boolean lastSpawnWasRareTripleEvent = false;
    }
}
