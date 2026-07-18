package com.champutils.profession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives naturally decaying leaves two additional vanilla random-tick opportunities,
 * producing roughly 3x normal decay speed while preserving the leaf block's own
 * decay logic, loot table, Fortune behavior, and mod compatibility.
 */
public final class AcceleratedLeafDecayManager {
    private static final int SEARCH_RADIUS = 8;
    private static final int VANILLA_RANDOM_TICK_CHANCE = 3;
    private static final int EXTRA_RANDOM_TICK_CHANCE = VANILLA_RANDOM_TICK_CHANCE * 2;
    private static final int RANDOM_TICK_DENOMINATOR = 4096;
    private static final int TRACK_DURATION_TICKS = 20 * 60 * 5;
    private static final int MAX_LEAF_CHECKS_PER_TICK = 8192;

    private static final Map<ResourceKey<Level>, ArrayDeque<PendingLeaf>> QUEUES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Set<Long>> QUEUED = new ConcurrentHashMap<>();

    private AcceleratedLeafDecayManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AcceleratedLeafDecayManager::tick);
    }

    public static void trackLeavesNearRemovedLog(ServerLevel level, BlockPos logPos) {
        if (level == null || logPos == null) return;

        ResourceKey<Level> key = level.dimension();
        ArrayDeque<PendingLeaf> queue = QUEUES.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        Set<Long> queued = QUEUED.computeIfAbsent(key, ignored -> new HashSet<>());
        long expiresAt = level.getGameTime() + TRACK_DURATION_TICKS;

        for (BlockPos cursor : BlockPos.betweenClosed(
                logPos.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                logPos.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            BlockState state = level.getBlockState(cursor);
            if (!state.is(BlockTags.LEAVES) || isPersistent(state)) continue;

            BlockPos immutable = cursor.immutable();
            if (queued.add(immutable.asLong())) {
                queue.addLast(new PendingLeaf(immutable, expiresAt));
            }
        }
    }

    private static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> key = level.dimension();
            ArrayDeque<PendingLeaf> queue = QUEUES.get(key);
            Set<Long> queued = QUEUED.get(key);
            if (queue == null || queued == null || queue.isEmpty()) continue;

            int checks = Math.min(queue.size(), MAX_LEAF_CHECKS_PER_TICK);
            long gameTime = level.getGameTime();

            for (int i = 0; i < checks; i++) {
                PendingLeaf pending = queue.removeFirst();
                long packedPos = pending.pos.asLong();
                BlockState state = level.getBlockState(pending.pos);

                if (gameTime >= pending.expiresAt
                        || !state.is(BlockTags.LEAVES)
                        || isPersistent(state)) {
                    queued.remove(packedPos);
                    continue;
                }

                // Keep the leaf tracked while vanilla distance propagation catches up.
                // Once it is naturally eligible to decay, invoke the block's own randomTick
                // instead of destroying it ourselves, preserving all native/modded drop logic.
                if (leafDistance(state) >= 7
                        && state.isRandomlyTicking()
                        && level.random.nextInt(RANDOM_TICK_DENOMINATOR) < EXTRA_RANDOM_TICK_CHANCE) {
                    state.randomTick(level, pending.pos, level.random);
                }

                BlockState updated = level.getBlockState(pending.pos);
                if (updated.is(BlockTags.LEAVES) && !isPersistent(updated)) {
                    queue.addLast(pending);
                } else {
                    queued.remove(packedPos);
                }
            }
        }
    }

    private static boolean isPersistent(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if ("persistent".equals(property.getName())) {
                Object value = getPropertyValue(state, property);
                return Boolean.TRUE.equals(value);
            }
        }
        return false;
    }

    private static int leafDistance(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if ("distance".equals(property.getName())) {
                Object value = getPropertyValue(state, property);
                if (value instanceof Integer integer) return integer;
            }
        }
        return 7;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getPropertyValue(BlockState state, Property<?> property) {
        return state.getValue((Property) property);
    }

    private record PendingLeaf(BlockPos pos, long expiresAt) {}
}
