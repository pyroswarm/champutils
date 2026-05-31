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
    private static final long DEFAULT_INCREMENT_SECONDS = 60L;

    private ProfilePlaytimeManager() {}

    public static void addOnlineMinute(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || player.isSpectator() || !PlayerProfileManager.hasActiveProfile(player)) continue;

            UUID profileId = PlayerProfileManager.activeProfileId(player);
            if (profileId == null || profileId.equals(player.getUUID())) continue;

            PROFILE_SECONDS.computeIfAbsent(profileId, ignored -> new AtomicLong(0L))
                    .addAndGet(DEFAULT_INCREMENT_SECONDS);
            DIRTY.add(profileId);
        }
    }

    public static long getCachedPlaytimeSeconds(ServerPlayer player) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) return 0L;
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

        DatabaseManager.executeAsync("warm profile playtime cache", connection -> {
            try {
                ensureSchema(connection);
                long loaded = 0L;
                try (var ps = connection.prepareStatement("select playtime_seconds from profile_player_stats where profile_id = ?")) {
                    ps.setObject(1, profileId);
                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) loaded = Math.max(0L, rs.getLong("playtime_seconds"));
                    }
                }
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
                    "insert into profile_player_stats (profile_id, playtime_seconds, updated_at) values (?, ?, now()) " +
                            "on conflict (profile_id) do update set playtime_seconds = greatest(profile_player_stats.playtime_seconds, excluded.playtime_seconds), updated_at = now()")) {
                for (Map.Entry<UUID, Long> entry : snapshot.entrySet()) {
                    ps.setObject(1, entry.getKey());
                    ps.setLong(2, Math.max(0L, entry.getValue()));
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
                    "insert into profile_player_stats (profile_id, playtime_seconds, updated_at) values (?, ?, now()) " +
                            "on conflict (profile_id) do update set playtime_seconds = greatest(profile_player_stats.playtime_seconds, excluded.playtime_seconds), updated_at = now()")) {
                for (UUID profileId : DIRTY) {
                    AtomicLong seconds = PROFILE_SECONDS.get(profileId);
                    if (seconds == null) continue;
                    ps.setObject(1, profileId);
                    ps.setLong(2, Math.max(0L, seconds.get()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            DIRTY.clear();
        } catch (Exception e) {
            e.printStackTrace();
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
