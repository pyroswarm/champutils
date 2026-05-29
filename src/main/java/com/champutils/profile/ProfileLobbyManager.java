package com.champutils.profile;

import com.champutils.menu.ProfileSelectionMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Simple neutral profile lobby.
 *
 * Lobby state is intentionally NOT a profile. While a player is here,
 * PlayerProfileManager has no active profile loaded for the account.
 */
public final class ProfileLobbyManager {
    public static final String PROFILE_LOBBY_DIMENSION = "multiworld:profile_lobby";

    public static final double LOBBY_X = 100.5D;
    public static final double LOBBY_Y = 100.0D;
    public static final double LOBBY_Z = 0.5D;
    public static final float LOBBY_YAW = 0.0F;
    public static final float LOBBY_PITCH = 0.0F;

    private ProfileLobbyManager() {}

    public static void sendToLobby(ServerPlayer player) {
        if (player == null || player.server == null) return;

        // Critical: the lobby is no-profile-loaded, never a profile.
        PlayerProfileManager.clearActiveForMenu(player);

        // Remove live profile-bound data so it cannot bleed into the next profile.
        CobblemonProfileStateManager.clearLive(player);
        VanillaProfileStateManager.clearLiveForMenu(player);

        applyLobbyProtections(player);
        teleportToLobby(player);
        ProfileSelectionMenu.open(player);
    }

    public static boolean isInLobby(ServerPlayer player) {
        return player != null && PlayerProfileManager.isInMainMenu(player);
    }

    public static void leaveLobby(ServerPlayer player) {
        if (player == null) return;
        ProfileSelectionMenu.clearForcedReopener(player);
        applyNormalPlayerState(player);
    }

    public static void teleportToLobby(ServerPlayer player) {
        if (player == null || player.server == null) return;
        ServerLevel level = resolveLobbyLevel(player);
        player.teleportTo(level, LOBBY_X, LOBBY_Y, LOBBY_Z, LOBBY_YAW, LOBBY_PITCH);
        player.setYRot(LOBBY_YAW);
        player.setYHeadRot(LOBBY_YAW);
        player.setXRot(LOBBY_PITCH);
        applyLobbyProtections(player);
    }

    public static ServerLevel resolveLobbyLevel(ServerPlayer player) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(PROFILE_LOBBY_DIMENSION));
        ServerLevel lobby = player.server.getLevel(key);
        if (lobby != null) {
            return lobby;
        }

        player.sendSystemMessage(Component.literal("Profile lobby dimension is missing. Falling back to overworld. Create/load " + PROFILE_LOBBY_DIMENSION + ".").withStyle(ChatFormatting.YELLOW));
        return player.server.overworld();
    }

    private static boolean isInProfileLobbyDimension(ServerPlayer player) {
        return player != null
                && player.serverLevel() != null
                && PROFILE_LOBBY_DIMENSION.equals(player.serverLevel().dimension().location().toString());
    }

    public static void applyLobbyProtections(ServerPlayer player) {
        if (player == null) return;
        player.setInvulnerable(true);
        player.setInvisible(isInProfileLobbyDimension(player));
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.resetFallDistance();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        player.clearFire();
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
        player.inventoryMenu.broadcastChanges();
    }

    public static void applyNormalPlayerState(ServerPlayer player) {
        if (player == null) return;
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.resetFallDistance();
    }
}
