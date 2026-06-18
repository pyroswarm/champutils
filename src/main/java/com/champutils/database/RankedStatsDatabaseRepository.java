package com.champutils.database;

import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.PlayerProfileManager;

import java.sql.PreparedStatement;
import java.util.UUID;

public final class RankedStatsDatabaseRepository {

    private static volatile boolean seasonColumnsEnsured = false;

    private RankedStatsDatabaseRepository() {}

    private static void ensureSeasonColumns(java.sql.Connection connection) throws Exception {
        if (seasonColumnsEnsured) return;
        try (PreparedStatement activeColumn = connection.prepareStatement(
                "alter table seasons add column if not exists active boolean not null default false"
        )) {
            activeColumn.executeUpdate();
        }

        try (PreparedStatement isActiveColumn = connection.prepareStatement(
                "alter table seasons add column if not exists is_active boolean not null default false"
        )) {
            isActiveColumn.executeUpdate();
        }
        seasonColumnsEnsured = true;
    }

    public static void syncPlayer(UUID uuid, String username, PlayerDataManager.PlayerData data) {
        if (uuid == null || username == null || username.isBlank() || data == null) return;

        UUID profileId = PlayerProfileManager.activeProfileId(uuid);
        String seasonId = SeasonDatabaseRepository.currentSeasonId();
        String seasonName = SeasonDatabaseRepository.currentSeasonName();

        int rp = Math.max(0, data.rp);
        int peakRp = Math.max(rp, data.peakRp);
        int wins = Math.max(0, data.rankedWins);
        int losses = Math.max(0, data.rankedLosses);
        int streak = Math.max(0, data.currentStreak);

        DatabaseManager.executeCoalescedAsync("ranked-stats:" + profileId + ":" + seasonId, "sync profile ranked stats " + profileId, connection -> {
            ensureSeasonColumns(connection);

            try (PreparedStatement deactivateStatement = connection.prepareStatement(
                    "update seasons set active = false, is_active = false where id <> ?"
            )) {
                deactivateStatement.setString(1, seasonId);
                deactivateStatement.executeUpdate();
            }

            try (PreparedStatement seasonStatement = connection.prepareStatement(
                    "insert into seasons (id, display_name, starts_at, active, is_active) values (?, ?, now(), true, true) " +
                            "on conflict (id) do update set display_name = excluded.display_name, active = true, is_active = true"
            )) {
                seasonStatement.setString(1, seasonId);
                seasonStatement.setString(2, seasonName);
                seasonStatement.executeUpdate();
            }

            try (PreparedStatement rankedStatement = connection.prepareStatement(
                    "insert into profile_ranked_stats (profile_id, season_id, rp, peak_rp, wins, losses, streak, updated_at) " +
                            "values (?, ?, ?, ?, ?, ?, ?, now()) " +
                            "on conflict (profile_id, season_id) do update set rp = excluded.rp, peak_rp = greatest(profile_ranked_stats.peak_rp, excluded.peak_rp), wins = excluded.wins, losses = excluded.losses, streak = excluded.streak, updated_at = now()"
            )) {
                rankedStatement.setObject(1, profileId);
                rankedStatement.setString(2, seasonId);
                rankedStatement.setInt(3, rp);
                rankedStatement.setInt(4, peakRp);
                rankedStatement.setInt(5, wins);
                rankedStatement.setInt(6, losses);
                rankedStatement.setInt(7, streak);
                rankedStatement.executeUpdate();
            }
        });
    }

    public static void syncPlayer(UUID uuid, String username) {
        PlayerDataManager.PlayerData data = PlayerDataManager.load(uuid, username);
        syncPlayer(uuid, username, data);
    }
}
