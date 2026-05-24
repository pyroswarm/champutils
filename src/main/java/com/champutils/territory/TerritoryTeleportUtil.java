package com.champutils.territory;

import com.champutils.teleport.SafeTeleportManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class TerritoryTeleportUtil {
    private TerritoryTeleportUtil() {}

    public static boolean teleportHome(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null) return false;
        ServerLevel level = resolveLevel(player.server, territory.worldName);
        if (level == null) return false;
        double y = safeY(level, territory.spawnX, territory.spawnY, territory.spawnZ);
        return SafeTeleportManager.teleport(player, level, territory.spawnX, y, territory.spawnZ, territory.spawnYaw, territory.spawnPitch);
    }

    public static boolean teleportInside(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null) return false;
        ServerLevel level = resolveLevel(player.server, territory.worldName);
        if (level == null) return false;
        double x = Math.max(territory.minX + 2.5D, Math.min(territory.maxX - 2.5D, player.getX()));
        double z = Math.max(territory.minZ + 2.5D, Math.min(territory.maxZ - 2.5D, player.getZ()));
        double y = safeY(level, x, player.getY(), z);
        return SafeTeleportManager.teleportNoBack(player, level, x, y, z, player.getYRot(), player.getXRot());
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

    private static double safeY(ServerLevel level, double x, double preferredY, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz) + 1;
        if (top > level.getMinBuildHeight() && top < level.getMaxBuildHeight()) {
            return top;
        }
        return Math.max(level.getMinBuildHeight() + 2, Math.min(level.getMaxBuildHeight() - 2, preferredY));
    }
}
