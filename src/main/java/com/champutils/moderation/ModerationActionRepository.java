package com.champutils.moderation;

import com.champutils.database.DatabaseManager;
import com.champutils.network.NetworkServerConfig;
import net.minecraft.server.level.ServerPlayer;

import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ModerationActionRepository {
    private ModerationActionRepository() {}

    public static void ensureSchemaAsync() {
        DatabaseManager.executeAsync("moderation schema", connection -> ensureSchema(connection));
    }

    private static void ensureSchema(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("create table if not exists moderation_actions (" +
                    "id uuid primary key, " +
                    "profile_id uuid null, " +
                    "account_uuid uuid null, " +
                    "moderator_uuid uuid null, " +
                    "moderator_name text not null default 'Console', " +
                    "target_name text not null, " +
                    "action_type text not null, " +
                    "reason text not null default '', " +
                    "issued_at timestamptz not null default now(), " +
                    "expires_at timestamptz null, " +
                    "revoked_at timestamptz null, " +
                    "revoked_by uuid null, " +
                    "server_name text not null default '', " +
                    "active boolean not null default true, " +
                    "metadata jsonb not null default '{}'::jsonb" +
                    ")");
            s.executeUpdate("alter table moderation_actions add column if not exists profile_id uuid null");
            s.executeUpdate("alter table moderation_actions add column if not exists account_uuid uuid null");
            s.executeUpdate("alter table moderation_actions add column if not exists moderator_uuid uuid null");
            s.executeUpdate("alter table moderation_actions add column if not exists moderator_name text not null default 'Console'");
            s.executeUpdate("alter table moderation_actions add column if not exists target_name text not null default ''");
            s.executeUpdate("alter table moderation_actions add column if not exists action_type text not null default 'WARN'");
            s.executeUpdate("alter table moderation_actions add column if not exists reason text not null default ''");
            s.executeUpdate("alter table moderation_actions add column if not exists issued_at timestamptz not null default now()");
            s.executeUpdate("alter table moderation_actions add column if not exists expires_at timestamptz null");
            s.executeUpdate("alter table moderation_actions add column if not exists revoked_at timestamptz null");
            s.executeUpdate("alter table moderation_actions add column if not exists revoked_by uuid null");
            s.executeUpdate("alter table moderation_actions add column if not exists server_name text not null default ''");
            s.executeUpdate("alter table moderation_actions add column if not exists active boolean not null default true");
            s.executeUpdate("alter table moderation_actions add column if not exists metadata jsonb not null default '{}'::jsonb");
            s.executeUpdate("create index if not exists idx_moderation_actions_account on moderation_actions(account_uuid)");
            s.executeUpdate("create index if not exists idx_moderation_actions_profile on moderation_actions(profile_id)");
            s.executeUpdate("create index if not exists idx_moderation_actions_target_lower on moderation_actions(lower(target_name))");
            s.executeUpdate("create index if not exists idx_moderation_actions_active_type on moderation_actions(action_type, active, expires_at)");
            s.executeUpdate("create index if not exists idx_moderation_actions_active_account_type on moderation_actions(account_uuid, action_type, issued_at desc) where active = true and revoked_at is null");
            s.executeUpdate("create index if not exists idx_moderation_actions_active_target_type on moderation_actions(lower(target_name), action_type, issued_at desc) where active = true and revoked_at is null");
            s.executeUpdate("create index if not exists idx_moderation_actions_warn_daily_account on moderation_actions(account_uuid, issued_at desc) where action_type = 'WARN'");
            s.executeUpdate("create index if not exists idx_moderation_actions_warn_daily_target on moderation_actions(lower(target_name), issued_at desc) where action_type = 'WARN'");
            s.executeUpdate("create index if not exists idx_moderation_actions_issued on moderation_actions(issued_at desc)");
        }
    }

    public static UUID insert(ActionDraft draft) {
        UUID id = UUID.randomUUID();
        if (draft == null) return id;
        DatabaseManager.executeAsync("insert moderation action", connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into moderation_actions(id, profile_id, account_uuid, moderator_uuid, moderator_name, target_name, action_type, reason, expires_at, server_name, active, metadata) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)")) {
                ps.setObject(1, id);
                ps.setObject(2, draft.profileId);
                ps.setObject(3, draft.accountUuid);
                ps.setObject(4, draft.moderatorUuid);
                ps.setString(5, safe(draft.moderatorName, "Console"));
                ps.setString(6, safe(draft.targetName, "Unknown"));
                ps.setString(7, draft.actionType.name());
                ps.setString(8, safe(draft.reason, ""));
                if (draft.expiresAt == null) ps.setNull(9, Types.TIMESTAMP_WITH_TIMEZONE);
                else ps.setObject(9, OffsetDateTime.ofInstant(draft.expiresAt, ZoneOffset.UTC));
                ps.setString(10, NetworkServerConfig.serverId());
                ps.setBoolean(11, draft.active);
                ps.setString(12, draft.metadataJson == null || draft.metadataJson.isBlank() ? "{}" : draft.metadataJson);
                ps.executeUpdate();
            }
        });
        return id;
    }

    public static ActivePunishment findActive(UUID accountUuid, String targetName, ActionType type) {
        expireOld(type);
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            String name = targetName == null ? "" : targetName.toLowerCase(Locale.ROOT);
            try (PreparedStatement ps = connection.prepareStatement(
                    "select id, reason, expires_at, moderator_name, target_name from moderation_actions " +
                            "where action_type = ? and active = true and revoked_at is null " +
                            "and (expires_at is null or expires_at > now()) " +
                            "and ((?::uuid is not null and account_uuid = ?::uuid) or lower(target_name) = ?) " +
                            "order by issued_at desc limit 1")) {
                ps.setString(1, type.name());
                ps.setObject(2, accountUuid);
                ps.setObject(3, accountUuid);
                ps.setString(4, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        OffsetDateTime expires = rs.getObject("expires_at", OffsetDateTime.class);
                        return new ActivePunishment(
                                (UUID) rs.getObject("id"),
                                rs.getString("reason"),
                                expires == null ? null : expires.toInstant(),
                                rs.getString("moderator_name"),
                                rs.getString("target_name")
                        );
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to query active " + type + ": " + e.getMessage());
        }
        return null;
    }

    public static boolean revokeActive(UUID accountUuid, String targetName, ActionType activeType, UUID revokedBy, String reason, String moderatorName) {
        boolean changed = false;
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            String name = targetName == null ? "" : targetName.toLowerCase(Locale.ROOT);
            try (PreparedStatement ps = connection.prepareStatement(
                    "update moderation_actions set active = false, revoked_at = now(), revoked_by = ? " +
                            "where action_type = ? and active = true and revoked_at is null " +
                            "and ((?::uuid is not null and account_uuid = ?::uuid) or lower(target_name) = ?)")) {
                ps.setObject(1, revokedBy);
                ps.setString(2, activeType.name());
                ps.setObject(3, accountUuid);
                ps.setObject(4, accountUuid);
                ps.setString(5, name);
                changed = ps.executeUpdate() > 0;
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to revoke active " + activeType + ": " + e.getMessage());
        }
        return changed;
    }

    public static void revokeActiveAsync(UUID accountUuid, String targetName, ActionType activeType, UUID revokedBy, String reason, String moderatorName) {
        DatabaseManager.executeAsync("revoke active moderation action " + activeType, connection -> {
            ensureSchema(connection);
            String name = targetName == null ? "" : targetName.toLowerCase(Locale.ROOT);
            try (PreparedStatement ps = connection.prepareStatement(
                    "update moderation_actions set active = false, revoked_at = now(), revoked_by = ? " +
                            "where action_type = ? and active = true and revoked_at is null " +
                            "and ((?::uuid is not null and account_uuid = ?::uuid) or lower(target_name) = ?)")) {
                ps.setObject(1, revokedBy);
                ps.setString(2, activeType.name());
                ps.setObject(3, accountUuid);
                ps.setObject(4, accountUuid);
                ps.setString(5, name);
                ps.executeUpdate();
            }
        });
    }

    public static List<ActionRecord> history(UUID accountUuid, String targetName) {
        List<ActionRecord> rows = new ArrayList<>();
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            String name = targetName == null ? "" : targetName.toLowerCase(Locale.ROOT);
            try (PreparedStatement ps = connection.prepareStatement(
                    "select id, action_type, moderator_name, target_name, reason, issued_at, expires_at, revoked_at, active " +
                            "from moderation_actions where ((?::uuid is not null and account_uuid = ?::uuid) or lower(target_name) = ?) " +
                            "order by issued_at desc")) {
                ps.setObject(1, accountUuid);
                ps.setObject(2, accountUuid);
                ps.setString(3, name);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) rows.add(read(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load moderation history: " + e.getMessage());
        }
        return rows;
    }

    public static int warningCount(UUID accountUuid, String targetName) {
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            String name = targetName == null ? "" : targetName.toLowerCase(Locale.ROOT);
            try (PreparedStatement ps = connection.prepareStatement(
                    "select count(*) from moderation_actions where action_type = 'WARN' and ((?::uuid is not null and account_uuid = ?::uuid) or lower(target_name) = ?)")) {
                ps.setObject(1, accountUuid);
                ps.setObject(2, accountUuid);
                ps.setString(3, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to count warnings: " + e.getMessage());
            return 0;
        }
    }

    public static int warningCountSince(UUID accountUuid, String targetName, Instant since) {
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            String name = targetName == null ? "" : targetName.toLowerCase(Locale.ROOT);
            Instant floor = since == null ? Instant.EPOCH : since;
            try (PreparedStatement ps = connection.prepareStatement(
                    "select count(*) from moderation_actions where action_type = 'WARN' and issued_at >= ? " +
                            "and ((?::uuid is not null and account_uuid = ?::uuid) or lower(target_name) = ?)")) {
                ps.setObject(1, OffsetDateTime.ofInstant(floor, ZoneOffset.UTC));
                ps.setObject(2, accountUuid);
                ps.setObject(3, accountUuid);
                ps.setString(4, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to count daily warnings: " + e.getMessage());
            return 0;
        }
    }

    private static void expireOld(ActionType type) {
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "update moderation_actions set active = false where action_type = ? and active = true and expires_at is not null and expires_at <= now()")) {
                ps.setString(1, type.name());
                ps.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    private static ActionRecord read(ResultSet rs) throws SQLException {
        OffsetDateTime issued = rs.getObject("issued_at", OffsetDateTime.class);
        OffsetDateTime expires = rs.getObject("expires_at", OffsetDateTime.class);
        OffsetDateTime revoked = rs.getObject("revoked_at", OffsetDateTime.class);
        return new ActionRecord(
                (UUID) rs.getObject("id"),
                ActionType.valueOf(rs.getString("action_type")),
                rs.getString("moderator_name"),
                rs.getString("target_name"),
                rs.getString("reason"),
                issued == null ? null : issued.toInstant(),
                expires == null ? null : expires.toInstant(),
                revoked == null ? null : revoked.toInstant(),
                rs.getBoolean("active")
        );
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public enum ActionType { WARN, KICK, MUTE, BAN, UNMUTE, UNBAN }

    public static final class ActionDraft {
        public UUID profileId;
        public UUID accountUuid;
        public UUID moderatorUuid;
        public String moderatorName;
        public String targetName;
        public ActionType actionType;
        public String reason;
        public Instant expiresAt;
        public boolean active = true;
        public String metadataJson = "{}";
    }

    public record ActivePunishment(UUID id, String reason, Instant expiresAt, String moderatorName, String targetName) {}
    public record ActionRecord(UUID id, ActionType actionType, String moderatorName, String targetName, String reason, Instant issuedAt, Instant expiresAt, Instant revokedAt, boolean active) {}

    public static ActionDraft draft(ServerPlayer moderator, ServerPlayer target, ActionType type, String reason) {
        ActionDraft d = new ActionDraft();
        d.profileId = com.champutils.profile.PlayerProfileManager.activeProfileId(target);
        d.accountUuid = target.getUUID();
        d.moderatorUuid = moderator == null ? null : moderator.getUUID();
        d.moderatorName = moderator == null ? "Console" : moderator.getGameProfile().getName();
        d.targetName = target.getGameProfile().getName();
        d.actionType = type;
        d.reason = reason;
        return d;
    }

    public static ActionDraft draftOffline(ServerPlayer moderator, String targetName, UUID accountUuid, ActionType type, String reason) {
        ActionDraft d = new ActionDraft();
        d.profileId = null;
        d.accountUuid = accountUuid;
        d.moderatorUuid = moderator == null ? null : moderator.getUUID();
        d.moderatorName = moderator == null ? "Console" : moderator.getGameProfile().getName();
        d.targetName = targetName == null ? "Unknown" : targetName;
        d.actionType = type;
        d.reason = reason;
        return d;
    }
}
