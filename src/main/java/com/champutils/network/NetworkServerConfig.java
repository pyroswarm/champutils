package com.champutils.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NetworkServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static NetworkServerConfig INSTANCE;

    public String serverId = "main-1";
    public ServerRole serverRole = ServerRole.ALL_IN_ONE;
    public boolean databaseIsSourceOfTruth = true;
    public boolean allowLocalFallbackWhileDatabaseOffline = true;
    public boolean enableCrossServerReadySchemas = true;

    /**
     * Shared HMAC secret used only when splitting profile selection onto a separate server.
     * Leave blank while running ALL_IN_ONE. Before using PROFILE_LOBBY/SURVIVAL, set the
     * same random 32+ character value on both servers.
     */
    public String profileTransferSecret = "CHANGE_ME_TO_A_32_PLUS_CHARACTER_RANDOM_SECRET";

    /** Server id that PROFILE_LOBBY should issue profile transfer tokens for. */
    public String survivalServerId = "survival";

    /** Velocity backend name for the profile lobby. Used by /profiles on SURVIVAL. */
    public String profileLobbyServerId = "profile_lobby";

    /** Seconds before a lobby-issued profile transfer token expires. Clamped to 10-300. */
    public int profileTransferTtlSeconds = 90;

    /**
     * Command run by the lobby server after token issue. Use {player}, {target_server},
     * {profile}, and {token}. For Bungee/Velocity setups, usually: server {player} survival
     */
    public String lobbyTransferCommand = "server {player} {target_server}";

    /** Command run by SURVIVAL when a player uses /profiles to return to profile selection. */
    public String returnToProfileLobbyCommand = "server {player} {target_server}";

    /**
     * Safety valve for testing SURVIVAL directly. Keep false in production once the proxy lobby
     * is live so players cannot bypass token-gated profile loading.
     */
    public boolean allowSurvivalDirectProfileMenu = true;

    /**
     * Prefer the configured proxy command for lobby -> survival movement.
     * Keep this false unless you have confirmed your proxy intercepts backend plugin messages reliably.
     * Sending both a plugin-message transfer and a /server-style transfer can create duplicate connect
     * attempts, which is a common cause of intermittent getsockopt/connect failures.
     */
    public boolean useProxyPluginMessageTransfer = true;

    public enum ServerRole {
        ALL_IN_ONE,
        PROFILE_LOBBY,
        SURVIVAL,
        HUB,
        EXPLORATION,
        TERRITORY
    }

    private NetworkServerConfig() {
    }

    public static synchronized void load() {
        Path path = getPath();

        try {
            Files.createDirectories(path.getParent());

            if (!Files.exists(path)) {
                INSTANCE = new NetworkServerConfig();
                Files.writeString(path, GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
                return;
            }

            String json = Files.readString(path, StandardCharsets.UTF_8);
            INSTANCE = GSON.fromJson(json, NetworkServerConfig.class);

            if (INSTANCE == null) {
                INSTANCE = new NetworkServerConfig();
            }

            INSTANCE.fillMissingDefaults();
            Files.writeString(path, GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load network_server.json. Falling back to main-1 ALL_IN_ONE.");
            e.printStackTrace();
            INSTANCE = new NetworkServerConfig();
        }
    }

    private void fillMissingDefaults() {
        if (serverId == null || serverId.isBlank()) {
            serverId = "main-1";
        }

        if (serverRole == null) {
            serverRole = ServerRole.ALL_IN_ONE;
        }

        if (survivalServerId == null || survivalServerId.isBlank()) {
            survivalServerId = "survival";
        }
        // Current Cobble Champs Velocity backend is named "survival".
        // Older generated configs used "survival-1", which makes the lobby issue a transfer
        // to the wrong backend even though manual /server survival works.
        if ("survival-1".equalsIgnoreCase(survivalServerId.trim())) {
            survivalServerId = "survival";
        }
        if (profileLobbyServerId == null || profileLobbyServerId.isBlank()) {
            profileLobbyServerId = "profile_lobby";
        }
        if (lobbyTransferCommand == null || lobbyTransferCommand.isBlank()) {
            lobbyTransferCommand = "server {player} {target_server}";
        }
        // Earlier builds generated "server {player} survival", which hard-coded the old
        // Velocity backend name and ignored survivalServerId. Migrate that exact legacy default
        // so switching configs back to survival-1 actually sends players to survival-1.
        if ("server {player} survival".equalsIgnoreCase(lobbyTransferCommand.trim())) {
            lobbyTransferCommand = "server {player} {target_server}";
        }
        if (returnToProfileLobbyCommand == null || returnToProfileLobbyCommand.isBlank()) {
            returnToProfileLobbyCommand = "server {player} {target_server}";
        }
        if (profileTransferTtlSeconds < 10) {
            profileTransferTtlSeconds = 10;
        }
        if (profileTransferTtlSeconds > 300) {
            profileTransferTtlSeconds = 300;
        }
    }

    public static NetworkServerConfig get() {
        if (INSTANCE == null) {
            load();
        }

        return INSTANCE;
    }

    public static String serverId() {
        return get().serverId;
    }

    public static ServerRole serverRole() {
        return get().serverRole;
    }

    public static Path getPath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("champutils")
                .resolve("network_server.json");
    }

    public String safeSummary() {
        return "serverId=" + serverId
                + ", serverRole=" + serverRole
                + ", survivalServerId=" + survivalServerId
                + ", databaseIsSourceOfTruth=" + databaseIsSourceOfTruth
                + ", allowLocalFallbackWhileDatabaseOffline=" + allowLocalFallbackWhileDatabaseOffline
                + ", allowSurvivalDirectProfileMenu=" + allowSurvivalDirectProfileMenu;
    }
}
