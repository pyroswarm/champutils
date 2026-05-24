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

    public enum ServerRole {
        ALL_IN_ONE,
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
                + ", databaseIsSourceOfTruth=" + databaseIsSourceOfTruth
                + ", allowLocalFallbackWhileDatabaseOffline=" + allowLocalFallbackWhileDatabaseOffline;
    }
}
