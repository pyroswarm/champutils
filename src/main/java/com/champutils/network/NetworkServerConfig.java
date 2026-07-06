package com.champutils.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    /** Preferred Velocity backend that PROFILE_LOBBY should try before overflow backends. */
    public String survivalServerId = "main_survival1";

    /** All Velocity backend names that are part of the shared Survival cluster. */
    public String[] survivalBackendIds = new String[]{"main_survival1", "survival2"};

    /** Maximum players allowed onto each survival backend through profile selection. */
    public int survivalPlayerCap = 50;

    /** If true, profile selection waits in a queue instead of starting a profile load when survival is full. */
    public boolean enableSurvivalQueue = true;

    /** How often the lobby/all-in-one server checks whether the next queued profile can enter survival. */
    public int survivalQueuePollSeconds = 5;

    /** LuckPerms groups checked for queue priority, in highest-to-lowest priority order. */
    public String[] survivalQueuePriorityGroups = new String[]{"vipplus", "vip+", "vip"};

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
     * attempts, which is a F-rank cause of intermittent getsockopt/connect failures.
     */
    public boolean useProxyPluginMessageTransfer = true;

    /** Polls Postgres for cross-server chat, broadcasts, and cache/event fanout. */
    public boolean enableNetworkEventBus = true;

    /** How often each backend polls shared network events. Clamped to 1-10 seconds. */
    public int networkEventPollSeconds = 1;

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
            survivalServerId = "main_survival1";
        }
        if ("survival".equalsIgnoreCase(survivalServerId.trim()) || "survival-1".equalsIgnoreCase(survivalServerId.trim())) {
            survivalServerId = "main_survival1";
        }
        if (survivalBackendIds == null || survivalBackendIds.length == 0) {
            survivalBackendIds = new String[]{"main_survival1", "survival2"};
        }
        survivalBackendIds = normalizedSurvivalBackends().toArray(String[]::new);
        if (survivalPlayerCap <= 0) {
            survivalPlayerCap = 50;
        }
        if (survivalQueuePollSeconds <= 0) {
            survivalQueuePollSeconds = 5;
        }
        if (survivalQueuePriorityGroups == null || survivalQueuePriorityGroups.length == 0) {
            survivalQueuePriorityGroups = new String[]{"vipplus", "vip+", "vip"};
        }
        if (profileLobbyServerId == null || profileLobbyServerId.isBlank()) {
            profileLobbyServerId = "profile_lobby";
        }
        if (lobbyTransferCommand == null || lobbyTransferCommand.isBlank()) {
            lobbyTransferCommand = "server {player} {target_server}";
        }
        // Earlier builds generated "server {player} survival", which hard-coded the old
        // Velocity backend name and ignored survivalServerId. Migrate that legacy default
        // so profile lobby transfers prefer main_survival1 and can overflow to survival2.
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
        if (networkEventPollSeconds < 1) {
            networkEventPollSeconds = 1;
        }
        if (networkEventPollSeconds > 10) {
            networkEventPollSeconds = 10;
        }
    }

    public List<String> normalizedSurvivalBackends() {
        List<String> ids = new ArrayList<>();
        addBackend(ids, survivalServerId);
        if (survivalBackendIds != null) {
            for (String id : survivalBackendIds) {
                addBackend(ids, id);
            }
        }
        addBackend(ids, "main_survival1");
        addBackend(ids, "survival2");
        return ids;
    }

    public String survivalBackendCsv() {
        return String.join(",", normalizedSurvivalBackends());
    }

    private static void addBackend(List<String> ids, String raw) {
        if (raw == null) {
            return;
        }
        String id = raw.trim();
        if (id.isBlank()) {
            return;
        }
        if ("survival".equalsIgnoreCase(id) || "survival-1".equalsIgnoreCase(id)) {
            id = "main_survival1";
        }
        String key = id.toLowerCase(Locale.ROOT);
        for (String existing : ids) {
            if (existing.toLowerCase(Locale.ROOT).equals(key)) {
                return;
            }
        }
        ids.add(id);
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

    public static boolean isAuthoritativeGameplayServer() {
        NetworkServerConfig config = get();
        if (config.serverRole != ServerRole.SURVIVAL && config.serverRole != ServerRole.ALL_IN_ONE) {
            return false;
        }
        String primary = config.survivalServerId == null || config.survivalServerId.isBlank()
                ? "main_survival1"
                : config.survivalServerId.trim();
        if ("survival".equalsIgnoreCase(primary) || "survival-1".equalsIgnoreCase(primary)) {
            primary = "main_survival1";
        }
        return config.serverId != null && config.serverId.equalsIgnoreCase(primary);
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
                + ", survivalBackendIds=" + survivalBackendCsv()
                + ", survivalPlayerCap=" + survivalPlayerCap
                + ", enableSurvivalQueue=" + enableSurvivalQueue
                + ", enableNetworkEventBus=" + enableNetworkEventBus
                + ", databaseIsSourceOfTruth=" + databaseIsSourceOfTruth
                + ", allowLocalFallbackWhileDatabaseOffline=" + allowLocalFallbackWhileDatabaseOffline
                + ", allowSurvivalDirectProfileMenu=" + allowSurvivalDirectProfileMenu;
    }
}
