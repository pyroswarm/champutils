package com.champutils.cosmetic;

import com.champutils.database.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
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

    public static CompletableFuture<TitleSnapshot> loadSnapshotAsync(UUID profileId) {
        if (!DatabaseManager.isEnabled() || profileId == null) {
            return CompletableFuture.completedFuture(new TitleSnapshot(new TreeSet<>(), ""));
        }
        final java.util.concurrent.atomic.AtomicReference<TitleSnapshot> out = new java.util.concurrent.atomic.AtomicReference<>(new TitleSnapshot(new TreeSet<>(), ""));
        return DatabaseManager.runAsync("load profile title snapshot", connection -> out.set(loadSnapshot(connection, profileId)))
                .thenApply(ignored -> out.get())
                .exceptionally(error -> {
                    System.err.println("[ChampUtils] Failed to load SQL title snapshot for profile " + profileId);
                    error.printStackTrace();
                    return new TitleSnapshot(new TreeSet<>(), "");
                });
    }

    public static TitleSnapshot loadSnapshot(Connection connection, UUID profileId) throws Exception {
        Set<String> titles = new TreeSet<>();
        String selected = "";
        if (connection == null || profileId == null) return new TitleSnapshot(titles, selected);
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
        return new TitleSnapshot(titles, selected);
    }

    public record TitleSnapshot(Set<String> unlocked, String selected) {}
}
