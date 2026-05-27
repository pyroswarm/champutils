package com.champutils.database;

import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.PlayerProfileManager;

import java.sql.PreparedStatement;
import java.util.UUID;

public final class RankedStatsDatabaseRepository {

    private RankedStatsDatabaseRepository() {}

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

        DatabaseManager.executeAsync("sync profile ranked stats " + profileId, connection -> {
            try (PreparedStatement deactivateStatement = connection.prepareStatement("update seasons set is_active = false where id <> ?")) {
                deactivateStatement.setString(1, seasonId);
                deactivateStatement.executeUpdate();
            } catch (Exception ignored) {
                try (PreparedStatement fallback = connection.prepareStatement("update seasons set active = false where id <> ?")) {
                    fallback.setString(1, seasonId);
                    fallback.executeUpdate();
                }
            }

            try (PreparedStatement seasonStatement = connection.prepareStatement(
                    "insert into seasons (id, display_name, starts_at, is_active) values (?, ?, now(), true) " +
                            "on conflict (id) do update set display_name = excluded.display_name, is_active = true"
            )) {
                seasonStatement.setString(1, seasonId);
                seasonStatement.setString(2, seasonName);
                seasonStatement.executeUpdate();
            } catch (Exception ignored) {
                try (PreparedStatement fallback = connection.prepareStatement(
                        "insert into seasons (id, display_name, starts_at, active) values (?, ?, now(), true) " +
                                "on conflict (id) do update set display_name = excluded.display_name, active = true"
                )) {
                    fallback.setString(1, seasonId);
                    fallback.setString(2, seasonName);
                    fallback.executeUpdate();
                }
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
