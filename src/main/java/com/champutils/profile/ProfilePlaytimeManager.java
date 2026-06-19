package com.champutils.profile;

import com.champutils.database.DatabaseManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance per-profile playtime tracker.
 *
 * Main-thread work is intentionally tiny:
 * - active profile lookup from PlayerProfileManager's in-memory ACTIVE cache
 * - AtomicLong increment in this cache
 *
 * SQL writes are batched and flushed on the database executor, never from the scoreboard
 * and never once per scoreboard render.
 */
public final class ProfilePlaytimeManager {
    private static final Map<UUID, AtomicLong> PROFILE_SECONDS = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> LOADED_FROM_DB = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> LOADING_FROM_DB = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, SessionMark> SESSION_MARKS = new ConcurrentHashMap<>();
    private static final long DEFAULT_INCREMENT_SECONDS = 60L;

    private ProfilePlaytimeManager() {}

    public static void addOnlineMinute(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) recordCurrentSession(player);
        flushAsync();
    }

    public static void recordCurrentSession(ServerPlayer player) {
        if (player == null || player.isSpectator() || !PlayerProfileManager.hasActiveProfile(player)) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return;
        long now = System.currentTimeMillis();
        SessionMark previous = SESSION_MARKS.put(player.getUUID(), new SessionMark(profileId, now));
        if (previous == null || !profileId.equals(previous.profileId)) {
            warmCacheAsync(profileId);
            return;
        }
        long elapsedSeconds = Math.max(0L, (now - previous.markedAtMillis) / 1000L);
        if (elapsedSeconds <= 0L) return;
        PROFILE_SECONDS.computeIfAbsent(profileId, ignored -> new AtomicLong(0L)).addAndGet(elapsedSeconds);
        DIRTY.add(profileId);
    }

    public static void flushPlayerBlockingBestEffort(ServerPlayer player) {
        if (player == null) return;
        recordCurrentSession(player);
        flushBlockingBestEffort();
    }

    public static void clearSession(ServerPlayer player) {
        if (player != null) SESSION_MARKS.remove(player.getUUID());
    }

    public static long getCachedPlaytimeSeconds(ServerPlayer player) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) return 0L;
        return getCachedPlaytimeSeconds(PlayerProfileManager.activeProfileId(player));
    }

    /**
     * Scoreboard-safe display value. It records the current active session first so the
     * sidebar does not sit at 0 while waiting for the next minute flush.
     */
    public static long getDisplayPlaytimeSeconds(ServerPlayer player) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) return 0L;
        recordCurrentSession(player);
        return getCachedPlaytimeSeconds(PlayerProfileManager.activeProfileId(player));
    }

    public static long getCachedPlaytimeSeconds(UUID profileId) {
        if (profileId == null) return 0L;
        warmCacheAsync(profileId);
        AtomicLong cached = PROFILE_SECONDS.get(profileId);
        if (cached != null) return Math.max(0L, cached.get());
        return 0L;
    }

    public static boolean hasAtLeastPlaytime(ServerPlayer player, long requiredSeconds) {
        long required = Math.max(0L, requiredSeconds);
        if (required <= 0L) return true;
        return getCachedPlaytimeSeconds(player) >= required;
    }

    public static void warmCacheAsync(UUID profileId) {
        if (profileId == null || !DatabaseManager.isEnabled()) return;
        if (LOADED_FROM_DB.contains(profileId)) return;
        if (!LOADING_FROM_DB.add(profileId)) return;

        DatabaseManager.executeCoalescedAsync("warm-playtime:" + profileId, "warm profile playtime cache", connection -> {
            try {
                ensureSchema(connection);
                long loaded = 0L;
                if (!profileExists(connection, profileId)) {
                    LOADED_FROM_DB.add(profileId);
                    return;
                }
                try (var ps = connection.prepareStatement("select playtime_seconds from profile_player_stats where profile_id = ?")) {
                    ps.setObject(1, profileId);
                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) loaded = Math.max(0L, rs.getLong("playtime_seconds"));
                    }
                }
                if (loaded <= 0L) loaded = loadLegacyProfilePlaytime(connection, profileId);
                long finalLoaded = loaded;
                PROFILE_SECONDS.compute(profileId, (id, existing) -> {
                    if (existing == null) return new AtomicLong(finalLoaded);
                    existing.set(Math.max(existing.get(), finalLoaded));
                    return existing;
                });
                LOADED_FROM_DB.add(profileId);
            } finally {
                LOADING_FROM_DB.remove(profileId);
            }
        });
    }


    /**
     * Loads the persisted playtime into the in-memory cache immediately.
     * This is meant for the async profile-switch SQL phase, not the server thread.
     */
    public static void loadCacheBlocking(Connection connection, UUID profileId) throws Exception {
        if (connection == null || profileId == null) return;
        ensureSchema(connection);
        long loaded = 0L;
        if (!profileExists(connection, profileId)) {
            LOADED_FROM_DB.add(profileId);
            return;
        }
        try (var ps = connection.prepareStatement("select playtime_seconds from profile_player_stats where profile_id = ?")) {
            ps.setObject(1, profileId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) loaded = Math.max(0L, rs.getLong("playtime_seconds"));
            }
        }
        if (loaded <= 0L) loaded = loadLegacyProfilePlaytime(connection, profileId);
        long finalLoaded = loaded;
        PROFILE_SECONDS.compute(profileId, (id, existing) -> {
            if (existing == null) return new AtomicLong(finalLoaded);
            existing.set(Math.max(existing.get(), finalLoaded));
            return existing;
        });
        LOADED_FROM_DB.add(profileId);
    }

    public static void flushPlayerAsyncBestEffort(ServerPlayer player) {
        if (player == null) return;
        recordCurrentSession(player);
        flushAsync();
    }

    public static void flushAsync() {
        if (DIRTY.isEmpty() || !DatabaseManager.isEnabled()) return;

        Map<UUID, Long> snapshot = new ConcurrentHashMap<>();
        for (UUID profileId : DIRTY) {
            AtomicLong seconds = PROFILE_SECONDS.get(profileId);
            if (seconds != null) snapshot.put(profileId, Math.max(0L, seconds.get()));
        }
        DIRTY.clear();

        if (snapshot.isEmpty()) return;

        DatabaseManager.executeAsync("flush profile playtime", connection -> {
            ensureSchema(connection);
            try (var ps = connection.prepareStatement(
                    "insert into profile_player_stats (profile_id, playtime_seconds, updated_at) " +
                            "select ?, ?, now() where exists (select 1 from player_profiles where id = ?) " +
                            "on conflict (profile_id) do update set playtime_seconds = greatest(profile_player_stats.playtime_seconds, excluded.playtime_seconds), updated_at = now()")) {
                for (Map.Entry<UUID, Long> entry : snapshot.entrySet()) {
                    UUID profileId = entry.getKey();
                    ps.setObject(1, profileId);
                    ps.setLong(2, Math.max(0L, entry.getValue()));
                    ps.setObject(3, profileId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public static void flushBlockingBestEffort() {
        if (DIRTY.isEmpty() || !DatabaseManager.isEnabled()) return;
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            try (var ps = connection.prepareStatement(
                    "insert into profile_player_stats (profile_id, playtime_seconds, updated_at) " +
                            "select ?, ?, now() where exists (select 1 from player_profiles where id = ?) " +
                            "on conflict (profile_id) do update set playtime_seconds = greatest(profile_player_stats.playtime_seconds, excluded.playtime_seconds), updated_at = now()")) {
                for (UUID profileId : DIRTY) {
                    AtomicLong seconds = PROFILE_SECONDS.get(profileId);
                    if (seconds == null) continue;
                    ps.setObject(1, profileId);
                    ps.setLong(2, Math.max(0L, seconds.get()));
                    ps.setObject(3, profileId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            DIRTY.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private record SessionMark(UUID profileId, long markedAtMillis) {}

    private static long loadLegacyProfilePlaytime(Connection connection, UUID profileId) {
        try (var ps = connection.prepareStatement("select coalesce((metadata->>'playtime_seconds')::bigint, 0) as playtime_seconds from player_profiles where id = ? and deleted_at is null limit 1")) {
            ps.setObject(1, profileId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return Math.max(0L, rs.getLong("playtime_seconds"));
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private static boolean profileExists(Connection connection, UUID profileId) throws Exception {
        if (profileId == null) return false;
        try (var ps = connection.prepareStatement("select 1 from player_profiles where id = ? and deleted_at is null limit 1")) {
            ps.setObject(1, profileId);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void ensureSchema(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists profile_player_stats (" +
                    "profile_id uuid primary key references player_profiles(id) on delete cascade, " +
                    "playtime_seconds bigint not null default 0, " +
                    "money numeric(18,2) not null default 0, " +
                    "battling_xp bigint not null default 0, " +
                    "battling_level integer not null default 1, " +
                    "total_level integer not null default 1, " +
                    "metadata jsonb not null default '{}'::jsonb, " +
                    "updated_at timestamptz not null default now())");
            statement.executeUpdate("alter table profile_player_stats add column if not exists playtime_seconds bigint not null default 0");
            statement.executeUpdate("create index if not exists idx_profile_player_stats_playtime on profile_player_stats(playtime_seconds)");
        }
    }
}
