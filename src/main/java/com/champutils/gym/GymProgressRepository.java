package com.champutils.gym;

import com.champutils.badge.BadgeType;
import com.champutils.database.DatabaseManager;
import com.champutils.profile.PlayerProfileManager;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.util.UUID;

public final class GymProgressRepository {
    private GymProgressRepository() {}

    public static void recordAttempt(ServerPlayer player, BadgeType gym, boolean won) {
        if (player == null || gym == null || !DatabaseManager.isEnabled()) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        DatabaseManager.executeAsync("record gym progress " + profileId + " " + gym.name(), connection -> {
            ensureSchema(connection);
            try (var ps = connection.prepareStatement(
                    "insert into profile_gym_progress (profile_id, gym_id, defeated, defeated_at, attempts, wins, losses) values (?, ?, ?, case when ? then now() else null end, 1, case when ? then 1 else 0 end, case when ? then 0 else 1 end) " +
                            "on conflict (profile_id, gym_id) do update set attempts = profile_gym_progress.attempts + 1, wins = profile_gym_progress.wins + case when excluded.defeated then 1 else 0 end, losses = profile_gym_progress.losses + case when excluded.defeated then 0 else 1 end, defeated = profile_gym_progress.defeated or excluded.defeated, defeated_at = case when profile_gym_progress.defeated_at is null and excluded.defeated then now() else profile_gym_progress.defeated_at end"
            )) {
                ps.setObject(1, profileId);
                ps.setString(2, gym.name());
                ps.setBoolean(3, won);
                ps.setBoolean(4, won);
                ps.setBoolean(5, won);
                ps.setBoolean(6, won);
                ps.executeUpdate();
            }
        });
    }

    public static boolean hasDefeated(ServerPlayer player, BadgeType gym) {
        if (player == null || gym == null || !DatabaseManager.isEnabled()) return false;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            try (var ps = connection.prepareStatement("select defeated from profile_gym_progress where profile_id = ? and gym_id = ?")) {
                ps.setObject(1, profileId);
                ps.setString(2, gym.name());
                try (var rs = ps.executeQuery()) { return rs.next() && rs.getBoolean("defeated"); }
            }
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public static int defeatedCount(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return 0;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            try (var ps = connection.prepareStatement("select count(*) as total from profile_gym_progress where profile_id = ? and defeated = true")) {
                ps.setObject(1, profileId);
                try (var rs = ps.executeQuery()) { return rs.next() ? rs.getInt("total") : 0; }
            }
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static void ensureSchema(Connection connection) throws Exception {
        try (var ps = connection.prepareStatement("create table if not exists profile_gym_progress (" +
                "profile_id uuid not null references player_profiles(id) on delete cascade, gym_id text not null, defeated boolean not null default false, defeated_at timestamptz, attempts integer not null default 0, wins integer not null default 0, losses integer not null default 0, best_time_seconds integer, data jsonb not null default '{}'::jsonb, primary key(profile_id, gym_id))")) {
            ps.executeUpdate();
        }
    }
}
