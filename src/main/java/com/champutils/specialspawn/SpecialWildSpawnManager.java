package com.champutils.specialspawn;

import com.champutils.debug.ChampDebugManager;
import com.champutils.gym.GymLevelCapUtil;
import com.champutils.profile.IslanderProfileManager;
import com.champutils.profile.IslanderMineManager;
import com.champutils.spawn.SpawnBlockRules;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biome;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashSet;
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
    private static boolean recoveredPersistentTags = false;
    private static final Map<UUID, Long> tracked = new ConcurrentHashMap<>();
    private static final long SPECIAL_DESPAWN_MILLIS = 15L * 60L * 1000L;
    private static final String SPECIAL_TAG = "champutils_special_spawn";
    private static final String SPECIAL_EXPIRES_TAG_PREFIX = "champutils_special_expires_";
    private static final String SPECIAL_TYPE_TAG_PREFIX = "champutils_special_type_";
    private static State state = new State();
    private static boolean stateLoaded = false;
    private static double cashShopChanceBoost = 0.0D;
    private static long cashShopChanceBoostExpiresAt = 0L;
    private static double cashShopParadoxChanceBoost = 0.0D;
    private static long cashShopParadoxChanceBoostExpiresAt = 0L;
    private static double cashShopUltraBeastChanceBoost = 0.0D;
    private static long cashShopUltraBeastChanceBoostExpiresAt = 0L;
    private static int ticksUntilParadoxCheck = 200;
    private static int ticksUntilUltraBeastCheck = 200;
    private static int ticksUntilRiftCheck = 20 * 60 * 60;
    private static final Map<String, Integer> worldLegendaryTimers = new ConcurrentHashMap<>();
    private static final Map<String, Integer> worldParadoxTimers = new ConcurrentHashMap<>();
    private static final Map<String, Integer> worldUltraBeastTimers = new ConcurrentHashMap<>();
    private static UUID lastSpecialSpawnPlayer = null;
    private static UUID lastParadoxSpawnPlayer = null;
    private static UUID lastUltraBeastSpawnPlayer = null;
    // Shared across legendary/mythical-pool, Paradox and Ultra Beast rolls so changing
    // categories cannot immediately target the same player again.
    private static UUID lastHeadlineRareSpawnPlayer = null;
    private static final double PLAYER_PITY_PER_MISSED_ROLL = 0.005D; // +0.5% player priority per missed eligible roll
    private static final double PLAYER_PITY_MAX = 0.50D; // capped at +50% priority weight
    private static final int RECENT_SPECIES_LIMIT = 5;

    private SpecialWildSpawnManager() {}

    private static void debug(String message) {
        ChampDebugManager.log(ChampDebugManager.Category.SPAWNS, "[ChampUtils][SpecialSpawnDebug] " + message);
    }

    public static void tick(MinecraftServer server) {
        if (!SpecialWildSpawnConfig.DATA.enabled) return;
        ensureStateLoaded();
        cleanupTrackedVoidFalls(server);

        ticksUntilCleanup--;
        if (ticksUntilCleanup <= 0) {
            ticksUntilCleanup = 200;
            cleanupTracked(server);
        }

        tickRiftEvents(server);

        Set<String> activeWorldKeys = activeSpecialSpawnWorldKeys(server);
        pruneWorldTimers(activeWorldKeys);
        for (String worldKey : activeWorldKeys) {
            ServerLevel level = levelByWorldKey(server, worldKey);
            if (level == null) continue;
            boolean islanderRoll = isIslanderSpecialSpawnLevel(level);

            int legendaryInterval = Math.max(20, SpecialWildSpawnConfig.DATA.checkIntervalTicks);
            int legendaryLeft = worldLegendaryTimers.getOrDefault(worldKey, legendaryInterval);
            legendaryLeft--;
            if (legendaryLeft <= 0) {
                worldLegendaryTimers.put(worldKey, legendaryInterval);
                cleanupTracked(server);
                if (tracked.size() < maxAliveTotal()) runSpawnRoll(server, legendaryInterval, islanderRoll, worldKey);
            } else {
                worldLegendaryTimers.put(worldKey, legendaryLeft);
            }

            if (SpecialWildSpawnConfig.DATA.paradoxOnlySpawnsEnabled) {
                int paradoxInterval = Math.max(20, SpecialWildSpawnConfig.DATA.paradoxCheckIntervalTicks);
                int paradoxLeft = worldParadoxTimers.getOrDefault(worldKey, paradoxInterval);
                paradoxLeft--;
                if (paradoxLeft <= 0) {
                    worldParadoxTimers.put(worldKey, paradoxInterval);
                    cleanupTracked(server);
                    if (activeParadoxOrUltraBeastCount(server) < maxAliveParadoxOrUltraBeast()) runParadoxSpawnRoll(server, paradoxInterval, islanderRoll, worldKey);
                } else {
                    worldParadoxTimers.put(worldKey, paradoxLeft);
                }
            }

            int ultraInterval = Math.max(20, SpecialWildSpawnConfig.DATA.ultraBeastCheckIntervalTicks);
            int ultraLeft = worldUltraBeastTimers.getOrDefault(worldKey, ultraInterval);
            ultraLeft--;
            if (ultraLeft <= 0) {
                worldUltraBeastTimers.put(worldKey, ultraInterval);
                cleanupTracked(server);
                if (activeParadoxOrUltraBeastCount(server) < maxAliveParadoxOrUltraBeast()) runUltraBeastSpawnRoll(server, ultraInterval, islanderRoll, worldKey);
            } else {
                worldUltraBeastTimers.put(worldKey, ultraLeft);
            }
        }
    }



    private static Set<String> activeSpecialSpawnWorldKeys(MinecraftServer server) {
        Set<String> keys = new LinkedHashSet<>();
        if (server == null) return keys;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || player.isSpectator() || player.serverLevel() == null) continue;
            ServerLevel level = player.serverLevel();
            if (!isSpecialSpawnManagedLevel(level)) continue;
            boolean islanderRoll = isIslanderSpecialSpawnLevel(level);
            if (isEligibleSpecialSpawnPlayer(player, islanderRoll)) keys.add(worldKey(level));
        }
        return keys;
    }

    private static void pruneWorldTimers(Set<String> activeWorldKeys) {
        final Set<String> liveWorldKeys = activeWorldKeys == null ? Collections.emptySet() : activeWorldKeys;
        worldLegendaryTimers.keySet().removeIf(key -> !liveWorldKeys.contains(key));
        worldParadoxTimers.keySet().removeIf(key -> !liveWorldKeys.contains(key));
        worldUltraBeastTimers.keySet().removeIf(key -> !liveWorldKeys.contains(key));
    }

    private static ServerLevel levelByWorldKey(MinecraftServer server, String worldKey) {
        if (server == null || worldKey == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (worldKey.equals(worldKey(level))) return level;
        }
        return null;
    }

    private static void tickRiftEvents(MinecraftServer server) {
        if (!SpecialWildSpawnConfig.DATA.riftEventsEnabled) return;
        ticksUntilRiftCheck--;
        if (ticksUntilRiftCheck > 0) return;
        int intervalTicks = Math.max(20, SpecialWildSpawnConfig.DATA.riftCheckIntervalTicks);
        ticksUntilRiftCheck = intervalTicks;

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.removeIf(p -> p == null || p.isSpectator() || !isEligibleSpecialSpawnPlayer(p, isIslanderSpecialSpawnLevel(p.serverLevel())));
        if (players.isEmpty()) return;

        double roll = RANDOM.nextDouble();
        if (roll < clamp01(SpecialWildSpawnConfig.DATA.shinyRiftChancePerCheck)) {
            runRiftEvent(server, players, new SpawnBucket("Shiny Rift", 1.0D, SpecialWildSpawnConfig.DATA.shinyRiftSpawns, SpecialWildSpawnConfig.DATA.shinyRiftLevelRange), true);
            return;
        }
        roll = RANDOM.nextDouble();
        if (roll < clamp01(SpecialWildSpawnConfig.DATA.paradoxRiftChancePerCheck)) {
            runRiftEvent(server, players, pickParadoxBucket(false), false);
            return;
        }
        roll = RANDOM.nextDouble();
        if (roll < clamp01(SpecialWildSpawnConfig.DATA.ultraBeastRiftChancePerCheck)) {
            runRiftEvent(server, players, pickUltraBeastBucket(false), false);
            return;
        }
        roll = RANDOM.nextDouble();
        if (roll < clamp01(SpecialWildSpawnConfig.DATA.legendaryRiftChancePerCheck)) {
            runRiftEvent(server, players, new SpawnBucket("Legendary Rift", 1.0D, SpecialWildSpawnConfig.DATA.legendarySpawns, SpecialWildSpawnConfig.DATA.levelRangeLegendary), false);
            return;
        }
        roll = RANDOM.nextDouble();
        if (roll < clamp01(SpecialWildSpawnConfig.DATA.mythicalRiftChancePerCheck)) {
            runRiftEvent(server, players, new SpawnBucket("Mythical Rift", 1.0D, SpecialWildSpawnConfig.DATA.mythicalSpawns, SpecialWildSpawnConfig.DATA.levelRangeMythical), false);
        }
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static void runRiftEvent(MinecraftServer server, List<ServerPlayer> players, SpawnBucket bucket, boolean shiny) {
        if (server == null || players == null || players.isEmpty() || bucket == null || bucket.entries == null || bucket.entries.isEmpty()) return;
        List<SpawnResult> results = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (player == null || player.serverLevel() == null) continue;
            boolean islanderRoll = isIslanderSpecialSpawnLevel(player.serverLevel());
            if (!isEligibleSpecialSpawnPlayer(player, islanderRoll)) continue;
            SpawnResult result = trySpawnFor(player, bucket, false, shiny, !shiny, true);
            if (result != null) {
                results.add(result);
                markRiftSpawned(result.type, result.species, islanderRoll, shiny);
            }
        }
        if (!results.isEmpty()) announceRiftEvent(server, bucket.type, shiny, results);
    }

    private static void tickParadoxSpawns(MinecraftServer server) {
        if (!SpecialWildSpawnConfig.DATA.paradoxOnlySpawnsEnabled) {
            debug("Paradox timer skipped: paradoxOnlySpawnsEnabled=false");
            return;
        }
        ticksUntilParadoxCheck--;
        if (ticksUntilParadoxCheck > 0) return;
        int intervalTicks = Math.max(20, SpecialWildSpawnConfig.DATA.paradoxCheckIntervalTicks);
        ticksUntilParadoxCheck = intervalTicks;

        cleanupTracked(server);
        int activeRare = activeParadoxOrUltraBeastCount(server);
        if (activeRare >= maxAliveParadoxOrUltraBeast()) {
            debug("Paradox timer skipped: active paradox/ultra beasts=" + activeRare + " maxAllowed=" + maxAliveParadoxOrUltraBeast());
            return;
        }

        debug("Paradox timer rolling. intervalTicks=" + intervalTicks + " activeRare=" + activeRare + " maxAllowed=" + maxAliveParadoxOrUltraBeast());
        runParadoxSpawnRoll(server, intervalTicks, false);
        runParadoxSpawnRoll(server, intervalTicks, true);
    }

    private static void runParadoxSpawnRoll(MinecraftServer server, int intervalTicks, boolean islanderRoll) {
        runParadoxSpawnRoll(server, intervalTicks, islanderRoll, null);
    }

    private static void runParadoxSpawnRoll(MinecraftServer server, int intervalTicks, boolean islanderRoll, String worldKey) {
        if (islanderRoll && !SpecialWildSpawnConfig.DATA.islanderSpecialSpawnsEnabled) {
            debug("Paradox " + (islanderRoll ? "islander" : "normal") + " roll skipped: islanderSpecialSpawnsEnabled=false");
            return;
        }
        int activeRare = activeParadoxOrUltraBeastCount(server);
        if (activeRare >= maxAliveParadoxOrUltraBeast()) {
            debug("Paradox " + (islanderRoll ? "islander" : "normal") + " roll skipped: active paradox/ultra beasts=" + activeRare + " maxAllowed=" + maxAliveParadoxOrUltraBeast());
            return;
        }

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        int beforeEligibility = players.size();
        players.removeIf(p -> p == null || p.isSpectator() || !isEligibleSpecialSpawnPlayer(p, islanderRoll) || (worldKey != null && !worldKey.equals(worldKey(p.serverLevel()))));
        if (players.isEmpty()) {
            debug("Paradox " + (islanderRoll ? "islander" : "normal") + " roll skipped: no eligible players out of " + beforeEligibility);
            return;
        }

        double chance = currentParadoxGlobalChancePerCheck(intervalTicks, islanderRoll, worldKey);
        double roll = RANDOM.nextDouble();
        debug(String.format(Locale.ROOT, "Paradox %s roll: eligible=%d chance=%.4f%% roll=%.4f%% last=%s", (islanderRoll ? "islander" : "normal"), players.size(), chance * 100.0D, roll * 100.0D, islanderRoll ? formatLastIslanderParadoxSpawnAgo() : formatLastNormalParadoxSpawnAgo()));
        if (roll >= chance) {
            recordParadoxPityOutcome(players, Collections.emptySet(), islanderRoll);
            return;
        }

        Collections.shuffle(players, RANDOM);
        excludePreviousRareRecipient(players, lastParadoxSpawnPlayer);
        if (players.isEmpty()) return;

        ServerPlayer player = pickPlayerByParadoxPity(players, islanderRoll);
        if (player == null) return;

        SpawnBucket bucket = pickParadoxBucket(islanderRoll);
        if (bucket == null) {
            debug("Paradox " + (islanderRoll ? "islander" : "normal") + " roll won but failed: pool is empty/null");
            recordParadoxPityOutcome(players, Collections.emptySet(), islanderRoll);
            return;
        }

        debug("Paradox " + (islanderRoll ? "islander" : "normal") + " roll won. Picked player=" + player.getGameProfile().getName() + " poolSize=" + bucket.entries.size());
        SpawnResult result = trySpawnFor(player, bucket, false);
        if (result == null) {
            for (ServerPlayer fallback : players) {
                if (fallback == player) continue;
                result = trySpawnFor(fallback, bucket, false);
                if (result != null) break;
            }
        }

        if (result != null) {
            debug("Paradox spawn succeeded: " + result.species + " at " + result.pos.getX() + "," + result.pos.getY() + "," + result.pos.getZ() + " in " + result.level.dimension().location());
            recordParadoxPityOutcome(players, Set.of(result.playerUuid), islanderRoll);
            lastParadoxSpawnPlayer = result.playerUuid;
            lastHeadlineRareSpawnPlayer = result.playerUuid;
            markParadoxSpawned(result.type, result.species, islanderRoll, worldKey(result.level));
            announce(server, result.type, result.species, result.level, result.pos, result.playerUuid);
        } else {
            debug("Paradox roll won but no spawn landed after attempts. Likely no safe/allowed position or spawn failure. Use /specialspawns forceparadox near yourself to test placement/species.");
            recordParadoxPityOutcome(players, Collections.emptySet(), islanderRoll);
        }
    }


    private static void tickUltraBeastSpawns(MinecraftServer server) {
        ticksUntilUltraBeastCheck--;
        if (ticksUntilUltraBeastCheck > 0) return;
        int intervalTicks = Math.max(20, SpecialWildSpawnConfig.DATA.ultraBeastCheckIntervalTicks);
        ticksUntilUltraBeastCheck = intervalTicks;

        cleanupTracked(server);
        if (activeParadoxOrUltraBeastCount(server) >= maxAliveParadoxOrUltraBeast()) return;

        runUltraBeastSpawnRoll(server, intervalTicks, false);
        runUltraBeastSpawnRoll(server, intervalTicks, true);
    }

    private static void excludePreviousRareRecipient(List<ServerPlayer> players, UUID categoryLastRecipient) {
        if (players == null || players.size() <= 1) return;
        UUID excluded = lastHeadlineRareSpawnPlayer != null ? lastHeadlineRareSpawnPlayer : categoryLastRecipient;
        if (excluded == null) return;
        players.removeIf(player -> player != null && excluded.equals(player.getUUID()));
    }

    private static void runUltraBeastSpawnRoll(MinecraftServer server, int intervalTicks, boolean islanderRoll) {
        runUltraBeastSpawnRoll(server, intervalTicks, islanderRoll, null);
    }

    private static void runUltraBeastSpawnRoll(MinecraftServer server, int intervalTicks, boolean islanderRoll, String worldKey) {
        if (islanderRoll && !SpecialWildSpawnConfig.DATA.islanderSpecialSpawnsEnabled) return;
        if (activeParadoxOrUltraBeastCount(server) >= maxAliveParadoxOrUltraBeast()) return;

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.removeIf(p -> p == null || p.isSpectator() || !isEligibleSpecialSpawnPlayer(p, islanderRoll) || (worldKey != null && !worldKey.equals(worldKey(p.serverLevel()))));
        if (players.isEmpty()) return;

        double chance = currentUltraBeastGlobalChancePerCheck(intervalTicks, islanderRoll, worldKey);
        if (RANDOM.nextDouble() >= chance) {
            recordUltraBeastPityOutcome(players, Collections.emptySet(), islanderRoll);
            return;
        }

        Collections.shuffle(players, RANDOM);
        excludePreviousRareRecipient(players, lastUltraBeastSpawnPlayer);
        if (players.isEmpty()) return;

        ServerPlayer player = pickPlayerByUltraBeastPity(players, islanderRoll);
        if (player == null) return;

        SpawnBucket bucket = pickUltraBeastBucket(islanderRoll);
        if (bucket == null) {
            recordUltraBeastPityOutcome(players, Collections.emptySet(), islanderRoll);
            return;
        }

        SpawnResult result = trySpawnFor(player, bucket, false);
        if (result == null) {
            for (ServerPlayer fallback : players) {
                if (fallback == player) continue;
                result = trySpawnFor(fallback, bucket, false);
                if (result != null) break;
            }
        }

        if (result != null) {
            recordUltraBeastPityOutcome(players, Set.of(result.playerUuid), islanderRoll);
            lastUltraBeastSpawnPlayer = result.playerUuid;
            lastHeadlineRareSpawnPlayer = result.playerUuid;
            markUltraBeastSpawned(result.type, result.species, islanderRoll, worldKey(result.level));
            announce(server, result.type, result.species, result.level, result.pos, result.playerUuid);
        } else {
            recordUltraBeastPityOutcome(players, Collections.emptySet(), islanderRoll);
        }
    }

    private static void runSpawnRoll(MinecraftServer server, int intervalTicks, boolean islanderRoll) {
        runSpawnRoll(server, intervalTicks, islanderRoll, null);
    }

    private static void runSpawnRoll(MinecraftServer server, int intervalTicks, boolean islanderRoll, String worldKey) {
        if (tracked.size() >= maxAliveTotal()) return;

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.removeIf(p -> p == null || p.isSpectator() || !isEligibleSpecialSpawnPlayer(p, islanderRoll) || (worldKey != null && !worldKey.equals(worldKey(p.serverLevel()))));
        if (players.isEmpty()) return;

        double chance = currentGlobalChancePerCheck(intervalTicks, islanderRoll, worldKey);
        if (RANDOM.nextDouble() >= chance) {
            recordPityOutcome(players, Collections.emptySet(), islanderRoll);
            return;
        }

        Collections.shuffle(players, RANDOM);
        excludePreviousRareRecipient(players, lastSpecialSpawnPlayer);
        if (players.isEmpty()) return;
        boolean rareTripleEvent = SpecialWildSpawnConfig.DATA.rareTripleSpawnEventEnabled
                && RANDOM.nextDouble() < Math.max(0.0D, Math.min(1.0D, SpecialWildSpawnConfig.DATA.rareTripleSpawnEventChance));

        if (rareTripleEvent) {
            runRareTripleSpawnEvent(server, players, islanderRoll);
            return;
        }

        ServerPlayer player = pickPlayerByPity(players, islanderRoll);
        if (player == null) return;

        SpawnBucket bucket = pickBucket(islanderRoll);
        if (bucket == null) {
            recordPityOutcome(players, Collections.emptySet(), islanderRoll);
            return;
        }

        SpawnResult result = trySpawnFor(player, bucket, false);
        if (result == null) {
            for (ServerPlayer fallback : players) {
                if (fallback == player) continue;
                result = trySpawnFor(fallback, bucket, false);
                if (result != null) break;
            }
        }
        if (result != null) {
            recordPityOutcome(players, Set.of(result.playerUuid), islanderRoll);
            lastSpecialSpawnPlayer = result.playerUuid;
            lastHeadlineRareSpawnPlayer = result.playerUuid;
            markSpawned(result.type, result.species, false, islanderRoll, worldKey(result.level));
            announce(server, result.type, result.species, result.level, result.pos);
        } else {
            recordPityOutcome(players, Collections.emptySet(), islanderRoll);
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

        recordPityOutcome(List.of(player), Set.of(result.playerUuid), islanderRoll);
        markSpawned(result.type, result.species, false, islanderRoll, worldKey(result.level));
        announce(player.getServer(), result.type, result.species, result.level, result.pos);
        return ForceSpawnResult.ok(result.type, result.species, result.pos);
    }

    public static boolean forceParadoxSpawnFor(ServerPlayer player) {
        return forceParadoxSpawnForResult(player).success;
    }

    public static ForceSpawnResult forceParadoxSpawnForResult(ServerPlayer player) {
        if (player == null) return ForceSpawnResult.fail("No player was found.");
        if (!SpecialWildSpawnConfig.DATA.enabled || !SpecialWildSpawnConfig.DATA.paradoxOnlySpawnsEnabled) return ForceSpawnResult.fail("Paradox wild spawns are disabled in the config.");
        if (player.serverLevel() == null) return ForceSpawnResult.fail("Player has no loaded world.");

        boolean islanderRoll = isIslanderSpecialSpawnLevel(player.serverLevel());
        String eligibilityReason = eligibilityFailureReason(player, islanderRoll);
        if (eligibilityReason != null) return ForceSpawnResult.fail(eligibilityReason);

        SpawnBucket bucket = pickParadoxBucket(islanderRoll);
        if (bucket == null) return ForceSpawnResult.fail("No valid paradox spawn pool exists for this world/profile type.");
        if (activeParadoxOrUltraBeastCount(player.getServer()) >= maxAliveParadoxOrUltraBeast()) return ForceSpawnResult.fail("A Paradox or Ultra Beast is already alive. Wait for it to be caught, defeated, or despawn after 15 minutes.");

        SpawnResult result = forceSpawnParadoxGuaranteed(player, bucket, islanderRoll);
        if (result == null) {
            return ForceSpawnResult.fail("Could not force a Paradox spawn. ChampUtils tried normal placement, an emergency admin-safe spawn pocket, every Paradox species in the pool, direct Cobblemon entity creation, and command fallback. Check the console for [ChampUtils][ParadoxForce] errors; this now means the selected species is not registered/loaded by Cobblemon or another mod is blocking entity creation.");
        }

        recordParadoxPityOutcome(List.of(player), Set.of(result.playerUuid), islanderRoll);
        markParadoxSpawned(result.type, result.species, islanderRoll, worldKey(result.level));
        announce(player.getServer(), result.type, result.species, result.level, result.pos, result.playerUuid);
        return ForceSpawnResult.ok(result.type, result.species, result.pos);
    }

    public static boolean forceUltraBeastSpawnFor(ServerPlayer player) {
        return forceUltraBeastSpawnForResult(player).success;
    }

    public static ForceSpawnResult forceUltraBeastSpawnForResult(ServerPlayer player) {
        if (player == null) return ForceSpawnResult.fail("No player was found.");
        if (!SpecialWildSpawnConfig.DATA.enabled) return ForceSpawnResult.fail("Ultra Beast wild spawns are disabled in the config.");
        if (player.serverLevel() == null) return ForceSpawnResult.fail("Player has no loaded world.");

        boolean islanderRoll = isIslanderSpecialSpawnLevel(player.serverLevel());
        String eligibilityReason = eligibilityFailureReason(player, islanderRoll);
        if (eligibilityReason != null) return ForceSpawnResult.fail(eligibilityReason);

        SpawnBucket bucket = pickUltraBeastBucket(islanderRoll);
        if (bucket == null) return ForceSpawnResult.fail("No valid Ultra Beast spawn pool exists for this world/profile type.");
        if (activeParadoxOrUltraBeastCount(player.getServer()) >= maxAliveParadoxOrUltraBeast()) return ForceSpawnResult.fail("A Paradox or Ultra Beast is already alive. Wait for it to be caught, defeated, or despawn after 15 minutes.");

        SpawnResult result = trySpawnFor(player, bucket, true);
        if (result == null) {
            return ForceSpawnResult.fail("No safe spawn position was found nearby, or the selected Ultra Beast failed to spawn.");
        }

        recordUltraBeastPityOutcome(List.of(player), Set.of(result.playerUuid), islanderRoll);
        markUltraBeastSpawned(result.type, result.species, islanderRoll, worldKey(result.level));
        announce(player.getServer(), result.type, result.species, result.level, result.pos, result.playerUuid);
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

        if (results.isEmpty()) {
            recordPityOutcome(players, Collections.emptySet(), islanderRoll);
            return;
        }

        Set<UUID> spawnedPlayers = new HashSet<>();
        for (SpawnResult result : results) spawnedPlayers.add(result.playerUuid);
        recordPityOutcome(players, spawnedPlayers, islanderRoll);

        SpawnResult last = results.get(results.size() - 1);
        markSpawned(last.type, last.species, true, islanderRoll);
        announceRareTripleEvent(server, results);
    }

    private static void cleanupTrackedVoidFalls(MinecraftServer server) {
        if (server == null || tracked.isEmpty()) return;
        tracked.keySet().removeIf(id -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity == null) continue;
                if (entity.getY() < level.getMinBuildHeight() - 16.0D) {
                    removeSpecialSpawnEntity(entity);
                    debug("Removed special spawn " + id + " after it fell into the void in " + level.dimension().location());
                    return true;
                }
                return false;
            }
            return false;
        });
    }

    public static void cleanupTracked(MinecraftServer server) {
        long now = System.currentTimeMillis();

        // Full-world entity scans are expensive. Recover persistent tags only at startup/rare intervals;
        // the in-memory tracked map remains authoritative between recoveries.
        if (!recoveredPersistentTags) {
            recoveredPersistentTags = true;
            recoverTaggedSpecialSpawns(server);
        }

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


    }

    private static void recoverTaggedSpecialSpawns(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!isTaggedSpecialSpawn(entity)) continue;
                protectSpecialSpawnEntity(entity);
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

    public static boolean isSpecialSpawnEntity(Entity entity) {
        return isTaggedSpecialSpawn(entity);
    }

    public static void protectSpecialSpawnEntity(Entity entity) {
        if (entity == null) return;
        try { entity.setInvulnerable(true); } catch (Throwable ignored) {}
        try { entity.clearFire(); } catch (Throwable ignored) {}
        try { entity.setRemainingFireTicks(0); } catch (Throwable ignored) {}
        if (entity instanceof Mob mob) {
            try { mob.setPersistenceRequired(); } catch (Throwable ignored) {}
        }
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
        markSpecialSpawnEntity(entity, expiresAt, "");
    }

    private static void markSpecialSpawnEntity(Entity entity, long expiresAt, String spawnType) {
        if (entity == null) return;
        entity.addTag(SPECIAL_TAG);
        entity.getTags().stream()
                .filter(tag -> tag != null && (tag.startsWith(SPECIAL_EXPIRES_TAG_PREFIX) || tag.startsWith(SPECIAL_TYPE_TAG_PREFIX)))
                .toList()
                .forEach(entity::removeTag);
        entity.addTag(SPECIAL_EXPIRES_TAG_PREFIX + expiresAt);
        String normalizedType = normalizeSpawnTypeTag(spawnType);
        if (!normalizedType.isBlank()) entity.addTag(SPECIAL_TYPE_TAG_PREFIX + normalizedType);

        protectSpecialSpawnEntity(entity);
        tracked.put(entity.getUUID(), expiresAt);
    }

    private static String normalizeSpawnTypeTag(String spawnType) {
        String lower = spawnType == null ? "" : spawnType.toLowerCase(Locale.ROOT);
        if (lower.contains("paradox")) return "paradox";
        if (lower.contains("ultra")) return "ultrabeast";
        if (lower.contains("legendary") || lower.contains("mythical")) return "legendary";
        return lower.replaceAll("[^a-z0-9_]+", "_");
    }

    private static boolean isParadoxOrUltraBeastSpecial(Entity entity) {
        if (entity == null) return false;
        for (String tag : entity.getTags()) {
            if (tag == null) continue;
            String lower = tag.toLowerCase(Locale.ROOT);
            if (lower.equals(SPECIAL_TYPE_TAG_PREFIX + "paradox") || lower.equals(SPECIAL_TYPE_TAG_PREFIX + "ultrabeast")) return true;
        }
        return false;
    }

    private static int activeParadoxOrUltraBeastCount(MinecraftServer server) {
        if (server == null) return 0;
        int count = 0;
        for (UUID id : tracked.keySet()) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity != null && entity.isAlive() && isParadoxOrUltraBeastSpecial(entity)) { count++; break; }
            }
        }
        return count;
    }

    private static int maxAliveParadoxOrUltraBeast() {
        return 1;
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

    public static long getLastNormalParadoxSpawnEpochMillis() {
        ensureStateLoaded();
        return Math.max(0L, state.lastParadoxSpawnEpochMillis);
    }

    public static long getLastIslanderParadoxSpawnEpochMillis() {
        ensureStateLoaded();
        return Math.max(0L, state.islanderLastParadoxSpawnEpochMillis);
    }

    public static String formatLastNormalParadoxSpawnAgo() {
        return formatAgo(getLastNormalParadoxSpawnEpochMillis());
    }

    public static String formatLastIslanderParadoxSpawnAgo() {
        return formatAgo(getLastIslanderParadoxSpawnEpochMillis());
    }

    public static long getLastNormalUltraBeastSpawnEpochMillis() {
        ensureStateLoaded();
        return Math.max(0L, state.lastUltraBeastSpawnEpochMillis);
    }

    public static long getLastIslanderUltraBeastSpawnEpochMillis() {
        ensureStateLoaded();
        return Math.max(0L, state.islanderLastUltraBeastSpawnEpochMillis);
    }

    public static String formatLastNormalUltraBeastSpawnAgo() {
        return formatAgo(getLastNormalUltraBeastSpawnEpochMillis());
    }

    public static String formatLastIslanderUltraBeastSpawnAgo() {
        return formatAgo(getLastIslanderUltraBeastSpawnEpochMillis());
    }

    public static String formatLastLegendarySpawnAgo(ServerPlayer player) {
        ensureStateLoaded();
        if (player == null || player.serverLevel() == null || !isSpecialSpawnManagedLevel(player.serverLevel())) return "Waiting...";
        return formatAgo(lastLegendaryMillis(isIslanderSpecialSpawnLevel(player.serverLevel()), worldKey(player.serverLevel())));
    }

    public static String formatLastParadoxSpawnAgo(ServerPlayer player) {
        ensureStateLoaded();
        if (player == null || player.serverLevel() == null || !isSpecialSpawnManagedLevel(player.serverLevel())) return "Waiting...";
        return formatAgo(lastParadoxMillis(isIslanderSpecialSpawnLevel(player.serverLevel()), worldKey(player.serverLevel())));
    }

    public static String formatLastUltraBeastSpawnAgo(ServerPlayer player) {
        ensureStateLoaded();
        if (player == null || player.serverLevel() == null || !isSpecialSpawnManagedLevel(player.serverLevel())) return "Waiting...";
        return formatAgo(lastUltraBeastMillis(isIslanderSpecialSpawnLevel(player.serverLevel()), worldKey(player.serverLevel())));
    }

    public static String formatNextLegendaryCheck(ServerPlayer player) {
        if (player == null || player.serverLevel() == null || !isSpecialSpawnManagedLevel(player.serverLevel())) return "Waiting...";
        int seconds = Math.max(0, worldLegendaryTimers.getOrDefault(worldKey(player.serverLevel()), Math.max(20, SpecialWildSpawnConfig.DATA.checkIntervalTicks))) / 20;
        return formatSeconds(seconds);
    }

    public static String formatNextParadoxCheck(ServerPlayer player) {
        if (player == null || player.serverLevel() == null || !isSpecialSpawnManagedLevel(player.serverLevel())) return "Waiting...";
        int seconds = Math.max(0, worldParadoxTimers.getOrDefault(worldKey(player.serverLevel()), Math.max(20, SpecialWildSpawnConfig.DATA.paradoxCheckIntervalTicks))) / 20;
        return formatSeconds(seconds);
    }

    public static String formatNextUltraBeastCheck(ServerPlayer player) {
        if (player == null || player.serverLevel() == null || !isSpecialSpawnManagedLevel(player.serverLevel())) return "Waiting...";
        int seconds = Math.max(0, worldUltraBeastTimers.getOrDefault(worldKey(player.serverLevel()), Math.max(20, SpecialWildSpawnConfig.DATA.ultraBeastCheckIntervalTicks))) / 20;
        return formatSeconds(seconds);
    }

    private static String formatSeconds(long seconds) {
        seconds = Math.max(0L, seconds);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + secs + "s";
        return secs + "s";
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

    public static void activateParadoxCashShopBoost(double amount, long durationMillis) {
        cashShopParadoxChanceBoost = Math.max(cashShopParadoxChanceBoost, Math.max(0.0D, amount));
        cashShopParadoxChanceBoostExpiresAt = Math.max(cashShopParadoxChanceBoostExpiresAt, System.currentTimeMillis() + Math.max(1L, durationMillis));
    }

    public static void deactivateParadoxCashShopBoost() {
        cashShopParadoxChanceBoost = 0.0D;
        cashShopParadoxChanceBoostExpiresAt = 0L;
    }

    public static void activateUltraBeastCashShopBoost(double boostFraction, long durationMillis) {
        cashShopUltraBeastChanceBoost = Math.max(0.0D, boostFraction);
        cashShopUltraBeastChanceBoostExpiresAt = System.currentTimeMillis() + Math.max(1000L, durationMillis);
    }

    public static void deactivateUltraBeastCashShopBoost() {
        cashShopUltraBeastChanceBoost = 0.0D;
        cashShopUltraBeastChanceBoostExpiresAt = 0L;
    }

    private static double activeCashShopChanceBoost() {
        if (cashShopChanceBoostExpiresAt <= System.currentTimeMillis()) {
            cashShopChanceBoost = 0.0D;
            cashShopChanceBoostExpiresAt = 0L;
        }
        return Math.max(0.0D, cashShopChanceBoost);
    }

    private static double activeParadoxCashShopChanceBoost() {
        if (cashShopParadoxChanceBoostExpiresAt <= System.currentTimeMillis()) {
            cashShopParadoxChanceBoost = 0.0D;
            cashShopParadoxChanceBoostExpiresAt = 0L;
        }
        return Math.max(0.0D, cashShopParadoxChanceBoost);
    }

    private static double activeUltraBeastCashShopChanceBoost() {
        if (cashShopUltraBeastChanceBoostExpiresAt <= System.currentTimeMillis()) {
            cashShopUltraBeastChanceBoost = 0.0D;
            cashShopUltraBeastChanceBoostExpiresAt = 0L;
        }
        return Math.max(0.0D, cashShopUltraBeastChanceBoost);
    }


    private static double currentGlobalChancePerCheck(int intervalTicks, boolean islanderRoll) {
        return currentGlobalChancePerCheck(intervalTicks, islanderRoll, null);
    }

    private static WorldSpawnState worldState(String key) {
        ensureStateLoaded();
        if (state.worldStates == null) state.worldStates = new HashMap<>();
        return state.worldStates.computeIfAbsent(key == null ? "" : key, ignored -> new WorldSpawnState());
    }

    private static long lastLegendaryMillis(boolean islanderRoll, String key) {
        if (key == null || key.isBlank()) return islanderRoll ? state.islanderLastSpawnEpochMillis : state.lastSpawnEpochMillis;
        return Math.max(0L, worldState(key).lastLegendarySpawnEpochMillis);
    }

    private static long lastParadoxMillis(boolean islanderRoll, String key) {
        if (key == null || key.isBlank()) return islanderRoll ? state.islanderLastParadoxSpawnEpochMillis : state.lastParadoxSpawnEpochMillis;
        return Math.max(0L, worldState(key).lastParadoxSpawnEpochMillis);
    }

    private static long lastUltraBeastMillis(boolean islanderRoll, String key) {
        if (key == null || key.isBlank()) return islanderRoll ? state.islanderLastUltraBeastSpawnEpochMillis : state.lastUltraBeastSpawnEpochMillis;
        return Math.max(0L, worldState(key).lastUltraBeastSpawnEpochMillis);
    }

    private static double currentGlobalChancePerCheck(int intervalTicks, boolean islanderRoll, String worldKey) {
        double configuredTarget = Math.max(1.0D, islanderRoll ? SpecialWildSpawnConfig.DATA.islanderTargetAverageSpawnMinutes : SpecialWildSpawnConfig.DATA.targetAverageSpawnMinutes);
        double targetMinutes = scaledTargetMinutes(configuredTarget, SpecialWildSpawnConfig.DATA.minimumTargetAverageSpawnMinutes);
        return chancePerCheck(intervalTicks, targetMinutes, lastLegendaryMillis(islanderRoll, worldKey), SpecialWildSpawnConfig.DATA.baseChanceMultiplier, SpecialWildSpawnConfig.DATA.pityChanceIncreasePerTargetWindow, SpecialWildSpawnConfig.DATA.maxPityMultiplier, activeCashShopChanceBoost());
    }

    private static double currentParadoxGlobalChancePerCheck(int intervalTicks, boolean islanderRoll) {
        return currentParadoxGlobalChancePerCheck(intervalTicks, islanderRoll, null);
    }

    private static double currentParadoxGlobalChancePerCheck(int intervalTicks, boolean islanderRoll, String worldKey) {
        double configuredTarget = Math.max(1.0D, islanderRoll ? SpecialWildSpawnConfig.DATA.islanderParadoxTargetAverageSpawnMinutes : SpecialWildSpawnConfig.DATA.paradoxTargetAverageSpawnMinutes);
        double targetMinutes = scaledTargetMinutes(configuredTarget, SpecialWildSpawnConfig.DATA.minimumParadoxTargetAverageSpawnMinutes);
        return chancePerCheck(intervalTicks, targetMinutes, lastParadoxMillis(islanderRoll, worldKey), SpecialWildSpawnConfig.DATA.paradoxBaseChanceMultiplier, SpecialWildSpawnConfig.DATA.paradoxPityChanceIncreasePerTargetWindow, SpecialWildSpawnConfig.DATA.paradoxMaxPityMultiplier, activeParadoxCashShopChanceBoost());
    }

    private static double currentUltraBeastGlobalChancePerCheck(int intervalTicks, boolean islanderRoll) {
        return currentUltraBeastGlobalChancePerCheck(intervalTicks, islanderRoll, null);
    }

    private static double currentUltraBeastGlobalChancePerCheck(int intervalTicks, boolean islanderRoll, String worldKey) {
        double configuredTarget = Math.max(1.0D, islanderRoll ? SpecialWildSpawnConfig.DATA.islanderUltraBeastTargetAverageSpawnMinutes : SpecialWildSpawnConfig.DATA.ultraBeastTargetAverageSpawnMinutes);
        double targetMinutes = scaledTargetMinutes(configuredTarget, SpecialWildSpawnConfig.DATA.minimumUltraBeastTargetAverageSpawnMinutes);
        return chancePerCheck(intervalTicks, targetMinutes, lastUltraBeastMillis(islanderRoll, worldKey), SpecialWildSpawnConfig.DATA.ultraBeastBaseChanceMultiplier, SpecialWildSpawnConfig.DATA.ultraBeastPityChanceIncreasePerTargetWindow, SpecialWildSpawnConfig.DATA.ultraBeastMaxPityMultiplier, activeUltraBeastCashShopChanceBoost());
    }

    private static double chancePerCheck(int intervalTicks, double targetMinutes, long lastSpawnMillis, double baseMultiplier, double pityPerTargetWindow, double maxPityMultiplier, double cashShopBoost) {
        targetMinutes = Math.max(1.0D, targetMinutes);
        double targetTicks = targetMinutes * 60.0D * 20.0D;
        double base = Math.max(0.000001D, Math.min(1.0D, intervalTicks / targetTicks));
        double elapsedTargetWindows = lastSpawnMillis <= 0L ? 0.0D : Math.max(0L, System.currentTimeMillis() - lastSpawnMillis) / (targetMinutes * 60_000.0D);
        double multiplier = baseMultiplier + (elapsedTargetWindows * pityPerTargetWindow);
        multiplier = Math.max(0.01D, Math.min(Math.max(0.01D, maxPityMultiplier), multiplier));
        return Math.max(0.0D, Math.min(1.0D, base * multiplier * (1.0D + Math.max(0.0D, cashShopBoost))));
    }

    private static double scaledTargetMinutes(double configuredTargetMinutes, double minimumTargetMinutes) {
        int online = onlinePlayerCount();
        int startsAt = Math.max(0, SpecialWildSpawnConfig.DATA.playerScalingStartPlayers);
        int maxPlayers = Math.max(startsAt + 1, SpecialWildSpawnConfig.DATA.playerScalingMaxPlayers);
        double configured = Math.max(1.0D, configuredTargetMinutes);
        double minimum = Math.max(1.0D, Math.min(configured, minimumTargetMinutes));
        if (online <= startsAt) return configured;
        double progress = Math.min(1.0D, (online - startsAt) / (double) (maxPlayers - startsAt));
        return configured - ((configured - minimum) * progress);
    }

    private static int onlinePlayerCount() {
        MinecraftServer server = com.champutils.battle.ServerLifecycleBridge.getServer();
        if (server == null) return 0;
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && !player.isSpectator()) count++;
        }
        return count;
    }

    private static int maxAliveTotal() {
        return Math.max(1, SpecialWildSpawnConfig.DATA.maxAliveSpecialWildPokemon)
                + Math.max(1, SpecialWildSpawnConfig.DATA.maxAliveParadoxWildPokemon)
                + Math.max(1, SpecialWildSpawnConfig.DATA.maxAliveUltraBeastWildPokemon);
    }

    public static PityView pityView(ServerPlayer player) {
        ensureStateLoaded();
        if (player == null || player.serverLevel() == null) {
            return new PityView(false, false, "No player/world loaded.", 0, 0.0D, 1.0D, 0.0D, 0.0D, "Never");
        }

        boolean islanderRoll = isIslanderSpecialSpawnLevel(player.serverLevel());
        String failure = eligibilityFailureReason(player, islanderRoll);
        int intervalTicks = Math.max(20, SpecialWildSpawnConfig.DATA.checkIntervalTicks);
        double globalChance = currentGlobalChancePerCheck(intervalTicks, islanderRoll, worldKey(player.serverLevel()));
        PlayerPity pity = pityData(player.getUUID());
        int misses = islanderRoll ? pity.islanderMisses : pity.normalMisses;
        double pityPercent = playerPityPercent(player, islanderRoll);
        double ownWeight = playerPriorityWeight(player, islanderRoll);

        double totalWeight = 0.0D;
        MinecraftServer server = player.getServer();
        if (server != null) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (online == null || online.isSpectator() || !isEligibleSpecialSpawnPlayer(online, islanderRoll) || !worldKey(online.serverLevel()).equals(worldKey(player.serverLevel()))) continue;
                totalWeight += playerPriorityWeight(online, islanderRoll);
            }
        }
        double personalChance = failure == null && totalWeight > 0.0D ? globalChance * (ownWeight / totalWeight) : 0.0D;
        return new PityView(islanderRoll, failure == null, failure == null ? "Eligible" : failure, misses, pityPercent, ownWeight, globalChance, personalChance, islanderRoll ? formatLastIslanderSpawnAgo() : formatLastNormalSpawnAgo());
    }

    public static PityView paradoxPityView(ServerPlayer player) {
        ensureStateLoaded();
        if (player == null || player.serverLevel() == null) {
            return new PityView(false, false, "No player/world loaded.", 0, 0.0D, 1.0D, 0.0D, 0.0D, "Never");
        }

        boolean islanderRoll = isIslanderSpecialSpawnLevel(player.serverLevel());
        String failure = eligibilityFailureReason(player, islanderRoll);
        int intervalTicks = Math.max(20, SpecialWildSpawnConfig.DATA.paradoxCheckIntervalTicks);
        double globalChance = currentParadoxGlobalChancePerCheck(intervalTicks, islanderRoll, worldKey(player.serverLevel()));
        PlayerPity pity = pityData(player.getUUID());
        int misses = islanderRoll ? pity.islanderParadoxMisses : pity.normalParadoxMisses;
        double pityPercent = playerParadoxPityPercent(player, islanderRoll);
        double ownWeight = playerParadoxPriorityWeight(player, islanderRoll);

        double totalWeight = 0.0D;
        MinecraftServer server = player.getServer();
        if (server != null) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (online == null || online.isSpectator() || !isEligibleSpecialSpawnPlayer(online, islanderRoll) || !worldKey(online.serverLevel()).equals(worldKey(player.serverLevel()))) continue;
                totalWeight += playerParadoxPriorityWeight(online, islanderRoll);
            }
        }
        double personalChance = failure == null && totalWeight > 0.0D ? globalChance * (ownWeight / totalWeight) : 0.0D;
        return new PityView(islanderRoll, failure == null, failure == null ? "Eligible" : failure, misses, pityPercent, ownWeight, globalChance, personalChance, islanderRoll ? formatLastIslanderParadoxSpawnAgo() : formatLastNormalParadoxSpawnAgo());
    }

    public static PityView ultraBeastPityView(ServerPlayer player) {
        ensureStateLoaded();
        if (player == null || player.serverLevel() == null) {
            return new PityView(false, false, "No player/world loaded.", 0, 0.0D, 1.0D, 0.0D, 0.0D, "Never");
        }

        boolean islanderRoll = isIslanderSpecialSpawnLevel(player.serverLevel());
        String failure = eligibilityFailureReason(player, islanderRoll);
        int intervalTicks = Math.max(20, SpecialWildSpawnConfig.DATA.ultraBeastCheckIntervalTicks);
        double globalChance = currentUltraBeastGlobalChancePerCheck(intervalTicks, islanderRoll, worldKey(player.serverLevel()));
        PlayerPity pity = pityData(player.getUUID());
        int misses = islanderRoll ? pity.islanderUltraBeastMisses : pity.normalUltraBeastMisses;
        double pityPercent = playerUltraBeastPityPercent(player, islanderRoll);
        double ownWeight = playerUltraBeastPriorityWeight(player, islanderRoll);

        double totalWeight = 0.0D;
        MinecraftServer server = player.getServer();
        if (server != null) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (online == null || online.isSpectator() || !isEligibleSpecialSpawnPlayer(online, islanderRoll) || !worldKey(online.serverLevel()).equals(worldKey(player.serverLevel()))) continue;
                totalWeight += playerUltraBeastPriorityWeight(online, islanderRoll);
            }
        }
        double personalChance = failure == null && totalWeight > 0.0D ? globalChance * (ownWeight / totalWeight) : 0.0D;
        return new PityView(islanderRoll, failure == null, failure == null ? "Eligible" : failure, misses, pityPercent, ownWeight, globalChance, personalChance, islanderRoll ? formatLastIslanderUltraBeastSpawnAgo() : formatLastNormalUltraBeastSpawnAgo());
    }

    private static PlayerPity pityData(UUID uuid) {
        ensureStateLoaded();
        if (state.playerPity == null) state.playerPity = new HashMap<>();
        if (state.worldStates == null) state.worldStates = new HashMap<>();
        return state.playerPity.computeIfAbsent(uuid.toString(), ignored -> new PlayerPity());
    }

    private static double playerPityPercent(ServerPlayer player, boolean islanderRoll) {
        PlayerPity pity = pityData(player.getUUID());
        int misses = islanderRoll ? pity.islanderMisses : pity.normalMisses;
        return Math.max(0.0D, Math.min(PLAYER_PITY_MAX, misses * PLAYER_PITY_PER_MISSED_ROLL));
    }

    private static double playerPriorityWeight(ServerPlayer player, boolean islanderRoll) {
        return 1.0D + playerPityPercent(player, islanderRoll);
    }

    private static double playerParadoxPityPercent(ServerPlayer player, boolean islanderRoll) {
        PlayerPity pity = pityData(player.getUUID());
        int misses = islanderRoll ? pity.islanderParadoxMisses : pity.normalParadoxMisses;
        return Math.max(0.0D, Math.min(PLAYER_PITY_MAX, misses * PLAYER_PITY_PER_MISSED_ROLL));
    }

    private static double playerParadoxPriorityWeight(ServerPlayer player, boolean islanderRoll) {
        return 1.0D + playerParadoxPityPercent(player, islanderRoll);
    }

    private static double playerUltraBeastPityPercent(ServerPlayer player, boolean islanderRoll) {
        PlayerPity pity = pityData(player.getUUID());
        int misses = islanderRoll ? pity.islanderUltraBeastMisses : pity.normalUltraBeastMisses;
        return Math.max(0.0D, Math.min(PLAYER_PITY_MAX, misses * PLAYER_PITY_PER_MISSED_ROLL));
    }

    private static double playerUltraBeastPriorityWeight(ServerPlayer player, boolean islanderRoll) {
        return 1.0D + playerUltraBeastPityPercent(player, islanderRoll);
    }

    private static ServerPlayer pickPlayerByPity(List<ServerPlayer> players, boolean islanderRoll) {
        if (players == null || players.isEmpty()) return null;
        double total = 0.0D;
        for (ServerPlayer player : players) total += playerPriorityWeight(player, islanderRoll);
        double roll = RANDOM.nextDouble() * Math.max(0.000001D, total);
        for (ServerPlayer player : players) {
            roll -= playerPriorityWeight(player, islanderRoll);
            if (roll <= 0.0D) return player;
        }
        return players.get(0);
    }

    private static ServerPlayer pickPlayerByParadoxPity(List<ServerPlayer> players, boolean islanderRoll) {
        if (players == null || players.isEmpty()) return null;
        double total = 0.0D;
        for (ServerPlayer player : players) total += playerParadoxPriorityWeight(player, islanderRoll);
        double roll = RANDOM.nextDouble() * Math.max(0.000001D, total);
        for (ServerPlayer player : players) {
            roll -= playerParadoxPriorityWeight(player, islanderRoll);
            if (roll <= 0.0D) return player;
        }
        return players.get(0);
    }

    private static ServerPlayer pickPlayerByUltraBeastPity(List<ServerPlayer> players, boolean islanderRoll) {
        if (players == null || players.isEmpty()) return null;
        double total = 0.0D;
        for (ServerPlayer player : players) total += playerUltraBeastPriorityWeight(player, islanderRoll);
        double roll = RANDOM.nextDouble() * Math.max(0.000001D, total);
        for (ServerPlayer player : players) {
            roll -= playerUltraBeastPriorityWeight(player, islanderRoll);
            if (roll <= 0.0D) return player;
        }
        return players.get(0);
    }

    private static void recordPityOutcome(List<ServerPlayer> eligiblePlayers, Set<UUID> spawnedPlayers, boolean islanderRoll) {
        if (eligiblePlayers == null || eligiblePlayers.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        Set<UUID> spawned = spawnedPlayers == null ? Collections.emptySet() : spawnedPlayers;
        for (ServerPlayer player : eligiblePlayers) {
            if (player == null) continue;
            PlayerPity pity = pityData(player.getUUID());
            if (islanderRoll) {
                pity.islanderLastEligibleMillis = now;
                if (spawned.contains(player.getUUID())) {
                    pity.islanderMisses = 0;
                    pity.islanderLastSpawnMillis = now;
                } else {
                    pity.islanderMisses = Math.min(10000, pity.islanderMisses + 1);
                }
            } else {
                pity.normalLastEligibleMillis = now;
                if (spawned.contains(player.getUUID())) {
                    pity.normalMisses = 0;
                    pity.normalLastSpawnMillis = now;
                } else {
                    pity.normalMisses = Math.min(10000, pity.normalMisses + 1);
                }
            }
            changed = true;
        }
        if (changed) saveState();
    }

    private static void recordParadoxPityOutcome(List<ServerPlayer> eligiblePlayers, Set<UUID> spawnedPlayers, boolean islanderRoll) {
        if (eligiblePlayers == null || eligiblePlayers.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        Set<UUID> spawned = spawnedPlayers == null ? Collections.emptySet() : spawnedPlayers;
        for (ServerPlayer player : eligiblePlayers) {
            if (player == null) continue;
            PlayerPity pity = pityData(player.getUUID());
            if (islanderRoll) {
                pity.islanderLastParadoxEligibleMillis = now;
                if (spawned.contains(player.getUUID())) {
                    pity.islanderParadoxMisses = 0;
                    pity.islanderLastParadoxSpawnMillis = now;
                } else {
                    pity.islanderParadoxMisses = Math.min(10000, pity.islanderParadoxMisses + 1);
                }
            } else {
                pity.normalLastParadoxEligibleMillis = now;
                if (spawned.contains(player.getUUID())) {
                    pity.normalParadoxMisses = 0;
                    pity.normalLastParadoxSpawnMillis = now;
                } else {
                    pity.normalParadoxMisses = Math.min(10000, pity.normalParadoxMisses + 1);
                }
            }
            changed = true;
        }
        if (changed) saveState();
    }
    private static void recordUltraBeastPityOutcome(List<ServerPlayer> eligiblePlayers, Set<UUID> spawnedPlayers, boolean islanderRoll) {
        if (eligiblePlayers == null || eligiblePlayers.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        Set<UUID> spawned = spawnedPlayers == null ? Collections.emptySet() : spawnedPlayers;
        for (ServerPlayer player : eligiblePlayers) {
            if (player == null) continue;
            PlayerPity pity = pityData(player.getUUID());
            if (islanderRoll) {
                pity.islanderLastUltraBeastEligibleMillis = now;
                if (spawned.contains(player.getUUID())) {
                    pity.islanderUltraBeastMisses = 0;
                    pity.islanderLastUltraBeastSpawnMillis = now;
                } else {
                    pity.islanderUltraBeastMisses = Math.min(10000, pity.islanderUltraBeastMisses + 1);
                }
            } else {
                pity.normalLastUltraBeastEligibleMillis = now;
                if (spawned.contains(player.getUUID())) {
                    pity.normalUltraBeastMisses = 0;
                    pity.normalLastUltraBeastSpawnMillis = now;
                } else {
                    pity.normalUltraBeastMisses = Math.min(10000, pity.normalUltraBeastMisses + 1);
                }
            }
            changed = true;
        }
        if (changed) saveState();
    }


    public static SpawnPoolReport report() {
        ensureStateLoaded();
        SpecialWildSpawnConfig.load();
        List<String> legendaryPool = speciesNames(legendaryTierEntries(false));
        List<String> paradoxPool = speciesNames(SpecialWildSpawnConfig.DATA.paradoxSpawns);
        List<String> ultraBeastPool = speciesNames(SpecialWildSpawnConfig.DATA.ultraBeastSpawns);
        return new SpawnPoolReport(
                legendaryPool.size(), paradoxPool.size(), ultraBeastPool.size(),
                duplicateSpecies(legendaryPool), duplicateSpecies(paradoxPool), duplicateSpecies(ultraBeastPool),
                new ArrayList<>(state.recentLegendarySpecies),
                new ArrayList<>(state.recentParadoxSpecies),
                new ArrayList<>(state.recentUltraBeastSpecies)
        );
    }

    private static List<String> speciesNames(List<SpecialWildSpawnConfig.SpawnEntry> entries) {
        List<String> out = new ArrayList<>();
        if (entries == null) return out;
        for (SpecialWildSpawnConfig.SpawnEntry entry : entries) {
            if (entry == null || entry.species == null || entry.species.isBlank()) continue;
            out.add(sanitize(entry.species));
        }
        return out;
    }

    private static List<String> duplicateSpecies(List<String> names) {
        Set<String> seen = new HashSet<>();
        LinkedHashSet<String> duplicates = new LinkedHashSet<>();
        for (String name : names) {
            if (!seen.add(name)) duplicates.add(name);
        }
        return new ArrayList<>(duplicates);
    }

    private static SpawnBucket pickBucket(boolean islanderRoll) {
        List<SpecialWildSpawnConfig.SpawnEntry> entries = legendaryTierEntries(islanderRoll);
        if (entries == null || entries.isEmpty()) return null;
        return new SpawnBucket(islanderRoll ? "islander legendary" : "a", 1.0D, entries, SpecialWildSpawnConfig.DATA.levelRangeLegendary);
    }

    private static List<SpecialWildSpawnConfig.SpawnEntry> legendaryTierEntries(boolean islanderRoll) {
        List<SpecialWildSpawnConfig.SpawnEntry> combined = new ArrayList<>();
        List<SpecialWildSpawnConfig.SpawnEntry> legendary = islanderRoll ? SpecialWildSpawnConfig.DATA.islanderLegendarySpawns : SpecialWildSpawnConfig.DATA.legendarySpawns;
        List<SpecialWildSpawnConfig.SpawnEntry> mythical = islanderRoll ? SpecialWildSpawnConfig.DATA.islanderMythicalSpawns : SpecialWildSpawnConfig.DATA.mythicalSpawns;
        if (legendary != null) combined.addAll(legendary);
        if (mythical != null) combined.addAll(mythical);
        return combined;
    }

    private static SpawnBucket pickParadoxBucket(boolean islanderRoll) {
        List<SpecialWildSpawnConfig.SpawnEntry> entries = islanderRoll ? SpecialWildSpawnConfig.DATA.islanderParadoxSpawns : SpecialWildSpawnConfig.DATA.paradoxSpawns;
        if (entries == null || entries.isEmpty()) return null;
        return new SpawnBucket(islanderRoll ? "islander paradox" : "paradox", 1.0D, entries, SpecialWildSpawnConfig.DATA.levelRangeParadox);
    }

    private static SpawnBucket pickUltraBeastBucket(boolean islanderRoll) {
        List<SpecialWildSpawnConfig.SpawnEntry> entries = islanderRoll ? SpecialWildSpawnConfig.DATA.islanderUltraBeastSpawns : SpecialWildSpawnConfig.DATA.ultraBeastSpawns;
        if (entries == null || entries.isEmpty()) return null;
        return new SpawnBucket(islanderRoll ? "islander ultra beast" : "ultra beast", 1.0D, entries, SpecialWildSpawnConfig.DATA.levelRangeUltraBeast);
    }

    private static void addBucket(List<SpawnBucket> buckets, String type, double weight, List<SpecialWildSpawnConfig.SpawnEntry> entries, String levelRange) {
        if (entries == null || entries.isEmpty()) return;
        buckets.add(new SpawnBucket(type, Math.max(0.01D, weight), entries, levelRange));
    }


    private static SpawnResult forceSpawnParadoxGuaranteed(ServerPlayer player, SpawnBucket bucket, boolean islanderRoll) {
        // Admin force must be reliable: first try the normal special-spawn path, then bypass
        // player distance, biome, recent-species, and normal safe-position failures with a tiny
        // temporary spawn pocket near the player. This does NOT create natural Paradox spawns;
        // it only affects /specialspawns forceparadox.
        SpawnResult normal = trySpawnFor(player, bucket, true);
        if (normal != null) return normal;

        ServerLevel level = player.serverLevel();
        if (level == null || bucket == null || bucket.entries == null || bucket.entries.isEmpty()) return null;

        List<SpecialWildSpawnConfig.SpawnEntry> entries = matchingEntriesIgnoringTime(bucket.entries);
        if (entries.isEmpty()) return null;
        Collections.shuffle(entries, RANDOM);

        BlockPos[] candidates = emergencyForcePositions(level, player.blockPosition());
        int levelValue = pickLevel(bucket.levelRange);
        int speciesFailures = 0;
        int positionFailures = 0;

        for (BlockPos raw : candidates) {
            BlockPos pos = prepareEmergencyForcePocket(level, raw);
            if (pos == null || !isSafeSpawnSpace(level, pos)) { positionFailures++; continue; }
            forceLoadSpawnChunk(level, pos);

            for (SpecialWildSpawnConfig.SpawnEntry entry : entries) {
                if (entry == null || entry.species == null || entry.species.isBlank()) continue;
                String species = entry.species;
                boolean spawned = spawnDirectlyIgnoringNearbyLimit(level, pos, species, levelValue, bucket.type, false, true);
                if (!spawned) spawned = spawnViaCobblemonCommand(player.getServer(), level, pos, species, levelValue, bucket.type, false, true);
                if (spawned) {
                    System.out.println("[ChampUtils][ParadoxForce] Forced Paradox spawn succeeded: " + species + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                    return new SpawnResult(bucket.type, species, level, pos, player.getUUID());
                }
                speciesFailures++;
            }
        }

        System.err.println("[ChampUtils][ParadoxForce] Failed forceparadox after emergency fallback. positionsFailed=" + positionFailures + " speciesAttemptsFailed=" + speciesFailures + " poolSize=" + entries.size());
        return null;
    }

    private static BlockPos[] emergencyForcePositions(ServerLevel level, BlockPos origin) {
        List<BlockPos> out = new ArrayList<>();
        out.add(origin);
        out.add(origin.above());
        int[][] offsets = {
                {1,0},{-1,0},{0,1},{0,-1},{2,0},{-2,0},{0,2},{0,-2},
                {2,2},{2,-2},{-2,2},{-2,-2},{4,0},{-4,0},{0,4},{0,-4}
        };
        for (int[] o : offsets) {
            BlockPos base = new BlockPos(origin.getX() + o[0], origin.getY(), origin.getZ() + o[1]);
            BlockPos surface = bestSurfaceAt(level, base, true);
            out.add(surface == null ? base : surface);
        }
        return out.toArray(new BlockPos[0]);
    }

    private static BlockPos prepareEmergencyForcePocket(ServerLevel level, BlockPos raw) {
        if (level == null || raw == null) return null;
        BlockPos pos = raw;
        if (!Level.isInSpawnableBounds(pos)) return null;
        forceLoadSpawnChunk(level, pos);

        // If raw is inside a block or in the player's feet, find a nearby Y with room.
        if (!isSafeSpawnSpace(level, pos)) {
            BlockPos scan = findOpenGroundNearY(level, pos, 12);
            if (scan != null) pos = scan;
        }

        try {
            if (!Level.isInSpawnableBounds(pos)) return null;
            if (level.getBlockState(pos.below()).is(Blocks.BEDROCK) || level.getBlockState(pos).is(Blocks.BEDROCK) || level.getBlockState(pos.above()).is(Blocks.BEDROCK)) return null;
            if (!level.getBlockState(pos.below()).isSolid() || !level.getFluidState(pos.below()).isEmpty()) {
                level.setBlock(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 3);
        } catch (Throwable throwable) {
            System.err.println("[ChampUtils][ParadoxForce] Could not prepare emergency spawn pocket at " + pos + ": " + throwable.getMessage());
        }
        return isSafeSpawnSpace(level, pos) ? pos : null;
    }

    private static SpawnResult trySpawnFor(ServerPlayer player, SpawnBucket bucket, boolean forced) {
        return trySpawnFor(player, bucket, forced, false, true, false);
    }

    private static SpawnResult trySpawnFor(ServerPlayer player, SpawnBucket bucket, boolean forced, boolean forceShiny, boolean forceNonShiny, boolean strictRequirements) {
        ServerLevel level = player.serverLevel();
        boolean islanderLevel = isIslanderSpecialSpawnLevel(level);
        int attempts = forced ? 160 : (islanderLevel ? 80 : 35);

        List<SpecialWildSpawnConfig.SpawnEntry> valid = strictRequirements ? matchingEntriesStrict(level, bucket.entries) : matchingEntries(level, bucket.entries);
        // Paradox/Ultra Beast timer spawns should not silently fail because every entry has
        // a time/biome mismatch. These pools are timer-driven, so ignore time as a fallback.
        if (valid.isEmpty() && (forced || islanderLevel || bucket.type.toLowerCase(Locale.ROOT).contains("paradox") || bucket.type.toLowerCase(Locale.ROOT).contains("ultra"))) {
            valid = matchingEntriesIgnoringTime(bucket.entries);
        }
        if (valid.isEmpty()) {
            debug("Spawn failed before placement: no valid entries for bucket=" + bucket.type + " rawPoolSize=" + (bucket.entries == null ? 0 : bucket.entries.size()));
            return null;
        }

        int blockedPosition = 0;
        int unsafeSpace = 0;
        int biomeMismatch = 0;
        int spawnApiFailed = 0;
        for (int attempt = 0; attempt < attempts; attempt++) {
            BlockPos pos = randomSpawnPos(level, player.blockPosition(), islanderLevel, forced, attempt);
            if (pos == null) { blockedPosition++; continue; }
            forceLoadSpawnChunk(level, pos);
            if (!isAllowedSpawnPosition(player, level, pos)) {
                BlockPos fallback = findAllowedPositionNearPlayer(player, level, pos, forced);
                if (fallback == null) { blockedPosition++; continue; }
                pos = fallback;
                forceLoadSpawnChunk(level, pos);
            }
            if ((forced || islanderLevel) && !isSafeSpawnSpace(level, pos)) {
                pos = prepareRobustSpawnSpace(level, pos, islanderLevel);
            }
            if (pos == null || !isSafeSpawnSpace(level, pos)) { unsafeSpace++; continue; }

            List<SpecialWildSpawnConfig.SpawnEntry> validAtPos = new ArrayList<>();
            for (SpecialWildSpawnConfig.SpawnEntry entry : valid) {
                if (matchesBiome(level, pos, entry)) validAtPos.add(entry);
            }
            if (validAtPos.isEmpty()) { biomeMismatch++; continue; }

            List<SpecialWildSpawnConfig.SpawnEntry> variationSafe = filterRecentSpecies(bucket.type, validAtPos);
            if (!variationSafe.isEmpty()) validAtPos = variationSafe;

            SpecialWildSpawnConfig.SpawnEntry picked = pickWeighted(validAtPos);
            if (picked == null) continue;

            // Special spawns use the progression cap of the specific player whose roll selected
            // this spawn. This keeps legendary, mythical, paradox, and ultra-beast encounters
            // appropriate for that player's current gym progression instead of using the pool's
            // configured random level range.
            int pokemonLevel = GymLevelCapUtil.currentWildCap(player);

            // Use a direct Cobblemon entity spawn first instead of going through the normal spawn action/pool.
            // This keeps special spawns independent from the player's nearby Cobblemon spawn cap.
            boolean spawned = spawnDirectlyIgnoringNearbyLimit(level, pos, picked.species, pokemonLevel, bucket.type, forceShiny, forceNonShiny);
            if (!spawned) {
                // Fallback for API changes: command spawning is still forced, but some Cobblemon versions/addons
                // can route command spawns through extra checks. Direct spawn above is the preferred path.
                spawned = spawnViaCobblemonCommand(player.getServer(), level, pos, picked.species, pokemonLevel, bucket.type, forceShiny, forceNonShiny);
            }

            if (!spawned) spawnApiFailed++;
            if (spawned) {
                return new SpawnResult(bucket.type, picked.species, level, pos, player.getUUID());
            }
        }
        debug("Spawn failed for bucket=" + bucket.type + " attempts=" + attempts + " blockedPosition=" + blockedPosition + " unsafeSpace=" + unsafeSpace + " biomeMismatch=" + biomeMismatch + " spawnApiFailed=" + spawnApiFailed + " validEntries=" + valid.size());
        return null;
    }


    private static List<SpecialWildSpawnConfig.SpawnEntry> filterRecentSpecies(String type, List<SpecialWildSpawnConfig.SpawnEntry> entries) {
        if (entries == null || entries.size() <= RECENT_SPECIES_LIMIT) return entries;
        List<String> recent = recentListForType(type);
        if (recent.isEmpty()) return entries;
        List<SpecialWildSpawnConfig.SpawnEntry> filtered = new ArrayList<>();
        for (SpecialWildSpawnConfig.SpawnEntry entry : entries) {
            if (entry == null || entry.species == null) continue;
            if (!recent.contains(sanitize(entry.species))) filtered.add(entry);
        }
        return filtered;
    }

    private static List<String> recentListForType(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.contains("paradox")) return state.recentParadoxSpecies;
        if (t.contains("ultra")) return state.recentUltraBeastSpecies;
        return state.recentLegendarySpecies;
    }

    private static void rememberRecentSpecies(List<String> recent, String species) {
        String clean = sanitize(species);
        if (clean.isBlank()) return;
        recent.remove(clean);
        recent.add(clean);
        while (recent.size() > RECENT_SPECIES_LIMIT) recent.remove(0);
    }

    private static void markRiftSpawned(String type, String species, boolean islanderRoll, boolean shiny) {
        if (shiny) {
            saveState();
            return;
        }
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.contains("paradox")) markParadoxSpawned(type, species, islanderRoll);
        else if (t.contains("ultra")) markUltraBeastSpawned(type, species, islanderRoll);
        else markSpawned(type, species, false, islanderRoll);
    }

    private static void markSpawned(String type, String species, boolean rareEvent, boolean islanderRoll) {
        markSpawned(type, species, rareEvent, islanderRoll, null);
    }

    private static void markSpawned(String type, String species, boolean rareEvent, boolean islanderRoll, String worldKey) {
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
        if (worldKey != null && !worldKey.isBlank()) {
            WorldSpawnState ws = worldState(worldKey);
            ws.lastLegendarySpawnEpochMillis = now;
            ws.lastLegendarySpawnType = type == null ? "" : type;
            ws.lastLegendarySpawnSpecies = species == null ? "" : species;
        }
        rememberRecentSpecies(state.recentLegendarySpecies, species);
        saveState();
    }

    private static void markParadoxSpawned(String type, String species, boolean islanderRoll) {
        markParadoxSpawned(type, species, islanderRoll, null);
    }

    private static void markParadoxSpawned(String type, String species, boolean islanderRoll, String worldKey) {
        long now = System.currentTimeMillis();
        if (islanderRoll) {
            state.islanderLastParadoxSpawnEpochMillis = now;
            state.islanderLastParadoxSpawnType = type == null ? "" : type;
            state.islanderLastParadoxSpawnSpecies = species == null ? "" : species;
        } else {
            state.lastParadoxSpawnEpochMillis = now;
            state.lastParadoxSpawnType = type == null ? "" : type;
            state.lastParadoxSpawnSpecies = species == null ? "" : species;
        }
        if (worldKey != null && !worldKey.isBlank()) {
            WorldSpawnState ws = worldState(worldKey);
            ws.lastParadoxSpawnEpochMillis = now;
            ws.lastParadoxSpawnType = type == null ? "" : type;
            ws.lastParadoxSpawnSpecies = species == null ? "" : species;
        }
        rememberRecentSpecies(state.recentParadoxSpecies, species);
        saveState();
    }

    private static void markUltraBeastSpawned(String type, String species, boolean islanderRoll) {
        markUltraBeastSpawned(type, species, islanderRoll, null);
    }

    private static void markUltraBeastSpawned(String type, String species, boolean islanderRoll, String worldKey) {
        long now = System.currentTimeMillis();
        if (islanderRoll) {
            state.islanderLastUltraBeastSpawnEpochMillis = now;
            state.islanderLastUltraBeastSpawnType = type == null ? "" : type;
            state.islanderLastUltraBeastSpawnSpecies = species == null ? "" : species;
        } else {
            state.lastUltraBeastSpawnEpochMillis = now;
            state.lastUltraBeastSpawnType = type == null ? "" : type;
            state.lastUltraBeastSpawnSpecies = species == null ? "" : species;
        }
        if (worldKey != null && !worldKey.isBlank()) {
            WorldSpawnState ws = worldState(worldKey);
            ws.lastUltraBeastSpawnEpochMillis = now;
            ws.lastUltraBeastSpawnType = type == null ? "" : type;
            ws.lastUltraBeastSpawnSpecies = species == null ? "" : species;
        }
        rememberRecentSpecies(state.recentUltraBeastSpecies, species);
        saveState();
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

    private static boolean spawnDirectlyIgnoringNearbyLimit(ServerLevel level, BlockPos pos, String species, int pokemonLevel, String spawnType, boolean forceShiny, boolean forceNonShiny) {
        try {
            if (level == null || pos == null || species == null || species.isBlank()) return false;
            if (!Level.isInSpawnableBounds(pos)) return false;

            String clean = sanitize(species);
            String compact = clean.replace("_", "");
            String hyphen = clean.replace("_", "-");
            int lvl = Math.max(1, Math.min(100, pokemonLevel));

            PokemonProperties properties = null;
            String[] attempts = new String[] {
                    clean + " lvl=" + lvl,
                    compact + " lvl=" + lvl,
                    hyphen + " lvl=" + lvl,
                    "species=" + clean + " lvl=" + lvl,
                    "species=" + compact + " lvl=" + lvl,
                    "species=" + hyphen + " lvl=" + lvl,
                    "species=cobblemon:" + clean + " lvl=" + lvl,
                    "species=cobblemon:" + compact + " lvl=" + lvl,
                    "species=cobblemon:" + hyphen + " lvl=" + lvl
            };
            String propertiesText = attempts[0];
            for (String attempt : attempts) {
                propertiesText = attempt;
                PokemonProperties parsed = PokemonProperties.Companion.parse(attempt);
                if (parsed.getSpecies() != null) {
                    properties = parsed;
                    break;
                }
            }
            if (properties == null || properties.getSpecies() == null) {
                System.err.println("[ChampUtils][SpecialSpawn] Cobblemon rejected species properties for " + species + " aliases=" + String.join(", ", attempts) + " lastParsedText=" + propertiesText);
                return false;
            }

            PokemonEntity entity = properties.createEntity(level, null);
            if (entity == null) return false;
            applyShinyOverride(entity, forceShiny, forceNonShiny);
            entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, entity.getYRot(), entity.getXRot());
            entity.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);

            long expiresAt = System.currentTimeMillis() + SPECIAL_DESPAWN_MILLIS;
            markSpecialSpawnEntity(entity, expiresAt, spawnType);
            boolean added = level.addFreshEntity(entity);
            if (!added) return false;
            markSpecialSpawnEntity(entity, expiresAt, spawnType);
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

    private static boolean spawnViaCobblemonCommand(MinecraftServer server, ServerLevel level, BlockPos pos, String species, int pokemonLevel, String spawnType, boolean forceShiny, boolean forceNonShiny) {
        try {
            Set<UUID> before = new java.util.HashSet<>();
            for (Entity entity : level.getEntities(null, new net.minecraft.world.phys.AABB(pos).inflate(24.0D))) {
                before.add(entity.getUUID());
            }

            String clean = sanitize(species);
            String compact = clean.replace("_", "");
            String hyphen = clean.replace("_", "-");
            CommandSourceStack source = server.createCommandSourceStack()
                    .withLevel(level)
                    .withPosition(new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D))
                    .withRotation(Vec2.ZERO)
                    .withPermission(4)
                    .withSuppressedOutput();

            String shinySuffix = forceShiny ? " shiny=true" : "";
            String[] commands = new String[] {
                    "spawnpokemonat " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " species=" + compact + " lvl=" + pokemonLevel + shinySuffix,
                    "spawnpokemonat " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " species=" + clean + " lvl=" + pokemonLevel + shinySuffix,
                    "spawnpokemonat " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " species=" + hyphen + " lvl=" + pokemonLevel + shinySuffix
            };

            long expiresAt = System.currentTimeMillis() + SPECIAL_DESPAWN_MILLIS;
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(source, command);

                boolean foundNewSpawn = false;
                for (Entity entity : level.getEntities(null, new net.minecraft.world.phys.AABB(pos).inflate(24.0D))) {
                    if (before.contains(entity.getUUID())) continue;
                    before.add(entity.getUUID());
                    if (entity instanceof PokemonEntity pokemonEntity) applyShinyOverride(pokemonEntity, forceShiny, forceNonShiny);
                    markSpecialSpawnEntity(entity, expiresAt, spawnType);
                    foundNewSpawn = true;
                }
                if (foundNewSpawn) return true;
            }

            System.err.println("[ChampUtils][SpecialSpawn] Command fallback failed for " + species + " tried aliases: " + compact + ", " + clean + ", " + hyphen);
            return false;
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to spawn special wild Pokémon: " + species);
            e.printStackTrace();
            return false;
        }
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
            if (SpawnBlockRules.isBlockedSpawnPosition(level, top)) continue;
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
        if (SpawnBlockRules.isBlockedSpawnPosition(level, pos)) return false;
        if (!Level.isInSpawnableBounds(pos)) return false;
        if (level.getBlockState(pos.below()).is(Blocks.BEDROCK) || level.getBlockState(pos).is(Blocks.BEDROCK) || level.getBlockState(pos.above()).is(Blocks.BEDROCK)) return false;
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
        if (level == null || pos == null || SpawnBlockRules.isBlockedSpawnPosition(level, pos) || !Level.isInSpawnableBounds(pos)) return null;
        if (!canEdit) return isSafeSpawnSpace(level, pos) ? pos : null;

        try {
            // Never replace containers/valuable blocks. This is only meant to fix grass, snow,
            // leaves, tall grass, bad territory spawn columns, and other harmless obstructions.
            if (!level.getBlockState(pos).isAir() && level.getBlockState(pos).getDestroySpeed(level, pos) < 0) return null;
            if (!level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above()).getDestroySpeed(level, pos.above()) < 0) return null;

            if (level.getBlockState(pos.below()).is(Blocks.BEDROCK) || level.getBlockState(pos).is(Blocks.BEDROCK) || level.getBlockState(pos.above()).is(Blocks.BEDROCK)) return null;
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
        return matchingEntriesIgnoringTime(entries);
    }

    private static List<SpecialWildSpawnConfig.SpawnEntry> matchingEntriesStrict(ServerLevel level, List<SpecialWildSpawnConfig.SpawnEntry> entries) {
        List<SpecialWildSpawnConfig.SpawnEntry> valid = new ArrayList<>();
        if (level == null || entries == null) return valid;
        for (SpecialWildSpawnConfig.SpawnEntry entry : entries) {
            if (entry == null || entry.species == null || entry.species.isBlank()) continue;
            if (!matchesTime(level, entry)) continue;
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


    private static boolean matchesBiome(ServerLevel level, BlockPos pos, SpecialWildSpawnConfig.SpawnEntry entry) {
        if (entry == null || entry.biomes == null || entry.biomes.isEmpty()) return true;
        if (level == null || pos == null) return false;
        ResourceLocation biomeId = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(level.getBiome(pos).value());
        if (biomeId == null) return false;
        String id = biomeId.toString().toLowerCase(Locale.ROOT);
        String path = biomeId.getPath().toLowerCase(Locale.ROOT);
        for (String raw : entry.biomes) {
            if (raw == null || raw.isBlank()) continue;
            String wanted = raw.trim().toLowerCase(Locale.ROOT);
            if (wanted.equals(id) || wanted.equals(path) || wanted.equals("#" + id) || wanted.equals("#" + path)) return true;
            if (wanted.endsWith("*") && id.startsWith(wanted.substring(0, wanted.length() - 1))) return true;
            if (!wanted.contains(":") && path.contains(wanted)) return true;
        }
        return false;
    }

    private static boolean matchesTime(ServerLevel level, SpecialWildSpawnConfig.SpawnEntry entry) {
        if (entry == null || entry.times == null || entry.times.isEmpty()) return true;
        long time = level.getDayTime() % 24000L;
        boolean day = time >= 0 && time < 12000L;
        boolean night = time >= 13000L && time < 23000L;
        boolean dusk = time >= 12000L && time < 13000L;
        boolean dawn = time >= 23000L || time < 1000L;
        for (String raw : entry.times) {
            if (raw == null) continue;
            String t = raw.trim().toLowerCase(Locale.ROOT);
            if (t.equals("any") || t.equals("all")) return true;
            if (t.equals("day") && day) return true;
            if (t.equals("night") && night) return true;
            if ((t.equals("dusk") || t.equals("sunset")) && dusk) return true;
            if ((t.equals("dawn") || t.equals("sunrise") || t.equals("morning")) && dawn) return true;
        }
        return false;
    }

    private static void applyShinyOverride(PokemonEntity entity, boolean forceShiny, boolean forceNonShiny) {
        if (entity == null || entity.getPokemon() == null) return;
        if (forceShiny) entity.getPokemon().setShiny(true);
        else if (forceNonShiny) entity.getPokemon().setShiny(false);
    }

    private static void announce(MinecraftServer server, String type, String species, ServerLevel level, BlockPos pos) {
        announce(server, type, species, level, pos, null);
    }

    private static void announce(MinecraftServer server, String type, String species, ServerLevel level, BlockPos pos, UUID spawnedPlayerUuid) {
        if (server == null || level == null || pos == null) return;
        String normalizedType = type == null ? "" : type.toLowerCase(Locale.ROOT);
        boolean legendary = normalizedType.contains("legendary") || normalizedType.contains("mythical");
        boolean broadcast = legendary ? SpecialWildSpawnConfig.DATA.broadcastLegendarySpawns : true;
        if (!broadcast) return;

        boolean islanderSpawn = isIslanderSpecialSpawnLevel(level);
        String name = pretty(species);
        String biome = prettyBiome(level, pos);
        String typeLabel = specialSpawnTypeLabel(type);
        String typeColor = specialSpawnTypeColor(type);
        Component msg = Component.literal("§6A wild " + typeColor + typeLabel + " §e" + name + " §6has appeared in §a" + biome + " §6at coordinates §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "§6.");

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null) continue;
            boolean islanderPlayer = PlayerProfileManager.isIslander(player);
            if (player.serverLevel() == null || !worldKey(player.serverLevel()).equals(worldKey(level))) continue;
            if (islanderSpawn) {
                if (!SpecialWildSpawnConfig.DATA.islanderOnlyNotifyIslanders || islanderPlayer) player.sendSystemMessage(msg);
            } else {
                if (!islanderPlayer) player.sendSystemMessage(msg);
            }
        }
    }

    private static String specialSpawnTypeLabel(String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (normalized.contains("ultra")) return "Ultra Beast";
        if (normalized.contains("paradox")) return "Paradox";
        if (normalized.contains("mythical")) return "Mythical";
        return "Legendary";
    }

    private static String specialSpawnTypeColor(String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (normalized.contains("ultra")) return "§b";
        if (normalized.contains("paradox")) return "§5";
        if (normalized.contains("mythical")) return "§d";
        return "§e";
    }

    private static String prettyBiome(ServerLevel level, BlockPos pos) {
        try {
            String raw = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(level.getBiome(pos).value()).toString();
            int colon = raw.lastIndexOf(':');
            String id = colon >= 0 ? raw.substring(colon + 1) : raw;
            String[] parts = id.replace('-', '_').split("_");
            StringBuilder out = new StringBuilder();
            for (String part : parts) {
                if (part == null || part.isBlank()) continue;
                if (out.length() > 0) out.append(' ');
                out.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1).toLowerCase(Locale.ROOT));
            }
            return out.length() == 0 ? raw : out.toString();
        } catch (Exception ignored) {
            return "Unknown Biome";
        }
    }

    private static void announceRiftEvent(MinecraftServer server, String type, boolean shiny, List<SpawnResult> results) {
        if (server == null || results == null || results.isEmpty()) return;
        String title = shiny ? "§d§l✦ A SHINY RIFT HAS TORN OPEN! ✦" : "§5§l✦ A " + type.toUpperCase(Locale.ROOT) + " HAS TORN OPEN! ✦";
        Component header = Component.literal(title);
        Component sub = Component.literal(shiny
                ? "§fA shiny Pokémon has appeared near every eligible trainer. This is an almost-never moment — go hunt!"
                : "§fRare Pokémon have appeared near eligible trainers. These rift spawns are forced non-shiny.");
        com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(server, header);
        com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(server, sub);
        for (SpawnResult result : results) {
            String name = pretty(result.species);
            String biome = prettyBiome(result.level, result.pos);
            com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(server, Component.literal("§7 - §e" + (shiny ? "Shiny " : "") + name + " §7appeared in §a" + biome + " §7at coordinates §f" + result.pos.getX() + ", " + result.pos.getY() + ", " + result.pos.getZ() + "§7."));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null) continue;
            player.playNotifySound(shiny ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.END_PORTAL_SPAWN, SoundSource.MASTER, 1.0F, shiny ? 1.6F : 0.85F);
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.8F, 1.0F);
        }
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
            String biome = prettyBiome(result.level, result.pos);
            lines.add(Component.literal("§7 - §e" + name + " §7appeared in §a" + biome + " §7at coordinates §f" + result.pos.getX() + ", " + result.pos.getY() + ", " + result.pos.getZ() + "§7."));
        }

        if (islanderOnly) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (PlayerProfileManager.isIslander(player)) {
                    for (Component line : lines) player.sendSystemMessage(line);
                }
            }
            return;
        }

        for (Component line : lines) com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(server, line);
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
        if (!isSpecialSpawnManagedLevel(level)) return "Special spawns only roll in multiworld:survival_overworld_# and multiworld:islander_# worlds.";
        String dimensionId = level.dimension().location().toString();
        if (IslanderMineManager.isMineWorld(level)) return "Islander mining worlds are resource-only and do not roll special spawns.";
        if (isDisabledDimension(level) || SpawnBlockRules.isBlockedSpawnLevel(level)) return "This dimension is disabled for special spawns: " + dimensionId;

        boolean islanderLevel = isIslanderSpecialSpawnLevel(level);
        boolean playerIsIslander = PlayerProfileManager.isIslander(player);

        if (islanderRoll) {
            if (!islanderLevel) return "Islander special spawns can only roll in islander worlds.";
            if (!SpecialWildSpawnConfig.DATA.islanderSpecialSpawnsEnabled) return "Islander special spawns are disabled in special_wild_spawns.json.";
            if (!playerIsIslander) return "Only Islander profiles can force Islander special spawns.";
            long required = Math.max(0L, SpecialWildSpawnConfig.DATA.islanderMinimumProfilePlaytimeSeconds);
            if (!ProfilePlaytimeManager.hasAtLeastPlaytime(player, required)) {
                return "This Islander profile needs at least " + formatRequiredPlaytime(required) + " of profile playtime before Islander special spawns unlock.";
            }
            return null;
        }

        if (islanderLevel) return "Normal special spawns cannot spawn in Islander worlds.";
        if (playerIsIslander) return "Islander profiles use the separate Islander special spawn timer/pool.";

        long required = Math.max(0L, SpecialWildSpawnConfig.DATA.minimumProfilePlaytimeSeconds);
        if (!ProfilePlaytimeManager.hasAtLeastPlaytime(player, required)) {
            return "This profile needs at least " + formatRequiredPlaytime(required) + " of profile playtime before special spawns unlock.";
        }

        // Do not require the world to be registered as an ExplorationWorldManager world. Some normal
        // gameplay worlds are Multiworld dimensions that are not active RTP entries. Territory checks
        // still prevent normal special spawns from landing inside player/guild territories.
        return null;
    }


    private static String formatRequiredPlaytime(long requiredSeconds) {
        long seconds = Math.max(0L, requiredSeconds);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (hours > 0L && minutes > 0L) return hours + "h " + minutes + "m";
        if (hours > 0L) return hours + "h";
        if (minutes > 0L) return minutes + "m";
        return seconds + "s";
    }

    private static boolean isAllowedSpawnPosition(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return false;
        if (IslanderMineManager.isMineWorld(level)) return false;

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

    private static String worldKey(ServerLevel level) {
        return level == null ? "" : level.dimension().location().toString().toLowerCase(Locale.ROOT);
    }

    private static boolean isSpecialSpawnManagedLevel(ServerLevel level) {
        if (level == null || IslanderMineManager.isMineWorld(level)) return false;
        ResourceLocation id = level.dimension().location();
        String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        boolean validNamespace = namespace.equals("multiworld");
        return validNamespace && (path.matches("survival_overworld_[0-9]+") || path.matches("islander_[0-9]+"));
    }

    private static boolean isNormalSpecialSpawnLevel(ServerLevel level) {
        if (!isSpecialSpawnManagedLevel(level)) return false;
        String path = level.dimension().location().getPath().toLowerCase(Locale.ROOT);
        return path.matches("survival_overworld_[0-9]+");
    }

    private static boolean isIslanderSpecialSpawnLevel(ServerLevel level) {
        if (!isSpecialSpawnManagedLevel(level)) return false;
        String path = level.dimension().location().getPath().toLowerCase(Locale.ROOT);
        return path.matches("islander_[0-9]+");
    }

    private static void ensureStateLoaded() {
        if (stateLoaded) return;
        stateLoaded = true;
        try {
            if (!STATE_FILE.getParentFile().exists()) STATE_FILE.getParentFile().mkdirs();
            if (!STATE_FILE.exists()) {
                state = new State();
                normalizeState();
                saveState();
                return;
            }
            try (FileReader reader = new FileReader(STATE_FILE)) {
                State loaded = GSON.fromJson(reader, State.class);
                state = loaded == null ? new State() : loaded;
                normalizeState();
            }
        } catch (Exception e) {
            state = new State();
            normalizeState();
            e.printStackTrace();
        }
    }


    private static void normalizeState() {
        if (state.playerPity == null) state.playerPity = new HashMap<>();
        if (state.recentLegendarySpecies == null) state.recentLegendarySpecies = new ArrayList<>();
        if (state.recentParadoxSpecies == null) state.recentParadoxSpecies = new ArrayList<>();
        if (state.recentUltraBeastSpecies == null) state.recentUltraBeastSpecies = new ArrayList<>();
        trimRecent(state.recentLegendarySpecies);
        trimRecent(state.recentParadoxSpecies);
        trimRecent(state.recentUltraBeastSpecies);
    }

    private static void trimRecent(List<String> recent) {
        if (recent == null) return;
        List<String> cleaned = new ArrayList<>();
        for (String species : recent) {
            String clean = sanitize(species);
            if (!clean.isBlank() && !cleaned.contains(clean)) cleaned.add(clean);
        }
        recent.clear();
        recent.addAll(cleaned);
        while (recent.size() > RECENT_SPECIES_LIMIT) recent.remove(0);
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


    public static DebugStatus debugStatus(ServerPlayer player) {
        ensureStateLoaded();
        SpecialWildSpawnConfig.load();
        boolean islanderRoll = player != null && player.serverLevel() != null && isIslanderSpecialSpawnLevel(player.serverLevel());
        int intervalTicks = Math.max(20, SpecialWildSpawnConfig.DATA.paradoxCheckIntervalTicks);
        double chance = player != null && player.serverLevel() != null ? currentParadoxGlobalChancePerCheck(intervalTicks, islanderRoll, worldKey(player.serverLevel())) : currentParadoxGlobalChancePerCheck(intervalTicks, islanderRoll);
        String eligibility = player == null ? "No player." : eligibilityFailureReason(player, islanderRoll);
        List<SpecialWildSpawnConfig.SpawnEntry> pool = islanderRoll ? SpecialWildSpawnConfig.DATA.islanderParadoxSpawns : SpecialWildSpawnConfig.DATA.paradoxSpawns;
        int poolSize = pool == null ? 0 : pool.size();
        long nextSeconds = player != null && player.serverLevel() != null ? Math.max(0, worldParadoxTimers.getOrDefault(worldKey(player.serverLevel()), Math.max(20, SpecialWildSpawnConfig.DATA.paradoxCheckIntervalTicks))) / 20L : Math.max(0, ticksUntilParadoxCheck) / 20L;
        return new DebugStatus(SpecialWildSpawnConfig.DATA.enabled, SpecialWildSpawnConfig.DATA.paradoxOnlySpawnsEnabled, ChampDebugManager.isEnabled(ChampDebugManager.Category.SPAWNS), islanderRoll, eligibility == null, eligibility == null ? "Eligible" : eligibility, poolSize, tracked.size(), maxAliveTotal() + Math.max(0, SpecialWildSpawnConfig.DATA.maxAliveParadoxWildPokemon), nextSeconds, chance, islanderRoll ? formatLastIslanderParadoxSpawnAgo() : formatLastNormalParadoxSpawnAgo());
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

    public record SpawnPoolReport(int legendaryCount, int paradoxCount, int ultraBeastCount, List<String> legendaryDuplicates, List<String> paradoxDuplicates, List<String> ultraBeastDuplicates, List<String> recentLegendary, List<String> recentParadox, List<String> recentUltraBeast) {}

    public record DebugStatus(boolean enabled, boolean paradoxEnabled, boolean debugLogsEnabled, boolean islanderRoll, boolean eligibleHere, String eligibilityMessage, int paradoxPoolSize, int trackedSpecials, int trackedLimit, long nextParadoxCheckSeconds, double paradoxChancePerCheck, String lastParadoxSpawnAgo) {}

    public record PityView(boolean islanderRoll, boolean eligible, String eligibilityMessage, int missedEligibleRolls, double pityPercent, double priorityWeight, double globalChancePerCheck, double estimatedPersonalChancePerCheck, String lastSpecialSpawnAgo) {}
    private record SpawnBucket(String type, double weight, List<SpecialWildSpawnConfig.SpawnEntry> entries, String levelRange) {}
    private record SpawnResult(String type, String species, ServerLevel level, BlockPos pos, UUID playerUuid) {}

    private static final class PlayerPity {
        int normalMisses = 0;
        int islanderMisses = 0;
        long normalLastEligibleMillis = 0L;
        long islanderLastEligibleMillis = 0L;
        long normalLastSpawnMillis = 0L;
        long islanderLastSpawnMillis = 0L;
        int normalParadoxMisses = 0;
        int islanderParadoxMisses = 0;
        long normalLastParadoxEligibleMillis = 0L;
        long islanderLastParadoxEligibleMillis = 0L;
        long normalLastParadoxSpawnMillis = 0L;
        long islanderLastParadoxSpawnMillis = 0L;
        int normalUltraBeastMisses = 0;
        int islanderUltraBeastMisses = 0;
        long normalLastUltraBeastEligibleMillis = 0L;
        long islanderLastUltraBeastEligibleMillis = 0L;
        long normalLastUltraBeastSpawnMillis = 0L;
        long islanderLastUltraBeastSpawnMillis = 0L;
    }

    private static final class State {
        long lastSpawnEpochMillis = 0L;
        String lastSpawnType = "";
        String lastSpawnSpecies = "";
        boolean lastSpawnWasRareTripleEvent = false;
        long islanderLastSpawnEpochMillis = 0L;
        String islanderLastSpawnType = "";
        String islanderLastSpawnSpecies = "";
        boolean islanderLastSpawnWasRareTripleEvent = false;
        long lastParadoxSpawnEpochMillis = 0L;
        String lastParadoxSpawnType = "";
        String lastParadoxSpawnSpecies = "";
        long islanderLastParadoxSpawnEpochMillis = 0L;
        String islanderLastParadoxSpawnType = "";
        String islanderLastParadoxSpawnSpecies = "";
        long lastUltraBeastSpawnEpochMillis = 0L;
        String lastUltraBeastSpawnType = "";
        String lastUltraBeastSpawnSpecies = "";
        long islanderLastUltraBeastSpawnEpochMillis = 0L;
        String islanderLastUltraBeastSpawnType = "";
        String islanderLastUltraBeastSpawnSpecies = "";
        List<String> recentLegendarySpecies = new ArrayList<>();
        List<String> recentParadoxSpecies = new ArrayList<>();
        List<String> recentUltraBeastSpecies = new ArrayList<>();
        Map<String, PlayerPity> playerPity = new HashMap<>();
        Map<String, WorldSpawnState> worldStates = new HashMap<>();
    }

    private static final class WorldSpawnState {
        long lastLegendarySpawnEpochMillis = 0L;
        String lastLegendarySpawnType = "";
        String lastLegendarySpawnSpecies = "";
        long lastParadoxSpawnEpochMillis = 0L;
        String lastParadoxSpawnType = "";
        String lastParadoxSpawnSpecies = "";
        long lastUltraBeastSpawnEpochMillis = 0L;
        String lastUltraBeastSpawnType = "";
        String lastUltraBeastSpawnSpecies = "";
    }
}
