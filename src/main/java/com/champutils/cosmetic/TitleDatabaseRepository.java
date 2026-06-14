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
            s.executeUpdate("create table if not exists player_titles (profile_id uuid not null, title_id text not null, unlocked_at timestamptz not null default now(), primary key (profile_id, title_id))");
            s.executeUpdate("alter table player_titles add column if not exists unlocked_at timestamptz not null default now()");
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
        try (var ps = readyConnection().prepareStatement("insert into player_titles (profile_id, title_id) values (?, ?) on conflict (profile_id, title_id) do nothing")) {
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
        try (var ps = readyConnection().prepareStatement("select title_id from player_titles where profile_id = ? order by title_id")) {
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
}
