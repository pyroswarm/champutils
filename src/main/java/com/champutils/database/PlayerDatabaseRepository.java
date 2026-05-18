package com.champutils.database;

import com.champutils.profile.PlayerDataManager;

import java.sql.PreparedStatement;
import java.util.UUID;

public final class PlayerDatabaseRepository {

    private static final String DEFAULT_SEASON_ID = "season_1";

    private PlayerDatabaseRepository() {
    }

    public static void sync(PlayerDataManager.PlayerData data) {
        if (data == null || data.uuid == null || data.uuid.isBlank()) {
            return;
        }

        DatabaseManager.executeAsync("sync ranked player " + data.uuid, connection -> {
            try (PreparedStatement playerStatement = connection.prepareStatement(
                    "insert into players (uuid, username, last_seen) values (?, ?, now()) " +
                            "on conflict (uuid) do update set username = excluded.username, last_seen = now()"
            )) {
                playerStatement.setString(1, data.uuid);
                playerStatement.setString(2, safeName(data));
                playerStatement.executeUpdate();
            }

            try (PreparedStatement rankedStatement = connection.prepareStatement(
                    "insert into ranked_stats (uuid, season_id, rp, wins, losses, updated_at) values (?, ?, ?, ?, ?, now()) " +
                            "on conflict (uuid, season_id) do update set rp = excluded.rp, wins = excluded.wins, losses = excluded.losses, updated_at = now()"
            )) {
                rankedStatement.setString(1, data.uuid);
                rankedStatement.setString(2, DEFAULT_SEASON_ID);
                rankedStatement.setInt(3, Math.max(0, data.rp));
                rankedStatement.setInt(4, Math.max(0, data.rankedWins));
                rankedStatement.setInt(5, Math.max(0, data.rankedLosses));
                rankedStatement.executeUpdate();
            }
        });
    }


    public static void saveAsync(UUID uuid, String name, PlayerDataManager.PlayerData data) {
        if (data == null) {
            return;
        }
        if (data.uuid == null && uuid != null) {
            data.uuid = uuid.toString();
        }
        if ((data.name == null || data.name.isBlank()) && name != null) {
            data.name = name;
        }
        sync(data);
    }

    public static void touchPlayer(UUID uuid, String name) {
        if (uuid == null) {
            return;
        }

        DatabaseManager.executeAsync("touch player " + uuid, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into players (uuid, username, last_seen) values (?, ?, now()) " +
                            "on conflict (uuid) do update set username = excluded.username, last_seen = now()"
            )) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name == null || name.isBlank() ? uuid.toString() : name);
                statement.executeUpdate();
            }
        });
    }

    private static String safeName(PlayerDataManager.PlayerData data) {
        if (data.name == null || data.name.isBlank()) {
            return data.uuid;
        }
        return data.name;
    }
}
