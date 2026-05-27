package com.champutils.profile;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

/**
 * Moves players into the profile selector state.
 *
 * In this state ChampUtils treats the account as having no active profile.
 * The configured special dimension should exist as a datapack/custom dimension.
 * If it is not loaded, the manager safely falls back to the overworld high up.
 */
public final class ProfileMainMenuManager {
    private static final String MAIN_MENU_DIMENSION = "champutils:profile_menu";
    private static final double MENU_X = 0.5D;
    private static final double MENU_Y = 128.0D;
    private static final double MENU_Z = 0.5D;

    private ProfileMainMenuManager() {}

    public static void enter(ServerPlayer player, boolean saveCurrentProfile) {
        if (player == null || player.server == null) return;

        if (saveCurrentProfile && PlayerProfileManager.hasActiveProfile(player)) {
            VanillaProfileStateManager.save(player);
            CobblemonProfileStateManager.save(player);
        }

        PlayerProfileManager.clearActiveForMenu(player);
        CobblemonProfileStateManager.clearLive(player);
        VanillaProfileStateManager.clearLiveForMenu(player);

        teleportToMenu(player);

        player.sendSystemMessage(Component.literal("Select a profile to enter the server.").withStyle(ChatFormatting.AQUA));
    }

    public static void teleportToMenu(ServerPlayer player) {
        if (player == null || player.server == null) return;
        ServerLevel menuLevel = findMenuLevel(player);
        player.teleportTo(menuLevel, MENU_X, MENU_Y, MENU_Z, 0.0F, 0.0F);
        player.setYRot(0.0F);
        player.setYHeadRot(0.0F);
        player.setXRot(0.0F);
    }

    private static ServerLevel findMenuLevel(ServerPlayer player) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(MAIN_MENU_DIMENSION));
        ServerLevel level = player.server.getLevel(key);
        if (level != null) return level;
        return player.server.overworld();
    }
}
