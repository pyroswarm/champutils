package com.champutils.worldevent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class WorldEventSpawnFinder {

    private static final Random RANDOM = new Random();

    private WorldEventSpawnFinder() {}

    public static BlockPos find(ServerLevel level, WorldEventConfig.EventDefinition event) {
        if (level == null || event == null) return null;

        int min = Math.max(0, Math.min(event.spawnRadiusMin, event.spawnRadiusMax));
        int max = Math.max(min + 1, Math.max(event.spawnRadiusMin, event.spawnRadiusMax));
        int attempts = Math.max(1200, event.safeSpawnAttempts * 15);

        List<BlockPos> origins = new ArrayList<>();
        BlockPos spawn = level.getSharedSpawnPos();
        origins.add(spawn == null ? BlockPos.ZERO : spawn);

        if (level.getServer() != null) {
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                if (player != null && player.level() == level) origins.add(player.blockPosition());
            }
        }
        Collections.shuffle(origins, RANDOM);

        for (BlockPos origin : origins) {
            BlockPos found = randomRingSearch(level, event, origin, min, max, attempts);
            if (found != null) return found;
        }

        // Last resort: if the configured ring is mostly ocean, scan progressively outward
        // from spawn/player origins. This keeps events random, but avoids total failure on
        // island/ocean maps where a ring can miss all land.
        int[] radii = new int[] { 64, 128, 256, 512, 1024, 2048, 4096, 8192 };
        for (BlockPos origin : origins) {
            for (int radius : radii) {
                BlockPos found = expandingGridSearch(level, event, origin, radius, 16);
                if (found != null) return found;
            }
        }

        return null;
    }

    public static BlockPos findNear(ServerLevel level, WorldEventConfig.EventDefinition event, BlockPos center, int searchRadius) {
        if (level == null || event == null || center == null) return null;

        int radius = Math.max(8, searchRadius);
        int attempts = Math.max(800, event.safeSpawnAttempts * 10);

        BlockPos centerSurface = surfacePosition(level, center.getX(), center.getZ());
        if (isValid(level, centerSurface, event)) return centerSurface;

        BlockPos found = randomRingSearch(level, event, center, 4, radius, attempts);
        if (found != null) return found;

        return expandingGridSearch(level, event, center, radius, 8);
    }

    private static BlockPos randomRingSearch(ServerLevel level, WorldEventConfig.EventDefinition event, BlockPos origin, int min, int max, int attempts) {
        if (origin == null) return null;
        for (int i = 0; i < attempts; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2D;
            int radius = min + RANDOM.nextInt((max - min) + 1);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos pos = surfacePosition(level, x, z);
            if (isValid(level, pos, event)) return pos;
        }
        return null;
    }

    private static BlockPos expandingGridSearch(ServerLevel level, WorldEventConfig.EventDefinition event, BlockPos origin, int radius, int stepChunks) {
        if (origin == null) return null;
        int step = Math.max(16, stepChunks * 16);
        List<BlockPos> candidates = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx += step) {
            candidates.add(new BlockPos(origin.getX() + dx, origin.getY(), origin.getZ() - radius));
            candidates.add(new BlockPos(origin.getX() + dx, origin.getY(), origin.getZ() + radius));
        }
        for (int dz = -radius + step; dz <= radius - step; dz += step) {
            candidates.add(new BlockPos(origin.getX() - radius, origin.getY(), origin.getZ() + dz));
            candidates.add(new BlockPos(origin.getX() + radius, origin.getY(), origin.getZ() + dz));
        }
        Collections.shuffle(candidates, RANDOM);

        for (BlockPos candidate : candidates) {
            BlockPos pos = surfacePosition(level, candidate.getX(), candidate.getZ());
            if (isValid(level, pos, event)) return pos;
        }
        return null;
    }

    private static BlockPos surfacePosition(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);

        // Heightmaps sometimes place the feet position on top of snow/tall grass or just
        // above leaves/fluids. Walk down a little until we find a real floor with air above.
        int minY = level.getMinBuildHeight() + 1;
        for (int i = 0; i < 12 && pos.getY() > minY; i++) {
            BlockState floor = level.getBlockState(pos.below());
            BlockState feet = level.getBlockState(pos);
            if (!floor.isAir() && floor.getFluidState().isEmpty()
                    && !floor.is(BlockTags.LEAVES)
                    && !floor.is(BlockTags.LOGS)
                    && feet.getFluidState().isEmpty()) {
                return pos;
            }
            pos = pos.below();
        }
        return pos;
    }

    private static boolean isValid(ServerLevel level, BlockPos pos, WorldEventConfig.EventDefinition event) {
        if (pos == null) return false;
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        if (pos.getY() <= level.getMinBuildHeight() + 1 || pos.getY() >= level.getMaxBuildHeight() - 2) return false;

        BlockState floor = level.getBlockState(pos.below());
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());

        if (floor.isAir()) return false;
        if (!floor.getFluidState().isEmpty()) return false;
        if (floor.is(BlockTags.LEAVES)) return false;
        if (floor.is(BlockTags.LOGS)) return false;

        if (!feet.getFluidState().isEmpty()) return false;
        if (!head.getFluidState().isEmpty()) return false;

        if (!feet.isAir() && !feet.getCollisionShape(level, pos).isEmpty()) return false;
        if (!head.isAir() && !head.getCollisionShape(level, pos.above()).isEmpty()) return false;

        // Also require the NPC's footprint to have some room so it does not appear clipped
        // into a cliff, tree, or cave wall.
        for (BlockPos check : new BlockPos[] { pos.north(), pos.south(), pos.east(), pos.west() }) {
            BlockState state = level.getBlockState(check);
            if (!state.isAir() && !state.getCollisionShape(level, check).isEmpty()) return false;
        }

        return !WorldEventConfig.REQUIRE_FLAN_UNCLAIMED || FlanClaimCompat.isAreaUnclaimed(level, pos, event.avoidClaimRadius);
    }
}
