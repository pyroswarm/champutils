package com.champutils.leaderboard;

import com.champutils.database.DatabaseManager;
import com.champutils.rank.SeasonManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProfileLeaderboardRepository {
    private ProfileLeaderboardRepository() {}

    private static final long CACHE_TTL_MILLIS = 60_000L;
    private static final Map<Board, List<Entry>> CACHE = new EnumMap<>(Board.class);
    private static volatile long lastRefreshAtMillis = 0L;
    private static final AtomicBoolean REFRESH_IN_PROGRESS = new AtomicBoolean(false);

    public enum Board {
        RANKED("leaderboard_ranked_profiles", "rp", "RP"),
        PROFESSIONS_OVERALL("leaderboard_professions_overall", "total_level", "Overall Level"),
        PROFESSIONS_MINING("leaderboard_professions_mining", "level", "Mining Level"),
        PROFESSIONS_FORESTRY("leaderboard_professions_forestry", "level", "Forestry Level"),
        PROFESSIONS_FARMING("leaderboard_professions_farming", "level", "Farming Level"),
        PROFESSIONS_BATTLING("leaderboard_professions_battling", "level", "Battle Level"),
        ECONOMY("leaderboard_economy_profiles", "credits", "Credits"),
        PLAYTIME("leaderboard_playtime_profiles", "playtime_hours", "Hours"),
        POKEDEX("leaderboard_pokedex_profiles", "caught_species", "Caught Species"),
        GYMS("leaderboard_gym_profiles", "badges", "Badges"),
        NUZLOCKE("leaderboard_nuzlocke_profiles", "score", "Nuzlocke Score"),
        ISLANDER("leaderboard_islander_profiles", "score", "Islander Score"),
        GUILDS("leaderboard_guilds", "xp", "Guild XP");

        public final String view;
        public final String valueColumn;
        public final String label;

        Board(String view, String valueColumn, String label) {
            this.view = view;
            this.valueColumn = valueColumn;
            this.label = label;
        }
    }

    public record Entry(
            UUID profileId,
            UUID playerUuid,
            String playerName,
            String profileName,
            String mode,
            String label,
            long value,
            int rp,
            int wins,
            int losses,
            long xp,
            int level,
            long secondaryValue
    ) {}

    public static void invalidateCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        lastRefreshAtMillis = 0L;
    }

    public static List<Entry> top(Board board, int limit) {
        refreshAllAsync(false);
        int safeLimit = Math.max(1, Math.min(100, limit));
        List<Entry> cached;
        synchronized (CACHE) {
            cached = CACHE.getOrDefault(board, List.of());
        }
        if (cached.size() <= safeLimit) return new ArrayList<>(cached);
        return new ArrayList<>(cached.subList(0, safeLimit));
    }

    public static void refreshAllAsync() {
        refreshAllAsync(false);
    }

    public static void refreshAllAsync(boolean force) {
        if (!DatabaseManager.isEnabled()) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastRefreshAtMillis < CACHE_TTL_MILLIS) return;
        if (!REFRESH_IN_PROGRESS.compareAndSet(false, true)) return;

        DatabaseManager.executeAsync("refresh leaderboard cache", connection -> {
            try {
                Map<Board, List<Entry>> next = new EnumMap<>(Board.class);
                for (Board board : Board.values()) {
                    try {
                        next.put(board, loadFresh(connection, board, 100));
                    } catch (Exception e) {
                        System.err.println("[ChampUtils] Failed to refresh " + board + " leaderboard: " + e.getMessage());
                        synchronized (CACHE) {
                            next.put(board, CACHE.getOrDefault(board, List.of()));
                        }
                    }
                }
                synchronized (CACHE) {
                    CACHE.clear();
                    CACHE.putAll(next);
                }
                lastRefreshAtMillis = System.currentTimeMillis();
            } finally {
                REFRESH_IN_PROGRESS.set(false);
            }
        });
    }

    public static List<Entry> topFresh(Board board, int limit) {
        refreshAllAsync(false);
        return top(board, limit);
    }

    public static java.util.concurrent.CompletableFuture<List<Entry>> topAsync(Board board, int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        if (!DatabaseManager.isEnabled()) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }
        return DatabaseManager.supplyAsync("load " + board + " leaderboard", connection -> {
            List<Entry> fresh = loadFresh(connection, board, safeLimit);
            synchronized (CACHE) {
                CACHE.put(board, fresh);
            }
            return fresh;
        });
    }

    private static List<Entry> loadFresh(Connection connection, Board board, int limit) throws Exception {
        return switch (board) {
            case RANKED -> ranked(connection, limit);
            case GUILDS -> guilds(connection, limit);
            case ECONOMY -> economy(connection, limit);
            case POKEDEX -> pokedex(connection, limit);
            case PROFESSIONS_OVERALL -> professionsOverall(connection, limit);
            case PROFESSIONS_MINING -> profession(connection, "MINING", board.label, limit);
            case PROFESSIONS_FORESTRY -> profession(connection, "FORESTRY", board.label, limit);
            case PROFESSIONS_FARMING -> profession(connection, "FARMING", board.label, limit);
            case PROFESSIONS_BATTLING -> profession(connection, "BATTLING", board.label, limit);
            case PLAYTIME -> playtime(connection, limit);
            case GYMS -> gyms(connection, limit);
            default -> generic(connection, board, limit);
        };
    }

    private static List<Entry> ranked(Connection connection, int limit) throws Exception {
        String seasonId = "season_" + Math.max(0, SeasonManager.CURRENT_SEASON);
        String sql = "select lb.profile_id, lb.player_uuid, lb.username, lb.profile_name, lb.mode, lb.rp, lb.wins, lb.losses " +
                "from leaderboard_ranked_profiles lb join player_profiles p on p.id = lb.profile_id " +
                "where lb.season_id = ? and p.deleted_at is null order by lb.rp desc, lb.wins desc, lb.losses asc limit ?";
        List<Entry> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, seasonId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Entry(
                            getUuid(rs, "profile_id"), getUuid(rs, "player_uuid"),
                            str(rs, "username"), str(rs, "profile_name"), str(rs, "mode"),
                            "RP", rs.getLong("rp"), rs.getInt("rp"), rs.getInt("wins"), rs.getInt("losses"),
                            0L, 0, 0L
                    ));
                }
            }
        } catch (Exception missingView) {
            String fallback = "select p.id as profile_id, p.player_uuid, coalesce(pl.username, p.name, 'Unknown') as username, " +
                    "p.name as profile_name, coalesce(p.mode, 'NORMAL') as mode, s.rp, s.wins, s.losses " +
                    "from profile_ranked_stats s join player_profiles p on p.id = s.profile_id " +
                    "left join players pl on pl.uuid = p.player_uuid " +
                    "where s.season_id = ? and p.deleted_at is null order by s.rp desc, s.wins desc, s.losses asc limit ?";
            try (PreparedStatement ps = connection.prepareStatement(fallback)) {
                ps.setString(1, seasonId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new Entry(
                                getUuid(rs, "profile_id"), getUuid(rs, "player_uuid"),
                                str(rs, "username"), str(rs, "profile_name"), str(rs, "mode"),
                                "RP", rs.getLong("rp"), rs.getInt("rp"), rs.getInt("wins"), rs.getInt("losses"),
                                0L, 0, 0L
                        ));
                    }
                }
            }
        }
        return rows;
    }


    private static List<Entry> economy(Connection connection, int limit) throws Exception {
        String sql = "select p.id as profile_id, p.player_uuid, coalesce(e.username, pl.username, p.name, 'Unknown') as username, " +
                "p.name as profile_name, coalesce(p.mode, 'NORMAL') as mode, coalesce(s.money::bigint, e.credits, 0) as value " +
                "from player_profiles p left join players pl on pl.uuid = p.player_uuid " +
                "left join player_economy e on e.uuid = p.player_uuid::text " +
                "left join profile_player_stats s on s.profile_id = p.id " +
                "where p.deleted_at is null order by coalesce(s.money::bigint, e.credits, 0) desc limit ?";
        List<Entry> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long value = rs.getLong("value");
                    rows.add(new Entry(getUuid(rs, "profile_id"), getUuid(rs, "player_uuid"), str(rs, "username"), str(rs, "profile_name"), str(rs, "mode"), "Credits", value, 0, 0, 0, 0L, 0, 0L));
                }
            }
        } catch (Exception first) {
            String fallback = "select null as profile_id, uuid as player_uuid, username, username as profile_name, 'ACCOUNT' as mode, credits as value from player_economy order by credits desc limit ?";
            try (PreparedStatement ps = connection.prepareStatement(fallback)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long value = rs.getLong("value");
                        rows.add(new Entry(null, getUuid(rs, "player_uuid"), str(rs, "username"), str(rs, "profile_name"), str(rs, "mode"), "Credits", value, 0, 0, 0, 0L, 0, 0L));
                    }
                }
            }
        }
        return rows;
    }

    private static List<Entry> pokedex(Connection connection, int limit) throws Exception {
        String profileSql = "select p.id as profile_id, p.player_uuid, coalesce(pl.username, p.name, 'Unknown') as username, " +
                "p.name as profile_name, coalesce(p.mode, 'NORMAL') as mode, count(distinct d.species_id) as value " +
                "from true_caught_dex d join player_profiles p on p.id = d.player_uuid " +
                "left join players pl on pl.uuid = p.player_uuid " +
                "where p.deleted_at is null group by p.id, p.player_uuid, pl.username, p.name, p.mode order by value desc limit ?";
        try {
            return pokedexRows(connection, profileSql, limit);
        } catch (Exception profileIdDexFailed) {
            // Legacy schemas used true_caught_dex.player_uuid as the account UUID.
            String legacySql = "select p.id as profile_id, p.player_uuid, coalesce(pl.username, p.name, 'Unknown') as username, " +
                    "p.name as profile_name, coalesce(p.mode, 'NORMAL') as mode, count(distinct d.species_id) as value " +
                    "from true_caught_dex d join player_profiles p on p.player_uuid = d.player_uuid " +
                    "left join players pl on pl.uuid = p.player_uuid " +
                    "where p.deleted_at is null group by p.id, p.player_uuid, pl.username, p.name, p.mode order by value desc limit ?";
            return pokedexRows(connection, legacySql, limit);
        }
    }

    private static List<Entry> pokedexRows(Connection connection, String sql, int limit) throws Exception {
        List<Entry> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long value = rs.getLong("value");
                    rows.add(new Entry(getUuid(rs, "profile_id"), getUuid(rs, "player_uuid"), str(rs, "username"), str(rs, "profile_name"), str(rs, "mode"), "Caught Species", value, 0, 0, 0, 0L, 0, 0L));
                }
            }
        }
        return rows;
    }

    private static List<Entry> profession(Connection connection, String profession, String label, int limit) throws Exception {
        String sql = "select p.id as profile_id, p.player_uuid, coalesce(pl.username, p.name, 'Unknown') as username, " +
                "p.name as profile_name, coalesce(p.mode, 'NORMAL') as mode, pf.level as value, pf.xp " +
                "from profile_professions pf join player_profiles p on p.id = pf.profile_id " +
                "left join players pl on pl.uuid = p.player_uuid " +
                "where p.deleted_at is null and pf.profession = ? order by pf.level desc, pf.xp desc limit ?";
        List<Entry> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, profession);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long value = rs.getLong("value");
                    rows.add(new Entry(getUuid(rs, "profile_id"), getUuid(rs, "player_uuid"), str(rs, "username"), str(rs, "profile_name"), str(rs, "mode"), label, value, 0, 0, 0, rs.getLong("xp"), (int)value, 0L));
                }
            }
        }
        return rows;
    }

    private static List<Entry> professionsOverall(Connection connection, int limit) throws Exception {
        String sql = "select p.id as profile_id, p.player_uuid, coalesce(pl.username, p.name, 'Unknown') as username, " +
                "p.name as profile_name, coalesce(p.mode, 'NORMAL') as mode, coalesce(sum(pf.level), 0) as value, coalesce(sum(pf.xp), 0) as xp " +
                "from player_profiles p left join players pl on pl.uuid = p.player_uuid " +
                "left join profile_professions pf on pf.profile_id = p.id " +
                "where p.deleted_at is null group by p.id, p.player_uuid, pl.username, p.name, p.mode order by value desc, xp desc limit ?";
        List<Entry> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long value = rs.getLong("value");
                    rows.add(new Entry(getUuid(rs, "profile_id"), getUuid(rs, "player_uuid"), str(rs, "username"), str(rs, "profile_name"), str(rs, "mode"), "Overall Level", value, 0, 0, 0, rs.getLong("xp"), (int)value, 0L));
                }
            }
        }
        return rows;
    }

    private static List<Entry> playtime(Connection connection, int limit) throws Exception {
        String sql = "select p.id as profile_id, p.player_uuid, coalesce(pl.username, p.name, 'Unknown') as username, " +
                "p.name as profile_name, coalesce(p.mode, 'NORMAL') as mode, floor(coalesce(s.playtime_seconds, 0) / 3600.0)::bigint as value " +
                "from player_profiles p left join players pl on pl.uuid = p.player_uuid " +
                "left join profile_player_stats s on s.profile_id = p.id where p.deleted_at is null " +
                "order by coalesce(s.playtime_seconds, 0) desc limit ?";
        List<Entry> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long value = rs.getLong("value");
                    rows.add(new Entry(getUuid(rs, "profile_id"), getUuid(rs, "player_uuid"), str(rs, "username"), str(rs, "profile_name"), str(rs, "mode"), "Hours", value, 0, 0, 0, 0L, 0, 0L));
                }
            }
        }
        return rows;
    }

    private static List<Entry> gyms(Connection connection, int limit) throws Exception {
        String sql = "select p.id as profile_id, p.player_uuid, coalesce(pl.username, p.name, 'Unknown') as username, " +
                "p.name as profile_name, coalesce(p.mode, 'NORMAL') as mode, " +
                "count(distinct clears.clear_id) as value " +
                "from player_profiles p left join players pl on pl.uuid = p.player_uuid " +
                "left join (" +
                "  select profile_id, gym_id::text as clear_id from profile_gym_progress where defeated = true " +
                "  union " +
                "  select profile_id, badge::text as clear_id from profile_badges " +
                ") clears on clears.profile_id = p.id " +
                "where p.deleted_at is null group by p.id, p.player_uuid, pl.username, p.name, p.mode order by value desc limit ?";
        List<Entry> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long value = rs.getLong("value");
                    rows.add(new Entry(getUuid(rs, "profile_id"), getUuid(rs, "player_uuid"), str(rs, "username"), str(rs, "profile_name"), str(rs, "mode"), "Badges", value, 0, 0, 0, 0L, (int)value, 0L));
                }
            }
        }
        return rows;
    }

    private static List<Entry> generic(Connection connection, Board board, int limit) throws Exception {
        String sql = "select lb.profile_id, lb.player_uuid, lb.username, lb.profile_name, lb.mode, lb." + board.valueColumn + " as value " +
                "from " + board.view + " lb join player_profiles p on p.id = lb.profile_id " +
                "where p.deleted_at is null order by lb." + board.valueColumn + " desc limit ?";
        List<Entry> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long value = rs.getLong("value");
                    rows.add(new Entry(
                            getUuid(rs, "profile_id"), getUuid(rs, "player_uuid"),
                            str(rs, "username"), str(rs, "profile_name"), str(rs, "mode"),
                            board.label, value, 0, 0, 0, 0L, (int) value, 0L
                    ));
                }
            }
        }
        return rows;
    }

    private static List<Entry> guilds(Connection connection, int limit) throws Exception {
        String sql = "select guild_id, guild_name, owner_name, level, xp, members from leaderboard_guilds order by xp desc, level desc, members desc limit ?";
        List<Entry> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Entry(
                            getUuid(rs, "guild_id"), null,
                            str(rs, "owner_name"), str(rs, "guild_name"), "GUILD",
                            "Guild XP", rs.getLong("xp"), 0, 0, 0, rs.getLong("xp"), rs.getInt("level"), rs.getLong("members")
                    ));
                }
            }
        } catch (Exception missingView) {
            String fallback = "select g.id as guild_id, g.name as guild_name, coalesce(o.username, gm.player_name, 'Unknown') as owner_name, " +
                    "g.level, g.xp, count(m.player_uuid) as members from guilds g " +
                    "left join players o on o.uuid = g.owner_uuid " +
                    "left join guild_members gm on gm.guild_id = g.id and gm.player_uuid = g.owner_uuid " +
                    "left join guild_members m on m.guild_id = g.id " +
                    "group by g.id, g.name, o.username, gm.player_name, g.level, g.xp order by g.xp desc, g.level desc, members desc limit ?";
            try (PreparedStatement ps = connection.prepareStatement(fallback)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new Entry(
                                getUuid(rs, "guild_id"), null,
                                str(rs, "owner_name"), str(rs, "guild_name"), "GUILD",
                                "Guild XP", rs.getLong("xp"), 0, 0, 0, rs.getLong("xp"), rs.getInt("level"), rs.getLong("members")
                        ));
                    }
                }
            }
        }
        return rows;
    }

    private static UUID getUuid(ResultSet rs, String column) {
        try {
            Object value = rs.getObject(column);
            if (value instanceof UUID uuid) return uuid;
            if (value != null) return UUID.fromString(value.toString());
        } catch (Exception ignored) {}
        return null;
    }

    private static String str(ResultSet rs, String column) {
        try {
            String value = rs.getString(column);
            return value == null || value.isBlank() ? "Unknown" : value;
        } catch (Exception ignored) {
            return "Unknown";
        }
    }
}
