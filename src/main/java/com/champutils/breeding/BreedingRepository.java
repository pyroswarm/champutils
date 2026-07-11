package com.champutils.breeding;

import com.champutils.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BreedingRepository {
    private static volatile CompletableFuture<Void> schemaReady = CompletableFuture.completedFuture(null);

    private BreedingRepository() {}

    public record Reservation(boolean accepted, Instant nextEggAt, String reason) {}

    public static synchronized void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) {
            CompletableFuture<Void> unavailable = new CompletableFuture<>();
            unavailable.completeExceptionally(new IllegalStateException("Database is not enabled."));
            schemaReady = unavailable;
            return;
        }
        schemaReady = DatabaseManager.runAsync("ensure breeding schema", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        create table if not exists public.champ_breeding_profiles (
                            profile_id uuid primary key,
                            next_egg_at timestamptz not null default now(),
                            total_eggs_created bigint not null default 0 check (total_eggs_created >= 0),
                            total_eggs_hatched bigint not null default 0 check (total_eggs_hatched >= 0),
                            updated_at timestamptz not null default now()
                        )
                        """);
                statement.executeUpdate("""
                        create table if not exists public.champ_breeding_eggs (
                            egg_uuid uuid primary key,
                            profile_id uuid not null,
                            parent_a_uuid uuid not null,
                            parent_b_uuid uuid not null,
                            parent_a_species text not null,
                            parent_b_species text not null,
                            offspring_species text not null,
                            steps_required integer not null check (steps_required > 0),
                            status text not null default 'INCUBATING' check (status in ('INCUBATING','HATCHED','LOST')),
                            created_at timestamptz not null default now(),
                            hatched_at timestamptz,
                            updated_at timestamptz not null default now(),
                            constraint chk_champ_breeding_distinct_parents check (parent_a_uuid <> parent_b_uuid)
                        )
                        """);
                statement.executeUpdate("create index if not exists idx_champ_breeding_eggs_profile_status on public.champ_breeding_eggs(profile_id, status)");
                statement.executeUpdate("create index if not exists idx_champ_breeding_eggs_incubating on public.champ_breeding_eggs(profile_id, created_at) where status = 'INCUBATING'");
                statement.executeUpdate("alter table public.champ_breeding_profiles enable row level security");
                statement.executeUpdate("alter table public.champ_breeding_eggs enable row level security");
                statement.executeUpdate("""
                        do $$
                        begin
                            if to_regclass('public.player_profiles') is not null
                               and not exists (select 1 from pg_constraint where conname = 'fk_champ_breeding_profiles_profile' and conrelid = 'public.champ_breeding_profiles'::regclass) then
                                alter table public.champ_breeding_profiles
                                    add constraint fk_champ_breeding_profiles_profile
                                    foreign key (profile_id) references public.player_profiles(id) on delete cascade;
                            end if;
                            if to_regclass('public.player_profiles') is not null
                               and not exists (select 1 from pg_constraint where conname = 'fk_champ_breeding_eggs_profile' and conrelid = 'public.champ_breeding_eggs'::regclass) then
                                alter table public.champ_breeding_eggs
                                    add constraint fk_champ_breeding_eggs_profile
                                    foreign key (profile_id) references public.player_profiles(id) on delete cascade;
                            end if;
                        end $$
                        """);
            }
        });
    }

    public static CompletableFuture<Reservation> reserveAndRegister(UUID profileId,
                                                                    UUID eggUuid,
                                                                    UUID parentAUuid,
                                                                    UUID parentBUuid,
                                                                    String parentASpecies,
                                                                    String parentBSpecies,
                                                                    String offspringSpecies,
                                                                    int requiredSteps,
                                                                    int cooldownSeconds) {
        if (profileId == null || eggUuid == null) {
            return CompletableFuture.completedFuture(new Reservation(false, null, "No active profile."));
        }
        return schemaReady.thenCompose(ignored -> DatabaseManager.supplyAsync("reserve breeding egg", connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Instant nextEggAt = null;
                String reserveSql = """
                        insert into public.champ_breeding_profiles(profile_id, next_egg_at, total_eggs_created, updated_at)
                        values (?, now() + make_interval(secs => ?), 1, now())
                        on conflict (profile_id) do update
                        set next_egg_at = now() + make_interval(secs => ?),
                            total_eggs_created = champ_breeding_profiles.total_eggs_created + 1,
                            updated_at = now()
                        where champ_breeding_profiles.next_egg_at <= now()
                        returning next_egg_at
                        """;
                try (PreparedStatement ps = connection.prepareStatement(reserveSql)) {
                    ps.setObject(1, profileId);
                    ps.setInt(2, Math.max(0, cooldownSeconds));
                    ps.setInt(3, Math.max(0, cooldownSeconds));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Timestamp timestamp = rs.getTimestamp(1);
                            nextEggAt = timestamp == null ? Instant.now() : timestamp.toInstant();
                        }
                    }
                }

                if (nextEggAt == null) {
                    connection.rollback();
                    return new Reservation(false, currentCooldown(connection, profileId), "Breeding is still on cooldown.");
                }

                try (PreparedStatement ps = connection.prepareStatement("""
                        insert into public.champ_breeding_eggs(
                            egg_uuid, profile_id, parent_a_uuid, parent_b_uuid,
                            parent_a_species, parent_b_species, offspring_species, steps_required
                        ) values (?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (egg_uuid) do nothing
                        """)) {
                    ps.setObject(1, eggUuid);
                    ps.setObject(2, profileId);
                    ps.setObject(3, parentAUuid);
                    ps.setObject(4, parentBUuid);
                    ps.setString(5, parentASpecies);
                    ps.setString(6, parentBSpecies);
                    ps.setString(7, offspringSpecies);
                    ps.setInt(8, Math.max(1, requiredSteps));
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException("Duplicate breeding egg UUID reservation.");
                    }
                }
                connection.commit();
                return new Reservation(true, nextEggAt, "Egg reserved.");
            } catch (Throwable error) {
                try { connection.rollback(); } catch (Throwable rollbackError) {}
                throw error;
            } finally {
                try { connection.setAutoCommit(previousAutoCommit); } catch (Throwable autoCommitError) {}
            }
        }));
    }

    private static Instant currentCooldown(Connection connection, UUID profileId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("select next_egg_at from public.champ_breeding_profiles where profile_id = ?")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Instant.now();
                Timestamp timestamp = rs.getTimestamp(1);
                return timestamp == null ? Instant.now() : timestamp.toInstant();
            }
        }
    }

    public static void markHatchedAsync(UUID profileId, UUID eggUuid) {
        if (!DatabaseManager.isEnabled() || profileId == null || eggUuid == null) return;
        schemaReady.whenComplete((ignored, schemaError) -> {
            if (schemaError != null) {
                System.err.println("[ChampUtils][Breeding] Could not reconcile hatched Egg because the breeding schema is unavailable.");
                schemaError.printStackTrace();
                return;
            }
            DatabaseManager.executeAsync("mark breeding egg hatched", connection -> {
                boolean previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    int changed;
                    try (PreparedStatement ps = connection.prepareStatement("""
                            update public.champ_breeding_eggs
                            set status = 'HATCHED', hatched_at = now(), updated_at = now()
                            where egg_uuid = ? and profile_id = ? and status = 'INCUBATING'
                            """)) {
                        ps.setObject(1, eggUuid);
                        ps.setObject(2, profileId);
                        changed = ps.executeUpdate();
                    }
                    if (changed > 0) {
                        try (PreparedStatement ps = connection.prepareStatement("""
                                update public.champ_breeding_profiles
                                set total_eggs_hatched = total_eggs_hatched + 1, updated_at = now()
                                where profile_id = ?
                                """)) {
                            ps.setObject(1, profileId);
                            ps.executeUpdate();
                        }
                    }
                    connection.commit();
                } catch (Throwable error) {
                    try { connection.rollback(); } catch (Throwable ignoredRollback) {}
                    throw error;
                } finally {
                    try { connection.setAutoCommit(previousAutoCommit); } catch (Throwable ignoredRestore) {}
                }
            });
        });
    }

    public static void markLostAndRefundAsync(UUID profileId, UUID eggUuid) {
        if (!DatabaseManager.isEnabled() || profileId == null || eggUuid == null) return;
        schemaReady.whenComplete((ignored, schemaError) -> {
            if (schemaError != null) {
                System.err.println("[ChampUtils][Breeding] Could not refund an undelivered Egg because the breeding schema is unavailable.");
                schemaError.printStackTrace();
                return;
            }
            DatabaseManager.executeAsync("refund undelivered breeding egg", connection -> {
                boolean previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    int changed;
                    try (PreparedStatement ps = connection.prepareStatement("""
                            update public.champ_breeding_eggs
                            set status = 'LOST', updated_at = now()
                            where egg_uuid = ? and profile_id = ? and status = 'INCUBATING'
                            """)) {
                        ps.setObject(1, eggUuid);
                        ps.setObject(2, profileId);
                        changed = ps.executeUpdate();
                    }
                    if (changed > 0) {
                        try (PreparedStatement ps = connection.prepareStatement("""
                                update public.champ_breeding_profiles
                                set next_egg_at = now(),
                                    total_eggs_created = greatest(0, total_eggs_created - 1),
                                    updated_at = now()
                                where profile_id = ?
                                """)) {
                            ps.setObject(1, profileId);
                            ps.executeUpdate();
                        }
                    }
                    connection.commit();
                } catch (Throwable error) {
                    try { connection.rollback(); } catch (Throwable ignoredRollback) {}
                    throw error;
                } finally {
                    try { connection.setAutoCommit(previousAutoCommit); } catch (Throwable ignoredRestore) {}
                }
            });
        });
    }

}
