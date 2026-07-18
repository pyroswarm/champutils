package com.champutils.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class SharedJsonStateRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile boolean schemaEnsured = false;

    private SharedJsonStateRepository() {
    }

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure shared json state schema", SharedJsonStateRepository::ensureSchema);
    }

    public static synchronized void ensureSchema(Connection connection) throws Exception {
        if (schemaEnsured || connection == null) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists profile_json_state (profile_id uuid not null, state_key text not null, payload text not null default '{}', updated_at timestamptz not null default now(), primary key (profile_id, state_key))");
            statement.executeUpdate("create table if not exists player_json_state (player_uuid uuid not null, state_key text not null, payload text not null default '{}', updated_at timestamptz not null default now(), primary key (player_uuid, state_key))");
            statement.executeUpdate("create table if not exists global_json_state (state_key text primary key, payload text not null default '{}', updated_at timestamptz not null default now())");
            statement.executeUpdate("alter table profile_json_state add column if not exists version bigint not null default 0");
            statement.executeUpdate("alter table player_json_state add column if not exists version bigint not null default 0");
            statement.executeUpdate("alter table global_json_state add column if not exists version bigint not null default 0");
            statement.executeUpdate("create index if not exists profile_json_state_updated_idx on profile_json_state (state_key, updated_at desc)");
            statement.executeUpdate("create index if not exists player_json_state_updated_idx on player_json_state (state_key, updated_at desc)");
        }
        schemaEnsured = true;
    }

    public static <T> T loadProfile(UUID profileId, String key, Class<T> type, T fallback) {
        if (profileId == null || key == null || key.isBlank() || type == null || !DatabaseManager.isEnabled()) return fallback;
        try {
            return DatabaseManager.supplyAsync("load profile json state " + key + " " + profileId, connection -> {
                ensureSchema(connection);
                try (PreparedStatement ps = connection.prepareStatement("select payload from profile_json_state where profile_id = ? and state_key = ?")) {
                    ps.setObject(1, profileId);
                    ps.setString(2, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) return fallback;
                        T value = GSON.fromJson(rs.getString(1), type);
                        return value == null ? fallback : value;
                    }
                }
            }).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load shared profile state '" + key + "' for " + profileId + ".");
            e.printStackTrace();
            return fallback;
        }
    }

    public static <T> CompletableFuture<T> loadProfileAsync(UUID profileId, String key, Class<T> type, T fallback) {
        if (profileId == null || key == null || key.isBlank() || type == null || !DatabaseManager.isEnabled()) {
            return CompletableFuture.completedFuture(fallback);
        }
        return DatabaseManager.supplyAsync("load profile json state " + key + " " + profileId, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("select payload from profile_json_state where profile_id = ? and state_key = ?")) {
                ps.setObject(1, profileId);
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return fallback;
                    T value = GSON.fromJson(rs.getString(1), type);
                    return value == null ? fallback : value;
                }
            }
        });
    }

    public static <T> T loadPlayer(UUID playerId, String key, Class<T> type, T fallback) {
        if (playerId == null || key == null || key.isBlank() || type == null || !DatabaseManager.isEnabled()) return fallback;
        try {
            return loadPlayerAsync(playerId, key, type, fallback).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load shared player state '" + key + "' for " + playerId + ".");
            e.printStackTrace();
            return fallback;
        }
    }

    public static <T> CompletableFuture<T> loadPlayerAsync(UUID playerId, String key, Class<T> type, T fallback) {
        if (playerId == null || key == null || key.isBlank() || type == null || !DatabaseManager.isEnabled()) {
            return CompletableFuture.completedFuture(fallback);
        }
        return DatabaseManager.supplyAsync("load player json state " + key + " " + playerId, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("select payload from player_json_state where player_uuid = ? and state_key = ?")) {
                ps.setObject(1, playerId);
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return fallback;
                    T value = GSON.fromJson(rs.getString(1), type);
                    return value == null ? fallback : value;
                }
            }
        });
    }

    public static <T> T loadGlobal(String key, Class<T> type, T fallback) {
        if (key == null || key.isBlank() || type == null || !DatabaseManager.isEnabled()) return fallback;
        try {
            return loadGlobalAsync(key, type, fallback).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load shared global state '" + key + "'.");
            e.printStackTrace();
            return fallback;
        }
    }

    public static <T> CompletableFuture<T> loadGlobalAsync(String key, Class<T> type, T fallback) {
        if (key == null || key.isBlank() || type == null || !DatabaseManager.isEnabled()) {
            return CompletableFuture.completedFuture(fallback);
        }
        return DatabaseManager.supplyAsync("load global json state " + key, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("select payload from global_json_state where state_key = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return fallback;
                    T value = GSON.fromJson(rs.getString(1), type);
                    return value == null ? fallback : value;
                }
            }
        });
    }

    public static void saveProfileBlocking(Connection connection, UUID profileId, String key, Object value) throws Exception {
        if (connection == null || profileId == null || key == null || key.isBlank() || value == null) return;
        ensureSchema(connection);
        String payload = GSON.toJson(value);
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into profile_json_state (profile_id, state_key, payload, updated_at, version) values (?, ?, ?, now(), 1) " +
                        "on conflict (profile_id, state_key) do update set payload = excluded.payload, updated_at = now(), version = profile_json_state.version + 1"
        )) {
            ps.setObject(1, profileId);
            ps.setString(2, key);
            ps.setString(3, payload);
            ps.executeUpdate();
        }
    }

    public static void saveProfile(UUID profileId, String key, Object value) {
        if (profileId == null || key == null || key.isBlank() || value == null || !DatabaseManager.isEnabled()) return;
        String payload = GSON.toJson(value);
        DatabaseManager.executeCoalescedAsync("profile-json-state:" + key + ":" + profileId, "save profile json state " + key, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into profile_json_state (profile_id, state_key, payload, updated_at, version) values (?, ?, ?, now(), 1) " +
                            "on conflict (profile_id, state_key) do update set payload = excluded.payload, updated_at = now(), version = profile_json_state.version + 1"
            )) {
                ps.setObject(1, profileId);
                ps.setString(2, key);
                ps.setString(3, payload);
                ps.executeUpdate();
            }
        });
    }

    public static CompletableFuture<Void> saveProfileAsync(UUID profileId, String key, Object value) {
        if (profileId == null || key == null || key.isBlank() || value == null || !DatabaseManager.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        String payload = GSON.toJson(value);
        return DatabaseManager.runAsync("save profile json state " + key + " " + profileId, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into profile_json_state (profile_id, state_key, payload, updated_at, version) values (?, ?, ?, now(), 1) " +
                            "on conflict (profile_id, state_key) do update set payload = excluded.payload, updated_at = now(), version = profile_json_state.version + 1"
            )) {
                ps.setObject(1, profileId);
                ps.setString(2, key);
                ps.setString(3, payload);
                ps.executeUpdate();
            }
        });
    }

    public static void savePlayer(UUID playerId, String key, Object value) {
        if (playerId == null || key == null || key.isBlank() || value == null || !DatabaseManager.isEnabled()) return;
        String payload = GSON.toJson(value);
        DatabaseManager.executeCoalescedAsync("player-json-state:" + key + ":" + playerId, "save player json state " + key, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into player_json_state (player_uuid, state_key, payload, updated_at, version) values (?, ?, ?, now(), 1) " +
                            "on conflict (player_uuid, state_key) do update set payload = excluded.payload, updated_at = now(), version = player_json_state.version + 1"
            )) {
                ps.setObject(1, playerId);
                ps.setString(2, key);
                ps.setString(3, payload);
                ps.executeUpdate();
            }
        });
    }

    public static CompletableFuture<Void> savePlayerAsync(UUID playerId, String key, Object value) {
        if (playerId == null || key == null || key.isBlank() || value == null || !DatabaseManager.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        String payload = GSON.toJson(value);
        return DatabaseManager.runAsync("save player json state " + key, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into player_json_state (player_uuid, state_key, payload, updated_at, version) values (?, ?, ?, now(), 1) " +
                            "on conflict (player_uuid, state_key) do update set payload = excluded.payload, updated_at = now(), version = player_json_state.version + 1"
            )) {
                ps.setObject(1, playerId);
                ps.setString(2, key);
                ps.setString(3, payload);
                ps.executeUpdate();
            }
        });
    }

    public static void saveGlobal(String key, Object value) {
        if (key == null || key.isBlank() || value == null || !DatabaseManager.isEnabled()) return;
        String payload = GSON.toJson(value);
        DatabaseManager.executeCoalescedAsync("global-json-state:" + key, "save global json state " + key, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into global_json_state (state_key, payload, updated_at, version) values (?, ?, now(), 1) " +
                            "on conflict (state_key) do update set payload = excluded.payload, updated_at = now(), version = global_json_state.version + 1"
            )) {
                ps.setString(1, key);
                ps.setString(2, payload);
                ps.executeUpdate();
            }
        });
    }

    public static CompletableFuture<Void> saveGlobalAsync(String key, Object value) {
        if (key == null || key.isBlank() || value == null || !DatabaseManager.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        String payload = GSON.toJson(value);
        return DatabaseManager.runAsync("save global json state " + key, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into global_json_state (state_key, payload, updated_at, version) values (?, ?, now(), 1) " +
                            "on conflict (state_key) do update set payload = excluded.payload, updated_at = now(), version = global_json_state.version + 1"
            )) {
                ps.setString(1, key);
                ps.setString(2, payload);
                ps.executeUpdate();
            }
        });
    }
    /**
     * Atomically loads, locks, mutates, and saves one profile-scoped JSON row. Use this for
     * state that may be changed by players on different backends at the same time, such as
     * guild-wide quest progress (where the guild id is used as the owner id).
     */
    public static <T, R> CompletableFuture<R> mutateProfileAsync(
            UUID profileId,
            String key,
            Class<T> type,
            T fallback,
            java.util.function.Function<T, R> mutator
    ) {
        if (profileId == null || key == null || key.isBlank() || type == null || fallback == null || mutator == null || !DatabaseManager.isEnabled()) {
            return CompletableFuture.completedFuture(mutator == null ? null : mutator.apply(fallback));
        }
        String fallbackPayload = GSON.toJson(fallback);
        return DatabaseManager.supplyAsync("mutate profile json state " + key + " " + profileId, connection -> {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement(
                        "insert into profile_json_state (profile_id, state_key, payload, updated_at, version) values (?, ?, ?, now(), 0) on conflict (profile_id, state_key) do nothing"
                )) {
                    insert.setObject(1, profileId);
                    insert.setString(2, key);
                    insert.setString(3, fallbackPayload);
                    insert.executeUpdate();
                }
                T value = fallback;
                try (PreparedStatement select = connection.prepareStatement(
                        "select payload from profile_json_state where profile_id = ? and state_key = ? for update"
                )) {
                    select.setObject(1, profileId);
                    select.setString(2, key);
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            T parsed = GSON.fromJson(rs.getString(1), type);
                            if (parsed != null) value = parsed;
                        }
                    }
                }
                R result = mutator.apply(value);
                try (PreparedStatement update = connection.prepareStatement(
                        "update profile_json_state set payload = ?, updated_at = now(), version = version + 1 where profile_id = ? and state_key = ?"
                )) {
                    update.setString(1, GSON.toJson(value));
                    update.setObject(2, profileId);
                    update.setString(3, key);
                    update.executeUpdate();
                }
                connection.commit();
                return result;
            } catch (Throwable error) {
                try { connection.rollback(); } catch (Exception ignored) {}
                if (error instanceof Exception exception) throw exception;
                throw new RuntimeException(error);
            } finally {
                try { connection.setAutoCommit(true); } catch (Exception ignored) {}
            }
        });
    }

    /** Atomic equivalent for network-global JSON rows. */
    public static <T, R> CompletableFuture<R> mutateGlobalAsync(
            String key,
            Class<T> type,
            T fallback,
            java.util.function.Function<T, R> mutator
    ) {
        if (key == null || key.isBlank() || type == null || fallback == null || mutator == null || !DatabaseManager.isEnabled()) {
            return CompletableFuture.completedFuture(mutator == null ? null : mutator.apply(fallback));
        }
        String fallbackPayload = GSON.toJson(fallback);
        return DatabaseManager.supplyAsync("mutate global json state " + key, connection -> {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement(
                        "insert into global_json_state (state_key, payload, updated_at, version) values (?, ?, now(), 0) on conflict (state_key) do nothing"
                )) {
                    insert.setString(1, key);
                    insert.setString(2, fallbackPayload);
                    insert.executeUpdate();
                }
                T value = fallback;
                try (PreparedStatement select = connection.prepareStatement(
                        "select payload from global_json_state where state_key = ? for update"
                )) {
                    select.setString(1, key);
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            T parsed = GSON.fromJson(rs.getString(1), type);
                            if (parsed != null) value = parsed;
                        }
                    }
                }
                R result = mutator.apply(value);
                try (PreparedStatement update = connection.prepareStatement(
                        "update global_json_state set payload = ?, updated_at = now(), version = version + 1 where state_key = ?"
                )) {
                    update.setString(1, GSON.toJson(value));
                    update.setString(2, key);
                    update.executeUpdate();
                }
                connection.commit();
                return result;
            } catch (Throwable error) {
                try { connection.rollback(); } catch (Exception ignored) {}
                if (error instanceof Exception exception) throw exception;
                throw new RuntimeException(error);
            } finally {
                try { connection.setAutoCommit(true); } catch (Exception ignored) {}
            }
        });
    }

}
