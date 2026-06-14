package com.champutils.worldfirst;

import com.champutils.database.DatabaseManager;

import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldFirstDatabaseRepository {
    private WorldFirstDatabaseRepository() {}

    public static void ensureSchema(java.sql.Connection c) throws Exception {
        try (java.sql.Statement s = c.createStatement()) {
            s.executeUpdate("create table if not exists world_first_claims (world_first_id text primary key, player_uuid uuid not null, player_name text not null default '', claimed_at timestamptz not null default now())");
            s.executeUpdate("alter table world_first_claims add column if not exists player_uuid uuid");
            s.executeUpdate("alter table world_first_claims add column if not exists player_name text not null default ''");
            s.executeUpdate("alter table world_first_claims add column if not exists claimed_at timestamptz not null default now()");
        }
    }

    private static java.sql.Connection readyConnection() throws Exception {
        java.sql.Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        return connection;
    }

    public static Map<String, WorldFirstManager.Claim> loadClaims() {
        Map<String, WorldFirstManager.Claim> out = new ConcurrentHashMap<>();
        if (!DatabaseManager.isEnabled()) return out;
        try (var ps = readyConnection().prepareStatement("select world_first_id, player_uuid, player_name, claimed_at from world_first_claims")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    WorldFirstManager.Claim claim = new WorldFirstManager.Claim();
                    claim.playerUuid = String.valueOf(rs.getObject("player_uuid"));
                    claim.playerName = rs.getString("player_name");
                    claim.claimedAt = String.valueOf(rs.getTimestamp("claimed_at").toInstant());
                    out.put(rs.getString("world_first_id"), claim);
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load SQL world first claims.");
            e.printStackTrace();
        }
        return out;
    }

    public static boolean claim(String id, java.util.UUID playerUuid, String playerName) {
        if (!DatabaseManager.isEnabled()) return false;
        try (var ps = readyConnection().prepareStatement("insert into world_first_claims (world_first_id, player_uuid, player_name, claimed_at) values (?, ?, ?, now()) on conflict (world_first_id) do nothing")) {
            ps.setString(1, id);
            ps.setObject(2, playerUuid);
            ps.setString(3, playerName == null ? "" : playerName);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save SQL world first claim " + id);
            e.printStackTrace();
            return false;
        }
    }
}
