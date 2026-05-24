package com.champutils.territory;

import com.champutils.teleport.SafeTeleportManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

public final class TerritoryTeleportUtil {
    private TerritoryTeleportUtil() {}

    public static boolean teleportHome(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null) return false;
        ServerLevel level = resolveLevel(player.server, territory.worldName);
        if (level == null) return false;

        SafeSpot spot = findSafeSpot(level, territory.spawnX, territory.spawnY, territory.spawnZ);
        return SafeTeleportManager.teleport(player, level, spot.x, spot.y, spot.z, territory.spawnYaw, territory.spawnPitch);
    }

    public static boolean teleportInside(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null) return false;
        ServerLevel level = resolveLevel(player.server, territory.worldName);
        if (level == null) return false;

        double minX = Math.min(territory.minX + 2.5D, territory.maxX - 0.5D);
        double maxX = Math.max(territory.maxX - 2.5D, territory.minX + 0.5D);
        double minZ = Math.min(territory.minZ + 2.5D, territory.maxZ - 0.5D);
        double maxZ = Math.max(territory.maxZ - 2.5D, territory.minZ + 0.5D);

        double x = Math.max(minX, Math.min(maxX, player.getX()));
        double z = Math.max(minZ, Math.min(maxZ, player.getZ()));
        SafeSpot spot = findSafeSpot(level, x, player.getY(), z);
        return SafeTeleportManager.teleportNoBack(player, level, spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
    }

    public static ServerLevel resolveLevel(MinecraftServer server, String worldName) {
        if (server == null || worldName == null || worldName.isBlank()) return null;
        try {
            ResourceLocation id = ResourceLocation.parse(worldName);
            ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
            return server.getLevel(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static double safeY(ServerLevel level, double x, double preferredY, double z) {
        return findSafeSpot(level, x, preferredY, z).y;
    }

    public static SafeSpot findSafeSpot(ServerLevel level, double x, double preferredY, double z) {
        if (level == null) return new SafeSpot(x, preferredY, z);

        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);

        // Force the destination chunk to exist before asking the heightmap. Without this, a brand-new Multiworld
        // dimension can report a bogus min-height result and /territory home may drop players at Y -63.
        forceChunk(level, ix, iz);

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        int heightmapY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
        BlockPos heightmapSpot = safeStandingPosAtOrBelow(level, ix, iz, Math.min(maxY - 2, heightmapY + 4), minY + 1);
        if (heightmapSpot != null) {
            return centered(heightmapSpot, x, z);
        }

        BlockPos preferredSpot = safeStandingPosAtOrBelow(level, ix, iz, Math.min(maxY - 2, (int) Math.ceil(preferredY) + 8), minY + 1);
        if (preferredSpot != null) {
            return centered(preferredSpot, x, z);
        }

        BlockPos fullScanSpot = safeStandingPosAtOrBelow(level, ix, iz, maxY - 2, minY + 1);
        if (fullScanSpot != null) {
            return centered(fullScanSpot, x, z);
        }

        // Last resort: never use the void/min-build-height for home teleports.
        return new SafeSpot(ix + 0.5D, Math.max(64.0D, minY + 4.0D), iz + 0.5D);
    }

    private static void forceChunk(ServerLevel level, int blockX, int blockZ) {
        try {
            ChunkAccess ignored = level.getChunk(blockX >> 4, blockZ >> 4);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to force-load territory destination chunk at " + blockX + ", " + blockZ);
            e.printStackTrace();
        }
    }

    private static BlockPos safeStandingPosAtOrBelow(ServerLevel level, int x, int z, int fromY, int minY) {
        BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos(x, fromY, z);
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos(x, fromY + 1, z);
        BlockPos.MutableBlockPos head = new BlockPos.MutableBlockPos(x, fromY + 2, z);

        for (int y = fromY; y >= minY; y--) {
            ground.set(x, y, z);
            feet.set(x, y + 1, z);
            head.set(x, y + 2, z);

            BlockState groundState = level.getBlockState(ground);
            BlockState feetState = level.getBlockState(feet);
            BlockState headState = level.getBlockState(head);

            if (isGoodGround(groundState) && isOpen(feetState) && isOpen(headState)) {
                return feet.immutable();
            }
        }
        return null;
    }

    private static boolean isGoodGround(BlockState state) {
        return state != null
                && !state.isAir()
                && state.getFluidState().isEmpty()
                && state.blocksMotion();
    }

    private static boolean isOpen(BlockState state) {
        return state == null || (!state.blocksMotion() && state.getFluidState().isEmpty());
    }

    private static SafeSpot centered(BlockPos feet, double requestedX, double requestedZ) {
        return new SafeSpot(Math.floor(requestedX) + 0.5D, feet.getY(), Math.floor(requestedZ) + 0.5D);
    }

    public record SafeSpot(double x, double y, double z) {}
}
