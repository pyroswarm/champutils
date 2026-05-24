package com.champutils.territory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TerritoryPhysicalBorderManager {
    private static final ArrayDeque<BorderColumn> QUEUE = new ArrayDeque<>();
    private static final Set<String> QUEUED = new HashSet<>();
    private static final Set<UUID> SCHEDULED_TERRITORIES = new HashSet<>();
    private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();

    private TerritoryPhysicalBorderManager() {}

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        TerritoryConfig.Data config = TerritoryConfig.get();
        if (!config.enabled || !config.physicalBarrierBorders) return;

        scheduleMissingBorders(server);
        buildQueuedColumns(server, config.barrierColumnsPerTick);
    }

    private static void scheduleMissingBorders(MinecraftServer server) {
        for (TerritoryRepository.Territory territory : TerritoryRepository.allCached()) {
            if (territory == null || territory.id == null) continue;
            if (!territory.lockBorder || !territory.isReady()) continue;
            if (SCHEDULED_TERRITORIES.contains(territory.id)) continue;

            ServerLevel level = TerritoryTeleportUtil.resolveLevel(server, territory.worldName);
            if (level == null) continue;

            scheduleTerritory(territory);
            SCHEDULED_TERRITORIES.add(territory.id);
        }
    }

    private static void scheduleTerritory(TerritoryRepository.Territory territory) {
        // Put the physical wall one block OUTSIDE the owned area so players do not lose the usable edge block.
        int minX = territory.minX - 1;
        int maxX = territory.maxX + 1;
        int minZ = territory.minZ - 1;
        int maxZ = territory.maxZ + 1;

        for (int x = minX; x <= maxX; x++) {
            enqueue(territory.worldName, x, minZ);
            enqueue(territory.worldName, x, maxZ);
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            enqueue(territory.worldName, minX, z);
            enqueue(territory.worldName, maxX, z);
        }
    }

    private static void enqueue(String worldName, int x, int z) {
        String key = worldName + ":" + x + ":" + z;
        if (!QUEUED.add(key)) return;
        QUEUE.addLast(new BorderColumn(worldName, x, z, key));
    }

    private static void buildQueuedColumns(MinecraftServer server, int maxColumns) {
        int built = 0;
        while (built < maxColumns && !QUEUE.isEmpty()) {
            BorderColumn column = QUEUE.pollFirst();
            if (column == null) continue;
            QUEUED.remove(column.key);

            ServerLevel level = TerritoryTeleportUtil.resolveLevel(server, column.worldName);
            if (level == null) continue;

            buildColumn(level, column.x, column.z);
            built++;
        }
    }

    private static void buildColumn(ServerLevel level, int x, int z) {
        try {
            level.getChunk(x >> 4, z >> 4);
        } catch (Exception ignored) {
            return;
        }

        int minY = level.getMinBuildHeight();
        int topY = level.getMaxBuildHeight() - 1; // one block below the build-height limit; highest legal block.
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, minY, z);

        for (int y = minY; y <= topY; y++) {
            pos.set(x, y, z);
            if (level.getBlockState(pos).getBlock() != Blocks.BARRIER) {
                level.setBlock(pos, BARRIER, 3);
            }
        }
    }

    private record BorderColumn(String worldName, int x, int z, String key) {}
}
