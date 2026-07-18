package com.champutils.profile;

import com.champutils.database.DatabaseManager;
import com.champutils.teleport.TeleportConfig;
import com.champutils.teleport.TeleportLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Consumer;

/** Resolves and atomically consumes the one-time spawn assigned to each profile. */
public final class ProfileFirstSpawnManager {
    private ProfileFirstSpawnManager() {}

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure profile first spawn schema", connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("create table if not exists profile_first_spawn_claims (" +
                        "profile_id uuid primary key references player_profiles(id) on delete cascade, " +
                        "player_uuid uuid not null, claimed_at timestamptz not null default now())");
            }
        });
    }

    public static void resolve(ServerPlayer player, Consumer<TeleportLocation> callback) {
        if (player == null || callback == null) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        TeleportLocation configured = TeleportConfig.getProfileFirstSpawn();
        if (profileId == null || configured == null || !DatabaseManager.isEnabled()) {
            callback.accept(null);
            return;
        }

        DatabaseManager.supplyAsync("check first profile spawn " + profileId, connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("create table if not exists profile_first_spawn_claims (" +
                        "profile_id uuid primary key references player_profiles(id) on delete cascade, " +
                        "player_uuid uuid not null, claimed_at timestamptz not null default now())");
            }
            try (var ps = connection.prepareStatement("select 1 from profile_first_spawn_claims where profile_id = ?")) {
                ps.setObject(1, profileId);
                try (var rs = ps.executeQuery()) { return !rs.next(); }
            }
        }).whenComplete((needsFirstSpawn, error) -> player.server.execute(() -> {
            if (error != null || !profileId.equals(PlayerProfileManager.activeProfileId(player))) {
                if (error != null) error.printStackTrace();
                callback.accept(null);
                return;
            }
            callback.accept(Boolean.TRUE.equals(needsFirstSpawn) ? configured : null);
        }));
    }

    public static void markClaimed(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) return;
        UUID playerUuid = player.getUUID();
        DatabaseManager.executeAsync("claim first profile spawn " + profileId, connection -> {
            try (var ps = connection.prepareStatement(
                    "insert into profile_first_spawn_claims (profile_id, player_uuid) values (?, ?) on conflict (profile_id) do nothing")) {
                ps.setObject(1, profileId);
                ps.setObject(2, playerUuid);
                ps.executeUpdate();
            }
        });
    }
}
