package com.champutils.database;

import java.sql.PreparedStatement;
import java.util.UUID;

public final class CrateCreditDatabaseRepository {

    private CrateCreditDatabaseRepository() {
    }

    public static void setCredits(UUID uuid, String crateId, int credits) {
        if (uuid == null || crateId == null || crateId.isBlank()) {
            return;
        }

        DatabaseManager.executeAsync("sync crate credits " + uuid + " " + crateId, connection -> {
            try (PreparedStatement playerStatement = connection.prepareStatement(
                    "insert into players (uuid, username, last_seen) values (?, ?, now()) " +
                            "on conflict (uuid) do nothing"
            )) {
                playerStatement.setString(1, uuid.toString());
                playerStatement.setString(2, uuid.toString());
                playerStatement.executeUpdate();
            }

            if (credits <= 0) {
                try (PreparedStatement deleteStatement = connection.prepareStatement(
                        "delete from crate_credits where uuid = ? and crate_id = ?"
                )) {
                    deleteStatement.setString(1, uuid.toString());
                    deleteStatement.setString(2, crateId);
                    deleteStatement.executeUpdate();
                }
                return;
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into crate_credits (uuid, crate_id, credits, updated_at) values (?, ?, ?, now()) " +
                            "on conflict (uuid, crate_id) do update set credits = excluded.credits, updated_at = now()"
            )) {
                statement.setString(1, uuid.toString());
                statement.setString(2, crateId);
                statement.setInt(3, credits);
                statement.executeUpdate();
            }
        });
    }
}
