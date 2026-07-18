package com.champutils.database;

import com.champutils.network.NetworkEventManager;
import com.champutils.profession.ProfessionBackpackConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/** SQL-backed network source of truth for profession_backpack.json. */
public final class SharedBackpackConfigRepository {
    public static final UUID INVALIDATION_ID = UUID.fromString("6f7e44bc-719b-4db4-b07a-703891b4459f");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_KEY = "profession_backpack";

    private SharedBackpackConfigRepository() {}

    public static void loadAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.supplyAsync("load shared backpack config", connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("select payload from shared_server_configs where config_key = ?")) {
                ps.setString(1, CONFIG_KEY);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        }).thenAccept(payload -> {
            if (payload != null && !payload.isBlank()) ProfessionBackpackConfig.applySharedJson(payload);
        }).exceptionally(error -> {
            System.err.println("[ChampUtils] Failed to load shared profession backpack config: " + error.getMessage());
            return null;
        });
    }

    public static void saveAsync(ProfessionBackpackConfig.Config config) {
        if (!DatabaseManager.isEnabled() || config == null) return;
        String payload = GSON.toJson(config);
        DatabaseManager.runAsync("save shared backpack config", connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into shared_server_configs (config_key, payload, updated_at) values (?, ?::jsonb, now()) " +
                    "on conflict (config_key) do update set payload = excluded.payload, updated_at = now()")) {
                ps.setString(1, CONFIG_KEY);
                ps.setString(2, payload);
                ps.executeUpdate();
            }
        }).thenRun(() -> NetworkEventManager.publishCacheInvalidation("BACKPACK_CONFIG", INVALIDATION_ID))
          .exceptionally(error -> {
              System.err.println("[ChampUtils] Failed to save shared profession backpack config: " + error.getMessage());
              return null;
          });
    }

    private static void ensureSchema(java.sql.Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "create table if not exists shared_server_configs (" +
                "config_key text primary key, payload jsonb not null, updated_at timestamptz not null default now())")) {
            ps.executeUpdate();
        }
    }
}
