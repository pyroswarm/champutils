package com.champutils.profile;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Compatibility wrapper for the profile main menu flow.
 * The actual simple lobby implementation lives in ProfileLobbyManager.
 */
public final class ProfileMainMenuManager {
    private ProfileMainMenuManager() {}

    public static void enter(ServerPlayer player, boolean saveCurrentProfile) {
        if (player == null || player.server == null) return;

        if (saveCurrentProfile && PlayerProfileManager.hasActiveProfile(player)) {
            PlayerProfileManager.saveActiveLocationAsync(player);
            VanillaProfileStateManager.saveAsync(player);
            CobblemonProfileStateManager.save(player);
            ProfileSessionLoader.unload(player);
        }

        ProfileLobbyManager.sendToLobby(player);
        player.sendSystemMessage(Component.literal("Select a profile to enter the server.").withStyle(ChatFormatting.AQUA));
    }

    public static void teleportToMenu(ServerPlayer player) {
        ProfileLobbyManager.teleportToLobby(player);
    }
}
