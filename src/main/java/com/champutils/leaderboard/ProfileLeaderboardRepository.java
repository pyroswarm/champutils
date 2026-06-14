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
                    next.put(board, loadFresh(connection, board, 100));
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
        if (!DatabaseManager.isEnabled()) return top(board, limit);
        int safeLimit = Math.max(1, Math.min(100, limit));
        try {
            List<Entry> rows = loadFresh(DatabaseManager.getConnection(), board, safeLimit);
            synchronized (CACHE) {
                CACHE.put(board, List.copyOf(rows));
            }
            lastRefreshAtMillis = System.currentTimeMillis();
            return rows;
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load fresh " + board + " leaderboard: " + e.getMessage());
            return top(board, safeLimit);
        }
    }

    private static List<Entry> loadFresh(Connection connection, Board board, int limit) throws Exception {
        return switch (board) {
            case RANKED -> ranked(connection, limit);
            case GUILDS -> guilds(connection, limit);
            default -> generic(connection, board, limit);
        };
    }

    private static List<Entry> ranked(Connection connection, int limit) throws Exception {
        String seasonId = "season_" + Math.max(1, SeasonManager.CURRENT_SEASON);
        String sql = "select profile_id, player_uuid, username, profile_name, mode, rp, wins, losses " +
                "from leaderboard_ranked_profiles where season_id = ? order by rp desc, wins desc, losses asc limit ?";
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
        }
        return rows;
    }

    private static List<Entry> generic(Connection connection, Board board, int limit) throws Exception {
        String sql = "select profile_id, player_uuid, username, profile_name, mode, " + board.valueColumn + " as value " +
                "from " + board.view + " order by " + board.valueColumn + " desc limit ?";
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
