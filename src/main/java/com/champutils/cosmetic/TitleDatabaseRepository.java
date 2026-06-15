package com.champutils.cosmetic;

import com.champutils.database.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * SQL storage for title ownership only.
 *
 * Important performance rule:
 * - Do not call this from chat rendering, ticks, menus every frame, etc.
 * - SQL writes should only happen when a profile actually earns a new title.
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
            s.executeUpdate("create table if not exists profile_selected_title (profile_id uuid primary key, title_id text, updated_at timestamptz not null default now())");
            s.executeUpdate("alter table profile_selected_title add column if not exists title_id text");
            s.executeUpdate("alter table profile_selected_title add column if not exists updated_at timestamptz not null default now()");
        }
        schemaEnsured = true;
    }

    private static Connection readyConnection() throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        return connection;
    }

    public static boolean unlock(java.util.UUID profileId, String titleId) {
        if (!DatabaseManager.isEnabled() || profileId == null || titleId == null || titleId.isBlank()) return false;
        try (var ps = readyConnection().prepareStatement("insert into profile_titles (profile_id, title_id) values (?, ?) on conflict (profile_id, title_id) do nothing")) {
            ps.setObject(1, profileId);
            ps.setString(2, titleId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to unlock SQL title " + titleId + " for profile " + profileId);
            e.printStackTrace();
            return false;
        }
    }

    public static Set<String> unlocked(java.util.UUID profileId) {
        Set<String> out = new TreeSet<>();
        if (!DatabaseManager.isEnabled() || profileId == null) return out;
        try (var ps = readyConnection().prepareStatement("select title_id from profile_titles where profile_id = ? order by title_id")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load SQL titles for profile " + profileId);
            e.printStackTrace();
        }
        return out;
    }

    public static String selected(java.util.UUID profileId) {
        if (!DatabaseManager.isEnabled() || profileId == null) return "";
        try (var ps = readyConnection().prepareStatement("select title_id from profile_selected_title where profile_id = ?")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1) == null ? "" : rs.getString(1);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load selected SQL title for profile " + profileId);
            e.printStackTrace();
        }
        return "";
    }

    public static void select(java.util.UUID profileId, String titleId) {
        if (!DatabaseManager.isEnabled() || profileId == null) return;
        try (var ps = readyConnection().prepareStatement("insert into profile_selected_title (profile_id, title_id, updated_at) values (?, ?, now()) on conflict (profile_id) do update set title_id = excluded.title_id, updated_at = now()")) {
            ps.setObject(1, profileId);
            ps.setString(2, titleId == null ? "" : titleId);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save selected SQL title for profile " + profileId);
            e.printStackTrace();
        }
    }

}
