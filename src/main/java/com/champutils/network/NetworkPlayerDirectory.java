package com.champutils.network;

import com.champutils.database.DatabaseManager;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkPlayerDirectory {
    private static final Map<String, OnlinePlayer> ONLINE_BY_NAME = new ConcurrentHashMap<>();
    private static volatile long lastRefreshMillis = 0L;

    private NetworkPlayerDirectory() {
    }

    public record OnlinePlayer(UUID playerUuid, String playerName, String serverId) {
    }

    public static void ensureSchema(Connection connection) throws Exception {
        if (connection == null) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "create table if not exists network_online_players (" +
                            "player_uuid uuid primary key, " +
                            "player_name text not null, " +
                            "server_id text not null, " +
                            "last_seen timestamptz not null default now(), " +
                            "expires_at timestamptz not null default (now() + interval '90 seconds')" +
                            ")"
            );
            statement.executeUpdate("create index if not exists network_online_players_name_idx on network_online_players (lower(player_name))");
            statement.executeUpdate("create index if not exists network_online_players_server_idx on network_online_players (server_id, expires_at desc)");
            statement.executeUpdate("create index if not exists network_online_players_expires_idx on network_online_players (expires_at)");
        }
    }

    public static void syncLocalPlayers(MinecraftServer server) {
        if (server == null || !DatabaseManager.isEnabled()) return;
        String serverId = NetworkServerConfig.serverId();
        List<OnlinePlayer> local = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null) continue;
            local.add(new OnlinePlayer(player.getUUID(), player.getGameProfile().getName(), serverId));
        }

        DatabaseManager.executeCoalescedAsync("network-online-players:" + serverId, "sync network online players", connection -> {
            ensureSchema(connection);
            try (PreparedStatement delete = connection.prepareStatement("delete from network_online_players where server_id = ?")) {
                delete.setString(1, serverId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into network_online_players (player_uuid, player_name, server_id, last_seen, expires_at) " +
                            "values (?, ?, ?, now(), now() + interval '90 seconds') " +
                            "on conflict (player_uuid) do update set player_name = excluded.player_name, server_id = excluded.server_id, last_seen = now(), expires_at = excluded.expires_at"
            )) {
                for (OnlinePlayer player : local) {
                    insert.setObject(1, player.playerUuid());
                    insert.setString(2, player.playerName());
                    insert.setString(3, player.serverId());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            refreshCache(connection);
        });
    }

    public static void markServerOffline(String serverId) {
        if (serverId == null || serverId.isBlank() || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeCoalescedAsync("network-online-players:" + serverId, "clear network online players", connection -> {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("delete from network_online_players where server_id = ?")) {
                statement.setString(1, serverId);
                statement.executeUpdate();
            }
            refreshCache(connection);
        });
    }

    public static void refreshCacheAsync() {
        if (!DatabaseManager.isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastRefreshMillis < 5_000L) return;
        lastRefreshMillis = now;
        DatabaseManager.executeAsync("refresh network player directory", connection -> {
            ensureSchema(connection);
            refreshCache(connection);
        });
    }

    private static void refreshCache(Connection connection) throws Exception {
        Map<String, OnlinePlayer> next = new ConcurrentHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select player_uuid::text, player_name, server_id from network_online_players where expires_at > now() order by lower(player_name)"
        );
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString(1));
                String name = rs.getString(2);
                String serverId = rs.getString(3);
                if (name == null || name.isBlank()) continue;
                next.put(name.toLowerCase(Locale.ROOT), new OnlinePlayer(uuid, name, serverId));
            }
        }
        ONLINE_BY_NAME.clear();
        ONLINE_BY_NAME.putAll(next);
    }

    public static OnlinePlayer find(String name) {
        if (name == null || name.isBlank()) return null;
        return ONLINE_BY_NAME.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public static List<OnlinePlayer> onlinePlayers() {
        return ONLINE_BY_NAME.values().stream()
                .sorted(Comparator.comparing(p -> p.playerName().toLowerCase(Locale.ROOT)))
                .toList();
    }

    public static List<String> names() {
        return onlinePlayers().stream().map(OnlinePlayer::playerName).toList();
    }

    public static CompletableFuture<Suggestions> suggestNames(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        refreshCacheAsync();
        return SharedSuggestionProvider.suggest(names(), builder);
    }
}
