package com.champutils.profile;

import com.champutils.debug.ChampDebugManager;
import com.champutils.database.DatabaseManager;
import com.champutils.network.NetworkServerConfig;
import com.champutils.teleport.SafeTeleportManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final ConcurrentHashMap<UUID, AcceptedTransferSession> ACCEPTED_SURVIVAL_SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> SURVIVAL_CONSUME_IN_FLIGHT = new ConcurrentHashMap<>();
    private static final long ACCEPTED_SESSION_TTL_MS = 120_000L;
    private static final int SURVIVAL_TOKEN_WAIT_ATTEMPTS = 20;
    private static final long SURVIVAL_TOKEN_WAIT_SLEEP_MS = 250L;
    private static final ConcurrentHashMap<UUID, Long> LOBBY_TRANSFER_IN_FLIGHT = new ConcurrentHashMap<>();
    private static final long LOBBY_TRANSFER_DEDUPE_MS = 15_000L;

    private ProfileNetworkTransferFlow() {}

    private record AcceptedTransferSession(UUID profileId, String profileName, String payloadJson, long acceptedAtMillis) {
        boolean isFresh(long now) {
            return now - acceptedAtMillis <= ACCEPTED_SESSION_TTL_MS;
        }
    }

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
        NetworkServerConfig config = NetworkServerConfig.get();
        String targetServerId = config.survivalServerId == null || config.survivalServerId.isBlank() ? "main_survival1" : config.survivalServerId;
        issueTransferFromLobby(player, profile, targetServerId, callback);
    }

    public static void issueTransferFromLobby(ServerPlayer player, PlayerProfileManager.ProfileRecord profile, String targetServerId, Consumer<String> callback) {
        if (player == null || profile == null) {
            if (callback != null) callback.accept("Could not start profile transfer.");
            return;
        }
        if (!DatabaseManager.isEnabled()) {
            if (callback != null) callback.accept("Profiles require the SQL database to be enabled.");
            return;
        }

        NetworkServerConfig config = NetworkServerConfig.get();
        String cleanTargetServerId = targetServerId == null || targetServerId.isBlank()
                ? (config.survivalServerId == null || config.survivalServerId.isBlank() ? "main_survival1" : config.survivalServerId)
                : targetServerId.trim();
        UUID playerUuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long existingTransfer = LOBBY_TRANSFER_IN_FLIGHT.get(playerUuid);
        if (existingTransfer != null && now - existingTransfer < LOBBY_TRANSFER_DEDUPE_MS) {
            if (callback != null) callback.accept("Profile transfer is already in progress.");
            return;
        }

        // Capture live state before any proxy/backend handoff. The snapshot is committed inside
        // the same DB task that issues the token, so the token payload can never be built from
        // stale party/PC SQL after an instant Alpha -> Omega transfer.
        ProfileStateFlushService.TransferFlushSnapshot transferSnapshot =
                ProfileStateFlushService.captureBeforeTransfer(player, "network_profile_transfer");

        if (!ProfileStateFlushService.flushBeforeTransfer(player, "network_profile_transfer", 3, java.util.concurrent.TimeUnit.SECONDS)) {
            if (callback != null) callback.accept("Could not safely save your profile before transfer. Please try again.");
            return;
        }
        LOBBY_TRANSFER_IN_FLIGHT.put(playerUuid, now);
        AtomicReference<String> issuedWireToken = new AtomicReference<>("");

        DatabaseManager.runAsync("issue profile transfer token", connection -> {
            ProfileStateFlushService.commitTransferSnapshot(connection, transferSnapshot, "network_profile_transfer");
            ProfileTransferTokenManager.ensureSchema(connection);
            ProfileTransferTokenManager.IssuedToken token = ProfileTransferTokenManager.issue(
                    connection,
                    playerUuid,
                    profile.profileId(),
                    config.profileTransferSecret,
                    config.serverId,
                    cleanTargetServerId,
                    config.profileTransferTtlSeconds
            );
            issuedWireToken.set(token.wireValue());

            long warmStart = System.currentTimeMillis();
            String payloadJson = PlayerProfileManager.buildNetworkTransferPayload(connection, playerUuid, profile.profileId());
            ProfileTransferTokenManager.attachMetadata(connection, token.tokenId(), payloadJson);
            ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] transfer profile payload prepare took " + (System.currentTimeMillis() - warmStart) + "ms for " + player.getGameProfile().getName());
            // ProfileTransferTokenManager.issue already writes the TOKEN_ISSUED audit row.
            // Do not write a second row with the same transfer_id because older beta schemas
            // may still have a unique transfer_id audit index.
        }).whenComplete((ignored, error) -> player.server.execute(() -> {
            if (!SafeTeleportManager.isLive(player)) {
                LOBBY_TRANSFER_IN_FLIGHT.remove(playerUuid);
                return;
            }
            if (error != null) {
                LOBBY_TRANSFER_IN_FLIGHT.remove(playerUuid);
                error.printStackTrace();
                if (callback != null) callback.accept("Could not prepare your profile transfer. Please try again or contact staff.");
                return;
            }

            ChampDebugManager.log(
                    ChampDebugManager.Category.PROFILES,
                    "[ChampUtils][ProfileTransferDebug] issuing lobby transfer player=" + player.getGameProfile().getName()
                            + " profile=" + profile.profileName()
                            + " source=" + config.serverId
                            + " target=" + cleanTargetServerId
                            + " backends=" + config.survivalBackendCsv()
            );
            scheduleProxyTransfer(player, profile, config, cleanTargetServerId, issuedWireToken.get());
            if (callback != null) callback.accept("Profile transfer token issued for " + profile.profileName() + ". Transfer scheduled.");
        }));
    }

    private static void scheduleProxyTransfer(ServerPlayer player, PlayerProfileManager.ProfileRecord profile, NetworkServerConfig config, String targetServerId, String wireToken) {
        if (player == null || player.server == null || config == null) return;
        try {
            // Close SGUI/vanilla containers before asking Velocity to switch servers. This keeps the transfer
            // as packet-quiet as possible and avoids racing menu/resource-pack/chat-session packets.
            if (player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
            }
        } catch (Throwable ignored) {
        }

        final UUID playerUuid = player.getUUID();
        final String playerName = player.getGameProfile().getName();

        java.util.concurrent.CompletableFuture
                .runAsync(() -> {}, java.util.concurrent.CompletableFuture.delayedExecutor(500, java.util.concurrent.TimeUnit.MILLISECONDS))
                .thenRun(() -> player.server.execute(() -> {
                    ServerPlayer live = player.server.getPlayerList().getPlayer(playerUuid);
                    if (!SafeTeleportManager.isLive(live)) {
                        LOBBY_TRANSFER_IN_FLIGHT.remove(playerUuid);
                        return;
                    }

                    // Velocity's /server command is a proxy/player command, not a normal backend
                    // console command. The old fallback tried to run "server {player} survival"
                    // from the profile_lobby backend, which can silently do nothing. Always request
                    // the proxy transfer through the standard BungeeCord/Velocity plugin-message
                    // channel first. Keep the configured command only as a last-resort fallback for
                    // unusual proxy setups that expose a real backend command.
                    boolean transferRequested = executeProxyTransfer(live, config, targetServerId);
                    if (!transferRequested) {
                        executeLobbyTransferCommand(live, profile, config, targetServerId, wireToken);
                    } else {
                        // If the proxy plugin-message channel is disabled/misconfigured, the send call
                        // can succeed locally but the player will still be sitting in profile_lobby.
                        // Give the proxy a moment, then run the configured fallback only if the player
                        // is still connected to this backend.
                        java.util.concurrent.CompletableFuture
                                .runAsync(() -> {}, java.util.concurrent.CompletableFuture.delayedExecutor(1500, java.util.concurrent.TimeUnit.MILLISECONDS))
                                .thenRun(() -> live.server.execute(() -> {
                                    ServerPlayer stillHere = live.server.getPlayerList().getPlayer(playerUuid);
                                    if (SafeTeleportManager.isLive(stillHere)) {
                                        executeLobbyTransferCommand(stillHere, profile, config, targetServerId, wireToken);
                                    }
                                }));
                    }

                    java.util.concurrent.CompletableFuture
                            .runAsync(() -> {}, java.util.concurrent.CompletableFuture.delayedExecutor(10, java.util.concurrent.TimeUnit.SECONDS))
                            .thenRun(() -> LOBBY_TRANSFER_IN_FLIGHT.remove(playerUuid));
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

        AcceptedTransferSession accepted = ACCEPTED_SURVIVAL_SESSIONS.get(playerUuid);
        long now = System.currentTimeMillis();
        if (accepted != null) {
            if (accepted.isFresh(now)) {
                String profileName = accepted.profileName();
                if (accepted.profileId() != null && profileName != null && !profileName.isBlank()) {
                    ProfileLoadingStateManager.beginBlankSilent(player, profileName);
                    PlayerProfileManager.loadFromNetworkTransferPayloadAsync(player, accepted.profileId(), accepted.payloadJson(), result -> {
                        if (result == null || !result.startsWith("Loaded")) {
                            player.sendSystemMessage(Component.literal(result == null ? "Could not load your profile." : result).withStyle(ChatFormatting.RED));
                        }
                    });
                }
                return true;
            }
            ACCEPTED_SURVIVAL_SESSIONS.remove(playerUuid, accepted);
        }

        if (SURVIVAL_CONSUME_IN_FLIGHT.putIfAbsent(playerUuid, Boolean.TRUE) != null) {
            return true;
        }

        ProfileLoadingStateManager.beginBlankSilent(player, "Profile");
        AtomicReference<String> transferredProfileName = new AtomicReference<>();
        AtomicReference<UUID> transferredProfileId = new AtomicReference<>();
        AtomicReference<String> transferredPayload = new AtomicReference<>();
        DatabaseManager.runAsync("consume profile transfer token on survival join", connection -> {
            ProfileTransferTokenManager.ensureSchema(connection);
            java.util.Optional<ProfileTransferTokenManager.ConsumedToken> consumed = java.util.Optional.empty();
            String lastTokenDebug = "not-checked";
            for (int attempt = 1; attempt <= SURVIVAL_TOKEN_WAIT_ATTEMPTS; attempt++) {
                consumed = ProfileTransferTokenManager.consumeLatestForPlayer(connection, playerUuid, config.profileTransferSecret, config.serverId);
                if (consumed.isPresent()) {
                    if (attempt > 1) {
                        ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[ChampUtils][ProfileTransferDebug] accepted transfer token for " + playerName + " after attempt " + attempt + ".");
                    }
                    break;
                }

                lastTokenDebug = ProfileTransferTokenManager.latestDebugForPlayer(connection, playerUuid);
                if (attempt == 1 || attempt % 10 == 0) {
                    ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[ChampUtils][ProfileTransferDebug] waiting for token for " + playerName + " attempt=" + attempt + "/" + SURVIVAL_TOKEN_WAIT_ATTEMPTS + " serverId=" + config.serverId + " latest=" + lastTokenDebug);
                }

                AcceptedTransferSession alreadyAccepted = ACCEPTED_SURVIVAL_SESSIONS.get(playerUuid);
                if (alreadyAccepted != null && alreadyAccepted.isFresh(System.currentTimeMillis())) {
                    transferredProfileId.set(alreadyAccepted.profileId());
                    transferredProfileName.set(alreadyAccepted.profileName());
                    transferredPayload.set(alreadyAccepted.payloadJson());
                    return;
                }

                try {
                    Thread.sleep(SURVIVAL_TOKEN_WAIT_SLEEP_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (consumed.isEmpty()) {
                if (config.allowSurvivalDirectProfileMenu) return;
                throw new IllegalStateException("No valid profile transfer token for this survival join. latest=" + lastTokenDebug);
            }

            UUID profileId = consumed.get().profileId();
            String profileName = readProfileName(connection, playerUuid, profileId);
            if (profileName == null || profileName.isBlank()) {
                throw new IllegalStateException("Transferred profile no longer exists or belongs to a different player.");
            }
            if (PlayerProfileManager.shouldSpawnAtServerSpawnForTransfer(connection, profileId, config.serverId)) {
                PlayerProfileManager.forceSpawnAtServerSpawnOnNextLoad(playerUuid, profileId);
            }
            PlayerProfileManager.markProfileServerSeen(connection, profileId, config.serverId);
            transferredProfileName.set(profileName);
            transferredProfileId.set(profileId);
            transferredPayload.set(consumed.get().metadataJson());
            ACCEPTED_SURVIVAL_SESSIONS.put(playerUuid, new AcceptedTransferSession(profileId, profileName, consumed.get().metadataJson(), System.currentTimeMillis()));
        }).whenComplete((ignored, error) -> player.server.execute(() -> {
            SURVIVAL_CONSUME_IN_FLIGHT.remove(playerUuid);
            if (!SafeTeleportManager.isLive(player)) return;

            if (error != null) {
                ProfileLoadingStateManager.end(player);
                Throwable cause = error.getCause() == null ? error : error.getCause();
                System.err.println("[ChampUtils] Survival profile transfer failed for " + playerName + ": " + cause.getMessage());
                player.connection.disconnect(Component.literal("No valid profile transfer token. Please join through the profile lobby."));
                return;
            }

            String profileName = transferredProfileName.get();
            UUID profileId = transferredProfileId.get();
            if (profileId != null && profileName != null && !profileName.isBlank()) {
                // The profile lobby already displayed the loading title before issuing the token.
                // Survival still has to attach the cached inventory/party stores to the live player,
                // but do not show a second loading popup here.
                // Keep the survival quarantine active through the entire profile switch.
                // loadFromNetworkTransferPayloadAsync owns the final unlock now; ending here re-opened the dangerous
                // gap where survival systems could run before the profile was fully hydrated.
                ProfileLoadingStateManager.beginBlankSilent(player, profileName);
                PlayerProfileManager.loadFromNetworkTransferPayloadAsync(player, profileId, transferredPayload.get(), result -> {
                    if (result == null || !result.startsWith("Loaded")) {
                        player.sendSystemMessage(Component.literal(result == null ? "Could not load your profile." : result).withStyle(ChatFormatting.RED));
                    }
                });
                return;
            }

            ProfileLoadingStateManager.end(player);
            if (config.allowSurvivalDirectProfileMenu) {
                ProfileLobbyManager.sendToLobby(player);
            }
        }));

        return true;
    }

    public static void clearAcceptedTransferSession(UUID playerUuid) {
        if (playerUuid == null) return;
        ACCEPTED_SURVIVAL_SESSIONS.remove(playerUuid);
        SURVIVAL_CONSUME_IN_FLIGHT.remove(playerUuid);
        LOBBY_TRANSFER_IN_FLIGHT.remove(playerUuid);
    }

    public static void sendLoadingTitle(ServerPlayer player, String profileName) {
        if (!SafeTeleportManager.isLive(player)) return;
        String cleanProfileName = profileName == null || profileName.isBlank() ? "Profile" : profileName.trim();
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§eLoading " + cleanProfileName + " Profile...")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7Please wait")));
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

    private static boolean executeProxyTransfer(ServerPlayer player, NetworkServerConfig config, String targetServerId) {
        if (config == null || !config.useProxyPluginMessageTransfer) {
            ChampDebugManager.log(
                    ChampDebugManager.Category.PROFILES,
                    "[ChampUtils][ProfileTransferDebug] proxy plugin-message transfer disabled by network_server.json."
            );
            return false;
        }
        String targetServer = resolveVelocityTargetServer(config, targetServerId);
        if (targetServer == null || targetServer.isBlank()) {
            ChampDebugManager.log(
                    ChampDebugManager.Category.PROFILES,
                    "[ChampUtils][ProfileTransferDebug] proxy transfer target was blank for targetServerId=" + targetServerId
            );
            return false;
        }
        boolean requested = ProxyTransferBridge.connect(player, targetServer);
        ChampDebugManager.log(
                ChampDebugManager.Category.PROFILES,
                "[ChampUtils][ProfileTransferDebug] proxy transfer request result=" + requested
                        + " player=" + (player == null ? "unknown" : player.getGameProfile().getName())
                        + " target=" + targetServer
        );
        return requested;
    }

    private static String resolveVelocityTargetServer(NetworkServerConfig config, String targetServerId) {
        if (config == null) return "";
        String target = targetServerId == null || targetServerId.isBlank()
                ? (config.survivalServerId == null ? "" : config.survivalServerId)
                : targetServerId.trim();

        String command = config.lobbyTransferCommand == null ? "" : config.lobbyTransferCommand.trim();
        if (!command.isBlank()) {
            String expanded = command
                    .replace("{player}", "__player__")
                    .replace("{target_server}", target)
                    .replace("{profile}", "__profile__")
                    .replace("{token}", "__token__")
                    .replace("/", "")
                    .trim();
            String[] parts = expanded.split("\\s+");
            if (parts.length >= 2 && parts[0].equalsIgnoreCase("server")) {
                return parts[parts.length - 1];
            }
        }
        return target;
    }

    private static void executeLobbyTransferCommand(ServerPlayer player, PlayerProfileManager.ProfileRecord profile, NetworkServerConfig config, String targetServerId, String wireToken) {
        if (player == null || player.server == null || config == null) return;
        String command = config.lobbyTransferCommand;
        if (command == null || command.isBlank()) return;
        String target = targetServerId == null || targetServerId.isBlank()
                ? (config.survivalServerId == null ? "" : config.survivalServerId)
                : targetServerId.trim();
        command = command
                .replace("{player}", player.getGameProfile().getName())
                .replace("{target_server}", target)
                .replace("{profile}", profile.profileName() == null ? "" : profile.profileName())
                .replace("{token}", wireToken == null ? "" : wireToken);
        if (command.startsWith("/")) command = command.substring(1);
        ChampDebugManager.log(
                ChampDebugManager.Category.PROFILES,
                "[ChampUtils][ProfileTransferDebug] running configured transfer fallback command=\"" + command + "\" player=" + player.getGameProfile().getName()
        );
        player.server.getCommands().performPrefixedCommand(player.server.createCommandSourceStack().withSuppressedOutput(), command);
    }

}
