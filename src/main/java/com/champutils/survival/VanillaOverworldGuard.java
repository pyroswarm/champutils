package com.champutils.survival;

import com.champutils.profile.ProfileNetworkTransferFlow;
import com.champutils.teleport.SafeTeleportManager;
import com.champutils.teleport.TeleportConfig;
import com.champutils.teleport.TeleportLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Keeps gameplay players out of the unused vanilla overworld without scanning worlds or chunks. */
public final class VanillaOverworldGuard {
    private static final int CHECK_INTERVAL_TICKS = 10;

    private VanillaOverworldGuard() {}

    public static void tick(MinecraftServer server) {
        if (server == null || ProfileNetworkTransferFlow.isProfileLobbyServer()) return;
        if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || player.hasDisconnected()) continue;
            if (!player.serverLevel().dimension().equals(Level.OVERWORLD)) continue;
            sendToSpawn(player);
        }
    }

    private static void sendToSpawn(ServerPlayer player) {
        TeleportLocation configuredSpawn = TeleportConfig.getSpawn();
        if (configuredSpawn != null && configuredSpawn.dimension != null
                && configuredSpawn.dimension.toLowerCase(java.util.Locale.ROOT).contains("spawn1")) {
            ServerLevel configuredLevel = TeleportConfig.resolveLevel(player.server, configuredSpawn.dimension);
            if (configuredLevel != null && SafeTeleportManager.teleportUncheckedNoBack(
                    player, configuredLevel, configuredSpawn.x, configuredSpawn.y, configuredSpawn.z, configuredSpawn.yaw, configuredSpawn.pitch)) {
                return;
            }
        }

        ServerLevel spawn = TeleportConfig.resolveLevel(player.server, "multiworld:spawn1");
        if (spawn == null) return;
        BlockPos pos = spawn.getSharedSpawnPos();
        if (SafeTeleportManager.teleportUncheckedNoBack(player, spawn, pos.getX() + 0.5D, pos.getY() + 0.1D, pos.getZ() + 0.5D, player.getYRot(), player.getXRot())) {
            player.sendSystemMessage(Component.literal("You were returned to spawn."));
        }
    }
}
