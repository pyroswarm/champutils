package com.champutils.profile;

import com.champutils.database.DatabaseManager;
import com.champutils.network.NetworkServerConfig;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Stores a player's set-and-forget preferred Survival backend selected in the profile lobby. */
public final class PreferredSurvivalServerManager {
    public static final String ALPHA_SERVER_ID = "main_survival1";
    public static final String OMEGA_SERVER_ID = "survival2";

    private PreferredSurvivalServerManager() {}

    public record Preference(String serverId, String displayName) {}

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure preferred survival server schema", PreferredSurvivalServerManager::ensureSchema);
    }

    public static void ensureSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists player_survival_server_preferences (" +
                    "player_uuid uuid primary key references players(uuid) on delete cascade, " +
                    "server_id text not null, " +
                    "updated_at timestamptz not null default now())");
            statement.executeUpdate("create index if not exists idx_player_survival_server_preferences_server on player_survival_server_preferences(server_id)");
        }
    }

    public static CompletableFuture<Preference> getAsync(ServerPlayer player) {
        if (player == null) return CompletableFuture.completedFuture(defaultPreference());
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(defaultPreference());
        UUID playerId = player.getUUID();
        return DatabaseManager.supplyAsync("load preferred survival server", connection -> {
            ensureSchema(connection);
            try (var ps = connection.prepareStatement("select server_id from player_survival_server_preferences where player_uuid = ?")) {
                ps.setObject(1, playerId);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) return toPreference(rs.getString(1)).orElse(defaultPreference());
                }
            }
            return defaultPreference();
        }).exceptionally(error -> defaultPreference());
    }

    public static CompletableFuture<Boolean> setAsync(ServerPlayer player, String serverId) {
        if (player == null || !isAllowedTarget(serverId)) return CompletableFuture.completedFuture(false);
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(false);
        UUID playerId = player.getUUID();
        String normalized = normalize(serverId);
        return DatabaseManager.supplyAsync("set preferred survival server", connection -> {
            ensureSchema(connection);
            try (var ps = connection.prepareStatement(
                    "insert into player_survival_server_preferences(player_uuid, server_id, updated_at) values (?, ?, now()) " +
                            "on conflict(player_uuid) do update set server_id = excluded.server_id, updated_at = now()")) {
                ps.setObject(1, playerId);
                ps.setString(2, normalized);
                return ps.executeUpdate() > 0;
            }
        }).exceptionally(error -> false);
    }

    public static Preference defaultPreference() {
        NetworkServerConfig config = NetworkServerConfig.get();
        String configured = config.survivalServerId == null || config.survivalServerId.isBlank() ? ALPHA_SERVER_ID : config.survivalServerId;
        return toPreference(configured).orElse(new Preference(ALPHA_SERVER_ID, "Nova"));
    }

    public static Optional<Preference> toPreference(String serverId) {
        String normalized = normalize(serverId);
        if (ALPHA_SERVER_ID.equalsIgnoreCase(normalized)) return Optional.of(new Preference(ALPHA_SERVER_ID, "Nova"));
        if (OMEGA_SERVER_ID.equalsIgnoreCase(normalized)) return Optional.of(new Preference(OMEGA_SERVER_ID, "Eclipse"));
        return Optional.empty();
    }

    public static boolean isAllowedTarget(String serverId) {
        String normalized = normalize(serverId);
        if (normalized.isBlank()) return false;
        if (!ALPHA_SERVER_ID.equalsIgnoreCase(normalized) && !OMEGA_SERVER_ID.equalsIgnoreCase(normalized)) return false;
        for (String id : NetworkServerConfig.get().normalizedSurvivalBackends()) {
            if (normalized.equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    public static String displayName(String serverId) {
        return toPreference(serverId).map(Preference::displayName).orElse(serverId == null ? "Survival" : serverId);
    }

    private static String normalize(String serverId) {
        if (serverId == null) return "";
        String value = serverId.trim().toLowerCase(Locale.ROOT);
        if (value.equals("alpha")) return ALPHA_SERVER_ID;
        if (value.equals("omega")) return OMEGA_SERVER_ID;
        if (value.equals("survival") || value.equals("survival-1")) return ALPHA_SERVER_ID;
        return serverId.trim();
    }
}
