package com.champutils.database;

import com.champutils.profile.PlayerDataManager;
import com.champutils.rank.SeasonManager;

import java.sql.PreparedStatement;

public final class SeasonDatabaseRepository {

    private SeasonDatabaseRepository() {
    }

    public static String currentSeasonId() {
        int season = Math.max(0, SeasonManager.CURRENT_SEASON);
        return "season_" + season;
    }

    public static String currentSeasonName() {
        if (SeasonManager.CURRENT_NAME == null || SeasonManager.CURRENT_NAME.isBlank()) {
            return "Season " + Math.max(0, SeasonManager.CURRENT_SEASON);
        }
        return SeasonManager.CURRENT_NAME;
    }

    /**
     * Used by DatabaseBootstrapSync and periodic syncs.
     * Ensures Supabase has exactly one active season row using the existing `active` column.
     */
    public static void syncCurrentSeason() {
        setActiveSeason(Math.max(0, SeasonManager.CURRENT_SEASON), currentSeasonName());
    }

    /**
     * Used by /season start/reset flows.
     * This marks every old season inactive, then upserts the new current season as active.
     */
    public static void setActiveSeason(int seasonNumber, String displayName) {
        int safeSeasonNumber = Math.max(0, seasonNumber);
        String seasonId = "season_" + safeSeasonNumber;
        String safeDisplayName = displayName == null || displayName.isBlank()
                ? "Season " + safeSeasonNumber
                : displayName;

        DatabaseManager.executeAsync("set active season " + seasonId, connection -> {
            ensureSeasonColumns(connection);

            try (PreparedStatement deactivate = connection.prepareStatement(
                    "update seasons set active = false, is_active = false"
            )) {
                deactivate.executeUpdate();
            }

            try (PreparedStatement upsert = connection.prepareStatement(
                    "insert into seasons (id, display_name, starts_at, active, is_active) values (?, ?, now(), true, true) " +
                            "on conflict (id) do update set " +
                            "display_name = excluded.display_name, " +
                            "active = true, " +
                            "is_active = true"
            )) {
                upsert.setString(1, seasonId);
                upsert.setString(2, safeDisplayName);
                upsert.executeUpdate();
            }
        });
    }

    public static void syncSeason(int seasonNumber, String displayName, boolean active) {
        int safeSeason = Math.max(0, seasonNumber);
        String safeName = displayName == null || displayName.isBlank()
                ? "Season " + safeSeason
                : displayName;

        syncSeason("season_" + safeSeason, safeName, active);
    }

    public static void syncSeason(String seasonId, String displayName, boolean active) {
        if (seasonId == null || seasonId.isBlank()) {
            return;
        }

        String safeName = displayName == null || displayName.isBlank()
                ? formatSeasonId(seasonId)
                : displayName;

        DatabaseManager.executeAsync("sync season " + seasonId, connection -> {
            ensureSeasonColumns(connection);

            if (active) {
                try (PreparedStatement deactivateStatement = connection.prepareStatement(
                        "update seasons set active = false, is_active = false where id <> ?"
                )) {
                    deactivateStatement.setString(1, seasonId);
                    deactivateStatement.executeUpdate();
                }
            }

            try (PreparedStatement upsertStatement = connection.prepareStatement(
                    "insert into seasons (id, display_name, starts_at, active, is_active) " +
                            "values (?, ?, now(), ?, ?) " +
                            "on conflict (id) do update set " +
                            "display_name = excluded.display_name, " +
                            "active = excluded.active, " +
                            "is_active = excluded.is_active"
            )) {
                upsertStatement.setString(1, seasonId);
                upsertStatement.setString(2, safeName);
                upsertStatement.setBoolean(3, active);
                upsertStatement.setBoolean(4, active);
                upsertStatement.executeUpdate();
            }
        });
    }

    private static void ensureSeasonColumns(java.sql.Connection connection) throws Exception {
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
    }

    public static void syncAllRankedPlayersForCurrentSeason() {
        syncCurrentSeason();

        for (PlayerDataManager.OfflinePlayerEntry entry : PlayerDataManager.getAllProfilePlayers()) {
            if (entry == null || entry.data == null) {
                continue;
            }
            PlayerDatabaseRepository.sync(entry.data);
        }
    }

    private static String formatSeasonId(String seasonId) {
        if (seasonId == null || seasonId.isBlank()) {
            return "Season 1";
        }
        if (seasonId.startsWith("season_")) {
            String suffix = seasonId.substring("season_".length());
            return "Season " + suffix;
        }
        return seasonId;
    }
}
