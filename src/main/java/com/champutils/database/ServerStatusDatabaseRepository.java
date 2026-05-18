package com.champutils.database;

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

        DatabaseManager.executeAsync("sync server status", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into server_status (id, online_players, max_players, motd, last_heartbeat) values ('main', ?, ?, ?, now()) " +
                            "on conflict (id) do update set online_players = excluded.online_players, max_players = excluded.max_players, motd = excluded.motd, last_heartbeat = now()"
            )) {
                statement.setInt(1, Math.max(0, onlinePlayers));
                statement.setInt(2, Math.max(0, maxPlayers));
                statement.setString(3, motd == null ? "" : motd);
                statement.executeUpdate();
            }
        });
    }

    public static void markOffline(MinecraftServer server) {
        int maxPlayers = server == null ? 0 : server.getPlayerList().getMaxPlayers();
        String motd = server == null || server.getMotd() == null ? "" : server.getMotd();

        DatabaseManager.executeAsync("mark server offline", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into server_status (id, online_players, max_players, motd, last_heartbeat) values ('main', 0, ?, ?, now()) " +
                            "on conflict (id) do update set online_players = 0, max_players = excluded.max_players, motd = excluded.motd, last_heartbeat = now()"
            )) {
                statement.setInt(1, Math.max(0, maxPlayers));
                statement.setString(2, motd);
                statement.executeUpdate();
            }
        });
    }
}
