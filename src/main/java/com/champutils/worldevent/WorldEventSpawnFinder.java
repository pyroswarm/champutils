package com.champutils.worldevent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

public final class WorldEventSpawnFinder {

    private static final Random RANDOM = new Random();

    private WorldEventSpawnFinder() {}

    public static BlockPos find(ServerLevel level, WorldEventConfig.EventDefinition event) {
        if (level == null || event == null) return null;

        int min = Math.max(0, Math.min(event.spawnRadiusMin, event.spawnRadiusMax));
        int max = Math.max(min + 1, Math.max(event.spawnRadiusMin, event.spawnRadiusMax));
        int attempts = Math.max(1, event.safeSpawnAttempts);

        for (int i = 0; i < attempts; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2D;
            int radius = min + RANDOM.nextInt((max - min) + 1);
            int x = (int)Math.round(Math.cos(angle) * radius);
            int z = (int)Math.round(Math.sin(angle) * radius);
            BlockPos pos = surfacePosition(level, x, z);
            if (isValid(level, pos, event)) return pos;
        }

        return null;
    }

    /**
     * Manual/admin event starts should be easy to test from wherever the admin is standing.
     * This ignores spawnRadiusMin/spawnRadiusMax and searches around the supplied center.
     * Automatic events still use find(...) and respect the configured radius from world spawn/origin.
     */
    public static BlockPos findNear(ServerLevel level, WorldEventConfig.EventDefinition event, BlockPos center, int searchRadius) {
        if (level == null || event == null || center == null) return null;

        int radius = Math.max(8, searchRadius);
        int attempts = Math.max(60, event.safeSpawnAttempts);

        BlockPos centerSurface = surfacePosition(level, center.getX(), center.getZ());
        if (isValid(level, centerSurface, event)) return centerSurface;

        for (int i = 0; i < attempts; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2D;
            int distance = 4 + RANDOM.nextInt(radius);
            int x = center.getX() + (int)Math.round(Math.cos(angle) * distance);
            int z = center.getZ() + (int)Math.round(Math.sin(angle) * distance);
            BlockPos pos = surfacePosition(level, x, z);
            if (isValid(level, pos, event)) return pos;
        }

        return null;
    }

    private static BlockPos surfacePosition(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private static boolean isValid(ServerLevel level, BlockPos pos, WorldEventConfig.EventDefinition event) {
        if (pos == null) return false;
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        if (level.getBlockState(pos.below()).isAir()) return false;
        if (!level.getBlockState(pos).isAir()) return false;
        if (!level.getBlockState(pos.above()).isAir()) return false;

        if (WorldEventConfig.REQUIRE_FLAN_UNCLAIMED && !FlanClaimCompat.isAreaUnclaimed(level, pos, event.avoidClaimRadius)) {
            return false;
        }

        return true;
    }
}
