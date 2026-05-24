package com.champutils.teleport;

import com.champutils.matchmaking.ArenaManager;
import com.champutils.territory.TerritoryRepository;
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
        if (player == null || level == null) return false;
        if (validateDestination && !canTeleportTo(player, level, x, y, z)) {
            player.sendSystemMessage(Component.literal("You cannot teleport to that location.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (rememberBack) BackManager.remember(player);
        player.teleportTo(level, x, y, z, yaw, pitch);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
        return true;
    }

    public static boolean canTeleportTo(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        if (player == null || level == null) return false;
        if (ArenaManager.isArenaLocation(level, x, z)) return false;

        TerritoryRepository.Territory territory = TerritoryRepository.findAt(level, BlockPos.containing(x, y, z));
        if (territory != null && !TerritoryRepository.canEnter(player, territory)) return false;

        // Territory worlds are intentionally partitioned. Do not allow /back or random
        // teleports to land in the empty space between territories.
        if (territory == null && TerritoryRepository.isTerritoryWorld(level)) return false;
        return true;
    }
}
