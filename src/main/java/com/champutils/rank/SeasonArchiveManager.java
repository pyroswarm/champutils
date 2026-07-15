package com.champutils.rank;

import com.champutils.database.DatabaseManager;
import com.champutils.network.NetworkEventManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Network-wide season history and Top 100 archive.
 *
 * The old implementation stored these files only on the backend that ran the season command.
 * This implementation keeps the files as human-readable mirrors, but PostgreSQL is authoritative
 * so Nova and Eclipse always expose the same history and administration operations.
 */
public final class SeasonArchiveManager {
    private static final UUID INVALIDATION_OWNER = new UUID(0L, 0L);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<SeasonRecord>> HISTORY_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, List<LadderEntry>> TOP_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    public static class SeasonRecord {
        public int season;
        public String seasonName;
        public String finishRank;
        public String peakRank;
        public int finalRp;
        public int peakRp;
        public int wins;
        public int losses;
        public int bestStreak;
    }

    public static class LadderEntry {
        public String player;
        public int rp;
        public String rank;

        public LadderEntry(String player, int rp, String rank) {
            this.player = player;
            this.rp = rp;
            this.rank = rank;
        }
    }

    private record StoredHistory(long id, String playerKey, String playerName, SeasonRecord record) {
    }

    private record ArchiveSnapshot(Map<String, List<SeasonRecord>> histories, Map<Integer, List<LadderEntry>> top) {
    }

    private SeasonArchiveManager() {
    }

    public static void initialize() {
        if (initialized) return;
        if (!DatabaseManager.isEnabled()) {
            loadLocalOnly();
            initialized = true;
            return;
        }

        try {
            ArchiveSnapshot snapshot = DatabaseManager.supplyAsync("initialize shared season archive", connection -> {
                ensureSchema(connection);
                migrateLocalFiles(connection);
                return readSnapshot(connection);
            }).get(10, TimeUnit.SECONDS);
            replaceCache(snapshot);
            initialized = true;
            writeAllLocalMirrors();
        } catch (Exception error) {
            System.err.println("[ChampUtils] Failed to initialize shared season archive; using local mirrors.");
            error.printStackTrace();
            loadLocalOnly();
            initialized = true;
        }
    }

