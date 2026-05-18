package com.champutils.database;

import com.champutils.profile.PlayerDataManager;
import com.champutils.rank.SeasonManager;

import java.sql.PreparedStatement;

public final class SeasonDatabaseRepository {

    private SeasonDatabaseRepository() {
    }

    public static String currentSeasonId() {
        int season = Math.max(1, SeasonManager.CURRENT_SEASON);
        return "season_" + season;
    }

    public static String currentSeasonName() {
        if (SeasonManager.CURRENT_NAME == null || SeasonManager.CURRENT_NAME.isBlank()) {
            return "Season " + Math.max(1, SeasonManager.CURRENT_SEASON);
        }

        return SeasonManager.CURRENT_NAME;
    }

    public static void syncCurrentSeason() {
        syncSeason(
                currentSeasonId(),
                currentSeasonName(),
                true
        );
    }

    public static void syncSeason(
            int seasonNumber,
            String displayName,
            boolean active
    ) {
        int safeSeason = Math.max(1, seasonNumber);
        String safeName = displayName == null || displayName.isBlank()
                ? "Season " + safeSeason
                : displayName;

        syncSeason(
                "season_" + safeSeason,
                safeName,
                active
        );
    }

    public static void syncSeason(
            String seasonId,
            String displayName,
            boolean active
    ) {
        if (seasonId == null || seasonId.isBlank()) {
            return;
        }

        String safeName = displayName == null || displayName.isBlank()
                ? seasonId
                : displayName;

        DatabaseManager.executeAsync(
                "sync season " + seasonId,
                connection -> {
                    if (active) {
                        try (PreparedStatement deactivateStatement = connection.prepareStatement(
                                "update seasons set active = false where id <> ?"
                        )) {
                            deactivateStatement.setString(1, seasonId);
                            deactivateStatement.executeUpdate();
                        }
                    }

                    try (PreparedStatement upsertStatement = connection.prepareStatement(
                            "insert into seasons (id, display_name, starts_at, active) " +
                                    "values (?, ?, now(), ?) " +
                                    "on conflict (id) do update set " +
                                    "display_name = excluded.display_name, " +
                                    "active = excluded.active"
                    )) {
                        upsertStatement.setString(1, seasonId);
                        upsertStatement.setString(2, safeName);
                        upsertStatement.setBoolean(3, active);
                        upsertStatement.executeUpdate();
                    }
                }
        );
    }

    public static void syncAllRankedPlayersForCurrentSeason() {
        syncCurrentSeason();

        for (PlayerDataManager.OfflinePlayerEntry entry : PlayerDataManager.getAllPlayers()) {
            if (entry == null || entry.data == null) {
                continue;
            }

            PlayerDatabaseRepository.sync(entry.data);
        }
    }
}
