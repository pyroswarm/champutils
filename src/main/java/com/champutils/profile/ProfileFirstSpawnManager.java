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

        UUID playerUuid = player.getUUID();
        DatabaseManager.supplyAsync("consume first profile spawn " + profileId, connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("create table if not exists profile_first_spawn_claims (" +
                        "profile_id uuid primary key references player_profiles(id) on delete cascade, " +
                        "player_uuid uuid not null, claimed_at timestamptz not null default now())");
            }
            try (var ps = connection.prepareStatement(
                    "insert into profile_first_spawn_claims (profile_id, player_uuid) values (?, ?) on conflict (profile_id) do nothing")) {
                ps.setObject(1, profileId);
                ps.setObject(2, playerUuid);
                return ps.executeUpdate() > 0;
            }
        }).whenComplete((firstSpawn, error) -> player.server.execute(() -> {
            if (error != null) {
                System.err.println("[ChampUtils] Failed to resolve first spawn for profile " + profileId + ".");
                error.printStackTrace();
                callback.accept(null);
                return;
            }
            if (!profileId.equals(PlayerProfileManager.activeProfileId(player))) {
                callback.accept(null);
                return;
            }
            callback.accept(Boolean.TRUE.equals(firstSpawn) ? configured : null);
        }));
    }
}
