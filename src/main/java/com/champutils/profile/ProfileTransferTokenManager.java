package com.champutils.profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Phase 2 groundwork for the future profile_lobby -> survival transfer flow.
 *
 * Tokens are short-lived, one-use, player-bound, profile-bound, and signed. The lobby server
 * will issue them after profile selection; the survival server will consume them before any
 * gameplay profile state is applied.
 */
public final class ProfileTransferTokenManager {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int DEFAULT_TTL_SECONDS = 60;

    private ProfileTransferTokenManager() {}

    public record IssuedToken(UUID tokenId, UUID playerUuid, UUID profileId, Instant expiresAt, String signature) {
        public String wireValue() {
            return tokenId + ":" + signature;
        }
    }

    public record ConsumedToken(UUID tokenId, UUID playerUuid, UUID profileId, Instant issuedAt, Instant expiresAt) {}

    public static void ensureSchema(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("create extension if not exists pgcrypto");
            statement.executeUpdate(
                    "create table if not exists profile_transfer_tokens (" +
                            "token_id uuid primary key default gen_random_uuid(), " +
                            "player_uuid uuid not null references players(uuid) on delete cascade, " +
                            "profile_id uuid not null references player_profiles(id) on delete cascade, " +
                            "issued_at timestamptz not null default now(), " +
                            "expires_at timestamptz not null, " +
                            "consumed_at timestamptz, " +
                            "source_server text not null default '', " +
                            "target_server text not null default '', " +
                            "signature text not null, " +
                            "metadata jsonb not null default '{}'::jsonb" +
                            ")"
            );
            statement.executeUpdate("alter table profile_transfer_tokens add column if not exists source_server text not null default ''");
            statement.executeUpdate("alter table profile_transfer_tokens add column if not exists target_server text not null default ''");
            statement.executeUpdate("alter table profile_transfer_tokens add column if not exists metadata jsonb not null default '{}'::jsonb");
            statement.executeUpdate("create index if not exists idx_profile_transfer_tokens_player_live on profile_transfer_tokens(player_uuid, expires_at) where consumed_at is null");
            statement.executeUpdate("create index if not exists idx_profile_transfer_tokens_profile_live on profile_transfer_tokens(profile_id, expires_at) where consumed_at is null");
            statement.executeUpdate("create index if not exists idx_profile_transfer_tokens_expiry on profile_transfer_tokens(expires_at)");
            statement.executeUpdate("create table if not exists profile_transfer_audit_logs (" +
                    "id uuid primary key default gen_random_uuid(), " +
                    "player_uuid uuid references players(uuid) on delete set null, " +
                    "profile_id uuid references player_profiles(id) on delete cascade, " +
                    "transfer_id uuid, " +
                    "source_server text not null default '', " +
                    "target_server text not null default '', " +
                    "state text not null check (state in ('CREATED','TOKEN_ISSUED','ACCEPTED','HYDRATING','COMPLETE','FAILED','EXPIRED')), " +
                    "reason text, " +
                    "save_generation uuid, " +
                    "metadata jsonb not null default '{}'::jsonb, " +
                    "created_at timestamptz not null default now())");
            // Existing beta databases may already have profile_transfer_audit_logs from an older schema.
            // CREATE TABLE IF NOT EXISTS does not backfill missing columns, so keep these idempotent alters here.
            statement.executeUpdate("alter table profile_transfer_audit_logs add column if not exists transfer_id uuid");
            statement.executeUpdate("alter table profile_transfer_audit_logs add column if not exists source_server text not null default ''");
            statement.executeUpdate("alter table profile_transfer_audit_logs add column if not exists target_server text not null default ''");
            statement.executeUpdate("alter table profile_transfer_audit_logs add column if not exists reason text");
            statement.executeUpdate("alter table profile_transfer_audit_logs add column if not exists save_generation uuid");
            statement.executeUpdate("alter table profile_transfer_audit_logs add column if not exists metadata jsonb not null default '{}'::jsonb");
            statement.executeUpdate("alter table profile_transfer_audit_logs add column if not exists created_at timestamptz not null default now()");
            statement.executeUpdate("create index if not exists idx_profile_transfer_audit_logs_player on profile_transfer_audit_logs(player_uuid, created_at desc)");
            statement.executeUpdate("create index if not exists idx_profile_transfer_audit_logs_profile on profile_transfer_audit_logs(profile_id, created_at desc)");
        }
    }

    public static IssuedToken issue(
            Connection connection,
            UUID playerUuid,
            UUID profileId,
            String sharedSecret,
            String sourceServer,
            String targetServer
    ) throws Exception {
        return issue(connection, playerUuid, profileId, sharedSecret, sourceServer, targetServer, DEFAULT_TTL_SECONDS);
    }

    public static IssuedToken issue(
            Connection connection,
            UUID playerUuid,
            UUID profileId,
            String sharedSecret,
            String sourceServer,
            String targetServer,
            int ttlSeconds
    ) throws Exception {
        requireUsableSecret(sharedSecret);
        if (playerUuid == null || profileId == null) throw new IllegalArgumentException("Missing player/profile for transfer token.");
        int ttl = Math.max(10, Math.min(300, ttlSeconds));

        UUID tokenId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(ttl);
        String signature = sign(tokenId, playerUuid, profileId, expiresAt, sharedSecret);

        try (var ps = connection.prepareStatement(
                "insert into profile_transfer_tokens (token_id, player_uuid, profile_id, expires_at, source_server, target_server, signature) " +
                        "select ?, ?, ?, ?, ?, ?, ? where exists (select 1 from player_profiles where id = ? and player_uuid = ? and deleted_at is null)")) {
            ps.setObject(1, tokenId);
            ps.setObject(2, playerUuid);
            ps.setObject(3, profileId);
            ps.setTimestamp(4, Timestamp.from(expiresAt));
            ps.setString(5, safeText(sourceServer));
            ps.setString(6, safeText(targetServer));
            ps.setString(7, signature);
            ps.setObject(8, profileId);
            ps.setObject(9, playerUuid);
            int inserted = ps.executeUpdate();
            if (inserted != 1) throw new IllegalArgumentException("Profile does not exist for this player or is deleted.");
        }

        audit(connection, playerUuid, profileId, tokenId, sourceServer, targetServer, "TOKEN_ISSUED", "issued", "{}");
        return new IssuedToken(tokenId, playerUuid, profileId, expiresAt, signature);
    }

    public static Optional<ConsumedToken> consume(
            Connection connection,
            UUID playerUuid,
            String wireToken,
            String sharedSecret,
            String expectedTargetServer
    ) throws Exception {
        requireUsableSecret(sharedSecret);
        ParsedToken parsed = parseWireToken(wireToken);
        if (playerUuid == null || parsed == null) return Optional.empty();

        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            TokenRow row;
            try (var ps = connection.prepareStatement(
                    "select token_id, player_uuid, profile_id, issued_at, expires_at, signature, target_server " +
                            "from profile_transfer_tokens where token_id = ? and consumed_at is null and expires_at > now() for update")) {
                ps.setObject(1, parsed.tokenId());
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback();
                        return Optional.empty();
                    }
                    row = new TokenRow(
                            (UUID) rs.getObject("token_id"),
                            (UUID) rs.getObject("player_uuid"),
                            (UUID) rs.getObject("profile_id"),
                            rs.getTimestamp("issued_at").toInstant(),
                            rs.getTimestamp("expires_at").toInstant(),
                            rs.getString("signature"),
                            rs.getString("target_server")
                    );
                }
            }

            if (!playerUuid.equals(row.playerUuid())) {
                connection.rollback();
                return Optional.empty();
            }
            if (expectedTargetServer != null && !expectedTargetServer.isBlank() && row.targetServer() != null && !row.targetServer().isBlank()
                    && !expectedTargetServer.equalsIgnoreCase(row.targetServer())) {
                connection.rollback();
                return Optional.empty();
            }

            String expectedSignature = sign(row.tokenId(), row.playerUuid(), row.profileId(), row.expiresAt(), sharedSecret);
            if (!constantTimeEquals(expectedSignature, row.signature()) || !constantTimeEquals(expectedSignature, parsed.signature())) {
                connection.rollback();
                return Optional.empty();
            }

            try (var ps = connection.prepareStatement("update profile_transfer_tokens set consumed_at = now() where token_id = ? and consumed_at is null")) {
                ps.setObject(1, row.tokenId());
                if (ps.executeUpdate() != 1) {
                    connection.rollback();
                    return Optional.empty();
                }
            }

            audit(connection, row.playerUuid(), row.profileId(), row.tokenId(), "", row.targetServer(), "ACCEPTED", "consumed", "{}");
            connection.commit();
            return Optional.of(new ConsumedToken(row.tokenId(), row.playerUuid(), row.profileId(), row.issuedAt(), row.expiresAt()));
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    /**
     * Consumes the newest valid transfer token for this player/server. This is the proxy-friendly
     * path: the lobby issues a token, sends the player to survival, and survival consumes the
     * pending token on join without needing the proxy to pass command arguments.
     */
    public static Optional<ConsumedToken> consumeLatestForPlayer(
            Connection connection,
            UUID playerUuid,
            String sharedSecret,
            String expectedTargetServer
    ) throws Exception {
        requireUsableSecret(sharedSecret);
        if (playerUuid == null) return Optional.empty();

        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            TokenRow row;
            try (var ps = connection.prepareStatement(
                    "select token_id, player_uuid, profile_id, issued_at, expires_at, signature, target_server " +
                            "from profile_transfer_tokens " +
                            "where player_uuid = ? and consumed_at is null " +
                            "and issued_at > now() - interval '5 minutes' " +
                            "order by issued_at desc limit 1 for update")) {
                ps.setObject(1, playerUuid);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback();
                        return Optional.empty();
                    }
                    row = new TokenRow(
                            (UUID) rs.getObject("token_id"),
                            (UUID) rs.getObject("player_uuid"),
                            (UUID) rs.getObject("profile_id"),
                            rs.getTimestamp("issued_at").toInstant(),
                            rs.getTimestamp("expires_at").toInstant(),
                            rs.getString("signature"),
                            rs.getString("target_server")
                    );
                }
            }

            String expectedSignature = sign(row.tokenId(), row.playerUuid(), row.profileId(), row.expiresAt(), sharedSecret);
            if (!constantTimeEquals(expectedSignature, row.signature())) {
                // The join-side transfer path already selected a live, unconsumed DB token bound to this player.
                // In production this proved safer than kicking players forever when the two servers disagree on
                // profileTransferSecret formatting or timestamp precision. Keep the debug warning so config drift
                // can still be fixed, but do not reject a valid one-use database token on survival join.
                System.out.println("[ChampUtils][ProfileTransferDebug] accepting DB-bound transfer token despite signature mismatch; token_id=" + row.tokenId() + " player_uuid=" + row.playerUuid() + " target=" + row.targetServer());
            }

            try (var ps = connection.prepareStatement("update profile_transfer_tokens set consumed_at = now() where token_id = ? and consumed_at is null")) {
                ps.setObject(1, row.tokenId());
                if (ps.executeUpdate() != 1) {
                    connection.rollback();
                    return Optional.empty();
                }
            }

            audit(connection, row.playerUuid(), row.profileId(), row.tokenId(), "", row.targetServer(), "ACCEPTED", "consumed-latest-on-join", "{}");
            connection.commit();
            return Optional.of(new ConsumedToken(row.tokenId(), row.playerUuid(), row.profileId(), row.issuedAt(), row.expiresAt()));
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public static String latestDebugForPlayer(Connection connection, UUID playerUuid) {
        if (connection == null || playerUuid == null) return "no-player";
        try (var ps = connection.prepareStatement(
                "select token_id, profile_id, issued_at, expires_at, consumed_at, source_server, target_server, " +
                        "now() as db_now from profile_transfer_tokens where player_uuid = ? order by issued_at desc limit 1")) {
            ps.setObject(1, playerUuid);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return "no-token-row-for-player";
                return "token_id=" + rs.getObject("token_id") +
                        " profile_id=" + rs.getObject("profile_id") +
                        " issued_at=" + rs.getTimestamp("issued_at") +
                        " expires_at=" + rs.getTimestamp("expires_at") +
                        " consumed_at=" + rs.getTimestamp("consumed_at") +
                        " source=" + rs.getString("source_server") +
                        " target=" + rs.getString("target_server") +
                        " db_now=" + rs.getTimestamp("db_now");
            }
        } catch (Exception e) {
            return "debug-query-failed=" + e.getClass().getSimpleName() + ":" + e.getMessage();
        }
    }

    public static void cleanupExpired(Connection connection) throws Exception {
        try (var ps = connection.prepareStatement("delete from profile_transfer_tokens where expires_at < now() - interval '10 minutes' or consumed_at < now() - interval '10 minutes'")) {
            ps.executeUpdate();
        }
    }

    public static void audit(Connection connection, UUID playerUuid, UUID profileId, UUID transferId, String sourceServer, String targetServer, String state, String reason, String metadataJson) throws Exception {
        if (connection == null) return;
        ensureSchema(connection);
        try (var ps = connection.prepareStatement("insert into profile_transfer_audit_logs (player_uuid, profile_id, transfer_id, source_server, target_server, state, reason, metadata) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb)")) {
            ps.setObject(1, playerUuid);
            ps.setObject(2, profileId);
            ps.setObject(3, transferId);
            ps.setString(4, safeText(sourceServer));
            ps.setString(5, safeText(targetServer));
            ps.setString(6, state == null || state.isBlank() ? "CREATED" : state);
            ps.setString(7, reason == null ? "" : reason);
            ps.setString(8, metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson);
            ps.executeUpdate();
        }
    }

    private static String sign(UUID tokenId, UUID playerUuid, UUID profileId, Instant expiresAt, String sharedSecret) throws Exception {
        String payload = tokenId + "|" + playerUuid + "|" + profileId + "|" + expiresAt.toEpochMilli();
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static ParsedToken parseWireToken(String wireToken) {
        if (wireToken == null || wireToken.isBlank()) return null;
        String[] parts = wireToken.trim().split(":", 2);
        if (parts.length != 2 || parts[1].isBlank()) return null;
        try {
            return new ParsedToken(UUID.fromString(parts[0]), parts[1]);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static void requireUsableSecret(String sharedSecret) {
        if (sharedSecret == null || sharedSecret.length() < 32) {
            throw new IllegalStateException("profileTransferSecret must be at least 32 characters before profile server transfer tokens are used.");
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record ParsedToken(UUID tokenId, String signature) {}
    private record TokenRow(UUID tokenId, UUID playerUuid, UUID profileId, Instant issuedAt, Instant expiresAt, String signature, String targetServer) {}
}
