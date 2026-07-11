package com.champutils.contracts;

import com.champutils.database.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.postgresql.util.PGobject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PlayerContractRepository {
    private static final Gson GSON = new Gson();

    private PlayerContractRepository() {}

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure player contracts schema", PlayerContractRepository::ensureSchema);
    }

    public static List<ContractSummary> fetchActive(String type, int limit) throws Exception {
        Connection connection = DatabaseManager.getConnection();
        List<ContractSummary> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "select id, owner_profile_id, owner_name, contract_type, title, reward_cents, criteria::text as criteria, " +
                        "completion_payload::text as completion_payload, completer_profile_id, completer_name, status, created_at, expires_at, completed_at, claimed_at " +
                        "from guild_player_contracts where status = 'ACTIVE' and expires_at > now() " +
                        (type == null || type.isBlank() ? "" : "and contract_type = ? ") +
                        "order by reward_cents desc, created_at asc limit ?"
        )) {
            int index = 1;
            if (type != null && !type.isBlank()) ps.setString(index++, type.toUpperCase(Locale.ROOT));
            ps.setInt(index, Math.max(1, Math.min(45, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(read(rs));
            }
        }
        return out;
    }

    public static List<ContractSummary> fetchOwnerContracts(UUID ownerProfileId, int limit) throws Exception {
        if (ownerProfileId == null) return List.of();
        Connection connection = DatabaseManager.getConnection();
        List<ContractSummary> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "select id, owner_profile_id, owner_name, contract_type, title, reward_cents, criteria::text as criteria, " +
                        "completion_payload::text as completion_payload, completer_profile_id, completer_name, status, created_at, expires_at, completed_at, claimed_at " +
                        "from guild_player_contracts where owner_profile_id = ? and status in ('ACTIVE','COMPLETED') " +
                        "order by case when status = 'COMPLETED' then 0 else 1 end, created_at desc limit ?"
        )) {
            ps.setObject(1, ownerProfileId);
            ps.setInt(2, Math.max(1, Math.min(45, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(read(rs));
            }
        }
        return out;
    }

    public static ContractSummary fetchActiveContract(UUID contractId) throws Exception {
        if (contractId == null) return null;
        Connection connection = DatabaseManager.getConnection();
        try (PreparedStatement ps = connection.prepareStatement(
                "select id, owner_profile_id, owner_name, contract_type, title, reward_cents, criteria::text as criteria, " +
                        "completion_payload::text as completion_payload, completer_profile_id, completer_name, status, created_at, expires_at, completed_at, claimed_at " +
                        "from guild_player_contracts where id = ? and status = 'ACTIVE' and expires_at > now() limit 1"
        )) {
            ps.setObject(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    public static UUID createContract(UUID ownerProfileId, String ownerName, String type, String title, long rewardCents, JsonObject criteria) throws Exception {
        if (ownerProfileId == null || type == null || title == null || rewardCents <= 0L) return null;
        Connection connection = DatabaseManager.getConnection();
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into guild_player_contracts (owner_profile_id, owner_name, contract_type, title, reward_cents, criteria, expires_at) " +
                        "values (?, ?, ?, ?, ?, ?, now() + interval '24 hours') returning id"
        )) {
            ps.setObject(1, ownerProfileId);
            ps.setString(2, safe(ownerName));
            ps.setString(3, type.toUpperCase(Locale.ROOT));
            ps.setString(4, safe(title));
            ps.setLong(5, rewardCents);
            ps.setObject(6, jsonb(criteria));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString(1)) : null;
            }
        }
    }

    public static boolean completeContract(UUID contractId, UUID completerProfileId, String completerName, JsonObject payload) throws Exception {
        if (contractId == null || completerProfileId == null || payload == null) return false;
        Connection connection = DatabaseManager.getConnection();
        boolean previousAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            ContractSummary locked = null;
            try (PreparedStatement ps = connection.prepareStatement(
                    "select id, owner_profile_id, owner_name, contract_type, title, reward_cents, criteria::text as criteria, " +
                            "completion_payload::text as completion_payload, completer_profile_id, completer_name, status, created_at, expires_at, completed_at, claimed_at " +
                            "from guild_player_contracts where id = ? and status = 'ACTIVE' and expires_at > now() for update"
            )) {
                ps.setObject(1, contractId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) locked = read(rs);
                }
            }
            if (locked == null || completerProfileId.equals(locked.ownerProfileId)) {
                connection.rollback();
                return false;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "update guild_player_contracts set status = 'COMPLETED', completion_payload = ?, completer_profile_id = ?, completer_name = ?, completed_at = now() " +
                            "where id = ? and status = 'ACTIVE'"
            )) {
                ps.setObject(1, jsonb(payload));
                ps.setObject(2, completerProfileId);
                ps.setString(3, safe(completerName));
                ps.setObject(4, contractId);
                if (ps.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            connection.commit();
            return true;
        } catch (Exception e) {
            try { connection.rollback(); } catch (Exception ignored) {}
            throw e;
        } finally {
            try { connection.setAutoCommit(previousAutoCommit); } catch (Exception ignored) {}
        }
    }

    public static ContractSummary fetchOldestCompletedForOwner(UUID ownerProfileId) throws Exception {
        if (ownerProfileId == null) return null;
        Connection connection = DatabaseManager.getConnection();
        try (PreparedStatement ps = connection.prepareStatement(
                "select id, owner_profile_id, owner_name, contract_type, title, reward_cents, criteria::text as criteria, " +
                        "completion_payload::text as completion_payload, completer_profile_id, completer_name, status, created_at, expires_at, completed_at, claimed_at " +
                        "from guild_player_contracts where owner_profile_id = ? and status = 'COMPLETED' order by completed_at asc limit 1"
        )) {
            ps.setObject(1, ownerProfileId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    public static boolean markClaimed(UUID contractId, UUID ownerProfileId) throws Exception {
        if (contractId == null || ownerProfileId == null) return false;
        Connection connection = DatabaseManager.getConnection();
        try (PreparedStatement ps = connection.prepareStatement(
                "update guild_player_contracts set status = 'CLAIMED', claimed_at = now() where id = ? and owner_profile_id = ? and status = 'COMPLETED'"
        )) {
            ps.setObject(1, contractId);
            ps.setObject(2, ownerProfileId);
            return ps.executeUpdate() == 1;
        }
    }

    public static ContractSummary cancelActiveContract(UUID contractId, UUID ownerProfileId) throws Exception {
        if (contractId == null || ownerProfileId == null) return null;
        Connection connection = DatabaseManager.getConnection();
        boolean previousAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            ContractSummary locked = null;
            try (PreparedStatement ps = connection.prepareStatement(
                    "select id, owner_profile_id, owner_name, contract_type, title, reward_cents, criteria::text as criteria, " +
                            "completion_payload::text as completion_payload, completer_profile_id, completer_name, status, created_at, expires_at, completed_at, claimed_at " +
                            "from guild_player_contracts where id = ? and owner_profile_id = ? and status = 'ACTIVE' for update"
            )) {
                ps.setObject(1, contractId);
                ps.setObject(2, ownerProfileId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) locked = read(rs);
                }
            }
            if (locked == null) {
                connection.rollback();
                return null;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "update guild_player_contracts set status = 'CANCELLED', refunded_at = now() where id = ? and owner_profile_id = ? and status = 'ACTIVE'"
            )) {
                ps.setObject(1, contractId);
                ps.setObject(2, ownerProfileId);
                if (ps.executeUpdate() != 1) {
                    connection.rollback();
                    return null;
                }
            }
            connection.commit();
            return locked;
        } catch (Exception e) {
            try { connection.rollback(); } catch (Exception ignored) {}
            throw e;
        } finally {
            try { connection.setAutoCommit(previousAutoCommit); } catch (Exception ignored) {}
        }
    }

    private static synchronized void ensureSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create extension if not exists pgcrypto");
            statement.executeUpdate("create table if not exists guild_player_contracts (" +
                    "id uuid primary key default gen_random_uuid(), " +
                    "owner_profile_id uuid not null references player_profiles(id) on delete cascade, " +
                    "owner_name text not null default '', " +
                    "contract_type text not null check (contract_type in ('ITEM','POKEMON')), " +
                    "title text not null default 'Player Contract', " +
                    "reward_cents bigint not null check (reward_cents > 0), " +
                    "criteria jsonb not null default '{}'::jsonb, completion_payload jsonb, " +
                    "completer_profile_id uuid references player_profiles(id) on delete set null, " +
                    "completer_name text not null default '', " +
                    "status text not null default 'ACTIVE', created_at timestamptz not null default now(), " +
                    "expires_at timestamptz not null default (now() + interval '24 hours'), " +
                    "completed_at timestamptz, claimed_at timestamptz, refunded_at timestamptz)");
            statement.executeUpdate("alter table guild_player_contracts add column if not exists completion_payload jsonb");
            statement.executeUpdate("alter table guild_player_contracts add column if not exists completer_profile_id uuid");
            statement.executeUpdate("alter table guild_player_contracts add column if not exists completer_name text not null default ''");
            statement.executeUpdate("alter table guild_player_contracts add column if not exists claimed_at timestamptz");
            statement.executeUpdate("alter table guild_player_contracts add column if not exists refunded_at timestamptz");
            statement.executeUpdate("create index if not exists idx_guild_player_contracts_active on guild_player_contracts(status, expires_at)");
            statement.executeUpdate("create index if not exists idx_guild_player_contracts_owner_active on guild_player_contracts(owner_profile_id, status)");
            statement.executeUpdate("create index if not exists idx_guild_player_contracts_completed_owner on guild_player_contracts(owner_profile_id, completed_at) where status = 'COMPLETED'");
        }
    }

    private static ContractSummary read(ResultSet rs) throws Exception {
        ContractSummary c = new ContractSummary();
        c.id = UUID.fromString(rs.getString("id"));
        c.ownerProfileId = (UUID) rs.getObject("owner_profile_id");
        c.ownerName = rs.getString("owner_name");
        c.type = rs.getString("contract_type");
        c.title = rs.getString("title");
        c.rewardCents = rs.getLong("reward_cents");
        c.criteria = parse(rs.getString("criteria"));
        c.completionPayload = parse(rs.getString("completion_payload"));
        c.completerProfileId = (UUID) rs.getObject("completer_profile_id");
        c.completerName = rs.getString("completer_name");
        c.status = rs.getString("status");
        c.createdAt = rs.getObject("created_at", OffsetDateTime.class);
        c.expiresAt = rs.getObject("expires_at", OffsetDateTime.class);
        c.completedAt = rs.getObject("completed_at", OffsetDateTime.class);
        c.claimedAt = rs.getObject("claimed_at", OffsetDateTime.class);
        return c;
    }

    private static JsonObject parse(String raw) {
        if (raw == null || raw.isBlank()) return new JsonObject();
        try {
            JsonObject object = GSON.fromJson(raw, JsonObject.class);
            return object == null ? new JsonObject() : object;
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private static PGobject jsonb(JsonObject object) throws Exception {
        PGobject pg = new PGobject();
        pg.setType("jsonb");
        pg.setValue(GSON.toJson(object == null ? new JsonObject() : object));
        return pg;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class ContractSummary {
        public UUID id;
        public UUID ownerProfileId;
        public String ownerName;
        public String type;
        public String title;
        public long rewardCents;
        public JsonObject criteria = new JsonObject();
        public JsonObject completionPayload = new JsonObject();
        public UUID completerProfileId;
        public String completerName;
        public String status;
        public OffsetDateTime createdAt;
        public OffsetDateTime expiresAt;
        public OffsetDateTime completedAt;
        public OffsetDateTime claimedAt;
    }
}
