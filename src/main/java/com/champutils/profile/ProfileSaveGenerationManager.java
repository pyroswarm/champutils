package com.champutils.profile;

import com.champutils.database.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Versioned profile save helper.
 *
 * Each queued save gets a local generation number before it leaves the server thread.
 * Older async writes for the same profile/type are rejected before touching SQL, and
 * the SQL commit still checks player_profiles.lock_version so another server cannot
 * silently overwrite a newer profile state.
 */
public final class ProfileSaveGenerationManager {
    private static final ConcurrentHashMap<String, AtomicLong> LOCAL_GENERATIONS = new ConcurrentHashMap<>();

    private ProfileSaveGenerationManager() {}

    public record QueuedSave(UUID profileId, UUID playerUuid, String saveType, long localGeneration, String reason) {}
    public record SqlSave(UUID generationId, long expectedLockVersion) {}

    public static void ensureSchema(Connection connection) throws Exception {
        if (connection == null) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create extension if not exists pgcrypto");
            statement.executeUpdate("alter table player_profiles add column if not exists save_generation uuid default gen_random_uuid()");
            statement.executeUpdate("alter table player_profiles add column if not exists lock_version bigint not null default 0");
            statement.executeUpdate("alter table player_profiles add column if not exists profile_version bigint not null default 0");
            statement.executeUpdate("alter table player_profiles add column if not exists last_save_generation uuid");
            statement.executeUpdate("alter table player_profiles add column if not exists last_saved_at timestamptz");
            statement.executeUpdate("create table if not exists profile_save_generations (" +
                    "id uuid primary key default gen_random_uuid(), " +
                    "profile_id uuid not null references player_profiles(id) on delete cascade, " +
                    "player_uuid uuid references players(uuid) on delete set null, " +
                    "server_id text not null default '', " +
                    "source text not null default 'server', " +
                    "state text not null default 'OPEN' check (state in ('OPEN','COMMITTED','ABORTED','RECOVERED','STALE_REJECTED')), " +
                    "expected_profile_version bigint, " +
                    "expected_lock_version bigint, " +
                    "committed_profile_version bigint, " +
                    "started_at timestamptz not null default now(), " +
                    "committed_at timestamptz, " +
                    "metadata jsonb not null default '{}'::jsonb)");
            statement.executeUpdate("alter table profile_save_generations add column if not exists expected_lock_version bigint");
            statement.executeUpdate("alter table profile_save_generations add column if not exists committed_profile_version bigint");
            statement.executeUpdate("create index if not exists idx_profile_save_generations_profile_started on profile_save_generations(profile_id, started_at desc)");
            statement.executeUpdate("create index if not exists idx_profile_save_generations_open on profile_save_generations(profile_id, started_at) where state = 'OPEN'");
        }
    }

    public static QueuedSave queue(UUID profileId, UUID playerUuid, String saveType, String reason) {
        if (profileId == null) return null;
        String type = saveType == null || saveType.isBlank() ? "PROFILE" : saveType.trim().toUpperCase();
        long generation = LOCAL_GENERATIONS.computeIfAbsent(profileId + ":" + type, ignored -> new AtomicLong()).incrementAndGet();
        return new QueuedSave(profileId, playerUuid, type, generation, reason == null ? "profile-save" : reason);
    }

    public static boolean isLatest(QueuedSave queuedSave) {
        if (queuedSave == null) return false;
        AtomicLong latest = LOCAL_GENERATIONS.get(queuedSave.profileId() + ":" + queuedSave.saveType());
        return latest != null && latest.get() == queuedSave.localGeneration();
    }

    public static void markStaleAsync(QueuedSave queuedSave) {
        if (queuedSave == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("mark stale profile save", connection -> {
            ensureSchema(connection);
            try (var ps = connection.prepareStatement("insert into profile_save_generations (profile_id, player_uuid, source, state, metadata) values (?, ?, ?, 'STALE_REJECTED', jsonb_build_object('reason', ?, 'local_generation', ?))")) {
                ps.setObject(1, queuedSave.profileId());
                ps.setObject(2, queuedSave.playerUuid());
                ps.setString(3, queuedSave.saveType());
                ps.setString(4, queuedSave.reason());
                ps.setLong(5, queuedSave.localGeneration());
                ps.executeUpdate();
            }
        });
    }

    public static SqlSave begin(Connection connection, QueuedSave queuedSave) throws Exception {
        ensureSchema(connection);
        long expectedLockVersion = 0L;
        try (var ps = connection.prepareStatement("select lock_version from player_profiles where id = ? and deleted_at is null")) {
            ps.setObject(1, queuedSave.profileId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Profile no longer exists: " + queuedSave.profileId());
                expectedLockVersion = rs.getLong("lock_version");
            }
        }
        try (var ps = connection.prepareStatement("insert into profile_save_generations (profile_id, player_uuid, source, state, expected_lock_version, metadata) values (?, ?, ?, 'OPEN', ?, jsonb_build_object('reason', ?, 'local_generation', ?)) returning id")) {
            ps.setObject(1, queuedSave.profileId());
            ps.setObject(2, queuedSave.playerUuid());
            ps.setString(3, queuedSave.saveType());
            ps.setLong(4, expectedLockVersion);
            ps.setString(5, queuedSave.reason());
            ps.setLong(6, queuedSave.localGeneration());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Could not create save generation.");
                return new SqlSave((UUID) rs.getObject("id"), expectedLockVersion);
            }
        }
    }

    public static long commit(Connection connection, UUID profileId, SqlSave sqlSave, String metadataJson) throws Exception {
        if (connection == null || profileId == null || sqlSave == null) throw new IllegalArgumentException("Missing save commit data.");
        long nextVersion = sqlSave.expectedLockVersion() + 1L;
        try (var ps = connection.prepareStatement("update player_profiles set lock_version = lock_version + 1, profile_version = greatest(coalesce(profile_version,0), lock_version + 1), save_generation = ?, last_save_generation = ?, last_saved_at = now(), updated_at = now() where id = ? and lock_version = ? and deleted_at is null")) {
            ps.setObject(1, sqlSave.generationId());
            ps.setObject(2, sqlSave.generationId());
            ps.setObject(3, profileId);
            ps.setLong(4, sqlSave.expectedLockVersion());
            if (ps.executeUpdate() != 1) {
                abort(connection, sqlSave.generationId(), "lock_version_conflict");
                throw new StaleProfileWriteException("Rejected stale profile save for " + profileId + " expected lock_version=" + sqlSave.expectedLockVersion());
            }
        }
        try (var ps = connection.prepareStatement("update profile_save_generations set state = 'COMMITTED', committed_at = now(), committed_profile_version = ?, metadata = coalesce(metadata, '{}'::jsonb) || coalesce(?::jsonb, '{}'::jsonb) where id = ?")) {
            ps.setLong(1, nextVersion);
            ps.setString(2, metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson);
            ps.setObject(3, sqlSave.generationId());
            ps.executeUpdate();
        }
        return nextVersion;
    }

    public static void abort(Connection connection, UUID generationId, String reason) throws Exception {
        if (connection == null || generationId == null) return;
        try (var ps = connection.prepareStatement("update profile_save_generations set state = 'ABORTED', metadata = coalesce(metadata, '{}'::jsonb) || jsonb_build_object('abort_reason', ?) where id = ? and state = 'OPEN'")) {
            ps.setString(1, reason == null ? "aborted" : reason);
            ps.setObject(2, generationId);
            ps.executeUpdate();
        }
    }

    public static final class StaleProfileWriteException extends Exception {
        public StaleProfileWriteException(String message) { super(message); }
    }
}
