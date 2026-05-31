package com.champutils.database;

import com.champutils.rank.SeasonManager;

import java.sql.PreparedStatement;

public final class SeasonProfileDatabaseRepository {
    private SeasonProfileDatabaseRepository() {}

    public static void rolloverToNewSeason(int oldSeason, int newSeason, String newSeasonName, int resetFloor, double resetPercent) {
        if (!DatabaseManager.isEnabled()) {
            return;
        }

        String oldSeasonId = "season_" + Math.max(1, oldSeason);
        String newSeasonId = "season_" + Math.max(1, newSeason);
        String safeName = newSeasonName == null || newSeasonName.isBlank() ? "Season " + Math.max(1, newSeason) : newSeasonName;

        DatabaseManager.executeAsync("profile season rollover " + oldSeasonId + " -> " + newSeasonId, connection -> {
            try (PreparedStatement schema = connection.prepareStatement(
                    "alter table seasons add column if not exists active boolean not null default false"
            )) { schema.executeUpdate(); }

            try (PreparedStatement endOld = connection.prepareStatement(
                    "update seasons set active = false, ends_at = coalesce(ends_at, now()) where id = ?"
            )) {
                endOld.setString(1, oldSeasonId);
                endOld.executeUpdate();
            }

            try (PreparedStatement deactivate = connection.prepareStatement("update seasons set active = false")) {
                deactivate.executeUpdate();
            }

            try (PreparedStatement newSeasonStatement = connection.prepareStatement(
                    "insert into seasons (id, display_name, starts_at, active) values (?, ?, now(), true) " +
                            "on conflict (id) do update set display_name = excluded.display_name, active = true"
            )) {
                newSeasonStatement.setString(1, newSeasonId);
                newSeasonStatement.setString(2, safeName);
                newSeasonStatement.executeUpdate();
            }

            try (PreparedStatement carry = connection.prepareStatement(
                    "insert into profile_ranked_stats (profile_id, season_id, rp, peak_rp, wins, losses, streak, updated_at) " +
                            "select p.id, ?, " +
                            "greatest(?, round(? + ((coalesce(old.rp, ?) - ?) * ?))::int), " +
                            "greatest(?, round(? + ((coalesce(old.rp, ?) - ?) * ?))::int), " +
                            "0, 0, 0, now() " +
                            "from player_profiles p " +
                            "left join profile_ranked_stats old on old.profile_id = p.id and old.season_id = ? " +
                            "where p.deleted_at is null and coalesce(p.is_pending_delete, false) = false " +
                            "on conflict (profile_id, season_id) do update set " +
                            "rp = excluded.rp, peak_rp = excluded.peak_rp, wins = 0, losses = 0, streak = 0, updated_at = now()"
            )) {
                carry.setString(1, newSeasonId);
                carry.setInt(2, resetFloor);
                carry.setInt(3, resetFloor);
                carry.setInt(4, resetFloor);
                carry.setInt(5, resetFloor);
                carry.setDouble(6, resetPercent);
                carry.setInt(7, resetFloor);
                carry.setInt(8, resetFloor);
                carry.setInt(9, resetFloor);
                carry.setInt(10, resetFloor);
                carry.setDouble(11, resetPercent);
                carry.setString(12, oldSeasonId);
                carry.executeUpdate();
            }
        });
    }

    public static void rollbackActiveSeason(int season, String seasonName) {
        if (!DatabaseManager.isEnabled()) return;
        SeasonDatabaseRepository.setActiveSeason(Math.max(1, season), seasonName);
    }
}
