package com.champutils.chat;

import com.champutils.database.DatabaseManager;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatPreferenceManager {
    private static final Map<UUID, ChatMode> MODES = new ConcurrentHashMap<>();
    private ChatPreferenceManager() {}

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure chat preference schema", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("create table if not exists player_chat_preferences (" +
                        "player_uuid uuid primary key, " +
                        "chat_mode text not null default 'local', " +
                        "updated_at timestamptz not null default now())");
            }
        });
    }

    public static ChatMode get(UUID uuid) {
        return MODES.getOrDefault(uuid, ChatMode.LOCAL);
    }

    public static void set(UUID uuid, ChatMode mode) {
        if (uuid == null || mode == null) return;
        MODES.put(uuid, mode);
        saveAsync(uuid, mode);
    }

    public static void setWithoutSave(UUID uuid, ChatMode mode) {
        if (uuid != null && mode != null) MODES.put(uuid, mode);
    }

    public static void load(ServerPlayer player) {
        if (player == null) return;
        if (!DatabaseManager.isEnabled()) {
            MODES.putIfAbsent(player.getUUID(), ChatMode.LOCAL);
            return;
        }

        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            try (var ps = connection.prepareStatement("select chat_mode from player_chat_preferences where player_uuid = ?")) {
                ps.setObject(1, player.getUUID());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ChatMode mode = ChatMode.parse(rs.getString("chat_mode"));
                        MODES.put(player.getUUID(), mode == null ? ChatMode.LOCAL : mode);
                        return;
                    }
                }
            }

            MODES.put(player.getUUID(), ChatMode.LOCAL);
            saveBlocking(player.getUUID(), ChatMode.LOCAL);
        } catch (Exception e) {
            e.printStackTrace();
            MODES.putIfAbsent(player.getUUID(), ChatMode.LOCAL);
        }
    }

    public static void save(ServerPlayer player) {
        if (player == null) return;
        saveBlocking(player.getUUID(), get(player.getUUID()));
    }

    public static void saveAsync(UUID uuid, ChatMode mode) {
        if (uuid == null || mode == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("save chat preference", connection -> {
            ensureSchema(connection);
            upsert(connection, uuid, mode);
        });
    }

    public static void saveBlocking(UUID uuid, ChatMode mode) {
        if (uuid == null || mode == null || !DatabaseManager.isEnabled()) return;
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            upsert(connection, uuid, mode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void clear(UUID uuid) {
        if (uuid != null) MODES.remove(uuid);
    }

    private static void ensureSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists player_chat_preferences (" +
                    "player_uuid uuid primary key, " +
                    "chat_mode text not null default 'local', " +
                    "updated_at timestamptz not null default now())");
        }
    }

    private static void upsert(Connection connection, UUID uuid, ChatMode mode) throws Exception {
        try (var ps = connection.prepareStatement("insert into player_chat_preferences (player_uuid, chat_mode, updated_at) values (?, ?, now()) " +
                "on conflict (player_uuid) do update set chat_mode = excluded.chat_mode, updated_at = now()")) {
            ps.setObject(1, uuid);
            ps.setString(2, mode.id);
            ps.executeUpdate();
        }
    }
}
