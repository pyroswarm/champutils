package com.champutils.matchmaking;

import com.champutils.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GlobalMatchmakingRepository {
    private static volatile boolean schemaReady = false;
    private GlobalMatchmakingRepository() {
    }

    public static synchronized void ensureSchema(Connection connection) throws Exception {
        if (schemaReady) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "create table if not exists global_matchmaking_queue (" +
                            "player_uuid uuid primary key, " +
                            "profile_id uuid, " +
                            "player_name text not null default '', " +
                            "queue_type text not null, " +
                            "source_server_id text not null, " +
                            "priority integer not null default 0, " +
                            "rp integer not null default 1000, " +
                            "rank_index integer not null default 0, " +
                            "team_snapshot jsonb not null default '{}'::jsonb, " +
                            "status text not null default 'QUEUED', " +
                            "queued_at timestamptz not null default now(), " +
                            "updated_at timestamptz not null default now(), " +
                            "expires_at timestamptz not null default (now() + interval '10 minutes')" +
                            ")"
            );
            statement.executeUpdate("alter table global_matchmaking_queue add column if not exists rp integer not null default 1000");
            statement.executeUpdate("alter table global_matchmaking_queue add column if not exists rank_index integer not null default 0");
            statement.executeUpdate("create index if not exists global_matchmaking_queue_pick_idx on global_matchmaking_queue (queue_type, status, priority desc, queued_at) where status = 'QUEUED'");

            statement.executeUpdate(
                    "create table if not exists global_matchmaking_sessions (" +
                            "id uuid primary key default gen_random_uuid(), " +
                            "queue_type text not null, " +
                            "player_one_uuid uuid not null, " +
                            "player_one_name text not null default '', " +
                            "player_two_uuid uuid not null, " +
                            "player_two_name text not null default '', " +
                            "battle_server_id text not null, " +
                            "status text not null default 'PENDING_ACCEPT', " +
                            "created_at timestamptz not null default now(), " +
                            "updated_at timestamptz not null default now(), " +
                            "expires_at timestamptz not null default (now() + interval '3 minutes'), " +
                            "started_at timestamptz" +
                            ")"
            );
            statement.executeUpdate("alter table global_matchmaking_sessions add column if not exists player_one_name text not null default ''");
            statement.executeUpdate("alter table global_matchmaking_sessions add column if not exists player_two_name text not null default ''");
            statement.executeUpdate("alter table global_matchmaking_sessions add column if not exists started_at timestamptz");
            statement.executeUpdate("create index if not exists global_matchmaking_sessions_player_idx on global_matchmaking_sessions (player_one_uuid, player_two_uuid, status)");

            statement.executeUpdate(
                    "create table if not exists global_pvp_return_locations (" +
                            "player_uuid uuid primary key, " +
                            "original_server_id text not null, " +
                            "world_id text not null, " +
                            "x double precision not null, y double precision not null, z double precision not null, " +
                            "yaw real not null default 0, pitch real not null default 0, " +
                            "status text not null default 'QUEUED', updated_at timestamptz not null default now()" +
                            ")"
            );
            statement.executeUpdate("create index if not exists global_pvp_return_server_idx on global_pvp_return_locations (original_server_id, status)");

            statement.executeUpdate(
                    "create table if not exists global_matchmaking_acceptances (" +
                            "session_id uuid not null references global_matchmaking_sessions(id) on delete cascade, " +
                            "player_uuid uuid not null, " +
                            "accepted boolean not null default false, " +
                            "responded_at timestamptz, " +
                            "primary key (session_id, player_uuid)" +
                            ")"
            );
        }
        schemaReady = true;
    }

    public static void saveReturnLocation(UUID playerUuid, String serverId, String worldId, double x, double y, double z, float yaw, float pitch) {
        if (playerUuid == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("save global pvp return " + playerUuid, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into global_pvp_return_locations (player_uuid, original_server_id, world_id, x, y, z, yaw, pitch, status, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', now()) " +
                            "on conflict (player_uuid) do update set original_server_id = excluded.original_server_id, world_id = excluded.world_id, x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch, status = 'QUEUED', updated_at = now()")) {
                ps.setObject(1, playerUuid); ps.setString(2, serverId == null ? "" : serverId); ps.setString(3, worldId == null ? "minecraft:overworld" : worldId);
                ps.setDouble(4, x); ps.setDouble(5, y); ps.setDouble(6, z); ps.setFloat(7, yaw); ps.setFloat(8, pitch); ps.executeUpdate();
            }
        });
    }

    public static CompletableFuture<ReturnLocation> getReturnLocation(UUID playerUuid) {
        if (playerUuid == null || !DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(null);
        return DatabaseManager.supplyAsync("load global pvp return " + playerUuid, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("select player_uuid, original_server_id, world_id, x, y, z, yaw, pitch, status from global_pvp_return_locations where player_uuid = ?")) {
                ps.setObject(1, playerUuid);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? readReturnLocation(rs) : null; }
            }
        });
    }

    public static void markReturning(UUID playerUuid) {
        if (playerUuid == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("mark global pvp returning " + playerUuid, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("update global_pvp_return_locations set status = 'RETURNING', updated_at = now() where player_uuid = ?")) { ps.setObject(1, playerUuid); ps.executeUpdate(); }
        });
    }

    public static CompletableFuture<List<ReturnLocation>> returningForServer(String serverId) {
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(List.of());
        return DatabaseManager.supplyAsync("load returning pvp players", connection -> {
            ensureSchema(connection);
            List<ReturnLocation> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("select player_uuid, original_server_id, world_id, x, y, z, yaw, pitch, status from global_pvp_return_locations where status = 'RETURNING' and lower(original_server_id) = lower(?) limit 50")) {
                ps.setString(1, serverId == null ? "" : serverId);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(readReturnLocation(rs)); }
            }
            return out;
        });
    }

    public static void clearReturnLocation(UUID playerUuid) {
        if (playerUuid == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("clear global pvp return " + playerUuid, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("delete from global_pvp_return_locations where player_uuid = ?")) { ps.setObject(1, playerUuid); ps.executeUpdate(); }
        });
    }

    private static ReturnLocation readReturnLocation(ResultSet rs) throws Exception {
        return new ReturnLocation((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getDouble(5), rs.getDouble(6), rs.getFloat(7), rs.getFloat(8), rs.getString(9));
    }

    public static void enqueue(UUID playerUuid, UUID profileId, String playerName, String type, String sourceServerId, int rp, int rankIndex) {
        if (playerUuid == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("enqueue global matchmaking " + playerUuid, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into global_matchmaking_queue (player_uuid, profile_id, player_name, queue_type, source_server_id, rp, rank_index, status, queued_at, updated_at, expires_at) " +
                            "values (?, ?, ?, ?, ?, ?, ?, 'QUEUED', now(), now(), now() + interval '10 minutes') " +
                            "on conflict (player_uuid) do update set profile_id = excluded.profile_id, player_name = excluded.player_name, queue_type = excluded.queue_type, source_server_id = excluded.source_server_id, rp = excluded.rp, rank_index = excluded.rank_index, status = 'QUEUED', queued_at = now(), updated_at = now(), expires_at = now() + interval '10 minutes'"
            )) {
                ps.setObject(1, playerUuid);
                ps.setObject(2, profileId);
                ps.setString(3, playerName == null ? "" : playerName);
                ps.setString(4, normalizeType(type));
                ps.setString(5, sourceServerId == null ? "" : sourceServerId);
                ps.setInt(6, rp);
                ps.setInt(7, rankIndex);
                ps.executeUpdate();
            }
        });
    }

    public static void leave(UUID playerUuid) {
        if (playerUuid == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("leave global matchmaking " + playerUuid, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("update global_matchmaking_queue set status = 'CANCELLED', updated_at = now() where player_uuid = ? and status = 'QUEUED'")) {
                ps.setObject(1, playerUuid);
                ps.executeUpdate();
            }
        });
    }

    public static CompletableFuture<List<Session>> tick(String preferredBattleServerId, int allowedRankSpread, boolean canHostBattle) {
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(List.of());
        String battleServer = preferredBattleServerId == null || preferredBattleServerId.isBlank() ? "main_survival1" : preferredBattleServerId;
        return DatabaseManager.supplyAsync("global matchmaking tick", connection -> {
            ensureSchema(connection);
            expireOld(connection);
            if (canHostBattle) {
                createOneSession(connection, battleServer, Math.max(0, allowedRankSpread));
            }
            return openSessions(connection);
        });
    }

    public static CompletableFuture<List<Session>> acceptedForBattleServer(String battleServerId) {
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(List.of());
        return DatabaseManager.supplyAsync("global matchmaking accepted sessions", connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "select id, queue_type, player_one_uuid, player_one_name, player_two_uuid, player_two_name, battle_server_id, status, expires_at " +
                            "from global_matchmaking_sessions where status = 'ACCEPTED' and lower(battle_server_id) = lower(?) and expires_at > now() order by created_at asc limit 20"
            )) {
                ps.setString(1, battleServerId == null ? "" : battleServerId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Session> sessions = new ArrayList<>();
                    while (rs.next()) sessions.add(readSession(rs));
                    return sessions;
                }
            }
        });
    }

    public static CompletableFuture<ResponseResult> respond(UUID sessionId, UUID playerUuid, boolean accepted) {
        if (sessionId == null || playerUuid == null || !DatabaseManager.isEnabled()) {
            return CompletableFuture.completedFuture(new ResponseResult(false, false, "Match session is unavailable."));
        }
        return DatabaseManager.supplyAsync("global matchmaking response " + sessionId, connection -> {
            ensureSchema(connection);
            boolean restoreAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Session session = lockSession(connection, sessionId);
                if (session == null) {
                    connection.rollback();
                    return new ResponseResult(false, false, "That match is unavailable.");
                }
                // Accept is idempotent. Cross-server polls and menu clicks may race with
                // the opponent's response, so an already-accepted session is success.
                if ("ACCEPTED".equalsIgnoreCase(session.status()) || "STARTED".equalsIgnoreCase(session.status())) {
                    connection.commit();
                    return new ResponseResult(true, true, "Both players accepted.");
                }
                if (!"PENDING_ACCEPT".equalsIgnoreCase(session.status())) {
                    connection.rollback();
                    return new ResponseResult(false, false, "That match is no longer waiting for acceptance.");
                }
                if (!playerUuid.equals(session.playerOneUuid()) && !playerUuid.equals(session.playerTwoUuid())) {
                    connection.rollback();
                    return new ResponseResult(false, false, "That match does not belong to you.");
                }
                if (!accepted) {
                    updateSessionStatus(connection, sessionId, "DECLINED");
                    requeueOther(connection, session, playerUuid);
                    connection.commit();
                    return new ResponseResult(true, false, "Match declined.");
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "insert into global_matchmaking_acceptances (session_id, player_uuid, accepted, responded_at) values (?, ?, true, now()) " +
                                "on conflict (session_id, player_uuid) do update set accepted = true, responded_at = now()"
                )) {
                    ps.setObject(1, sessionId);
                    ps.setObject(2, playerUuid);
                    ps.executeUpdate();
                }
                if (bothAccepted(connection, sessionId)) {
                    updateSessionStatus(connection, sessionId, "ACCEPTED");
                    connection.commit();
                    return new ResponseResult(true, true, "Both players accepted.");
                }
                connection.commit();
                return new ResponseResult(true, false, "Match accepted.");
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(restoreAutoCommit);
            }
        });
    }

    public static void markExpired(UUID sessionId) {
        if (sessionId == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("expire global match " + sessionId, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("update global_matchmaking_sessions set status = 'EXPIRED', updated_at = now() where id = ? and status in ('PENDING_ACCEPT', 'ACCEPTED')")) {
                ps.setObject(1, sessionId);
                ps.executeUpdate();
            }
        });
    }

    public static void markStarted(UUID sessionId) {
        if (sessionId == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("mark global match started " + sessionId, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("update global_matchmaking_sessions set status = 'STARTED', started_at = now(), updated_at = now() where id = ? and status = 'ACCEPTED'")) {
                ps.setObject(1, sessionId);
                ps.executeUpdate();
            }
        });
    }

    private static void expireOld(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("update global_matchmaking_queue set status = 'EXPIRED', updated_at = now() where status = 'QUEUED' and expires_at <= now()");
            statement.executeUpdate("update global_matchmaking_sessions set status = 'EXPIRED', updated_at = now() where status in ('PENDING_ACCEPT', 'ACCEPTED') and expires_at <= now()");
        }
    }

    private static void createOneSession(Connection connection, String battleServer, int allowedRankSpread) throws Exception {
        boolean restoreAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            List<QueueEntry> entries = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "select player_uuid, profile_id, player_name, queue_type, source_server_id, rp, rank_index from global_matchmaking_queue " +
                            "where status = 'QUEUED' and expires_at > now() order by queue_type, priority desc, queued_at asc limit 32 for update skip locked"
            )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new QueueEntry(
                                (UUID) rs.getObject(1),
                                (UUID) rs.getObject(2),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getString(5),
                                rs.getInt(6),
                                rs.getInt(7)
                        ));
                    }
                }
            }
            for (int i = 0; i < entries.size(); i++) {
                QueueEntry a = entries.get(i);
                for (int j = i + 1; j < entries.size(); j++) {
                    QueueEntry b = entries.get(j);
                    if (!normalizeType(a.queueType()).equals(normalizeType(b.queueType()))) continue;
                    // A cross-server match must be hosted by one of the matched players' source servers.
                    // This prevents an unrelated third backend from claiming the session and guarantees
                    // that the arena world/coordinates come from either player one's or player two's server.
                    boolean claimantIsPlayerServer = battleServer.equalsIgnoreCase(a.sourceServerId())
                            || battleServer.equalsIgnoreCase(b.sourceServerId());
                    if (!claimantIsPlayerServer) continue;
                    if ("ranked".equals(normalizeType(a.queueType())) && Math.abs(a.rankIndex() - b.rankIndex()) > allowedRankSpread) continue;
                    UUID sessionId = UUID.randomUUID();
                    try (PreparedStatement insert = connection.prepareStatement(
                            "insert into global_matchmaking_sessions (id, queue_type, player_one_uuid, player_one_name, player_two_uuid, player_two_name, battle_server_id, status, expires_at) " +
                                    "values (?, ?, ?, ?, ?, ?, ?, 'PENDING_ACCEPT', now() + interval '5 minutes')"
                    )) {
                        insert.setObject(1, sessionId);
                        insert.setString(2, normalizeType(a.queueType()));
                        insert.setObject(3, a.playerUuid());
                        insert.setString(4, a.playerName());
                        insert.setObject(5, b.playerUuid());
                        insert.setString(6, b.playerName());
                        insert.setString(7, battleServer);
                        insert.executeUpdate();
                    }
                    try (PreparedStatement accept = connection.prepareStatement("insert into global_matchmaking_acceptances (session_id, player_uuid) values (?, ?), (?, ?)")) {
                        accept.setObject(1, sessionId);
                        accept.setObject(2, a.playerUuid());
                        accept.setObject(3, sessionId);
                        accept.setObject(4, b.playerUuid());
                        accept.executeUpdate();
                    }
                    try (PreparedStatement update = connection.prepareStatement("update global_matchmaking_queue set status = 'MATCHED', updated_at = now() where player_uuid in (?, ?)")) {
                        update.setObject(1, a.playerUuid());
                        update.setObject(2, b.playerUuid());
                        update.executeUpdate();
                    }
                    connection.commit();
                    return;
                }
            }
            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(restoreAutoCommit);
        }
    }

    private static List<Session> openSessions(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "select id, queue_type, player_one_uuid, player_one_name, player_two_uuid, player_two_name, battle_server_id, status, expires_at " +
                        "from global_matchmaking_sessions where status in ('PENDING_ACCEPT', 'ACCEPTED') and expires_at > now() order by created_at asc limit 100"
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Session> sessions = new ArrayList<>();
                while (rs.next()) sessions.add(readSession(rs));
                return sessions;
            }
        }
    }

    private static Session lockSession(Connection connection, UUID sessionId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "select id, queue_type, player_one_uuid, player_one_name, player_two_uuid, player_two_name, battle_server_id, status, expires_at from global_matchmaking_sessions where id = ? for update"
        )) {
            ps.setObject(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readSession(rs) : null;
            }
        }
    }

    private static boolean bothAccepted(Connection connection, UUID sessionId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("select count(*) from global_matchmaking_acceptances where session_id = ? and accepted = true")) {
            ps.setObject(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) >= 2;
            }
        }
    }

    private static void updateSessionStatus(Connection connection, UUID sessionId, String status) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("update global_matchmaking_sessions set status = ?, updated_at = now() where id = ?")) {
            ps.setString(1, status);
            ps.setObject(2, sessionId);
            ps.executeUpdate();
        }
    }

    private static void requeueOther(Connection connection, Session session, UUID declinedBy) throws Exception {
        UUID other = declinedBy.equals(session.playerOneUuid()) ? session.playerTwoUuid() : session.playerOneUuid();
        try (PreparedStatement ps = connection.prepareStatement("update global_matchmaking_queue set status = 'QUEUED', queued_at = now(), updated_at = now(), expires_at = now() + interval '10 minutes' where player_uuid = ?")) {
            ps.setObject(1, other);
            ps.executeUpdate();
        }
    }

    private static Session readSession(ResultSet rs) throws Exception {
        return new Session(
                (UUID) rs.getObject(1),
                normalizeType(rs.getString(2)),
                (UUID) rs.getObject(3),
                rs.getString(4),
                (UUID) rs.getObject(5),
                rs.getString(6),
                rs.getString(7),
                rs.getString(8),
                rs.getTimestamp(9) == null ? Instant.now() : rs.getTimestamp(9).toInstant()
        );
    }

    public static boolean hasPlayer(Session session, UUID playerId) {
        return session != null && playerId != null && (playerId.equals(session.playerOneUuid()) || playerId.equals(session.playerTwoUuid()));
    }

    public static UUID opponent(Session session, UUID playerId) {
        if (session == null || playerId == null) return null;
        if (playerId.equals(session.playerOneUuid())) return session.playerTwoUuid();
        if (playerId.equals(session.playerTwoUuid())) return session.playerOneUuid();
        return null;
    }

    public static String opponentName(Session session, UUID playerId) {
        if (session == null || playerId == null) return "";
        if (playerId.equals(session.playerOneUuid())) return session.playerTwoName();
        if (playerId.equals(session.playerTwoUuid())) return session.playerOneName();
        return "";
    }

    public static String normalizeType(String value) {
        return "ranked".equalsIgnoreCase(value == null ? "" : value.trim()) ? "ranked" : "casual";
    }

    public record QueueEntry(UUID playerUuid, UUID profileId, String playerName, String queueType, String sourceServerId, int rp, int rankIndex) {
    }

    public record Session(UUID id, String queueType, UUID playerOneUuid, String playerOneName, UUID playerTwoUuid, String playerTwoName, String battleServerId, String status, Instant expiresAt) {
    }

    public record ResponseResult(boolean recorded, boolean bothAccepted, String message) {
    }

    public record ReturnLocation(UUID playerUuid, String originalServerId, String worldId, double x, double y, double z, float yaw, float pitch, String status) {
    }
}
