package com.champutils.database;

import com.champutils.network.NetworkServerConfig;
import net.minecraft.server.MinecraftServer;

import java.sql.PreparedStatement;

public final class ServerStatusDatabaseRepository {

    private ServerStatusDatabaseRepository() {
    }

    public static void sync(MinecraftServer server) {
        if (server == null) {
            return;
        }

        int onlinePlayers = server.getPlayerList().getPlayerCount();
        int maxPlayers = server.getPlayerList().getMaxPlayers();
        String motd = server.getMotd();
        String serverId = NetworkServerConfig.serverId();
        String serverRole = NetworkServerConfig.serverRole().name();

        DatabaseManager.executeAsync("sync server status", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into server_status (id, online_players, max_players, motd, last_heartbeat) values (?, ?, ?, ?, now()) " +
                            "on conflict (id) do update set online_players = excluded.online_players, max_players = excluded.max_players, motd = excluded.motd, last_heartbeat = now()"
            )) {
                statement.setString(1, serverId);
                statement.setInt(2, Math.max(0, onlinePlayers));
                statement.setInt(3, Math.max(0, maxPlayers));
                statement.setString(4, motd == null ? "" : motd);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into server_nodes (server_id, server_role, online_players, max_players, motd, last_heartbeat) values (?, ?, ?, ?, ?, now()) " +
                            "on conflict (server_id) do update set server_role = excluded.server_role, online_players = excluded.online_players, max_players = excluded.max_players, motd = excluded.motd, last_heartbeat = now()"
            )) {
                statement.setString(1, serverId);
                statement.setString(2, serverRole);
                statement.setInt(3, Math.max(0, onlinePlayers));
                statement.setInt(4, Math.max(0, maxPlayers));
                statement.setString(5, motd == null ? "" : motd);
                statement.executeUpdate();
            }
        });
    }

    public static void markOffline(MinecraftServer server) {
        int maxPlayers = server == null ? 0 : server.getPlayerList().getMaxPlayers();
        String motd = server == null || server.getMotd() == null ? "" : server.getMotd();
        String serverId = NetworkServerConfig.serverId();
        String serverRole = NetworkServerConfig.serverRole().name();

        DatabaseManager.executeAsync("mark server offline", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into server_status (id, online_players, max_players, motd, last_heartbeat) values (?, 0, ?, ?, now()) " +
                            "on conflict (id) do update set online_players = 0, max_players = excluded.max_players, motd = excluded.motd, last_heartbeat = now()"
            )) {
                statement.setString(1, serverId);
                statement.setInt(2, Math.max(0, maxPlayers));
                statement.setString(3, motd);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into server_nodes (server_id, server_role, online_players, max_players, motd, last_heartbeat) values (?, ?, 0, ?, ?, now()) " +
                            "on conflict (server_id) do update set server_role = excluded.server_role, online_players = 0, max_players = excluded.max_players, motd = excluded.motd, last_heartbeat = now()"
            )) {
                statement.setString(1, serverId);
                statement.setString(2, serverRole);
                statement.setInt(3, Math.max(0, maxPlayers));
                statement.setString(4, motd);
                statement.executeUpdate();
            }
        });
    }
}
