package com.champutils.database;

import com.champutils.profession.ProfessionDataManager;
import com.champutils.profession.ProfessionType;

import java.sql.PreparedStatement;
import java.util.UUID;

public final class ProfessionDatabaseRepository {

    private ProfessionDatabaseRepository() {
    }

    public static void sync(ProfessionDataManager.ProfessionData data) {
        if (data == null || data.uuid == null || data.uuid.isBlank()) {
            return;
        }

        DatabaseManager.executeAsync("sync profession player " + data.uuid, connection -> {
            try (PreparedStatement playerStatement = connection.prepareStatement(
                    "insert into players (uuid, username, last_seen) values (?, ?, now()) " +
                            "on conflict (uuid) do update set username = excluded.username, last_seen = now()"
            )) {
                playerStatement.setString(1, data.uuid);
                playerStatement.setString(2, data.name == null || data.name.isBlank() ? data.uuid : data.name);
                playerStatement.executeUpdate();
            }

            try (PreparedStatement professionStatement = connection.prepareStatement(
                    "insert into profession_stats (uuid, profession, level, xp, updated_at) values (?, ?, ?, ?, now()) " +
                            "on conflict (uuid, profession) do update set level = excluded.level, xp = excluded.xp, updated_at = now()"
            )) {
                for (ProfessionType type : ProfessionType.values()) {
                    String key = type.name();
                    professionStatement.setString(1, data.uuid);
                    professionStatement.setString(2, key);
                    professionStatement.setInt(3, Math.max(1, data.levels.getOrDefault(key, 1)));
                    professionStatement.setLong(4, Math.max(0, data.xp.getOrDefault(key, 0)));
                    professionStatement.addBatch();
                }
                professionStatement.executeBatch();
            }
        });
    }


    public static void saveAsync(UUID uuid, ProfessionDataManager.ProfessionData data) {
        if (data == null) {
            return;
        }
        if (data.uuid == null && uuid != null) {
            data.uuid = uuid.toString();
        }
        sync(data);
    }

    public static void touchPlayer(UUID uuid, String name) {
        PlayerDatabaseRepository.touchPlayer(uuid, name);
    }
}
