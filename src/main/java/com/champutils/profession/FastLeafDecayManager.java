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

/** Lightweight server-side accelerated decay for natural leaves after a log is removed. */
public final class FastLeafDecayManager {
    private static final int SEARCH_RADIUS = 8;
    private static final int DECAY_DELAY_TICKS = 8;
    private static final int MAX_DECAYS_PER_TICK = 256;
    private static final Map<ResourceKey<Level>, ArrayDeque<PendingLeaf>> QUEUES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Set<Long>> QUEUED = new ConcurrentHashMap<>();

    private FastLeafDecayManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FastLeafDecayManager::tick);
    }

    public static void queueTreeLeaves(ServerLevel level, BlockPos logPos) {
        if (level == null || logPos == null) return;
        ResourceKey<Level> key = level.dimension();
        ArrayDeque<PendingLeaf> queue = QUEUES.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        Set<Long> queued = QUEUED.computeIfAbsent(key, ignored -> new HashSet<>());
        long dueTick = level.getGameTime() + DECAY_DELAY_TICKS;

        for (BlockPos cursor : BlockPos.betweenClosed(
                logPos.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                logPos.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            BlockState state = level.getBlockState(cursor);
            if (!state.is(BlockTags.LEAVES) || isPersistent(state)) continue;
            BlockPos immutable = cursor.immutable();
            if (queued.add(immutable.asLong())) queue.addLast(new PendingLeaf(immutable, dueTick, 0));
        }
    }

    private static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> key = level.dimension();
            ArrayDeque<PendingLeaf> queue = QUEUES.get(key);
            Set<Long> queued = QUEUED.get(key);
            if (queue == null || queued == null || queue.isEmpty()) continue;

            int processed = 0;
            int available = queue.size();
            while (processed < MAX_DECAYS_PER_TICK && available-- > 0 && !queue.isEmpty()) {
                PendingLeaf pending = queue.removeFirst();
                if (pending.dueTick > level.getGameTime()) {
                    queue.addLast(pending);
                    continue;
                }
                BlockState state = level.getBlockState(pending.pos);
                if (!state.is(BlockTags.LEAVES) || isPersistent(state)) {
                    queued.remove(pending.pos.asLong());
                    continue;
                }

                int distance = leafDistance(state);
                if (distance >= 7) {
                    level.destroyBlock(pending.pos, true);
                    queued.remove(pending.pos.asLong());
                    processed++;
                } else if (pending.retries < 3) {
                    // Give vanilla distance propagation a little more time on very large trees.
                    queue.addLast(new PendingLeaf(pending.pos, level.getGameTime() + 4, pending.retries + 1));
                } else {
                    queued.remove(pending.pos.asLong());
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

    private record PendingLeaf(BlockPos pos, long dueTick, int retries) {}
}
