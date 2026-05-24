package com.champutils.territory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the starter island for skyblock-style territory worlds.
 * This intentionally only touches the small starter area, not the whole territory.
 */
public final class TerritorySkyblockIslandManager {
    private TerritorySkyblockIslandManager() {}

    public static void ensureStarterIsland(ServerLevel level, TerritoryRepository.Territory territory) {
        if (level == null || territory == null) return;
        TerritoryConfig.Data cfg = TerritoryConfig.get();
        if (!cfg.skyblockTerritoryWorlds || !cfg.createSkyblockStarterIsland) return;

        int centerX = territory.centerX;
        int centerZ = territory.centerZ;
        int baseY = Math.max(level.getMinBuildHeight() + 8, Math.min(level.getMaxBuildHeight() - 16, cfg.defaultSpawnY));
        int radius = Math.max(3, cfg.skyblockIslandRadius);

        clearStarterVolume(level, centerX, baseY, centerZ, radius + 8);
        buildIsland(level, centerX, baseY, centerZ, radius);
        buildTree(level, centerX + 4, baseY + 1, centerZ + 3);
        buildSpawnPad(level, centerX, baseY + 1, centerZ);

        territory.spawnX = centerX + 0.5D;
        territory.spawnY = baseY + 2.0D;
        territory.spawnZ = centerZ + 0.5D;
    }

    private static void clearStarterVolume(ServerLevel level, int cx, int baseY, int cz, int radius) {
        int minY = Math.max(level.getMinBuildHeight(), baseY - 6);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, baseY + 20);
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void buildIsland(ServerLevel level, int cx, int baseY, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius + 0.25D) continue;

                int x = cx + dx;
                int z = cz + dz;
                int edgeDrop = (int) Math.max(0, distance - (radius - 3));
                int topY = baseY - Math.min(2, edgeDrop);

                level.setBlock(new BlockPos(x, topY, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, topY - 1, z), Blocks.DIRT.defaultBlockState(), 3);
                if (distance < radius - 2) level.setBlock(new BlockPos(x, topY - 2, z), Blocks.DIRT.defaultBlockState(), 3);
                if (distance < radius - 4) level.setBlock(new BlockPos(x, topY - 3, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }

    private static void buildSpawnPad(ServerLevel level, int cx, int y, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y + 1, cz + dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.setBlock(new BlockPos(cx, y - 1, cz), Blocks.STONE_BRICKS.defaultBlockState(), 3);
    }

    private static void buildTree(ServerLevel level, int x, int y, int z) {
        for (int i = 0; i < 5; i++) {
            level.setBlock(new BlockPos(x, y + i, z), Blocks.OAK_LOG.defaultBlockState(), 3);
        }

        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dy = 3; dy <= 6; dy++) {
            int radius = dy >= 5 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && dy >= 5) continue;
                    BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, leaves, 3);
                }
            }
        }
    }
}
