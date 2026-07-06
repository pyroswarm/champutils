package com.champutils.database;

import net.minecraft.server.MinecraftServer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Database retention and bloat-control tasks for high-write gameplay tables.
 *
 * Live player state is kept in the profile_* state tables and normal gameplay tables.
 * This manager only prunes recovery/history rows that exist to survive crashes or debug
 * profile saves. Those rows were the source of Beta 1 database growth because every full
 * NBT/Cobblemon save created another large text/json snapshot.
 */
public final class DatabaseMaintenanceManager {
    private static final long TICKS_BETWEEN_CLEANUPS = 6L * 60L * 60L * 20L; // 6 hours
    private static final long MIN_MANUAL_CLEANUP_INTERVAL_MILLIS = 60L * 60L * 1000L; // 1 hour

    private static final AtomicBoolean CLEANUP_QUEUED = new AtomicBoolean(false);
    private static volatile long nextCleanupTick = TICKS_BETWEEN_CLEANUPS;
    private static volatile long lastCleanupAtMillis = 0L;

    private DatabaseMaintenanceManager() {}

    public static void ensureAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure database maintenance schema", connection -> {
            ensureSchema(connection);
            cleanup(connection);
        });
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !DatabaseManager.isEnabled()) return;
        long tick = server.getTickCount();
        if (tick <= 0 || tick < nextCleanupTick) return;
        nextCleanupTick = tick + TICKS_BETWEEN_CLEANUPS;
        requestCleanupIfDue("scheduled");
    }

    /**
     * Called from hot save paths after a successful commit. The atomic guard keeps this from
     * spamming SQL when many players save at once.
     */
    public static void requestCleanupIfDue(String reason) {
        if (!DatabaseManager.isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastCleanupAtMillis < MIN_MANUAL_CLEANUP_INTERVAL_MILLIS) return;
        if (!CLEANUP_QUEUED.compareAndSet(false, true)) return;

        DatabaseManager.executeAsync("database maintenance cleanup: " + (reason == null ? "unknown" : reason), connection -> {
            try {
                ensureSchema(connection);
                cleanup(connection);
                lastCleanupAtMillis = System.currentTimeMillis();
            } finally {
                CLEANUP_QUEUED.set(false);
            }
        });
    }

    public static void ensureSchema(Connection connection) throws Exception {
        if (connection == null) return;
        try (Statement statement = connection.createStatement()) {
            // Snapshot/recovery table timestamp hardening. Some beta-created tables were missing
            // the exact timestamp columns needed for retention pruning.
            statement.executeUpdate("alter table if exists public.profile_save_generations add column if not exists started_at timestamptz not null default now()");
            statement.executeUpdate("alter table if exists public.profile_save_generations add column if not exists committed_at timestamptz");
            statement.executeUpdate("alter table if exists public.profile_atomic_snapshots add column if not exists created_at timestamptz not null default now()");
            statement.executeUpdate("alter table if exists public.profile_atomic_snapshots add column if not exists completed_at timestamptz");
            statement.executeUpdate("alter table if exists public.profile_battle_recovery add column if not exists started_at timestamptz not null default now()");
            statement.executeUpdate("alter table if exists public.profile_battle_recovery add column if not exists last_heartbeat timestamptz not null default now()");
            statement.executeUpdate("alter table if exists public.profile_battle_recovery add column if not exists ended_at timestamptz");
            statement.executeUpdate("alter table if exists public.profile_battle_recovery_events add column if not exists event_at timestamptz not null default now()");

            // Indexes used by retention queries and latest-save recovery lookups. Optional tables
            // may not exist yet during early startup, so only index tables that are present.
            if (tableExists(connection, "profile_atomic_snapshots")) {
                statement.executeUpdate("create index if not exists idx_profile_atomic_snapshots_created on public.profile_atomic_snapshots(profile_id, snapshot_type, created_at desc)");
                statement.executeUpdate("create index if not exists idx_profile_atomic_snapshots_status_created on public.profile_atomic_snapshots(status, created_at)");
            }
            if (tableExists(connection, "profile_save_generations")) {
                statement.executeUpdate("create index if not exists idx_profile_save_generations_profile_started on public.profile_save_generations(profile_id, started_at desc)");
                statement.executeUpdate("create index if not exists idx_profile_save_generations_state_started on public.profile_save_generations(state, started_at)");
            }
            if (tableExists(connection, "profile_battle_recovery")) {
                statement.executeUpdate("create index if not exists idx_profile_battle_recovery_status_started on public.profile_battle_recovery(status, started_at)");
                statement.executeUpdate("create index if not exists idx_profile_battle_recovery_ended on public.profile_battle_recovery(ended_at)");
            }
            if (tableExists(connection, "profile_battle_recovery_events")) {
                statement.executeUpdate("create index if not exists idx_profile_battle_recovery_events_event_at on public.profile_battle_recovery_events(event_at)");
            }
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        if (connection == null || tableName == null || tableName.isBlank()) return false;
        try (PreparedStatement statement = connection.prepareStatement(
                "select to_regclass(?) is not null as exists"
        )) {
            statement.setString(1, "public." + tableName.replaceAll("[^A-Za-z0-9_]", ""));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean("exists");
            }
        }
    }

    public static void cleanup(Connection connection) throws Exception {
        if (connection == null) return;
        try (Statement statement = connection.createStatement()) {
            if (tableExists(connection, "profile_atomic_snapshots")) {
                // Pending snapshots should only exist during an in-flight save. Anything old is a dead
                // interrupted write and safe to prune.
                statement.executeUpdate("delete from public.profile_atomic_snapshots where status = 'PENDING' and created_at < now() - interval '1 day'");

                // Keep only the newest COMPLETE snapshot per profile/type. Live state is already stored
                // in profile_vanilla_state/profile_cobblemon_storage, so older duplicate NBT blobs are waste.
                statement.executeUpdate("with ranked as (" +
                        "select id, row_number() over (partition by profile_id, snapshot_type order by completed_at desc nulls last, created_at desc) as rn " +
                        "from public.profile_atomic_snapshots where status = 'COMPLETE') " +
                        "delete from public.profile_atomic_snapshots s using ranked r " +
                        "where s.id = r.id and r.rn > 1");
            }

            if (tableExists(connection, "profile_save_generations")) {
                // Save generations are metadata, but still grow forever without retention. Keep the
                // newest 20 per profile for debugging and prune old closed/stale generations.
                statement.executeUpdate("with ranked as (" +
                        "select id, state, started_at, coalesce(committed_at, started_at) as effective_at, " +
                        "row_number() over (partition by profile_id order by coalesce(committed_at, started_at) desc, started_at desc) as rn " +
                        "from public.profile_save_generations where state <> 'OPEN') " +
                        "delete from public.profile_save_generations g using ranked r " +
                        "where g.id = r.id and (r.rn > 20 or r.effective_at < now() - interval '14 days')");

                // OPEN save generations older than a day are interrupted writes, not active saves.
                statement.executeUpdate("update public.profile_save_generations set state = 'ABORTED', metadata = coalesce(metadata, '{}'::jsonb) || jsonb_build_object('auto_aborted_by', 'db_maintenance') where state = 'OPEN' and started_at < now() - interval '1 day'");
            }

            if (tableExists(connection, "profile_battle_recovery")) {
                // Battle recovery should be short-lived. Mark ancient OPEN rows as expired, then delete
                // closed rows after a week. Events cascade through recovery_id.
                statement.executeUpdate("update public.profile_battle_recovery set status = 'EXPIRED', ended_at = now(), metadata = coalesce(metadata, '{}'::jsonb) || jsonb_build_object('expired_by', 'db_maintenance') where status = 'OPEN' and last_heartbeat < now() - interval '6 hours'");
                statement.executeUpdate("delete from public.profile_battle_recovery where status <> 'OPEN' and coalesce(ended_at, started_at) < now() - interval '7 days'");
            }

            if (tableExists(connection, "profile_battle_recovery_events")) {
                statement.executeUpdate("delete from public.profile_battle_recovery_events where event_at < now() - interval '7 days' and not exists (select 1 from public.profile_battle_recovery r where r.id = profile_battle_recovery_events.recovery_id)");
            }

            if (tableExists(connection, "notifications")) {
                statement.executeUpdate("delete from public.notifications where created_at < now() - interval '30 days'");
            }
            if (tableExists(connection, "wondertrade_history")) {
                statement.executeUpdate("delete from public.wondertrade_history where traded_at < now() - interval '30 days'");
            }
            if (tableExists(connection, "ranked_token_ledger")) {
                statement.executeUpdate("delete from public.ranked_token_ledger where rewarded_at < now() - interval '90 days'");
            }
        } catch (Exception e) {
            // Some installs may not have every optional table yet. Retention should never block server start.
            System.err.println("[ChampUtils] Database maintenance cleanup skipped/partial: " + e.getMessage());
        }
    }

}
