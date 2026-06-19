package com.champutils.permissions;

import com.champutils.badge.BadgeUnlockManager;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Central LuckPerms-aware command permission helper.
 *
 * Minecraft's CommandSourceStack#hasPermission checks vanilla/op permission levels.
 * This helper keeps ops working, allows console/command blocks, and also allows
 * non-opped staff to run protected commands through LuckPerms nodes.
 */
public final class PermissionUtil {
    private PermissionUtil() {}

    public static boolean has(CommandSourceStack source, String permission) {
        if (source == null || permission == null || permission.isBlank()) return false;

        // Console and command blocks should keep access to admin commands.
        if (source.getEntity() == null) return true;

        // Server ops remain full administrators.
        if (source.hasPermission(4)) return true;

        ServerPlayer player = source.getPlayer();
        if (player == null) return false;

        // champutils.admin should imply all ChampUtils staff/admin commands even if
        // LuckPerms group inheritance is not configured yet.
        if (!permission.equals("champutils.admin") && LuckPermsHook.hasPermission(player, "champutils.admin")) {
            return true;
        }

        return LuckPermsHook.hasPermission(player, permission) || BadgeUnlockManager.grantsPermission(player, permission);
    }
}
