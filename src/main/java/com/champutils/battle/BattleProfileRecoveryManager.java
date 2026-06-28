package com.champutils.battle;

import com.champutils.database.DatabaseManager;
import com.champutils.profile.PlayerProfileManager;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable profile/battle recovery guard.
 *
 * A battle now creates a SQL recovery row on start, heartbeats while active, and closes the row
 * on normal battle completion/disconnect cleanup. On startup, OPEN rows from interrupted server
 * sessions are marked CRASHED/RECOVERED and profile guards are cleared so players are never
 * trapped in half-loaded battle/profile state.
 */
public final class BattleProfileRecoveryManager {
    private static final Map<UUID, UUID> BATTLE_PROFILE_BY_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, String> BATTLE_ID_BY_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> RECOVERY_ID_BY_PLAYER = new ConcurrentHashMap<>();

    private BattleProfileRecoveryManager() {}

    public static void ensureSchema(Connection connection) throws Exception {
        if (connection == null) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create extension if not exists pgcrypto");
            statement.executeUpdate("create table if not exists profile_battle_recovery (" +
                    "id uuid primary key default gen_random_uuid(), " +
                    "battle_id text not null, " +
                    "profile_id uuid not null, " +
                    "player_uuid uuid, " +
                    "server_id text not null default '', " +
                    "battle_type text not null default 'UNKNOWN', " +
                    "status text not null default 'OPEN' check (status in ('OPEN','WON','LOST','FLED','CRASHED','RECOVERED','EXPIRED')), " +
                    "started_at timestamptz not null default now(), " +
                    "last_heartbeat timestamptz not null default now(), " +
                    "ended_at timestamptz, " +
                    "pre_battle_generation uuid, " +
                    "post_battle_generation uuid, " +
                    "recovery_payload jsonb not null default '{}'::jsonb, " +
                    "metadata jsonb not null default '{}'::jsonb)");
            statement.executeUpdate("create table if not exists profile_battle_recovery_events (" +
                    "id bigserial primary key, " +
                    "recovery_id uuid not null references profile_battle_recovery(id) on delete cascade, " +
                    "event_type text not null, " +
                    "event_at timestamptz not null default now(), " +
                    "payload jsonb not null default '{}'::jsonb)");
            statement.executeUpdate("create index if not exists idx_profile_battle_recovery_open on profile_battle_recovery(profile_id, last_heartbeat) where status = 'OPEN'");
            statement.executeUpdate("create index if not exists idx_profile_battle_recovery_player on profile_battle_recovery(player_uuid, started_at desc)");
        }
    }

    public static void handleBattleStarted(ServerPlayer player, Object battle) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return;

        String battleId = battleId(battle);
        BATTLE_PROFILE_BY_PLAYER.put(player.getUUID(), profileId);
        BATTLE_ID_BY_PLAYER.put(player.getUUID(), battleId);
        PlayerProfileManager.markProfileGuardAsync(profileId, "battle:" + battleId);
        PlayerProfileManager.forceSaveActiveProfileStateAsync(player, "battle-start");

        DatabaseManager.executeAsync("open profile battle recovery", connection -> {
            ensureSchema(connection);
            try (var ps = connection.prepareStatement("insert into profile_battle_recovery (battle_id, profile_id, player_uuid, server_id, battle_type, recovery_payload) values (?, ?, ?, ?, ?, jsonb_build_object('player_name', ?)) returning id")) {
                ps.setString(1, battleId);
                ps.setObject(2, profileId);
                ps.setObject(3, player.getUUID());
                ps.setString(4, serverId());
                ps.setString(5, String.valueOf(BattleContextManager.getContext(player.getUUID())));
                ps.setString(6, player.getGameProfile().getName());
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UUID recoveryId = (UUID) rs.getObject("id");
                        RECOVERY_ID_BY_PLAYER.put(player.getUUID(), recoveryId);
                        insertEvent(connection, recoveryId, "START", "{}");
                    }
                }
            }
        });
    }

    public static void heartbeat(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return;
        UUID recoveryId = RECOVERY_ID_BY_PLAYER.get(player.getUUID());
        if (recoveryId == null) return;
        DatabaseManager.executeCoalescedAsync("battle-recovery-heartbeat:" + recoveryId, "battle recovery heartbeat", connection -> {
            ensureSchema(connection);
            try (var ps = connection.prepareStatement("update profile_battle_recovery set last_heartbeat = now() where id = ? and status = 'OPEN'")) {
                ps.setObject(1, recoveryId);
                ps.executeUpdate();
            }
        });
    }

    public static void handleBattleEnded(ServerPlayer player, String reason) {
        if (player == null) return;
        UUID recoveryId = RECOVERY_ID_BY_PLAYER.remove(player.getUUID());
        UUID profileId = BATTLE_PROFILE_BY_PLAYER.remove(player.getUUID());
        BATTLE_ID_BY_PLAYER.remove(player.getUUID());
        if (profileId == null && PlayerProfileManager.hasActiveProfile(player)) {
            profileId = PlayerProfileManager.activeProfileId(player);
        }
        if (profileId != null && !profileId.equals(player.getUUID())) {
            PlayerProfileManager.forceSaveActiveProfileStateAsync(player, reason == null ? "battle-end" : reason);
            PlayerProfileManager.clearProfileGuardAsync(profileId);
        }
        if (recoveryId != null) closeRecoveryAsync(recoveryId, statusFromReason(reason), reason);
    }

    public static void handleDisconnectBeforeUnload(ServerPlayer player) {
        if (player == null) return;
        UUID profileId = BATTLE_PROFILE_BY_PLAYER.get(player.getUUID());
        if (profileId == null && PlayerProfileManager.hasActiveProfile(player)) {
            profileId = PlayerProfileManager.activeProfileId(player);
        }
        if (profileId == null || profileId.equals(player.getUUID())) return;
        PlayerProfileManager.forceSaveActiveProfileStateAsync(player, "disconnect-before-battle-cleanup");
        PlayerProfileManager.clearProfileGuardAsync(profileId);
        UUID recoveryId = RECOVERY_ID_BY_PLAYER.remove(player.getUUID());
        if (recoveryId != null) closeRecoveryAsync(recoveryId, "FLED", "disconnect-before-battle-cleanup");
    }

    public static void clearLocal(ServerPlayer player) {
        if (player == null) return;
        BATTLE_PROFILE_BY_PLAYER.remove(player.getUUID());
        BATTLE_ID_BY_PLAYER.remove(player.getUUID());
        RECOVERY_ID_BY_PLAYER.remove(player.getUUID());
    }

    public static void recoverInterruptedGuardsAsync() {
        BATTLE_PROFILE_BY_PLAYER.clear();
        BATTLE_ID_BY_PLAYER.clear();
        RECOVERY_ID_BY_PLAYER.clear();
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("recover open battle recovery rows", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("alter table player_profiles add column if not exists profile_guard_reason text");
                statement.executeUpdate("alter table player_profiles add column if not exists profile_guard_updated_at timestamptz");
                statement.executeUpdate("update player_profiles set is_locked = false, profile_guard_reason = null, profile_guard_updated_at = null where is_locked = true or profile_guard_reason is not null");
            } catch (Throwable t) {
                System.err.println("[ChampUtils] Could not clear stale profile guards during battle recovery startup: " + t.getMessage());
            }

            ensureSchema(connection);
            try (var ps = connection.prepareStatement("update profile_battle_recovery set status = 'CRASHED', ended_at = now(), metadata = coalesce(metadata, '{}'::jsonb) || jsonb_build_object('startup_recovered', true) where status = 'OPEN'")) {
                ps.executeUpdate();
            }
        });
    }

    private static void closeRecoveryAsync(UUID recoveryId, String status, String reason) {
        if (recoveryId == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("close battle recovery", connection -> {
            ensureSchema(connection);
            try (var ps = connection.prepareStatement("update profile_battle_recovery set status = ?, ended_at = now(), last_heartbeat = now(), metadata = coalesce(metadata, '{}'::jsonb) || jsonb_build_object('close_reason', ?) where id = ? and status = 'OPEN'")) {
                ps.setString(1, status == null ? "RECOVERED" : status);
                ps.setString(2, reason == null ? "battle-end" : reason);
                ps.setObject(3, recoveryId);
                ps.executeUpdate();
            }
            insertEvent(connection, recoveryId, "END", "{\"reason\":\"" + jsonEscape(reason == null ? "battle-end" : reason) + "\"}");
        });
    }

    private static void insertEvent(Connection connection, UUID recoveryId, String eventType, String payloadJson) throws Exception {
        try (var ps = connection.prepareStatement("insert into profile_battle_recovery_events (recovery_id, event_type, payload) values (?, ?, ?::jsonb)")) {
            ps.setObject(1, recoveryId);
            ps.setString(2, eventType);
            ps.setString(3, payloadJson == null ? "{}" : payloadJson);
            ps.executeUpdate();
        }
    }

    private static String statusFromReason(String reason) {
        if (reason == null) return "RECOVERED";
        String lower = reason.toLowerCase();
        if (lower.contains("win") || lower.contains("victory")) return "WON";
        if (lower.contains("loss") || lower.contains("lose")) return "LOST";
        if (lower.contains("disconnect") || lower.contains("forfeit") || lower.contains("fled")) return "FLED";
        return "RECOVERED";
    }

    private static String serverId() {
        String configured = System.getProperty("champutils.serverId");
        if (configured == null || configured.isBlank()) configured = System.getenv("CHAMPUTILS_SERVER_ID");
        return configured == null || configured.isBlank() ? "survival" : configured;
    }

    private static String battleId(Object battle) {
        if (battle == null) return "unknown";
        for (String methodName : new String[] { "getBattleId", "battleId", "getId", "id" }) {
            try {
                var method = battle.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(battle);
                if (value != null) return String.valueOf(value);
            } catch (Throwable ignored) {}
        }
        return Integer.toHexString(System.identityHashCode(battle));
    }

    private static String jsonEscape(String input) {
        return input == null ? "" : input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
