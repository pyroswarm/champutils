package com.champutils.teleport;

import com.champutils.matchmaking.ArenaManager;
import com.champutils.adventurer.AdventurerGuildManager;
import com.champutils.territory.TerritoryRepository;
import com.champutils.worldborder.ChampWorldBorderConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Central player teleport helper.
 * Stores /back locations before normal teleports and blocks destinations that the
 * player should not be able to return to later.
 */
public final class SafeTeleportManager {
    private SafeTeleportManager() {}

    public static boolean teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        return teleport(player, level, x, y, z, yaw, pitch, true, true);
    }

    public static boolean teleportNoBack(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        return teleport(player, level, x, y, z, yaw, pitch, false, true);
    }

    public static boolean teleportUncheckedNoBack(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        return teleport(player, level, x, y, z, yaw, pitch, false, false);
    }

    public static boolean teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch, boolean rememberBack, boolean validateDestination) {
        if (!isLive(player) || level == null) return false;
        if (validateDestination && !canTeleportTo(player, level, x, y, z)) {
            player.sendSystemMessage(Component.literal("You cannot teleport to that location.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (rememberBack) BackManager.remember(player);
        prepareForTeleport(player);
        player.teleportTo(level, x, y, z, yaw, pitch);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.resetFallDistance();
        return true;
    }

    /**
     * ServerPlayer objects can stay referenced briefly after logout/profile transfer.
     * Teleporting or sending packets through those stale objects is what produces the
     * "Fetching packet for removed entity ... removed=UNLOADED_WITH_PLAYER" spam and
     * can leave other clients seeing a ghost/old player position.
     */
    public static boolean isLive(ServerPlayer player) {
        if (player == null) return false;
        if (player.server == null || player.connection == null) return false;
        if (player.hasDisconnected() || player.isRemoved()) return false;
        ServerPlayer current = player.server.getPlayerList().getPlayer(player.getUUID());
        return current == player;
    }

    public static void prepareForTeleport(ServerPlayer player) {
        if (player == null) return;
        try {
            if (player.isPassenger()) player.stopRiding();
            player.ejectPassengers();
        } catch (Throwable ignored) {
        }
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.resetFallDistance();
    }

    public static boolean canTeleportTo(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        if (player == null || level == null) return false;
        if (ArenaManager.isArenaLocation(level, x, z)) return false;
        if (AdventurerGuildManager.isBattleTowerDestination(level, x, y, z) && !AdventurerGuildManager.isAttemptingBattleTower(player)) return false;

        ChampWorldBorderConfig.BorderEntry border = ChampWorldBorderConfig.get(level.dimension().location().toString());
        if (border != null) {
            double minX = border.centerX - border.radius;
            double maxX = border.centerX + border.radius;
            double minZ = border.centerZ - border.radius;
            double maxZ = border.centerZ + border.radius;
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                return false;
            }
        }

        TerritoryRepository.Territory territory = TerritoryRepository.findAt(level, BlockPos.containing(x, y, z));
        if (territory != null && !TerritoryRepository.canEnter(player, territory)) return false;

        // Territory worlds are intentionally partitioned. Do not allow /back or random
        // teleports to land in the empty space between territories.
        if (territory == null && TerritoryRepository.isTerritoryWorld(level)) return false;
        return true;
    }
}