    public static void refreshAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.supplyAsync("refresh shared season archive", connection -> {
            ensureSchema(connection);
            return readSnapshot(connection);
        }).whenComplete((snapshot, error) -> {
            if (error != null || snapshot == null) {
                if (error != null) error.printStackTrace();
                return;
            }
            replaceCache(snapshot);
            writeAllLocalMirrors();
        });
    }

    private static void ensureInitialized() {
        if (!initialized) initialize();
    }

    private static void ensureSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "create table if not exists season_player_archives (" +
                            "id bigserial primary key, " +
                            "record_key text not null unique, " +
                            "player_name_key text not null, " +
                            "player_name text not null, " +
                            "season integer not null, " +
                            "payload text not null, " +
                            "created_at timestamptz not null default now()" +
                            ")"
            );
            statement.executeUpdate("create index if not exists season_player_archives_player_idx on season_player_archives (player_name_key, id)");
            statement.executeUpdate("create index if not exists season_player_archives_season_idx on season_player_archives (season)");
            statement.executeUpdate(
                    "create table if not exists season_top100_archives (" +
                            "season integer primary key, " +
                            "payload text not null, " +
                            "updated_at timestamptz not null default now()" +
                            ")"
            );
        }
    }

    private static File seasonDir() {
        File dir = new File("config/champutils/seasons");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File getPlayerFile(String player) {
        return new File(seasonDir(), safeFileName(player) + ".json");
    }

    private static File getTop100File(int season) {
        return new File(seasonDir(), "season_" + season + "_top100.json");
    }

    private static String safeFileName(String value) {
        String safe = value == null ? "unknown" : value.trim();
        safe = safe.replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.isBlank() ? "unknown" : safe;
    }

    private static String playerKey(String player) {
        return player == null ? "" : player.trim().toLowerCase(Locale.ROOT);
    }

    public static void ensurePlayerFile(String player) {
        ensureInitialized();
        String key = playerKey(player);
        if (key.isBlank()) return;
        HISTORY_CACHE.putIfAbsent(key, new ArrayList<>());
        File file = getPlayerFile(player);
        if (!file.exists()) writeHistoryMirror(player, List.of());
    }

    public static List<SeasonRecord> getHistory(String player) {
        ensureInitialized();
        List<SeasonRecord> history = HISTORY_CACHE.get(playerKey(player));
        return history == null ? new ArrayList<>() : deepCopyHistory(history);
    }

    public static void archive(String player, SeasonRecord record) {
        ensureInitialized();
        if (player == null || player.isBlank() || record == null) return;
        String key = playerKey(player);
        SeasonRecord copy = copyRecord(record);
        synchronized (SeasonArchiveManager.class) {
            List<SeasonRecord> history = new ArrayList<>(HISTORY_CACHE.getOrDefault(key, List.of()));
            history.add(copy);
            HISTORY_CACHE.put(key, history);
            writeHistoryMirror(player, history);
        }

        if (!DatabaseManager.isEnabled()) return;
        String recordKey = UUID.randomUUID().toString();
        DatabaseManager.runAsync("archive season result for " + player, connection -> {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into season_player_archives (record_key, player_name_key, player_name, season, payload) values (?, ?, ?, ?, ?)"
            )) {
                statement.setString(1, recordKey);
                statement.setString(2, key);
                statement.setString(3, player);
                statement.setInt(4, copy.season);
                statement.setString(5, GSON.toJson(copy));
                statement.executeUpdate();
            }
        }).whenComplete((ignored, error) -> {
            if (error != null) error.printStackTrace();
            else publishInvalidation();
        });
    }

    public static void saveTop100Snapshot(int season, List<LadderEntry> top) {
        ensureInitialized();
        List<LadderEntry> copy = deepCopyTop(top == null ? List.of() : top);
        TOP_CACHE.put(season, copy);
        writeTopMirror(season, copy);
        if (!DatabaseManager.isEnabled()) return;

        DatabaseManager.runAsync("save season Top 100 snapshot " + season, connection -> {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into season_top100_archives (season, payload, updated_at) values (?, ?, now()) " +
                            "on conflict (season) do update set payload = excluded.payload, updated_at = now()"
            )) {
                statement.setInt(1, season);
                statement.setString(2, GSON.toJson(copy));
                statement.executeUpdate();
            }
        }).whenComplete((ignored, error) -> {
            if (error != null) error.printStackTrace();
            else publishInvalidation();
        });
    }

    public static List<LadderEntry> getTop100Snapshot(int season) {
        ensureInitialized();
        return deepCopyTop(TOP_CACHE.getOrDefault(season, List.of()));
    }

    public static void removeLastSeason(String player) {
        ensureInitialized();
        String key = playerKey(player);
        synchronized (SeasonArchiveManager.class) {
            List<SeasonRecord> history = new ArrayList<>(HISTORY_CACHE.getOrDefault(key, List.of()));
            if (history.isEmpty()) return;
            history.remove(history.size() - 1);
            HISTORY_CACHE.put(key, history);
            writeHistoryMirror(player, history);
        }
        if (!DatabaseManager.isEnabled()) return;

        DatabaseManager.runAsync("remove last season archive for " + player, connection -> {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "delete from season_player_archives where id = (" +
                            "select id from season_player_archives where player_name_key = ? order by id desc limit 1)"
            )) {
                statement.setString(1, key);
                statement.executeUpdate();
            }
        }).whenComplete((ignored, error) -> {
            if (error != null) error.printStackTrace();
            else publishInvalidation();
        });
    }

    public static void removeSeason(String player, int seasonNumber) {
        ensureInitialized();
        String key = playerKey(player);
        synchronized (SeasonArchiveManager.class) {
            List<SeasonRecord> history = new ArrayList<>(HISTORY_CACHE.getOrDefault(key, List.of()));
            history.removeIf(record -> record != null && record.season == seasonNumber);
            HISTORY_CACHE.put(key, history);
            writeHistoryMirror(player, history);
        }
        if (!DatabaseManager.isEnabled()) return;

        DatabaseManager.runAsync("remove season archive " + seasonNumber + " for " + player, connection -> {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "delete from season_player_archives where player_name_key = ? and season = ?"
            )) {
                statement.setString(1, key);
                statement.setInt(2, seasonNumber);
                statement.executeUpdate();
            }
        }).whenComplete((ignored, error) -> {
            if (error != null) error.printStackTrace();
            else publishInvalidation();
        });
    }

    public static void removeSeasonSnapshot(int season) {
        ensureInitialized();
        TOP_CACHE.remove(season);
        File file = getTop100File(season);
        if (file.exists() && !file.delete()) {
            System.err.println("[ChampUtils] Could not delete local season Top 100 mirror " + file + ".");
        }
        if (!DatabaseManager.isEnabled()) return;

        DatabaseManager.runAsync("remove season Top 100 snapshot " + season, connection -> {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "delete from season_top100_archives where season = ?"
            )) {
                statement.setInt(1, season);
                statement.executeUpdate();
            }
        }).whenComplete((ignored, error) -> {
            if (error != null) error.printStackTrace();
            else publishInvalidation();
        });
    }

    /** All archived player keys, used by rollback without relying on one backend's local directory. */
    public static List<String> archivedPlayerNames() {
        ensureInitialized();
        List<String> names = new ArrayList<>();
        HISTORY_CACHE.forEach((key, history) -> {
            if (history != null && !history.isEmpty()) names.add(key);
        });
        return names;
    }

    private static ArchiveSnapshot readSnapshot(Connection connection) throws Exception {
        Map<String, List<SeasonRecord>> histories = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select id, player_name_key, player_name, payload from season_player_archives order by player_name_key, id"
        ); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                SeasonRecord record = GSON.fromJson(rs.getString(4), SeasonRecord.class);
                if (record == null) continue;
                histories.computeIfAbsent(rs.getString(2), ignored -> new ArrayList<>()).add(record);
            }
        }

        Map<Integer, List<LadderEntry>> top = new HashMap<>();
        Type topType = new TypeToken<List<LadderEntry>>() {}.getType();
        try (PreparedStatement statement = connection.prepareStatement(
                "select season, payload from season_top100_archives order by season"
        ); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                List<LadderEntry> entries = GSON.fromJson(rs.getString(2), topType);
                top.put(rs.getInt(1), entries == null ? new ArrayList<>() : entries);
            }
        }
        return new ArchiveSnapshot(histories, top);
    }

    private static void migrateLocalFiles(Connection connection) throws Exception {
        File[] files = seasonDir().listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;
        Type historyType = new TypeToken<List<SeasonRecord>>() {}.getType();
        Type topType = new TypeToken<List<LadderEntry>>() {}.getType();

        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("season_") && name.endsWith("_top100.json")) {
                int season;
                try {
                    season = Integer.parseInt(name.substring("season_".length(), name.length() - "_top100.json".length()));
                } catch (Exception ignored) {
                    continue;
                }
                try (FileReader reader = new FileReader(file)) {
                    List<LadderEntry> entries = GSON.fromJson(reader, topType);
                    if (entries == null) entries = new ArrayList<>();
                    try (PreparedStatement statement = connection.prepareStatement(
                            "insert into season_top100_archives (season, payload, updated_at) values (?, ?, now()) on conflict (season) do nothing"
                    )) {
                        statement.setInt(1, season);
                        statement.setString(2, GSON.toJson(entries));
                        statement.executeUpdate();
                    }
                } catch (Exception error) {
                    System.err.println("[ChampUtils] Could not migrate season snapshot " + file + ".");
                }
                continue;
            }

            String player = name.substring(0, name.length() - ".json".length());
            String key = playerKey(player);
            if (key.isBlank()) continue;
            try (FileReader reader = new FileReader(file)) {
                List<SeasonRecord> history = GSON.fromJson(reader, historyType);
                if (history == null) continue;
                for (int index = 0; index < history.size(); index++) {
                    SeasonRecord record = history.get(index);
                    if (record == null) continue;
                    String fingerprint = migrationFingerprint(key, index, record);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "insert into season_player_archives (record_key, player_name_key, player_name, season, payload) " +
                                    "values (?, ?, ?, ?, ?) on conflict (record_key) do nothing"
                    )) {
                        statement.setString(1, fingerprint);
                        statement.setString(2, key);
                        statement.setString(3, player);
                        statement.setInt(4, record.season);
                        statement.setString(5, GSON.toJson(record));
                        statement.executeUpdate();
                    }
                }
            } catch (Exception error) {
                System.err.println("[ChampUtils] Could not migrate season history " + file + ".");
            }
        }
    }

    private static String migrationFingerprint(String playerKey, int index, SeasonRecord record) {
        String value = playerKey + "|" + index + "|" + record.season + "|" + record.seasonName + "|" +
                record.finishRank + "|" + record.peakRank + "|" + record.finalRp + "|" + record.peakRp + "|" +
                record.wins + "|" + record.losses + "|" + record.bestStreak;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void replaceCache(ArchiveSnapshot snapshot) {
        if (snapshot == null) return;
        HISTORY_CACHE.clear();
        snapshot.histories().forEach((key, history) -> HISTORY_CACHE.put(key, deepCopyHistory(history)));
        TOP_CACHE.clear();
        snapshot.top().forEach((season, entries) -> TOP_CACHE.put(season, deepCopyTop(entries)));
    }

    private static void loadLocalOnly() {
        HISTORY_CACHE.clear();
        TOP_CACHE.clear();
        File[] files = seasonDir().listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;
        Type historyType = new TypeToken<List<SeasonRecord>>() {}.getType();
        Type topType = new TypeToken<List<LadderEntry>>() {}.getType();
        for (File file : files) {
            String name = file.getName();
            try (FileReader reader = new FileReader(file)) {
                if (name.startsWith("season_") && name.endsWith("_top100.json")) {
                    int season = Integer.parseInt(name.substring("season_".length(), name.length() - "_top100.json".length()));
                    List<LadderEntry> entries = GSON.fromJson(reader, topType);
                    TOP_CACHE.put(season, entries == null ? new ArrayList<>() : entries);
                } else {
                    String player = name.substring(0, name.length() - ".json".length());
                    List<SeasonRecord> history = GSON.fromJson(reader, historyType);
                    HISTORY_CACHE.put(playerKey(player), history == null ? new ArrayList<>() : history);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void writeAllLocalMirrors() {
        HISTORY_CACHE.forEach((player, history) -> writeHistoryMirror(player, history));
        TOP_CACHE.forEach(SeasonArchiveManager::writeTopMirror);
    }

    private static void writeHistoryMirror(String player, List<SeasonRecord> history) {
        try (FileWriter writer = new FileWriter(getPlayerFile(player))) {
            GSON.toJson(history == null ? List.of() : history, writer);
        } catch (Exception error) {
            System.err.println("[ChampUtils] Failed to write season history mirror for " + player + ".");
        }
    }

    private static void writeTopMirror(int season, List<LadderEntry> top) {
        try (FileWriter writer = new FileWriter(getTop100File(season))) {
            GSON.toJson(top == null ? List.of() : top, writer);
        } catch (Exception error) {
            System.err.println("[ChampUtils] Failed to write season Top 100 mirror for " + season + ".");
        }
    }

    private static List<SeasonRecord> deepCopyHistory(List<SeasonRecord> source) {
        List<SeasonRecord> copy = new ArrayList<>();
        if (source == null) return copy;
        for (SeasonRecord record : source) {
            if (record != null) copy.add(copyRecord(record));
        }
        return copy;
    }

    private static SeasonRecord copyRecord(SeasonRecord source) {
        SeasonRecord copy = new SeasonRecord();
        copy.season = source.season;
        copy.seasonName = source.seasonName;
        copy.finishRank = source.finishRank;
        copy.peakRank = source.peakRank;
        copy.finalRp = source.finalRp;
        copy.peakRp = source.peakRp;
        copy.wins = source.wins;
        copy.losses = source.losses;
        copy.bestStreak = source.bestStreak;
        return copy;
    }

    private static List<LadderEntry> deepCopyTop(List<LadderEntry> source) {
        List<LadderEntry> copy = new ArrayList<>();
        if (source == null) return copy;
        for (LadderEntry entry : source) {
            if (entry != null) copy.add(new LadderEntry(entry.player, entry.rp, entry.rank));
        }
        return copy;
    }

    private static void publishInvalidation() {
        NetworkEventManager.publishCacheInvalidation("SEASON_ARCHIVE", INVALIDATION_OWNER);
    }
}
