package com.champutils.moderation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight redstone AutoMod.
 *
 * This intentionally does not scan loaded chunks. The mixin only reports block-state changes that are already
 * happening, and this manager reviews a tiny queue of active chunks on a timer. Quiet chunks cost nothing.
 */
public final class RedstoneAutoModManager {
    private static final Map<ChunkKey, ChunkActivity> ACTIVE = new HashMap<>();
    private static final ArrayDeque<ChunkKey> REVIEW_QUEUE = new ArrayDeque<>();
    private static final Set<ChunkKey> QUEUED = new HashSet<>();
    private static final Map<ChunkKey, Long> DISABLED_UNTIL = new HashMap<>();
    private static final Map<ChunkKey, Long> ALERT_COOLDOWN_UNTIL = new HashMap<>();

    private RedstoneAutoModManager() {}

    public static void recordBlockStateChange(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (!enabled(level) || pos == null || oldState == null || newState == null) return;
        if (oldState == newState || oldState.equals(newState)) return;
        boolean oldRedstone = isTrackedRedstone(oldState);
        boolean newRedstone = isTrackedRedstone(newState);
        if (!oldRedstone && !newRedstone) return;

        ChunkKey key = ChunkKey.of(level, pos);
        ChunkActivity activity = ACTIVE.get(key);
        if (activity == null) {
            if (ACTIVE.size() >= Math.max(128, ModerationConfig.DATA.redstoneMaxTrackedChunks)) return;
            activity = new ChunkActivity();
            ACTIVE.put(key, activity);
        }

        long now = System.currentTimeMillis();
        activity.lastSeenMillis = now;
        activity.updates++;
        if (newRedstone) activity.componentUpdates++;
        if (isClocky(newState) || isClocky(oldState)) activity.clockLikeUpdates++;
        if (!QUEUED.contains(key)) {
            QUEUED.add(key);
            REVIEW_QUEUE.addLast(key);
        }
    }

    public static boolean shouldCancelRedstoneChange(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (!enabled(level) || !ModerationConfig.DATA.redstoneDisableFlaggedChunks) return false;
        if (pos == null || oldState == null || newState == null) return false;
        ChunkKey key = ChunkKey.of(level, pos);
        Long until = DISABLED_UNTIL.get(key);
        if (until == null) return false;
        long now = System.currentTimeMillis();
        if (until <= now) {
            DISABLED_UNTIL.remove(key);
            return false;
        }

        // Let players remove/break redstone. Only stop new redstone states from continuing a clock/update storm.
        return isTrackedRedstone(newState);
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !ModerationConfig.DATA.redstoneAutoModEnabled) return;
        if (server.getTickCount() % Math.max(20, ModerationConfig.DATA.redstoneReviewIntervalTicks) != 0) return;

        long now = System.currentTimeMillis();
        expireOld(now);

