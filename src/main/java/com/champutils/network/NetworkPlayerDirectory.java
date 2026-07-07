package com.champutils.network;

import com.champutils.database.DatabaseManager;
import com.champutils.chat.ChatTagResolver;
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

    public record OnlinePlayer(UUID playerUuid, String playerName, String serverId, int pingMs, String rankTag, String titleTag) {
    }

    public static void ensureSchema(Connection connection) throws Exception {
        if (connection == null) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "create table if not exists network_online_players (" +
                            "player_uuid uuid primary key, " +
                            "player_name text not null, " +
                            "server_id text not null, " +
                            "ping_ms integer not null default -1, " +
                            "last_seen timestamptz not null default now(), " +
                            "expires_at timestamptz not null default (now() + interval '90 seconds')" +
                            ")"
            );
            statement.executeUpdate("alter table network_online_players add column if not exists ping_ms integer not null default -1");
            statement.executeUpdate("alter table network_online_players add column if not exists rank_tag text not null default ''");
            statement.executeUpdate("alter table network_online_players add column if not exists title_tag text not null default ''");
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
            local.add(new OnlinePlayer(
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    serverId,
                    ping(player),
                    ChatTagResolver.donationRankLegacy(player),
                    ChatTagResolver.activeTitleLegacy(player)
            ));
        }

        DatabaseManager.executeCoalescedAsync("network-online-players:" + serverId, "sync network online players", connection -> {
            ensureSchema(connection);
            try (PreparedStatement delete = connection.prepareStatement("delete from network_online_players where server_id = ?")) {
                delete.setString(1, serverId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into network_online_players (player_uuid, player_name, server_id, ping_ms, rank_tag, title_tag, last_seen, expires_at) " +
                            "values (?, ?, ?, ?, ?, ?, now(), now() + interval '90 seconds') " +
                            "on conflict (player_uuid) do update set player_name = excluded.player_name, server_id = excluded.server_id, ping_ms = excluded.ping_ms, rank_tag = excluded.rank_tag, title_tag = excluded.title_tag, last_seen = now(), expires_at = excluded.expires_at"
            )) {
                for (OnlinePlayer player : local) {
                    insert.setObject(1, player.playerUuid());
                    insert.setString(2, player.playerName());
                    insert.setString(3, player.serverId());
                    insert.setInt(4, player.pingMs());
                    insert.setString(5, limit(player.rankTag(), 80));
                    insert.setString(6, limit(player.titleTag(), 120));
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
                "select player_uuid::text, player_name, server_id, ping_ms, rank_tag, title_tag from network_online_players where expires_at > now() order by lower(player_name)"
        );
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString(1));
                String name = rs.getString(2);
                String serverId = rs.getString(3);
                int pingMs = rs.getInt(4);
                String rankTag = rs.getString(5);
                String titleTag = rs.getString(6);
                if (name == null || name.isBlank()) continue;
                next.put(name.toLowerCase(Locale.ROOT), new OnlinePlayer(uuid, name, serverId, pingMs, rankTag == null ? "" : rankTag, titleTag == null ? "" : titleTag));
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

    private static int ping(ServerPlayer player) {
        if (player == null || player.connection == null) return -1;
        try {
            Object value = player.connection.getClass().getMethod("latency").invoke(player.connection);
            if (value instanceof Number number) return Math.max(0, number.intValue());
        } catch (Throwable ignored) {
        }
        try {
            Object value = player.connection.getClass().getField("latency").get(player.connection);
            if (value instanceof Number number) return Math.max(0, number.intValue());
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
