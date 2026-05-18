package com.champutils.database;

import com.champutils.profile.PlayerDataManager;
import com.champutils.rank.SeasonManager;

import java.sql.PreparedStatement;
import java.util.UUID;

public final class PlayerDatabaseRepository {

    private PlayerDatabaseRepository() {
    }

    public static void sync(PlayerDataManager.PlayerData data) {
        if (data == null || data.uuid == null || data.uuid.isBlank()) {
            return;
        }

        if (SeasonManager.isCompletingSeasonReset()) {
            return;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(data.uuid);
        }
        catch (IllegalArgumentException e) {
            return;
        }

        RankedStatsDatabaseRepository.syncPlayer(
                uuid,
                safeName(data),
                data
        );
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