        int budget = Math.max(1, ModerationConfig.DATA.redstoneMaxChunksReviewedPerRun);
        for (int i = 0; i < budget && !REVIEW_QUEUE.isEmpty(); i++) {
            ChunkKey key = REVIEW_QUEUE.removeFirst();
            QUEUED.remove(key);
            ChunkActivity activity = ACTIVE.get(key);
            if (activity == null) continue;

            int updates = activity.updates;
            int clockLike = activity.clockLikeUpdates;
            int componentUpdates = activity.componentUpdates;
            activity.updates = 0;
            activity.clockLikeUpdates = 0;
            activity.componentUpdates = 0;

            if (updates <= 0 && clockLike <= 0 && componentUpdates <= 0) continue;

            int soft = Math.max(50, ModerationConfig.DATA.redstoneSoftUpdateThresholdPerReview);
            int hard = Math.max(soft + 1, ModerationConfig.DATA.redstoneHardUpdateThresholdPerReview);
            int emergency = Math.max(hard + 1, ModerationConfig.DATA.redstoneEmergencyUpdateThresholdPerReview);
            int clockThreshold = Math.max(20, ModerationConfig.DATA.redstoneClockLikeThresholdPerReview);

            if (updates >= emergency) {
                disableChunk(key, now, server, "emergency redstone update spike", updates, clockLike, componentUpdates);
            } else if (updates >= hard || clockLike >= clockThreshold) {
                activity.violations++;
                if (activity.violations >= Math.max(1, ModerationConfig.DATA.redstoneViolationsBeforeDisable)) {
                    disableChunk(key, now, server, "repeated heavy redstone activity", updates, clockLike, componentUpdates);
                } else {
                    alert(server, key, "hard redstone flag", updates, clockLike, componentUpdates, false);
                }
            } else if (updates >= soft) {
                alert(server, key, "soft redstone flag", updates, clockLike, componentUpdates, false);
            }

            if ((activity.updates > 0 || activity.clockLikeUpdates > 0 || activity.componentUpdates > 0) && !QUEUED.contains(key)) {
                QUEUED.add(key);
                REVIEW_QUEUE.addLast(key);
            }
        }
    }

    public static void clearAll(MinecraftServer server) {
        ACTIVE.clear();
        REVIEW_QUEUE.clear();
        QUEUED.clear();
        DISABLED_UNTIL.clear();
        ALERT_COOLDOWN_UNTIL.clear();
        if (server != null) ModerationManager.alertAdmins(server, "§a[Redstone AutoMod] Cleared all tracked redstone activity and disabled chunks.");
    }

    public static void clearChunk(Level level, ChunkPos chunkPos) {
        if (level == null || chunkPos == null) return;
        ChunkKey key = ChunkKey.of(level, chunkPos.x, chunkPos.z);
        ACTIVE.remove(key);
        QUEUED.remove(key);
        DISABLED_UNTIL.remove(key);
        ALERT_COOLDOWN_UNTIL.remove(key);
    }

    public static Status status() {
        return new Status(ACTIVE.size(), DISABLED_UNTIL.size(), REVIEW_QUEUE.size());
    }

    public static String currentChunkStatus(Level level, BlockPos pos) {
        if (level == null || pos == null) return "No chunk selected.";
        ChunkKey key = ChunkKey.of(level, pos);
        ChunkActivity activity = ACTIVE.get(key);
        Long disabled = DISABLED_UNTIL.get(key);
        long remaining = disabled == null ? 0L : Math.max(0L, disabled - System.currentTimeMillis());
        return "Redstone chunk " + key.dimension + " [" + key.chunkX + ", " + key.chunkZ + "] "
                + "updates=" + (activity == null ? 0 : activity.updates)
                + ", clockLike=" + (activity == null ? 0 : activity.clockLikeUpdates)
                + ", violations=" + (activity == null ? 0 : activity.violations)
                + ", disabledRemainingMs=" + remaining;
    }

    private static void disableChunk(ChunkKey key, long now, MinecraftServer server, String reason, int updates, int clockLike, int componentUpdates) {
        long minutes = Math.max(1, ModerationConfig.DATA.redstoneDisableMinutes);
        DISABLED_UNTIL.put(key, now + minutes * 60_000L);
        alert(server, key, reason + " - temporarily disabled for " + minutes + "m", updates, clockLike, componentUpdates, true);
    }

    private static void alert(MinecraftServer server, ChunkKey key, String reason, int updates, int clockLike, int componentUpdates, boolean force) {
        if (server == null || key == null) return;
        long now = System.currentTimeMillis();
        long cooldownUntil = ALERT_COOLDOWN_UNTIL.getOrDefault(key, 0L);
        if (!force && cooldownUntil > now) return;
        ALERT_COOLDOWN_UNTIL.put(key, now + Math.max(30, ModerationConfig.DATA.redstoneAlertCooldownSeconds) * 1000L);

        String message = "§c[Redstone AutoMod] §7" + reason
                + " §8| §f" + key.dimension + " §7chunk §f" + key.chunkX + "," + key.chunkZ
                + " §8| §7updates=§f" + updates
                + " §7clockLike=§f" + clockLike
                + " §7components=§f" + componentUpdates;
        ModerationManager.alertAdmins(server, message);
        ModerationManager.webhook("Redstone AutoMod: " + reason + " | dimension=" + key.dimension + " | chunk=" + key.chunkX + "," + key.chunkZ + " | updates=" + updates + " | clockLike=" + clockLike + " | components=" + componentUpdates);
    }

    private static void expireOld(long now) {
        long staleAfter = Math.max(60_000L, ModerationConfig.DATA.redstoneTrackedChunkExpirySeconds * 1000L);
        Iterator<Map.Entry<ChunkKey, ChunkActivity>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ChunkKey, ChunkActivity> entry = iterator.next();
            if (now - entry.getValue().lastSeenMillis > staleAfter) {
                QUEUED.remove(entry.getKey());
                iterator.remove();
            }
        }
        DISABLED_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
        ALERT_COOLDOWN_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static boolean enabled(Level level) {
        return level != null && !level.isClientSide && ModerationConfig.DATA.redstoneAutoModEnabled;
    }

    private static boolean isTrackedRedstone(BlockState state) {
        if (state == null || state.isAir()) return false;
        Block block = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        String value = id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
        return value.contains("redstone")
                || value.contains("observer")
                || value.contains("piston")
                || value.contains("hopper")
                || value.contains("dispenser")
                || value.contains("dropper")
                || value.contains("comparator")
                || value.contains("repeater");
    }

    private static boolean isClocky(BlockState state) {
        if (state == null || state.isAir()) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String value = id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
        return value.contains("observer")
                || value.contains("piston")
                || value.contains("comparator")
                || value.contains("repeater")
                || value.contains("redstone_wire")
                || value.contains("redstone_torch");
    }

    private static final class ChunkActivity {
        int updates;
        int componentUpdates;
        int clockLikeUpdates;
        int violations;
        long lastSeenMillis = System.currentTimeMillis();
    }

    private record ChunkKey(String dimension, int chunkX, int chunkZ) {
        static ChunkKey of(Level level, BlockPos pos) {
            return of(level, pos.getX() >> 4, pos.getZ() >> 4);
        }

        static ChunkKey of(Level level, int chunkX, int chunkZ) {
            String dimension = level.dimension().location().toString();
            return new ChunkKey(dimension, chunkX, chunkZ);
        }
    }

    public record Status(int trackedChunks, int disabledChunks, int queuedChunks) {}
}
