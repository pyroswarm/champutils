package com.champutils.database;

import java.sql.PreparedStatement;
import java.util.UUID;

public final class WorldEventStatsDatabaseRepository {

    private WorldEventStatsDatabaseRepository() {
    }

    public static void recordCompletion(
            UUID uuid,
            String username,
            String eventId,
            int rareDrops
    ) {
        if (uuid == null || eventId == null || eventId.isBlank()) {
            return;
        }

        String safeUsername =
                username == null || username.isBlank()
                        ? uuid.toString()
                        : username;

        int safeRareDrops =
                Math.max(0, rareDrops);

        DatabaseManager.executeAsync(
                "record world event completion " + uuid + " " + eventId,
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
                                            "insert into world_event_stats " +
                                                    "(uuid, event_id, completions, rare_drops, updated_at) " +
                                                    "values (?, ?, 1, ?, now()) " +
                                                    "on conflict (uuid, event_id) do update set " +
                                                    "completions = world_event_stats.completions + 1, " +
                                                    "rare_drops = world_event_stats.rare_drops + excluded.rare_drops, " +
                                                    "updated_at = now()"
                                    )
                    ) {
                        statement.setString(1, uuid.toString());
                        statement.setString(2, eventId);
                        statement.setInt(3, safeRareDrops);
                        statement.executeUpdate();
                    }
                }
        );
    }
}
