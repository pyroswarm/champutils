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
    public String host = "aws-1-us-west-2.pooler.supabase.com";
    public int port = 5432;
    public String database = "postgres";
    public String username = "postgres.rzztdnkggkkpghkstdfr";
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

                Files.writeString(
                        path,
                        GSON.toJson(config),
                        StandardCharsets.UTF_8
                );

                return config;
            }

            String json = Files.readString(path, StandardCharsets.UTF_8);

            DatabaseConfig config = GSON.fromJson(json, DatabaseConfig.class);

            if (config == null) {
                config = new DatabaseConfig();
            }

            config.fillMissingDefaults();

            Files.writeString(
                    path,
                    GSON.toJson(config),
                    StandardCharsets.UTF_8
            );

            return config;
        }
        catch (IOException e) {
            System.err.println(
                    "[ChampUtils] Failed to load database config at " +
                            path.toAbsolutePath()
            );

            e.printStackTrace();

            DatabaseConfig disabled = new DatabaseConfig();
            disabled.enabled = false;

            return disabled;
        }
    }

    private void fillMissingDefaults() {
        if (host == null || host.isBlank()) {
            host = "aws-1-us-west-2.pooler.supabase.com";
        }

        if (port <= 0) {
            port = 5432;
        }

        if (database == null || database.isBlank()) {
            database = "postgres";
        }

        if (username == null || username.isBlank()) {
            username = "postgres.rzztdnkggkkpghkstdfr";
        }

        if (password == null) {
            password = "CHANGE_ME";
        }

        if (saveIntervalSeconds <= 0) {
            saveIntervalSeconds = 60;
        }
    }

    public boolean isConfigured() {
        return enabled
                && host != null
                && !host.isBlank()
                && database != null
                && !database.isBlank()
                && username != null
                && !username.isBlank()
                && password != null
                && !password.isBlank()
                && !password.equals("CHANGE_ME");
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database + "?sslmode=require";
    }

    public String safeSummary() {
        return "enabled=" + enabled
                + ", host=" + host
                + ", port=" + port
                + ", database=" + database
                + ", username=" + username
                + ", passwordSet=" + (
                        password != null &&
                                !password.isBlank() &&
                                !password.equals("CHANGE_ME")
                );
    }
}
