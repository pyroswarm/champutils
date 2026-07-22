package com.champutils.adventurer;

import com.champutils.database.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class AdventurerGuildDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STRING_LONG_MAP_TYPE = new TypeToken<HashMap<String, Long>>() {}.getType();
    private static final Type STRING_SET_TYPE = new TypeToken<HashSet<String>>() {}.getType();
    private static volatile boolean schemaEnsured = false;

    private AdventurerGuildDataManager() {}

    public static final class PlayerData {
        public String uuid;
        public String name;
        public long renown = 0L;
        public long guildMarks = 0L;
        public HashSet<String> claimedRankRewards = new HashSet<>();

        public int towerFloor = 1;
        public int bestTowerFloor = 0;
        public int towerClears = 0;
        public int activeTowerFloor = 0;
        public String activeTowerNpcUuid = "";
        public long activeTowerStartedMillis = 0L;
        public long lastTowerStartMillis = 0L;
        public long lastTowerEndMillis = 0L;
        public HashMap<String, Long> towerRewardClaims = new HashMap<>();
        public boolean ultimateClimbActive = false;
        public long ultimateClimbStartedMillis = 0L;
        public long lastUltimateClimbAttemptMillis = 0L;
        public int ultimateClimbClears = 0;

        public long lastRoamingLeagueStartMillis = 0L;
        public int roamingLeagueDailySpawns = 0;
        public String roamingLeagueDailyKey = "";

        public String dailyKey = "";
        public int dailyPvpMatches = 0;
        public int dailyPvpWins = 0;
        public boolean dailyPvpClaimed = false;

        public String weeklyKey = "";
        public int weeklyPvpMatches = 0;
        public int weeklyPvpWins = 0;
        public int weeklyRankedWins = 0;
        public boolean weeklyPvpClaimed = false;
    }

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure adventurer guild schema", AdventurerGuildDataManager::ensureSchema);
    }

    private static File dir() {
        File dir = new File("config/champutils/adventurers/players");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File file(UUID profileId) {
        return new File(dir(), profileId.toString() + ".json");
    }

    public static PlayerData load(UUID profileId, String name) {
        PlayerData data = null;

        if (profileId != null && DatabaseManager.isEnabled() && !isMinecraftServerThread()) {
            try {
                if (Thread.currentThread().getName().startsWith("ChampUtils-Database-")) {
                    Connection connection = DatabaseManager.getConnection();
                    ensureSchema(connection);
                    data = loadSql(connection, profileId, name);
                } else {
                    data = DatabaseManager.supplyAsync("load adventurer guild profile " + profileId, connection -> {
                        ensureSchema(connection);
                        return loadSql(connection, profileId, name);
                    }).get(3, TimeUnit.SECONDS);
                }
                if (data != null) {
                    normalize(data, profileId, name);
                    saveLocal(profileId, data);
                    return data;
                }
            } catch (Exception e) {
                System.err.println("[ChampUtils] Could not load Adventurer's Guild data from SQL for profile " + profileId + "; using local fallback.");
                e.printStackTrace();
            }
        }

        try {
            File file = file(profileId);
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    data = GSON.fromJson(reader, PlayerData.class);
                }
            }
            if (data == null) data = new PlayerData();
            normalize(data, profileId, name);
            if (profileId != null && DatabaseManager.isEnabled() && file.exists()) {
                saveToDatabaseAsync(profileId, data);
            }
            return data;
        } catch (Exception e) {
            e.printStackTrace();
            data = new PlayerData();
            normalize(data, profileId, name);
            return data;
        }
    }

    public static void save(UUID profileId, PlayerData data) {
        if (profileId == null || data == null) return;
        PlayerData snapshot = copyOf(data);
        normalize(snapshot, profileId, snapshot.name);
        saveLocal(profileId, snapshot);
        saveToDatabaseAsync(profileId, snapshot);
    }

    public static void syncToDatabase(PlayerData data) {
        if (data == null || data.uuid == null || data.uuid.isBlank()) return;
        try {
            UUID profileId = UUID.fromString(data.uuid);
            PlayerData snapshot = copyOf(data);
            normalize(snapshot, profileId, snapshot.name);
            saveToDatabaseAsync(profileId, snapshot);
        } catch (Exception ignored) {
        }
    }

    public static List<PlayerData> getAllLocalPlayers() {
        List<PlayerData> out = new ArrayList<>();
        File[] files = dir().listFiles((parent, name) -> name != null && name.endsWith(".json"));
        if (files == null) return out;
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                PlayerData data = GSON.fromJson(reader, PlayerData.class);
                if (data == null) continue;
                String fileName = file.getName();
                UUID profileId = UUID.fromString(fileName.substring(0, fileName.length() - ".json".length()));
                normalize(data, profileId, data.name);
                out.add(data);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static void saveLocal(UUID profileId, PlayerData data) {
        if (profileId == null || data == null) return;
        try (FileWriter writer = new FileWriter(file(profileId))) {
            GSON.toJson(data, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveToDatabaseAsync(UUID profileId, PlayerData data) {
        if (profileId == null || data == null || !DatabaseManager.isEnabled()) return;
        PlayerData snapshot = copyOf(data);
        normalize(snapshot, profileId, snapshot.name);
        DatabaseManager.executeCoalescedAsync("adventurer-guild:" + profileId, "save adventurer guild profile " + profileId, connection -> {
            ensureSchema(connection);
            upsertSql(connection, profileId, snapshot);
        });
    }

    private static synchronized void ensureSchema(Connection connection) throws Exception {
        if (schemaEnsured || connection == null) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists public.profile_adventurer_guild (profile_id uuid primary key)");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists player_name text not null default ''");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists renown bigint not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists guild_marks bigint not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists claimed_rank_rewards jsonb not null default '[]'::jsonb");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists tower_floor integer not null default 1");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists best_tower_floor integer not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists tower_clears integer not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists active_tower_floor integer not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists active_tower_npc_uuid text not null default ''");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists active_tower_started_millis bigint not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists last_tower_start_millis bigint not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists last_tower_end_millis bigint not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists tower_reward_claims jsonb not null default '{}'::jsonb");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists ultimate_climb_active boolean not null default false");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists ultimate_climb_started_millis bigint not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists last_ultimate_climb_attempt_millis bigint not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists ultimate_climb_clears integer not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists last_roaming_league_start_millis bigint not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists roaming_league_daily_spawns integer not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists roaming_league_daily_key text not null default ''");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists daily_key text not null default ''");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists daily_pvp_matches integer not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists daily_pvp_wins integer not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists daily_pvp_claimed boolean not null default false");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists weekly_key text not null default ''");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists weekly_pvp_matches integer not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists weekly_pvp_wins integer not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists weekly_ranked_wins integer not null default 0");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists weekly_pvp_claimed boolean not null default false");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists metadata jsonb not null default '{}'::jsonb");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists created_at timestamptz not null default now()");
            statement.executeUpdate("alter table public.profile_adventurer_guild add column if not exists updated_at timestamptz not null default now()");
            statement.executeUpdate("do $$ begin " +
                    "if exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'player_profiles') " +
                    "and not exists (select 1 from pg_constraint where conname = 'profile_adventurer_guild_profile_fk') then " +
                    "alter table public.profile_adventurer_guild add constraint profile_adventurer_guild_profile_fk foreign key (profile_id) references public.player_profiles(id) on delete cascade; " +
                    "end if; end $$");
            statement.executeUpdate("create index if not exists idx_profile_adventurer_guild_renown on public.profile_adventurer_guild(renown desc)");
            statement.executeUpdate("create index if not exists idx_profile_adventurer_guild_updated on public.profile_adventurer_guild(updated_at desc)");
        }
        schemaEnsured = true;
    }

    private static PlayerData loadSql(Connection connection, UUID profileId, String name) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "select * from public.profile_adventurer_guild where profile_id = ?")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                PlayerData data = new PlayerData();
                data.uuid = profileId.toString();
                data.name = rs.getString("player_name");
                if (data.name == null || data.name.isBlank()) data.name = name == null ? "" : name;
                data.renown = rs.getLong("renown");
                data.guildMarks = rs.getLong("guild_marks");
                data.claimedRankRewards = parseStringSet(rs.getString("claimed_rank_rewards"));
                data.towerFloor = rs.getInt("tower_floor");
                data.bestTowerFloor = rs.getInt("best_tower_floor");
                data.towerClears = rs.getInt("tower_clears");
                data.activeTowerFloor = rs.getInt("active_tower_floor");
                data.activeTowerNpcUuid = rs.getString("active_tower_npc_uuid");
                data.activeTowerStartedMillis = rs.getLong("active_tower_started_millis");
                data.lastTowerStartMillis = rs.getLong("last_tower_start_millis");
                data.lastTowerEndMillis = rs.getLong("last_tower_end_millis");
                data.towerRewardClaims = parseStringLongMap(rs.getString("tower_reward_claims"));
                data.ultimateClimbActive = rs.getBoolean("ultimate_climb_active");
                data.ultimateClimbStartedMillis = rs.getLong("ultimate_climb_started_millis");
                data.lastUltimateClimbAttemptMillis = rs.getLong("last_ultimate_climb_attempt_millis");
                data.ultimateClimbClears = rs.getInt("ultimate_climb_clears");
                data.lastRoamingLeagueStartMillis = rs.getLong("last_roaming_league_start_millis");
                data.roamingLeagueDailySpawns = rs.getInt("roaming_league_daily_spawns");
                data.roamingLeagueDailyKey = rs.getString("roaming_league_daily_key");
                data.dailyKey = rs.getString("daily_key");
                data.dailyPvpMatches = rs.getInt("daily_pvp_matches");
                data.dailyPvpWins = rs.getInt("daily_pvp_wins");
                data.dailyPvpClaimed = rs.getBoolean("daily_pvp_claimed");
                data.weeklyKey = rs.getString("weekly_key");
                data.weeklyPvpMatches = rs.getInt("weekly_pvp_matches");
                data.weeklyPvpWins = rs.getInt("weekly_pvp_wins");
                data.weeklyRankedWins = rs.getInt("weekly_ranked_wins");
                data.weeklyPvpClaimed = rs.getBoolean("weekly_pvp_claimed");
                return data;
            }
        }
    }

    private static void upsertSql(Connection connection, UUID profileId, PlayerData data) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into public.profile_adventurer_guild (" +
                        "profile_id, player_name, renown, guild_marks, claimed_rank_rewards, tower_floor, best_tower_floor, tower_clears, " +
                        "active_tower_floor, active_tower_npc_uuid, active_tower_started_millis, last_tower_start_millis, last_tower_end_millis, " +
                        "tower_reward_claims, ultimate_climb_active, ultimate_climb_started_millis, last_ultimate_climb_attempt_millis, ultimate_climb_clears, " +
                        "last_roaming_league_start_millis, roaming_league_daily_spawns, roaming_league_daily_key, daily_key, daily_pvp_matches, daily_pvp_wins, daily_pvp_claimed, " +
                        "weekly_key, weekly_pvp_matches, weekly_pvp_wins, weekly_ranked_wins, weekly_pvp_claimed, updated_at" +
                        ") select ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now() " +
                        "where exists (select 1 from public.player_profiles where id = ? and deleted_at is null) " +
                        "on conflict (profile_id) do update set player_name=excluded.player_name, renown=excluded.renown, guild_marks=excluded.guild_marks, " +
                        "claimed_rank_rewards=excluded.claimed_rank_rewards, tower_floor=excluded.tower_floor, best_tower_floor=excluded.best_tower_floor, tower_clears=excluded.tower_clears, " +
                        "active_tower_floor=excluded.active_tower_floor, active_tower_npc_uuid=excluded.active_tower_npc_uuid, active_tower_started_millis=excluded.active_tower_started_millis, " +
                        "last_tower_start_millis=excluded.last_tower_start_millis, last_tower_end_millis=excluded.last_tower_end_millis, tower_reward_claims=excluded.tower_reward_claims, " +
                        "ultimate_climb_active=excluded.ultimate_climb_active, ultimate_climb_started_millis=excluded.ultimate_climb_started_millis, " +
                        "last_ultimate_climb_attempt_millis=excluded.last_ultimate_climb_attempt_millis, ultimate_climb_clears=excluded.ultimate_climb_clears, " +
                        "last_roaming_league_start_millis=excluded.last_roaming_league_start_millis, roaming_league_daily_spawns=excluded.roaming_league_daily_spawns, roaming_league_daily_key=excluded.roaming_league_daily_key, " +
                        "daily_key=excluded.daily_key, daily_pvp_matches=excluded.daily_pvp_matches, daily_pvp_wins=excluded.daily_pvp_wins, daily_pvp_claimed=excluded.daily_pvp_claimed, " +
                        "weekly_key=excluded.weekly_key, weekly_pvp_matches=excluded.weekly_pvp_matches, weekly_pvp_wins=excluded.weekly_pvp_wins, weekly_ranked_wins=excluded.weekly_ranked_wins, weekly_pvp_claimed=excluded.weekly_pvp_claimed, updated_at=now()")) {
            int i=1;
            ps.setObject(i++, profileId); ps.setString(i++, safe(data.name)); ps.setLong(i++, Math.max(0L,data.renown)); ps.setLong(i++, Math.max(0L,data.guildMarks));
            ps.setString(i++, GSON.toJson(data.claimedRankRewards == null ? Set.of() : data.claimedRankRewards));
            ps.setInt(i++, Math.max(1,data.towerFloor)); ps.setInt(i++, Math.max(0,data.bestTowerFloor)); ps.setInt(i++, Math.max(0,data.towerClears));
            ps.setInt(i++, Math.max(0,data.activeTowerFloor)); ps.setString(i++, safe(data.activeTowerNpcUuid)); ps.setLong(i++, Math.max(0L,data.activeTowerStartedMillis));
            ps.setLong(i++, Math.max(0L,data.lastTowerStartMillis)); ps.setLong(i++, Math.max(0L,data.lastTowerEndMillis));
            ps.setString(i++, GSON.toJson(data.towerRewardClaims == null ? Map.of() : data.towerRewardClaims)); ps.setBoolean(i++, data.ultimateClimbActive);
            ps.setLong(i++, Math.max(0L,data.ultimateClimbStartedMillis)); ps.setLong(i++, Math.max(0L,data.lastUltimateClimbAttemptMillis)); ps.setInt(i++, Math.max(0,data.ultimateClimbClears));
            ps.setLong(i++, Math.max(0L,data.lastRoamingLeagueStartMillis)); ps.setInt(i++, Math.max(0,data.roamingLeagueDailySpawns)); ps.setString(i++, safe(data.roamingLeagueDailyKey));
            ps.setString(i++, safe(data.dailyKey)); ps.setInt(i++, Math.max(0,data.dailyPvpMatches)); ps.setInt(i++, Math.max(0,data.dailyPvpWins)); ps.setBoolean(i++, data.dailyPvpClaimed);
            ps.setString(i++, safe(data.weeklyKey)); ps.setInt(i++, Math.max(0,data.weeklyPvpMatches)); ps.setInt(i++, Math.max(0,data.weeklyPvpWins)); ps.setInt(i++, Math.max(0,data.weeklyRankedWins)); ps.setBoolean(i++, data.weeklyPvpClaimed);
            ps.setObject(i, profileId); ps.executeUpdate();
        }
    }

    private static HashMap<String, Long> parseStringLongMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try { HashMap<String, Long> parsed = GSON.fromJson(json, STRING_LONG_MAP_TYPE); return parsed == null ? new HashMap<>() : parsed; }
        catch (Exception ignored) { return new HashMap<>(); }
    }

    private static HashSet<String> parseStringSet(String json) {
        if (json == null || json.isBlank()) return new HashSet<>();
        try {
            HashSet<String> parsed = GSON.fromJson(json, STRING_SET_TYPE);
            return parsed == null ? new HashSet<>() : parsed;
        } catch (Exception ignored) {
            return new HashSet<>();
        }
    }

    private static PlayerData copyOf(PlayerData source) {
        PlayerData data = new PlayerData();
        if (source == null) return data;
        data.uuid = source.uuid;
        data.name = source.name;
        data.renown = source.renown;
        data.guildMarks = source.guildMarks;
        data.claimedRankRewards = source.claimedRankRewards == null ? new HashSet<>() : new HashSet<>(source.claimedRankRewards);
        data.towerFloor = source.towerFloor;
        data.bestTowerFloor = source.bestTowerFloor;
        data.towerClears = source.towerClears;
        data.activeTowerFloor = source.activeTowerFloor;
        data.activeTowerNpcUuid = source.activeTowerNpcUuid;
        data.activeTowerStartedMillis = source.activeTowerStartedMillis;
        data.lastTowerStartMillis = source.lastTowerStartMillis;
        data.lastTowerEndMillis = source.lastTowerEndMillis;
        data.towerRewardClaims = source.towerRewardClaims == null ? new HashMap<>() : new HashMap<>(source.towerRewardClaims);
        data.ultimateClimbActive = source.ultimateClimbActive;
        data.ultimateClimbStartedMillis = source.ultimateClimbStartedMillis;
        data.lastUltimateClimbAttemptMillis = source.lastUltimateClimbAttemptMillis;
        data.ultimateClimbClears = source.ultimateClimbClears;
        data.lastRoamingLeagueStartMillis = source.lastRoamingLeagueStartMillis;
        data.roamingLeagueDailySpawns = source.roamingLeagueDailySpawns;
        data.roamingLeagueDailyKey = source.roamingLeagueDailyKey;
        data.dailyKey = source.dailyKey;
        data.dailyPvpMatches = source.dailyPvpMatches;
        data.dailyPvpWins = source.dailyPvpWins;
        data.dailyPvpClaimed = source.dailyPvpClaimed;
        data.weeklyKey = source.weeklyKey;
        data.weeklyPvpMatches = source.weeklyPvpMatches;
        data.weeklyPvpWins = source.weeklyPvpWins;
        data.weeklyRankedWins = source.weeklyRankedWins;
        data.weeklyPvpClaimed = source.weeklyPvpClaimed;
        return data;
    }

    private static void normalize(PlayerData data, UUID profileId, String name) {
        data.uuid = profileId == null ? "" : profileId.toString();
        data.name = name == null || name.isBlank() ? safe(data.name) : name;
        if (data.claimedRankRewards == null) data.claimedRankRewards = new HashSet<>();
        if (data.towerFloor <= 0) data.towerFloor = 1;
        if (data.dailyKey == null) data.dailyKey = "";
        if (data.weeklyKey == null) data.weeklyKey = "";
        if (data.roamingLeagueDailyKey == null) data.roamingLeagueDailyKey = "";
        if (data.activeTowerNpcUuid == null) data.activeTowerNpcUuid = "";
        if (data.towerRewardClaims == null) data.towerRewardClaims = new HashMap<>();
        data.renown = Math.max(0L, data.renown);
        data.guildMarks = Math.max(0L, data.guildMarks);
        data.bestTowerFloor = Math.max(0, data.bestTowerFloor);
        data.towerClears = Math.max(0, data.towerClears);
        data.activeTowerFloor = Math.max(0, data.activeTowerFloor);
        data.dailyPvpMatches = Math.max(0, data.dailyPvpMatches);
        data.dailyPvpWins = Math.max(0, data.dailyPvpWins);
        data.weeklyPvpMatches = Math.max(0, data.weeklyPvpMatches);
        data.weeklyPvpWins = Math.max(0, data.weeklyPvpWins);
        data.weeklyRankedWins = Math.max(0, data.weeklyRankedWins);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean isMinecraftServerThread() {
        String name = Thread.currentThread().getName();
        return name != null && name.equalsIgnoreCase("Server thread");
    }

}
