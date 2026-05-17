package com.champutils.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class DatabaseConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public String host = "db.rzztdnkggkkpghkstdfr.supabase.co";
    public int port = 5432;
    public String database = "postgres";
    public String username = "postgres";
    public String password = "CHANGE_ME";
    public int saveIntervalSeconds = 60;

    public static Path getConfigPath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("champutils")
                .resolve("database.json");
    }

    public static DatabaseConfig loadOrCreate() {
        Path path = getConfigPath();

        try {
            Files.createDirectories(path.getParent());

            if (!Files.exists(path)) {
                DatabaseConfig config = new DatabaseConfig();
                Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
                return config;
            }

            String json = Files.readString(path, StandardCharsets.UTF_8);
            DatabaseConfig config = GSON.fromJson(json, DatabaseConfig.class);

            if (config == null) {
                config = new DatabaseConfig();
            }

            return config;
        } catch (IOException e) {
            e.printStackTrace();
            DatabaseConfig disabled = new DatabaseConfig();
            disabled.enabled = false;
            return disabled;
        }
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    public boolean isConfigured() {
        return password != null && !password.equals("CHANGE_ME");
    }
}
