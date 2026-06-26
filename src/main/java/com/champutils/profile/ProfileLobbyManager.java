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
import net.minecraft.world.level.GameType;
import net.minecraft.core.BlockPos;

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

    public static final int LOBBY_SPAWN_X = 100;
    public static final int LOBBY_SPAWN_Y = 100;
    public static final int LOBBY_SPAWN_Z = 0;

    public static final double PROFILE_NPC_X = 100.5D;
    public static final double PROFILE_NPC_Y = 100.0D;
    public static final double PROFILE_NPC_Z = 0.5D;
    public static final float LOBBY_YAW = 90.0F;
    public static final float LOBBY_PITCH = 0.0F;

    private ProfileLobbyManager() {}

    public static void sendToLobby(ServerPlayer player) {
        if (player == null || player.server == null) return;
        ProfileLobbyDebug.log("sendToLobby.begin", player);

        // Critical: the lobby is no-profile-loaded, never a profile.
        PlayerProfileManager.clearActiveForMenu(player);

        // In network PROFILE_LOBBY mode, do not clear live Cobblemon/vanilla inventories during join.
        // That packet burst can happen before Velocity/Polymer has fully finished the backend play handshake.
        // The lobby server should have an isolated lightweight world and no profile data to protect anyway.
        if (!ProfileNetworkTransferFlow.isProfileLobbyServer()) {
            CobblemonProfileStateManager.clearLive(player);
            VanillaProfileStateManager.clearLiveForMenu(player);
        }

        applyLobbyProtections(player);
        teleportToLobby(player);
        if (ProfileNetworkTransferFlow.isProfileLobbyServer()) {
            ProfileLobbyDebug.log("sendToLobby.menuAutoOpen.skippedProfileLobby", player);
            player.sendSystemMessage(Component.literal("Right-click the Select a Profile NPC or use /profiles to choose a profile.").withStyle(ChatFormatting.YELLOW));
        } else {
            player.server.execute(() -> {
                if (player.hasDisconnected()) return;
                ProfileLobbyDebug.log("sendToLobby.openMenu.delayed.allInOne", player);
                ProfileSelectionMenu.open(player);
            });
        }
        ProfileLobbyDebug.log("sendToLobby.end", player);
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
        applyProfileWorldSpawn(level);
        player.teleportTo(level, LOBBY_X, LOBBY_Y, LOBBY_Z, LOBBY_YAW, LOBBY_PITCH);
        player.setYRot(LOBBY_YAW);
        player.setYHeadRot(LOBBY_YAW);
        player.setXRot(LOBBY_PITCH);
        applyLobbyProtections(player);
    }

    public static void applyProfileWorldSpawn(ServerLevel level) {
        if (level == null) return;
        if (!PROFILE_LOBBY_DIMENSION.equals(level.dimension().location().toString())) return;
        try {
            level.setDefaultSpawnPos(new BlockPos(LOBBY_SPAWN_X, LOBBY_SPAWN_Y, LOBBY_SPAWN_Z), LOBBY_YAW);
        } catch (Exception ignored) {
        }
    }

    public static ResourceKey<Level> resolveLobbyKey() {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(PROFILE_LOBBY_DIMENSION));
    }

    public static ServerLevel resolveLobbyLevel(ServerPlayer player) {
        ResourceKey<Level> key = resolveLobbyKey();
        ServerLevel lobby = player.server.getLevel(key);
        if (lobby != null) {
            return lobby;
        }

        throw new IllegalStateException("Profile lobby dimension is missing: " + PROFILE_LOBBY_DIMENSION + ". Refusing to fall back to overworld.");
    }

    private static boolean isInProfileLobbyDimension(ServerPlayer player) {
        return player != null
                && player.serverLevel() != null
                && PROFILE_LOBBY_DIMENSION.equals(player.serverLevel().dimension().location().toString());
    }

    public static void applyLobbyProtections(ServerPlayer player) {
        if (player == null) return;
        player.setInvulnerable(true);
        boolean profileDimension = isInProfileLobbyDimension(player);
        player.setInvisible(profileDimension);
        if (profileDimension) {
            try { player.setGameMode(GameType.ADVENTURE); } catch (Exception ignored) {}
        }
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.resetFallDistance();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        player.clearFire();
        if (!ProfileNetworkTransferFlow.isProfileLobbyServer()) {
            player.getInventory().clearContent();
            player.getEnderChestInventory().clearContent();
            player.inventoryMenu.broadcastChanges();
        }
    }

    public static void applyNormalPlayerState(ServerPlayer player) {
        if (player == null) return;
        try { player.setGameMode(GameType.SURVIVAL); } catch (Exception ignored) {}
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.resetFallDistance();
    }
}
