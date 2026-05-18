package com.champutils.database;

import java.sql.PreparedStatement;
import java.util.UUID;

public final class CreditsDatabaseRepository {

    private static boolean schemaEnsured = false;

    private CreditsDatabaseRepository() {
    }

    private static void ensureSchema(java.sql.Connection connection) throws Exception {
        if (schemaEnsured) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists player_economy (" +
                        "uuid text primary key, " +
                        "username text not null, " +
                        "credits bigint not null default 0, " +
                        "lifetime_earned bigint not null default 0, " +
                        "lifetime_spent bigint not null default 0, " +
                        "updated_at timestamp with time zone default now()" +
                        ")"
        )) {
            statement.executeUpdate();
        }

        schemaEnsured = true;
    }

    public static void sync(
            UUID playerId,
            String username,
            long credits,
            long lifetimeEarned,
            long lifetimeSpent
    ) {
        if (playerId == null) {
            return;
        }

        long safeCredits = Math.max(0L, credits);
        long safeEarned = Math.max(0L, lifetimeEarned);
        long safeSpent = Math.max(0L, lifetimeSpent);
        String safeUsername = username == null || username.isBlank()
                ? playerId.toString()
                : username;

        DatabaseManager.executeAsync("sync credits " + playerId, connection -> {
            ensureSchema(connection);

            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into player_economy (uuid, username, credits, lifetime_earned, lifetime_spent, updated_at) " +
                            "values (?, ?, ?, ?, ?, now()) " +
                            "on conflict (uuid) do update set " +
                            "username = excluded.username, " +
                            "credits = excluded.credits, " +
                            "lifetime_earned = excluded.lifetime_earned, " +
                            "lifetime_spent = excluded.lifetime_spent, " +
                            "updated_at = now()"
            )) {
                statement.setString(1, playerId.toString());
                statement.setString(2, safeUsername);
                statement.setLong(3, safeCredits);
                statement.setLong(4, safeEarned);
                statement.setLong(5, safeSpent);
                statement.executeUpdate();
            }
        });
    }
}
