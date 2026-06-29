package com.champutils.cosmetic;

import com.champutils.database.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQL storage for title ownership.
 *
 * Performance rule:
 * - Reads used by chat/menu/title rendering must come from TitleManager cache.
 * - Writes are queued async and flushed by the global database flush on disconnect/restart/stop.
 */
public final class TitleDatabaseRepository {
    private static volatile boolean schemaEnsured = false;

    private TitleDatabaseRepository() {}

    public static synchronized void ensureSchema(Connection c) throws Exception {
        if (schemaEnsured) return;
        try (java.sql.Statement s = c.createStatement()) {
            s.executeUpdate("create table if not exists profile_titles (profile_id uuid not null, title_id text not null, display text not null default '', unlocked_at timestamptz not null default now(), primary key (profile_id, title_id))");
            s.executeUpdate("alter table profile_titles add column if not exists display text not null default ''");
            s.executeUpdate("alter table profile_titles add column if not exists unlocked_at timestamptz not null default now()");
            s.executeUpdate("create index if not exists idx_profile_titles_profile on profile_titles(profile_id)");

            s.executeUpdate("create table if not exists profile_selected_title (profile_id uuid primary key, title_id text, updated_at timestamptz not null default now())");
            s.executeUpdate("alter table profile_selected_title add column if not exists title_id text");
            s.executeUpdate("alter table profile_selected_title add column if not exists updated_at timestamptz not null default now()");

            s.executeUpdate("create table if not exists profile_sub_titles (profile_id uuid not null, title_id text not null, slot integer not null default 0, updated_at timestamptz not null default now(), primary key (profile_id, title_id))");
            s.executeUpdate("alter table profile_sub_titles add column if not exists slot integer not null default 0");
            s.executeUpdate("alter table profile_sub_titles add column if not exists updated_at timestamptz not null default now()");
            s.executeUpdate("create index if not exists idx_profile_sub_titles_profile on profile_sub_titles(profile_id)");

            s.executeUpdate("create table if not exists account_titles (account_uuid uuid not null, title_id text not null, display text not null default '', unlocked_at timestamptz not null default now(), primary key (account_uuid, title_id))");
            s.executeUpdate("alter table account_titles add column if not exists display text not null default ''");
            s.executeUpdate("alter table account_titles add column if not exists unlocked_at timestamptz not null default now()");
            s.executeUpdate("create index if not exists idx_account_titles_account on account_titles(account_uuid)");
        }
        schemaEnsured = true;
    }

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure title schema", TitleDatabaseRepository::ensureSchema);
    }

    public static CompletableFuture<Boolean> unlockAsync(UUID profileId, String titleId) {
        if (!DatabaseManager.isEnabled() || profileId == null || titleId == null || titleId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        final String normalized = titleId.trim();
        return DatabaseManager.runAsync("unlock profile title", connection -> {
            ensureSchema(connection);
            try (var ps = connection.prepareStatement("insert into profile_titles (profile_id, title_id) values (?, ?) on conflict (profile_id, title_id) do nothing")) {
                ps.setObject(1, profileId);
                ps.setString(2, normalized);
                ps.executeUpdate();
            }
        }).thenApply(ignored -> true).exceptionally(error -> {
            System.err.println("[ChampUtils] Failed to unlock SQL title " + normalized + " for profile " + profileId);
            error.printStackTrace();
            return false;
        });
    }

    public static CompletableFuture<Boolean> unlockAccountAsync(UUID accountUuid, String titleId) {
        if (!DatabaseManager.isEnabled() || accountUuid == null || titleId == null || titleId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        final String normalized = titleId.trim();
        return DatabaseManager.runAsync("unlock account title", connection -> {
            ensureSchema(connection);
            try (var ps = connection.prepareStatement("insert into account_titles (account_uuid, title_id) values (?, ?) on conflict (account_uuid, title_id) do nothing")) {
                ps.setObject(1, accountUuid);
                ps.setString(2, normalized);
                ps.executeUpdate();
            }
        }).thenApply(ignored -> true).exceptionally(error -> {
            System.err.println("[ChampUtils] Failed to unlock SQL account title " + normalized + " for account " + accountUuid);
            error.printStackTrace();
            return false;
        });
    }

    public static void selectAsync(UUID profileId, String titleId) {
        if (!DatabaseManager.isEnabled() || profileId == null) return;
        DatabaseManager.executeCoalescedAsync("profile-selected-title:" + profileId, "save selected profile title", connection -> {
            ensureSchema(connection);
            try (var ps = connection.prepareStatement("insert into profile_selected_title (profile_id, title_id, updated_at) values (?, ?, now()) on conflict (profile_id) do update set title_id = excluded.title_id, updated_at = now()")) {
                ps.setObject(1, profileId);
                ps.setString(2, titleId == null ? "" : titleId);
                ps.executeUpdate();
            }
        });
    }

    public static void saveSubtitlesAsync(UUID profileId, Set<String> titleIds) {
        if (!DatabaseManager.isEnabled() || profileId == null) return;
        Set<String> snapshot = new LinkedHashSet<>();
        if (titleIds != null) {
            for (String titleId : titleIds) {
                if (titleId != null && !titleId.isBlank()) snapshot.add(titleId.trim());
                if (snapshot.size() >= 3) break;
            }
        }
        DatabaseManager.executeCoalescedAsync("profile-subtitles:" + profileId, "save profile hidden subtitles", connection -> {
            ensureSchema(connection);
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (var delete = connection.prepareStatement("delete from profile_sub_titles where profile_id = ?")) {
                    delete.setObject(1, profileId);
                    delete.executeUpdate();
                }
                int slot = 0;
                try (var insert = connection.prepareStatement("insert into profile_sub_titles (profile_id, title_id, slot, updated_at) values (?, ?, ?, now()) on conflict (profile_id, title_id) do update set slot = excluded.slot, updated_at = now()")) {
                    for (String titleId : snapshot) {
                        insert.setObject(1, profileId);
                        insert.setString(2, titleId);
                        insert.setInt(3, slot++);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            }
            catch (Exception e) {
                connection.rollback();
                throw e;
            }
            finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        });
    }

    public static CompletableFuture<TitleSnapshot> loadSnapshotAsync(UUID profileId) {
        if (!DatabaseManager.isEnabled() || profileId == null) {
            return CompletableFuture.completedFuture(new TitleSnapshot(new TreeSet<>(), "", new LinkedHashSet<>()));
        }
        final java.util.concurrent.atomic.AtomicReference<TitleSnapshot> out = new java.util.concurrent.atomic.AtomicReference<>(new TitleSnapshot(new TreeSet<>(), "", new LinkedHashSet<>()));
        return DatabaseManager.runAsync("load profile title snapshot", connection -> out.set(loadSnapshot(connection, profileId)))
                .thenApply(ignored -> out.get())
                .exceptionally(error -> {
                    System.err.println("[ChampUtils] Failed to load SQL title snapshot for profile " + profileId);
                    error.printStackTrace();
                    return new TitleSnapshot(new TreeSet<>(), "", new LinkedHashSet<>());
                });
    }

    public static CompletableFuture<Set<String>> loadAccountTitlesAsync(UUID accountUuid) {
        if (!DatabaseManager.isEnabled() || accountUuid == null) {
            return CompletableFuture.completedFuture(new TreeSet<>());
        }
        final java.util.concurrent.atomic.AtomicReference<Set<String>> out = new java.util.concurrent.atomic.AtomicReference<>(new TreeSet<>());
        return DatabaseManager.runAsync("load account title snapshot", connection -> out.set(loadAccountTitles(connection, accountUuid)))
                .thenApply(ignored -> out.get())
                .exceptionally(error -> {
                    System.err.println("[ChampUtils] Failed to load SQL account titles for account " + accountUuid);
                    error.printStackTrace();
                    return new TreeSet<>();
                });
    }

    public static Set<String> loadAccountTitles(Connection connection, UUID accountUuid) throws Exception {
        Set<String> titles = new TreeSet<>();
        if (connection == null || accountUuid == null) return titles;
        ensureSchema(connection);
        try (var ps = connection.prepareStatement("select title_id from account_titles where account_uuid = ? order by title_id")) {
            ps.setObject(1, accountUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) titles.add(rs.getString(1));
            }
        }
        return titles;
    }

    public static TitleSnapshot loadSnapshot(Connection connection, UUID profileId) throws Exception {
        Set<String> titles = new TreeSet<>();
        Set<String> subtitles = new LinkedHashSet<>();
        String selected = "";
        if (connection == null || profileId == null) return new TitleSnapshot(titles, selected, subtitles);
        ensureSchema(connection);
        try (var ps = connection.prepareStatement("select title_id from profile_titles where profile_id = ? order by title_id")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) titles.add(rs.getString(1));
            }
        }
        try (var ps = connection.prepareStatement("select title_id from profile_selected_title where profile_id = ?")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) selected = rs.getString(1) == null ? "" : rs.getString(1);
            }
        }
        try (var ps = connection.prepareStatement("select title_id from profile_sub_titles where profile_id = ? order by slot, title_id")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && subtitles.size() < 3) {
                    String titleId = rs.getString(1);
                    if (titleId != null && !titleId.isBlank() && !titleId.equals(selected) && titles.contains(titleId)) {
                        subtitles.add(titleId);
                    }
                }
            }
        }
        return new TitleSnapshot(titles, selected, subtitles);
    }

    public record TitleSnapshot(Set<String> unlocked, String selected, Set<String> subtitles) {}
}
