package com.champutils.database;

import com.champutils.profile.PlayerDataManager;
import com.champutils.rank.SeasonManager;

import java.sql.PreparedStatement;
import java.util.UUID;

public final class RankedStatsDatabaseRepository {

    private RankedStatsDatabaseRepository() {
    }

    public static void syncPlayer(
            UUID uuid,
            String username,
            PlayerDataManager.PlayerData data
    ) {
        if (uuid == null || username == null || username.isBlank() || data == null) {
            return;
        }

        if (SeasonManager.isCompletingSeasonReset()) {
            return;
        }

        String seasonId = SeasonDatabaseRepository.currentSeasonId();
        String seasonName = SeasonDatabaseRepository.currentSeasonName();

        int rp = Math.max(0, data.rp);
        int wins = Math.max(0, data.rankedWins);
        int losses = Math.max(0, data.rankedLosses);

        DatabaseManager.executeAsync(
                "sync ranked stats " + uuid,
                connection -> {
                    try (PreparedStatement playerStatement = connection.prepareStatement(
                            "insert into players (uuid, username, last_seen) " +
                                    "values (?, ?, now()) " +
                                    "on conflict (uuid) do update set " +
                                    "username = excluded.username, " +
                                    "last_seen = now()"
                    )) {
                        playerStatement.setString(1, uuid.toString());
                        playerStatement.setString(2, username);
                        playerStatement.executeUpdate();
                    }

                    try (PreparedStatement deactivateStatement = connection.prepareStatement(
                            "update seasons set active = false where id <> ?"
                    )) {
                        deactivateStatement.setString(1, seasonId);
                        deactivateStatement.executeUpdate();
                    }

                    try (PreparedStatement seasonStatement = connection.prepareStatement(
                            "insert into seasons (id, display_name, starts_at, active) " +
                                    "values (?, ?, now(), true) " +
                                    "on conflict (id) do update set " +
                                    "display_name = excluded.display_name, " +
                                    "active = true"
                    )) {
                        seasonStatement.setString(1, seasonId);
                        seasonStatement.setString(2, seasonName);
                        seasonStatement.executeUpdate();
                    }

                    try (PreparedStatement rankedStatement = connection.prepareStatement(
                            "insert into ranked_stats " +
                                    "(uuid, season_id, rp, wins, losses, updated_at) " +
                                    "values (?, ?, ?, ?, ?, now()) " +
                                    "on conflict (uuid, season_id) do update set " +
                                    "rp = excluded.rp, " +
                                    "wins = excluded.wins, " +
                                    "losses = excluded.losses, " +
                                    "updated_at = now()"
                    )) {
                        rankedStatement.setString(1, uuid.toString());
                        rankedStatement.setString(2, seasonId);
                        rankedStatement.setInt(3, rp);
                        rankedStatement.setInt(4, wins);
                        rankedStatement.setInt(5, losses);
                        rankedStatement.executeUpdate();
                    }
                }
        );
    }

    public static void syncPlayer(
            UUID uuid,
            String username
    ) {
        PlayerDataManager.PlayerData data = PlayerDataManager.load(uuid, username);
        syncPlayer(uuid, username, data);
    }
}
