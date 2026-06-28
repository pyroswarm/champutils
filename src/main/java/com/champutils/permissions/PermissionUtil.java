package com.champutils.permissions;

import com.champutils.network.NetworkServerConfig;

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

        // The profile lobby must be able to run without LuckPerms installed.
        // During login Minecraft asks Brigadier which commands the player can use.
        // If this method touches LuckPermsHook while LuckPerms is absent, classloading
        // crashes the server before the player can finish joining.
        if (NetworkServerConfig.serverRole() == NetworkServerConfig.ServerRole.PROFILE_LOBBY) {
            // Let the known owner UUID manage the lightweight profile lobby without requiring LuckPerms.
            // OP still works through source.hasPermission(4) above, but this also supports setup commands
            // if the player is temporarily de-opped while debugging Velocity command-tree issues.
            if (player.getUUID().toString().equalsIgnoreCase("d3012576-dc9f-41f4-baac-e926f3e053c2")) {
                return permission.equals("champutils.staff") || permission.equals("champutils.admin");
            }
            return false;
        }

        // champutils.admin should imply all ChampUtils staff/admin commands even if
        // LuckPerms group inheritance is not configured yet.
        if (!permission.equals("champutils.admin") && LuckPermsHook.hasPermission(player, "champutils.admin")) {
            return true;
        }

        return LuckPermsHook.hasPermission(player, permission);
    }
}
