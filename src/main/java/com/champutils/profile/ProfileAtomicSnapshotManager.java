package com.champutils.profile;

import com.champutils.database.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * Crash-safe profile snapshot helper.
 *
 * Save flow is intentionally temp -> active -> complete inside one SQL transaction:
 * 1) insert a PENDING snapshot row
 * 2) upsert the live restore table
 * 3) mark the snapshot COMPLETE
 *
 * Loaders only ever restore from the live table or COMPLETE snapshots. A reboot can never
 * restore a half-written PENDING snapshot. Hot paths use coalesced async tasks so only the
 * newest pending save for each profile/type is written during save bursts.
 */
public final class ProfileAtomicSnapshotManager {
    private ProfileAtomicSnapshotManager() {}

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure atomic profile snapshot schema", ProfileAtomicSnapshotManager::ensureSchema);
    }

    public static void ensureSchema(Connection connection) throws Exception {
        if (connection == null) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create extension if not exists pgcrypto");
            statement.executeUpdate("create table if not exists profile_atomic_snapshots (" +
                    "id uuid primary key default gen_random_uuid(), " +
                    "profile_id uuid not null references player_profiles(id) on delete cascade, " +
                    "player_uuid uuid references players(uuid) on delete cascade, " +
                    "snapshot_type text not null check (snapshot_type in ('VANILLA','COBBLEMON')), " +
                    "vanilla_snbt text, " +
                    "party_nbt text, " +
                    "pc_nbt text, " +
                    "reason text, " +
                    "status text not null default 'PENDING' check (status in ('PENDING','COMPLETE')), " +
                    "created_at timestamptz not null default now(), " +
                    "completed_at timestamptz)");
            statement.executeUpdate("create index if not exists idx_profile_atomic_snapshots_latest on profile_atomic_snapshots(profile_id, snapshot_type, completed_at desc) where status = 'COMPLETE'");
            statement.executeUpdate("create index if not exists idx_profile_atomic_snapshots_pending on profile_atomic_snapshots(profile_id, snapshot_type, created_at) where status = 'PENDING'");
            statement.executeUpdate("alter table profile_atomic_snapshots add column if not exists save_generation uuid");
            statement.executeUpdate("alter table profile_atomic_snapshots add column if not exists expected_lock_version bigint");
            statement.executeUpdate("alter table profile_atomic_snapshots add column if not exists committed_lock_version bigint");
            statement.executeUpdate("alter table profile_vanilla_state add column if not exists save_generation uuid");
            statement.executeUpdate("alter table profile_vanilla_state add column if not exists lock_version bigint not null default 0");
            statement.executeUpdate("alter table profile_cobblemon_storage add column if not exists save_generation uuid");
            statement.executeUpdate("alter table profile_cobblemon_storage add column if not exists lock_version bigint not null default 0");
            ProfileSaveGenerationManager.ensureSchema(connection);
        }
    }

    public static void saveVanillaCoalesced(UUID profileId, UUID playerUuid, String playerName, String snbt, String reason) {
        if (profileId == null || playerUuid == null || snbt == null || snbt.isBlank() || !DatabaseManager.isEnabled()) return;
        ProfileSaveGenerationManager.QueuedSave queuedSave = ProfileSaveGenerationManager.queue(profileId, playerUuid, "VANILLA", reason);
        DatabaseManager.executeCoalescedAsync("profile-vanilla-snapshot:" + profileId, "atomic vanilla profile snapshot", connection -> {
            if (!ProfileSaveGenerationManager.isLatest(queuedSave)) {
                ProfileSaveGenerationManager.markStaleAsync(queuedSave);
                return;
            }
            saveVanillaBlocking(connection, profileId, playerUuid, snbt, reason, queuedSave);
            PlayerProfileManager.cacheVanillaState(profileId, snbt);
        });
    }

    public static void saveVanillaBlocking(Connection connection, UUID profileId, UUID playerUuid, String snbt, String reason) throws Exception {
        saveVanillaBlocking(connection, profileId, playerUuid, snbt, reason, ProfileSaveGenerationManager.queue(profileId, playerUuid, "VANILLA", reason));
    }

    public static void saveVanillaBlocking(Connection connection, UUID profileId, UUID playerUuid, String snbt, String reason, ProfileSaveGenerationManager.QueuedSave queuedSave) throws Exception {
        if (connection == null || profileId == null || playerUuid == null || snbt == null || snbt.isBlank()) return;
        ensureSchema(connection);
        boolean previousAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            ProfileSaveGenerationManager.SqlSave sqlSave = ProfileSaveGenerationManager.begin(connection, queuedSave);
            UUID snapshotId;
            try (var insert = connection.prepareStatement("insert into profile_atomic_snapshots (profile_id, player_uuid, snapshot_type, vanilla_snbt, reason, status, save_generation, expected_lock_version) values (?, ?, 'VANILLA', ?, ?, 'PENDING', ?, ?) returning id")) {
                insert.setObject(1, profileId);
                insert.setObject(2, playerUuid);
                insert.setString(3, snbt);
                insert.setString(4, safeReason(reason));
                insert.setObject(5, sqlSave.generationId());
                insert.setLong(6, sqlSave.expectedLockVersion());
                try (ResultSet rs = insert.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("Could not create vanilla profile snapshot row.");
                    snapshotId = (UUID) rs.getObject("id");
                }
            }
            long committedVersion = ProfileSaveGenerationManager.commit(connection, profileId, sqlSave, "{\"snapshot_type\":\"VANILLA\"}");
            try (var ps = connection.prepareStatement("insert into profile_vanilla_state (profile_id, player_uuid, vanilla_snbt, updated_at, save_generation, lock_version) values (?, ?, ?, now(), ?, ?) " +
                    "on conflict (profile_id) do update set vanilla_snbt = excluded.vanilla_snbt, player_uuid = excluded.player_uuid, save_generation = excluded.save_generation, lock_version = excluded.lock_version, updated_at = now()")) {
                ps.setObject(1, profileId);
                ps.setObject(2, playerUuid);
                ps.setString(3, snbt);
                ps.setObject(4, sqlSave.generationId());
                ps.setLong(5, committedVersion);
                ps.executeUpdate();
            }
            try (var complete = connection.prepareStatement("update profile_atomic_snapshots set status = 'COMPLETE', completed_at = now(), committed_lock_version = ? where id = ?")) {
                complete.setLong(1, committedVersion);
                complete.setObject(2, snapshotId);
                complete.executeUpdate();
            }
            connection.commit();
        } catch (Exception e) {
            try { connection.rollback(); } catch (Exception ignored) {}
            throw e;
        } finally {
            try { connection.setAutoCommit(previousAutoCommit); } catch (Exception ignored) {}
        }
    }

    public static void saveCobblemonCoalesced(UUID profileId, String partyNbt, String pcNbt, String reason) {
        if (profileId == null || (partyNbt == null && pcNbt == null) || !DatabaseManager.isEnabled()) return;
        UUID playerUuid = CobblemonProfileStorageBridge.accountUuidForProfile(profileId);
        ProfileSaveGenerationManager.QueuedSave queuedSave = ProfileSaveGenerationManager.queue(profileId, playerUuid, "COBBLEMON", reason);
        DatabaseManager.executeCoalescedAsync("profile-cobblemon-snapshot:" + profileId, "atomic Cobblemon profile snapshot", connection -> {
            if (!ProfileSaveGenerationManager.isLatest(queuedSave)) {
                ProfileSaveGenerationManager.markStaleAsync(queuedSave);
                return;
            }
            saveCobblemonBlocking(connection, profileId, partyNbt, pcNbt, reason, queuedSave);
        });
    }

    public static void saveCobblemonBlocking(Connection connection, UUID profileId, String partyNbt, String pcNbt, String reason) throws Exception {
        saveCobblemonBlocking(connection, profileId, partyNbt, pcNbt, reason, ProfileSaveGenerationManager.queue(profileId, CobblemonProfileStorageBridge.accountUuidForProfile(profileId), "COBBLEMON", reason));
    }

    public static void saveCobblemonBlocking(Connection connection, UUID profileId, String partyNbt, String pcNbt, String reason, ProfileSaveGenerationManager.QueuedSave queuedSave) throws Exception {
        if (connection == null || profileId == null || (partyNbt == null && pcNbt == null)) return;
        ensureSchema(connection);
        boolean previousAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            ProfileSaveGenerationManager.SqlSave sqlSave = ProfileSaveGenerationManager.begin(connection, queuedSave);
            UUID snapshotId;
            try (var insert = connection.prepareStatement("insert into profile_atomic_snapshots (profile_id, player_uuid, snapshot_type, party_nbt, pc_nbt, reason, status, save_generation, expected_lock_version) values (?, ?, 'COBBLEMON', ?, ?, ?, 'PENDING', ?, ?) returning id")) {
                insert.setObject(1, profileId);
                insert.setObject(2, queuedSave == null ? null : queuedSave.playerUuid());
                insert.setString(3, partyNbt);
                insert.setString(4, pcNbt);
                insert.setString(5, safeReason(reason));
                insert.setObject(6, sqlSave.generationId());
                insert.setLong(7, sqlSave.expectedLockVersion());
                try (ResultSet rs = insert.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("Could not create Cobblemon profile snapshot row.");
                    snapshotId = (UUID) rs.getObject("id");
                }
            }
            long committedVersion = ProfileSaveGenerationManager.commit(connection, profileId, sqlSave, "{\"snapshot_type\":\"COBBLEMON\"}");
            try (var ps = connection.prepareStatement("insert into profile_cobblemon_storage (profile_id, party_nbt, pc_nbt, updated_at, save_generation, lock_version) values (?, ?, ?, now(), ?, ?) " +
                    "on conflict (profile_id) do update set " +
                    "party_nbt = coalesce(excluded.party_nbt, profile_cobblemon_storage.party_nbt), " +
                    "pc_nbt = coalesce(excluded.pc_nbt, profile_cobblemon_storage.pc_nbt), " +
                    "save_generation = excluded.save_generation, lock_version = excluded.lock_version, updated_at = now()")) {
                ps.setObject(1, profileId);
                ps.setString(2, partyNbt);
                ps.setString(3, pcNbt);
                ps.setObject(4, sqlSave.generationId());
                ps.setLong(5, committedVersion);
                ps.executeUpdate();
            }
            try (var complete = connection.prepareStatement("update profile_atomic_snapshots set status = 'COMPLETE', completed_at = now(), committed_lock_version = ? where id = ?")) {
                complete.setLong(1, committedVersion);
                complete.setObject(2, snapshotId);
                complete.executeUpdate();
            }
            connection.commit();
        } catch (Exception e) {
            try { connection.rollback(); } catch (Exception ignored) {}
            throw e;
        } finally {
            try { connection.setAutoCommit(previousAutoCommit); } catch (Exception ignored) {}
        }
    }

    public static String latestCompletedVanilla(Connection connection, UUID profileId) throws Exception {
        if (connection == null || profileId == null) return null;
        ensureSchema(connection);
        try (var ps = connection.prepareStatement("select vanilla_snbt from profile_atomic_snapshots where profile_id = ? and snapshot_type = 'VANILLA' and status = 'COMPLETE' and vanilla_snbt is not null order by completed_at desc limit 1")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("vanilla_snbt") : null;
            }
        }
    }

    public static String latestCompletedCobblemon(Connection connection, UUID profileId, boolean party) throws Exception {
        if (connection == null || profileId == null) return null;
        ensureSchema(connection);
        String column = party ? "party_nbt" : "pc_nbt";
        try (var ps = connection.prepareStatement("select " + column + " from profile_atomic_snapshots where profile_id = ? and snapshot_type = 'COBBLEMON' and status = 'COMPLETE' and " + column + " is not null order by completed_at desc limit 1")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(column) : null;
            }
        }
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) return "profile-save";
        return reason.length() > 120 ? reason.substring(0, 120) : reason;
    }
}
