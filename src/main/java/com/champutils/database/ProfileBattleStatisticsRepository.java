package com.champutils.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Atomic, profile-scoped battle title counters shared by every backend server. */
public final class ProfileBattleStatisticsRepository {
    public record Snapshot(long pokemonDefeats, long wildPokemonDefeats, int highestLevelGapVictory) {}

    private ProfileBattleStatisticsRepository() {}

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure profile battle statistics columns", ProfileBattleStatisticsRepository::ensureSchema);
    }

    public static void ensureSchema(java.sql.Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("alter table public.profile_statistics add column if not exists pokemon_defeats bigint not null default 0");
            statement.executeUpdate("alter table public.profile_statistics add column if not exists wild_pokemon_defeats bigint not null default 0");
            statement.executeUpdate("alter table public.profile_statistics add column if not exists highest_level_gap_victory integer not null default 0");
        }
    }

    public static CompletableFuture<Snapshot> recordDefeat(UUID profileId, boolean wild, int levelGap) {
        if (profileId == null || !DatabaseManager.isEnabled()) {
            return CompletableFuture.completedFuture(new Snapshot(0, 0, Math.max(0, levelGap)));
        }
        return DatabaseManager.supplyAsync("record profile battle title progress " + profileId, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into public.profile_statistics " +
                    "(profile_id, pokemon_defeats, wild_pokemon_defeats, highest_level_gap_victory, updated_at) " +
                    "values (?, 1, ?, ?, now()) " +
                    "on conflict (profile_id) do update set " +
                    "pokemon_defeats = public.profile_statistics.pokemon_defeats + 1, " +
                    "wild_pokemon_defeats = public.profile_statistics.wild_pokemon_defeats + excluded.wild_pokemon_defeats, " +
                    "highest_level_gap_victory = greatest(public.profile_statistics.highest_level_gap_victory, excluded.highest_level_gap_victory), " +
                    "updated_at = now() " +
                    "returning pokemon_defeats, wild_pokemon_defeats, highest_level_gap_victory"
            )) {
                ps.setObject(1, profileId);
                ps.setLong(2, wild ? 1L : 0L);
                ps.setInt(3, Math.max(0, levelGap));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new Snapshot(rs.getLong(1), rs.getLong(2), rs.getInt(3));
                }
            }
        });
    }
}
