package com.champutils.profile;

import com.champutils.database.DatabaseManager;
import com.champutils.network.NetworkServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Wires the future two-server profile flow without changing the existing single-server path.
 *
 * ALL_IN_ONE keeps using PlayerProfileManager.switchAsync.
 * PROFILE_LOBBY issues a short-lived signed token, then asks the proxy to move the player.
 * SURVIVAL consumes the newest live token on join, then loads that profile with the existing loader.
 */
public final class ProfileNetworkTransferFlow {
    private ProfileNetworkTransferFlow() {}

    public static boolean isProfileLobbyServer() {
        return NetworkServerConfig.serverRole() == NetworkServerConfig.ServerRole.PROFILE_LOBBY;
    }

    public static boolean isSurvivalServer() {
        return NetworkServerConfig.serverRole() == NetworkServerConfig.ServerRole.SURVIVAL;
    }

    public static boolean shouldUseLobbyTransferFlow() {
        return isProfileLobbyServer();
    }

    public static void issueTransferFromLobby(ServerPlayer player, PlayerProfileManager.ProfileRecord profile, Consumer<String> callback) {
        if (player == null || profile == null) {
            if (callback != null) callback.accept("Could not start profile transfer.");
            return;
        }
        if (!DatabaseManager.isEnabled()) {
            if (callback != null) callback.accept("Profiles require the SQL database to be enabled.");
            return;
        }

        NetworkServerConfig config = NetworkServerConfig.get();
        UUID playerUuid = player.getUUID();
        AtomicReference<String> issuedWireToken = new AtomicReference<>("");

        DatabaseManager.runAsync("issue profile transfer token", connection -> {
            ProfileTransferTokenManager.ensureSchema(connection);
            ProfileTransferTokenManager.IssuedToken token = ProfileTransferTokenManager.issue(
                    connection,
                    playerUuid,
                    profile.profileId(),
                    config.profileTransferSecret,
                    config.serverId,
                    config.survivalServerId,
                    config.profileTransferTtlSeconds
            );
            issuedWireToken.set(token.wireValue());
            ProfileTransferTokenManager.audit(connection, playerUuid, profile.profileId(), token.tokenId(), config.serverId, config.survivalServerId, "TOKEN_ISSUED", "lobby-profile-selected", "{}");
        }).whenComplete((ignored, error) -> player.server.execute(() -> {
            if (player.hasDisconnected()) return;
            if (error != null) {
                error.printStackTrace();
                if (callback != null) callback.accept("Could not create profile transfer token. Check profileTransferSecret/database logs.");
                return;
            }

            player.sendSystemMessage(Component.literal("Profile ready. Sending you to survival...").withStyle(ChatFormatting.GREEN));
            executeLobbyTransferCommand(player, profile, config, issuedWireToken.get());
            if (callback != null) callback.accept("Profile transfer token issued for " + profile.profileName() + ".");
        }));
    }

    /**
     * Returns true when SURVIVAL role handled join by attempting token consumption.
     * If false, normal menu/direct profile flow may continue.
     */
    public static boolean consumePendingTransferOnJoin(ServerPlayer player) {
        if (player == null || !isSurvivalServer() || !DatabaseManager.isEnabled()) return false;

        NetworkServerConfig config = NetworkServerConfig.get();
        UUID playerUuid = player.getUUID();
        String playerName = player.getGameProfile().getName();

        AtomicReference<String> transferredProfileName = new AtomicReference<>();
        DatabaseManager.runAsync("consume profile transfer token on survival join", connection -> {
            ProfileTransferTokenManager.ensureSchema(connection);
            var consumed = ProfileTransferTokenManager.consumeLatestForPlayer(connection, playerUuid, config.profileTransferSecret, config.serverId);
            if (consumed.isEmpty()) {
                if (config.allowSurvivalDirectProfileMenu) return;
                throw new IllegalStateException("No valid profile transfer token for this survival join.");
            }

            UUID profileId = consumed.get().profileId();
            String profileName = readProfileName(connection, playerUuid, profileId);
            if (profileName == null || profileName.isBlank()) {
                throw new IllegalStateException("Transferred profile no longer exists or belongs to a different player.");
            }
            transferredProfileName.set(profileName);
        }).whenComplete((ignored, error) -> player.server.execute(() -> {
            if (player.hasDisconnected()) return;

            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                System.err.println("[ChampUtils] Survival profile transfer failed for " + playerName + ": " + cause.getMessage());
                cause.printStackTrace();
                player.connection.disconnect(Component.literal("No valid profile transfer token. Please join through the profile lobby."));
                return;
            }

            String profileName = transferredProfileName.get();
            if (profileName != null && !profileName.isBlank()) {
                player.sendSystemMessage(Component.literal("Loading transferred profile...").withStyle(ChatFormatting.YELLOW));
                PlayerProfileManager.switchAsync(player, profileName, result ->
                        player.sendSystemMessage(Component.literal(result).withStyle(result.startsWith("Loaded") ? ChatFormatting.GREEN : ChatFormatting.RED)));
                return;
            }

            if (config.allowSurvivalDirectProfileMenu) {
                ProfileLobbyManager.sendToLobby(player);
            }
        }));

        return true;
    }

    private static String readProfileName(Connection connection, UUID playerUuid, UUID profileId) throws Exception {
        try (var ps = connection.prepareStatement("select name from player_profiles where id = ? and player_uuid = ? and deleted_at is null and is_pending_delete = false limit 1")) {
            ps.setObject(1, profileId);
            ps.setObject(2, playerUuid);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("name") : null;
            }
        }
    }

    private static void executeLobbyTransferCommand(ServerPlayer player, PlayerProfileManager.ProfileRecord profile, NetworkServerConfig config, String wireToken) {
        if (player == null || player.server == null || config == null) return;
        String command = config.lobbyTransferCommand;
        if (command == null || command.isBlank()) return;
        command = command
                .replace("{player}", player.getGameProfile().getName())
                .replace("{target_server}", config.survivalServerId == null ? "" : config.survivalServerId)
                .replace("{profile}", profile.profileName() == null ? "" : profile.profileName())
                .replace("{token}", wireToken == null ? "" : wireToken);
        if (command.startsWith("/")) command = command.substring(1);
        player.server.getCommands().performPrefixedCommand(player.server.createCommandSourceStack().withSuppressedOutput(), command);
    }

}
