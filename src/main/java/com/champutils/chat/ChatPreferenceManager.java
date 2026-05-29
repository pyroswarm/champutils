package com.champutils.chat;

import com.champutils.database.DatabaseManager;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatPreferenceManager {
    public record ChatPreferences(ChatMode mode) {
        public static ChatPreferences defaults() {
            return new ChatPreferences(ChatMode.LOCAL);
        }
    }

    private static final Map<UUID, ChatMode> MODES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ChatPreferences> CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();

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

    public static ChatPreferences getCachedOrDefault(UUID uuid) {
        if (uuid == null) return ChatPreferences.defaults();
        ChatPreferences cached = CACHE.get(uuid);
        if (cached != null) return cached;
        return new ChatPreferences(MODES.getOrDefault(uuid, ChatMode.LOCAL));
    }

    public static void set(UUID uuid, ChatMode mode) {
        if (uuid == null || mode == null) return;
        ChatPreferences prefs = new ChatPreferences(mode);
        CACHE.put(uuid, prefs);
        MODES.put(uuid, mode);
        DIRTY.add(uuid);
        saveAsync(uuid, mode);
    }

    public static void setWithoutSave(UUID uuid, ChatMode mode) {
        if (uuid == null || mode == null) return;
        ChatPreferences prefs = new ChatPreferences(mode);
        CACHE.put(uuid, prefs);
        MODES.put(uuid, mode);
    }

    /**
     * Async account-level preload. SQL runs only on the database executor.
     */
    public static CompletableFuture<ChatPreferences> loadAsync(UUID playerUuid) {
        if (playerUuid == null) return CompletableFuture.completedFuture(ChatPreferences.defaults());

        ChatPreferences cached = CACHE.get(playerUuid);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        if (!DatabaseManager.isEnabled()) {
            ChatPreferences defaults = ChatPreferences.defaults();
            CACHE.putIfAbsent(playerUuid, defaults);
            MODES.putIfAbsent(playerUuid, defaults.mode());
            return CompletableFuture.completedFuture(defaults);
        }

        final ChatPreferences[] loaded = new ChatPreferences[] { ChatPreferences.defaults() };
        return DatabaseManager.runAsync("load chat preference", connection -> {
            long start = System.currentTimeMillis();
            try {
                loaded[0] = loadFromSql(connection, playerUuid);
            } finally {
                System.out.println("[PROFILE-TIMING] ChatPreferenceManager.loadAsync.SQL took " + (System.currentTimeMillis() - start) + "ms");
            }
        }).handle((ignored, error) -> {
            if (error != null) {
                System.err.println("[ChampUtils] Failed to load chat preferences async for " + playerUuid + ": " + error.getMessage());
                ChatPreferences fallback = getCachedOrDefault(playerUuid);
                CACHE.putIfAbsent(playerUuid, fallback);
                MODES.putIfAbsent(playerUuid, fallback.mode());
                return fallback;
            }
            CACHE.put(playerUuid, loaded[0]);
            MODES.put(playerUuid, loaded[0].mode());
            return loaded[0];
        });
    }

    /**
     * Server-thread apply only. Never performs SQL.
     */
    public static void apply(ServerPlayer player, ChatPreferences prefs) {
        long start = System.currentTimeMillis();
        try {
            if (player == null || prefs == null) return;
            UUID uuid = player.getUUID();
            CACHE.put(uuid, prefs);
            MODES.put(uuid, prefs.mode() == null ? ChatMode.LOCAL : prefs.mode());
        } finally {
            System.out.println("[PROFILE-TIMING] ChatPreferenceManager.apply took " + (System.currentTimeMillis() - start) + "ms");
        }
    }

    /**
     * Legacy compatibility entry point. It no longer blocks the server thread on SQL.
     */
    public static void load(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        loadAsync(uuid).thenAccept(prefs -> {
            if (player.server == null) return;
            player.server.execute(() -> {
                ServerPlayer current = player.server.getPlayerList().getPlayer(uuid);
                if (current != null) apply(current, prefs);
            });
        });
    }

    public static void preloadOnJoin(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        loadAsync(uuid).thenAccept(prefs -> {
            if (player.server == null) return;
            player.server.execute(() -> {
                ServerPlayer current = player.server.getPlayerList().getPlayer(uuid);
                if (current != null) apply(current, prefs);
            });
        });
    }

    public static void save(ServerPlayer player) {
        if (player == null) return;
        saveAsync(player.getUUID(), get(player.getUUID()));
    }

    public static void saveAsync(UUID uuid, ChatMode mode) {
        if (uuid == null || mode == null || !DatabaseManager.isEnabled()) return;
        if (!DIRTY.contains(uuid)) return;
        ChatPreferences prefs = new ChatPreferences(mode);
        CACHE.put(uuid, prefs);
        MODES.put(uuid, mode);
        DatabaseManager.executeAsync("save chat preference", connection -> {
            ensureSchema(connection);
            upsert(connection, uuid, mode);
            DIRTY.remove(uuid);
        });
    }

    public static void saveBlocking(UUID uuid, ChatMode mode) {
        if (uuid == null || mode == null || !DatabaseManager.isEnabled()) return;
        if (!DIRTY.contains(uuid)) return;
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            upsert(connection, uuid, mode);
            ChatPreferences prefs = new ChatPreferences(mode);
            CACHE.put(uuid, prefs);
            MODES.put(uuid, mode);
            DIRTY.remove(uuid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void clear(UUID uuid) {
        if (uuid != null) {
            MODES.remove(uuid);
            CACHE.remove(uuid);
        }
    }

    private static ChatPreferences loadFromSql(Connection connection, UUID uuid) throws Exception {
        ensureSchema(connection);
        try (var ps = connection.prepareStatement("select chat_mode from player_chat_preferences where player_uuid = ?")) {
            ps.setObject(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ChatMode mode = ChatMode.parse(rs.getString("chat_mode"));
                    return new ChatPreferences(mode == null ? ChatMode.LOCAL : mode);
                }
            }
        }

        ChatPreferences defaults = ChatPreferences.defaults();
        upsert(connection, uuid, defaults.mode());
        return defaults;
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
