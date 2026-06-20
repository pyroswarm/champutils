package com.champutils.database;

import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.network.NetworkServerConfig;

import java.sql.PreparedStatement;
import java.util.UUID;

public final class PlayerDatabaseRepository {

    private static boolean schemaEnsured = false;

    private PlayerDatabaseRepository() {}

    private static String getCurrentSeasonId() {
        return "season_" + Math.max(0, com.champutils.rank.SeasonManager.CURRENT_SEASON);
    }

    private static void ensureSchema(java.sql.Connection connection) throws Exception {
        if (schemaEnsured) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists profile_player_stats (" +
                        "profile_id uuid primary key references player_profiles(id) on delete cascade, " +
                        "playtime_seconds bigint not null default 0, money numeric(18,2) not null default 0, " +
                        "battling_xp bigint not null default 0, battling_level integer not null default 1, total_level integer not null default 1, " +
                        "metadata jsonb not null default '{}'::jsonb, updated_at timestamptz not null default now())"
        )) { statement.executeUpdate(); }
        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists profile_ranked_stats (" +
                        "profile_id uuid not null references player_profiles(id) on delete cascade, season_id text not null default 'default', " +
                        "rp integer not null default 1000, peak_rp integer not null default 1000, wins integer not null default 0, losses integer not null default 0, " +
                        "streak integer not null default 0, updated_at timestamptz not null default now(), primary key(profile_id, season_id))"
        )) { statement.executeUpdate(); }
        schemaEnsured = true;
    }

    public static void sync(PlayerDataManager.PlayerData data) {
        if (data == null || data.uuid == null || data.uuid.isBlank()) return;

        DatabaseManager.executeCoalescedAsync("player-stats:" + data.uuid, "sync profile player " + data.uuid, connection -> {
            ensureSchema(connection);

            UUID profileId = UUID.fromString(data.uuid);
            UUID playerUuid = resolvePlayerUuid(connection, profileId);
            if (playerUuid == null) {
                // The local profile cache/files can briefly contain a profile id that was deleted
                // from Supabase/player_profiles during a beta wipe, profile deletion, or failed sync.
                // Do NOT write child rows for missing profiles: that violates FK constraints and can
                // spam the async DB executor forever with orphan profile_player_stats/profile_ranked_stats writes.
                System.out.println("[ChampUtils] Skipping database sync for missing/deleted profile: " + profileId);
                return;
            }
            String username = safeName(data);

            if (playerUuid != null) {
                try (PreparedStatement playerStatement = connection.prepareStatement(
                        "insert into players (uuid, username, playtime_seconds, last_seen, last_server_id) values (?, ?, ?, now(), ?) " +
                                "on conflict (uuid) do update set username = excluded.username, playtime_seconds = greatest(players.playtime_seconds, excluded.playtime_seconds), last_seen = now(), last_server_id = excluded.last_server_id"
                )) {
                    playerStatement.setObject(1, playerUuid);
                    playerStatement.setString(2, username);
                    playerStatement.setLong(3, Math.max(0L, data.playtimeSeconds));
                    playerStatement.setString(4, NetworkServerConfig.serverId());
                    playerStatement.executeUpdate();
                }
            }

            try (PreparedStatement playerStats = connection.prepareStatement(
                    "insert into profile_player_stats (profile_id, playtime_seconds, updated_at) values (?, ?, now()) " +
                            "on conflict (profile_id) do update set playtime_seconds = greatest(profile_player_stats.playtime_seconds, excluded.playtime_seconds), updated_at = now()"
            )) {
                playerStats.setObject(1, profileId);
                playerStats.setLong(2, Math.max(0L, data.playtimeSeconds));
                playerStats.executeUpdate();
            }

            try (PreparedStatement rankedStatement = connection.prepareStatement(
                    "insert into profile_ranked_stats (profile_id, season_id, rp, peak_rp, wins, losses, streak, updated_at) values (?, ?, ?, ?, ?, ?, ?, now()) " +
                            "on conflict (profile_id, season_id) do update set rp = excluded.rp, peak_rp = greatest(profile_ranked_stats.peak_rp, excluded.peak_rp), wins = excluded.wins, losses = excluded.losses, streak = excluded.streak, updated_at = now()"
            )) {
                rankedStatement.setObject(1, profileId);
                rankedStatement.setString(2, getCurrentSeasonId());
                rankedStatement.setInt(3, Math.max(0, data.rp));
                rankedStatement.setInt(4, Math.max(0, data.peakRp));
                rankedStatement.setInt(5, Math.max(0, data.rankedWins));
                rankedStatement.setInt(6, Math.max(0, data.rankedLosses));
                rankedStatement.setInt(7, Math.max(0, data.currentStreak));
                rankedStatement.executeUpdate();
            }
        });
    }

    public static void saveAsync(UUID uuid, String name, PlayerDataManager.PlayerData data) {
        if (data == null) return;
        UUID profileId = PlayerProfileManager.activeProfileId(uuid);
        data.uuid = profileId.toString();
        if ((data.name == null || data.name.isBlank()) && name != null) data.name = name;
        sync(data);
    }

    public static void touchPlayer(UUID uuid, String name) {
        if (uuid == null) return;
        DatabaseManager.executeCoalescedAsync("touch-player:" + uuid, "touch player " + uuid, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into players (uuid, username, last_seen, last_server_id) values (?, ?, now(), ?) " +
                            "on conflict (uuid) do update set username = excluded.username, last_seen = now(), last_server_id = excluded.last_server_id"
            )) {
                statement.setObject(1, uuid);
                statement.setString(2, name == null || name.isBlank() ? uuid.toString() : name);
                statement.setString(3, NetworkServerConfig.serverId());
                statement.executeUpdate();
            }
        });
    }

    private static UUID resolvePlayerUuid(java.sql.Connection connection, UUID profileId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("select player_uuid from player_profiles where id = ?")) {
            ps.setObject(1, profileId);
            try (var rs = ps.executeQuery()) { return rs.next() ? (UUID) rs.getObject("player_uuid") : null; }
        }
    }

    private static String safeName(PlayerDataManager.PlayerData data) {
        return data.name == null || data.name.isBlank() ? data.uuid : data.name;
    }
}
