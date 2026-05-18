package com.champutils.database;

import com.champutils.dungeon.DungeonRarity;

import java.sql.PreparedStatement;
import java.util.UUID;

public final class DungeonProgressDatabaseRepository {

    private DungeonProgressDatabaseRepository() {
    }

    public static void recordClear(
            UUID uuid,
            String username,
            String dungeonId,
            DungeonRarity rarity
    ) {
        if (uuid == null || dungeonId == null || dungeonId.isBlank()) {
            return;
        }

        String safeUsername =
                username == null || username.isBlank()
                        ? uuid.toString()
                        : username;

        int tier =
                rarity == null
                        ? 1
                        : rarity.ordinal() + 1;

        DatabaseManager.executeAsync(
                "record dungeon clear " + uuid + " " + dungeonId,
                connection -> {

                    try (
                            PreparedStatement playerStatement =
                                    connection.prepareStatement(
                                            "insert into players (uuid, username, last_seen) " +
                                                    "values (?, ?, now()) " +
                                                    "on conflict (uuid) do update set " +
                                                    "username = excluded.username, " +
                                                    "last_seen = now()"
                                    )
                    ) {
                        playerStatement.setString(1, uuid.toString());
                        playerStatement.setString(2, safeUsername);
                        playerStatement.executeUpdate();
                    }

                    try (
                            PreparedStatement statement =
                                    connection.prepareStatement(
                                            "insert into dungeon_progress " +
                                                    "(uuid, dungeon_id, clears, highest_tier, updated_at) " +
                                                    "values (?, ?, 1, ?, now()) " +
                                                    "on conflict (uuid, dungeon_id) do update set " +
                                                    "clears = dungeon_progress.clears + 1, " +
                                                    "highest_tier = greatest(dungeon_progress.highest_tier, excluded.highest_tier), " +
                                                    "updated_at = now()"
                                    )
                    ) {
                        statement.setString(1, uuid.toString());
                        statement.setString(2, dungeonId);
                        statement.setInt(3, Math.max(1, tier));
                        statement.executeUpdate();
                    }
                }
        );
    }
}
