package com.champutils.profile;

import com.champutils.chat.ChatPreferenceManager;
import com.champutils.database.DatabaseManager;
import com.champutils.teleport.SafeTeleportManager;
import com.champutils.teleport.TeleportConfig;
import com.champutils.teleport.TeleportLocation;
import com.champutils.menu.ProfileSelectionMenu;
import com.champutils.permissions.LuckPermsHook;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PlayerProfileManager {
    public static final int DEFAULT_MAX_PROFILES = 2;

    private static final Map<UUID, ProfileRecord> ACTIVE = new ConcurrentHashMap<>();

    private PlayerProfileManager() {}

    public record ProfileRecord(
            UUID profileId,
            UUID playerUuid,
            String profileName,
            ProfileGameMode gameMode,
            String monotypeType,
            boolean active,
            boolean pendingDelete,
            OffsetDateTime deleteAvailableAt
    ) {}

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure SQL profile schema compatibility", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("create extension if not exists pgcrypto");
                statement.executeUpdate("create table if not exists players (" +
                        "uuid uuid primary key, username text not null, playtime_seconds bigint not null default 0, " +
                        "first_seen timestamptz not null default now(), last_seen timestamptz not null default now(), " +
                        "last_server_id text, metadata jsonb not null default '{}'::jsonb)");
                statement.executeUpdate("create table if not exists player_profile_limits (" +
                        "player_uuid uuid primary key references players(uuid) on delete cascade, " +
                        "max_profiles integer not null default 2, instant_delete boolean not null default false, " +
                        "source text not null default 'DEFAULT', updated_at timestamptz not null default now())");
                statement.executeUpdate("create table if not exists player_profiles (" +
                        "id uuid primary key default gen_random_uuid(), player_uuid uuid not null references players(uuid) on delete cascade, " +
                        "name text not null, mode text not null check (mode in ('NORMAL','IRONMAN','MONOTYPE','ISLANDER','NUZLOCKE')), monotype text, " +
                        "is_locked boolean not null default false, is_pending_delete boolean not null default false, delete_available_at timestamptz, " +
                        "created_at timestamptz not null default now(), last_used_at timestamptz, deleted_at timestamptz, metadata jsonb not null default '{}'::jsonb)");

                statement.executeUpdate("alter table player_profiles drop constraint if exists player_profiles_mode_check");
                statement.executeUpdate("alter table player_profiles add constraint player_profiles_mode_check check (mode in ('NORMAL','IRONMAN','MONOTYPE','ISLANDER','NUZLOCKE'))");
                statement.executeUpdate("alter table player_profiles add column if not exists last_dimension text");
                statement.executeUpdate("alter table player_profiles add column if not exists last_x double precision");
                statement.executeUpdate("alter table player_profiles add column if not exists last_y double precision");
                statement.executeUpdate("alter table player_profiles add column if not exists last_z double precision");
                statement.executeUpdate("alter table player_profiles add column if not exists last_yaw real");
                statement.executeUpdate("alter table player_profiles add column if not exists last_pitch real");
                // Drop the old non-partial unique index if it exists. It kept soft-deleted profile names reserved forever.
                statement.executeUpdate("drop index if exists idx_unique_profile_name_per_player_uuid");
                statement.executeUpdate("drop index if exists player_profiles_unique_name_per_player_uuid");
                statement.executeUpdate("create unique index if not exists player_profiles_unique_live_name on player_profiles(player_uuid, lower(name)) where deleted_at is null");
                statement.executeUpdate("create index if not exists idx_player_profiles_player_live on player_profiles(player_uuid) where deleted_at is null");
                statement.executeUpdate("create index if not exists idx_player_profiles_player_name_live on player_profiles(player_uuid, lower(name)) where deleted_at is null");
                statement.executeUpdate("create index if not exists idx_player_profiles_pending_delete on player_profiles(player_uuid, is_pending_delete, delete_available_at) where deleted_at is null");
                statement.executeUpdate("create table if not exists player_active_profiles (" +
                        "player_uuid uuid primary key references players(uuid) on delete cascade, " +
                        "profile_id uuid not null references player_profiles(id) on delete cascade, updated_at timestamptz not null default now())");
                statement.executeUpdate("create index if not exists idx_player_active_profiles_profile on player_active_profiles(profile_id)");
                statement.executeUpdate("create table if not exists profile_ranked_stats (" +
                        "profile_id uuid not null references player_profiles(id) on delete cascade, season_id text not null default 'default', " +
                        "rp integer not null default 1000, peak_rp integer not null default 1000, wins integer not null default 0, losses integer not null default 0, " +
                        "streak integer not null default 0, updated_at timestamptz not null default now(), primary key(profile_id, season_id))");
                statement.executeUpdate("create table if not exists profile_player_stats (" +
                        "profile_id uuid primary key references player_profiles(id) on delete cascade, playtime_seconds bigint not null default 0, " +
                        "money numeric(18,2) not null default 0, battling_xp bigint not null default 0, battling_level integer not null default 1, " +
                        "total_level integer not null default 1, metadata jsonb not null default '{}'::jsonb, updated_at timestamptz not null default now())");
                statement.executeUpdate("create table if not exists profile_gym_progress (" +
                        "profile_id uuid not null references player_profiles(id) on delete cascade, gym_id text not null, defeated boolean not null default false, " +
                        "defeated_at timestamptz, attempts integer not null default 0, wins integer not null default 0, losses integer not null default 0, " +
                        "best_time_seconds integer, data jsonb not null default '{}'::jsonb, primary key(profile_id, gym_id))");
                statement.executeUpdate("create table if not exists profile_flan_claims (" +
                        "id uuid primary key default gen_random_uuid(), profile_id uuid not null references player_profiles(id) on delete cascade, " +
                        "player_uuid uuid not null references players(uuid) on delete cascade, flan_claim_id text not null, server_id text not null, world_name text not null, " +
                        "claim_data jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), unique(profile_id, flan_claim_id))");
            }
        });
    }

    
public static void handleJoin(ServerPlayer player) {
    if (player == null) return;
    if (!DatabaseManager.isEnabled()) {
        ACTIVE.put(player.getUUID(), new ProfileRecord(player.getUUID(), player.getUUID(), "Offline", ProfileGameMode.NORMAL, null, true, false, null));
        player.sendSystemMessage(Component.literal("Profiles require the SQL database. Using unsafe offline fallback.").withStyle(ChatFormatting.RED));
        return;
    }

    try {
        Connection connection = DatabaseManager.getConnection();
        ensurePlayerRow(connection, player);
        syncLimitFromLuckPerms(connection, player);

        if (listBlocking(player).isEmpty()) {
            createProfile(connection, player, "Default", ProfileGameMode.NORMAL, null, false);
            player.sendSystemMessage(Component.literal("Created your first profile: Default.").withStyle(ChatFormatting.GREEN));
        }

        clearActiveForMenu(player);
        player.server.execute(() -> ProfileMainMenuManager.enter(player, false));
        player.server.execute(() -> ProfileSelectionMenu.open(player));
    } catch (Exception e) {
        e.printStackTrace();
        ACTIVE.put(player.getUUID(), new ProfileRecord(player.getUUID(), player.getUUID(), "Fallback", ProfileGameMode.NORMAL, null, true, false, null));
        player.sendSystemMessage(Component.literal("Could not prepare your SQL profiles. Check console/database logs.").withStyle(ChatFormatting.RED));
    }
}

public static void clearActiveForMenu(ServerPlayer player) {
    if (player != null) ACTIVE.remove(player.getUUID());
}

public static boolean isInMainMenu(ServerPlayer player) {
    return player != null && !ACTIVE.containsKey(player.getUUID());
}


    public static java.util.Collection<ProfileRecord> activeProfilesSnapshot() {
        return java.util.List.copyOf(ACTIVE.values());
    }

public static void unload(UUID playerUuid) {
        if (playerUuid != null) ACTIVE.remove(playerUuid);
    }

    public static ProfileRecord active(ServerPlayer player) {
        if (player == null) return null;
        return ACTIVE.get(player.getUUID());
    }

    public static boolean hasActiveProfile(ServerPlayer player) {
        if (player == null) return false;
        ProfileRecord record = ACTIVE.get(player.getUUID());
        return record != null && !record.profileId().equals(player.getUUID());
    }

    public static UUID activeProfileId(ServerPlayer player) {
        ProfileRecord record = active(player);
        return record == null ? player.getUUID() : record.profileId();
    }

    public static UUID activeProfileId(UUID playerUuid) {
        ProfileRecord record = ACTIVE.get(playerUuid);
        return record == null ? playerUuid : record.profileId();
    }

    public static ProfileGameMode gameMode(ServerPlayer player) {
        ProfileRecord record = active(player);
        return record == null ? ProfileGameMode.NORMAL : record.gameMode();
    }

    public static String monotypeType(ServerPlayer player) {
        ProfileRecord record = active(player);
        return record == null ? null : record.monotypeType();
    }

    public static boolean isIronman(ServerPlayer player) {
        return gameMode(player).usesIronmanRules();
    }

    public static boolean isIslander(ServerPlayer player) {
        return gameMode(player) == ProfileGameMode.ISLANDER;
    }

    public static boolean isNuzlocke(ServerPlayer player) {
        return gameMode(player) == ProfileGameMode.NUZLOCKE;
    }

    public static boolean blocksAuctionHouse(ServerPlayer player) {
        return gameMode(player).blocksAuctionHouse();
    }

    public static ProfileLimit limitBlocking(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return new ProfileLimit(DEFAULT_MAX_PROFILES, false);
        try {
            Connection connection = DatabaseManager.getConnection();
            ensurePlayerRow(connection, player);
            syncLimitFromLuckPerms(connection, player);
            try (var ps = connection.prepareStatement("select max_profiles, instant_delete from player_profile_limits where player_uuid = ?")) {
                ps.setObject(1, player.getUUID());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return new ProfileLimit(Math.max(DEFAULT_MAX_PROFILES, rs.getInt("max_profiles")), rs.getBoolean("instant_delete"));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return new ProfileLimit(DEFAULT_MAX_PROFILES, false);
    }

    public record ProfileLimit(int maxProfiles, boolean instantDelete) {}


public static java.util.List<String> profileNamesBlocking(ServerPlayer player) {
    java.util.List<String> names = new java.util.ArrayList<>();
    for (ProfileRecord profile : listBlocking(player)) {
        if (profile != null && !profile.pendingDelete()) names.add(profile.profileName());
    }
    return names;
}

    public static List<ProfileRecord> listBlocking(ServerPlayer player) {
        List<ProfileRecord> profiles = new ArrayList<>();
        if (player == null || !DatabaseManager.isEnabled()) {
            profiles.add(active(player));
            return profiles;
        }
        try (var statement = DatabaseManager.getConnection().prepareStatement(
                "select p.id, p.player_uuid, p.name, p.mode, p.monotype, (a.profile_id is not null) as active, p.is_pending_delete, p.delete_available_at " +
                        "from player_profiles p left join player_active_profiles a on a.player_uuid = p.player_uuid and a.profile_id = p.id " +
                        "where p.player_uuid = ? and p.deleted_at is null order by p.created_at asc")) {
            statement.setObject(1, player.getUUID());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) profiles.add(fromResultSet(rs));
            }
        }
        catch (Exception e) { e.printStackTrace(); }
        return profiles;
    }

    public static String createBlocking(ServerPlayer player, String name, ProfileGameMode mode, String monotypeType) {
        if (player == null) return "Could not create profile.";
        String clean = cleanName(name);
        if (clean == null) return "Profile names must be 3-16 letters/numbers/underscore.";
        if (mode == ProfileGameMode.MONOTYPE && (monotypeType == null || monotypeType.isBlank())) return "Monotype profiles need a type, example: /profiles create FireRun monotype fire";
        if (!DatabaseManager.isEnabled()) return "Profiles require the SQL database to be enabled.";
        try {
            Connection connection = DatabaseManager.getConnection();
            ensurePlayerRow(connection, player);
            syncLimitFromLuckPerms(connection, player);
            finalizePendingDeletesBlocking(player);
            ProfileRecord existing = readByName(connection, player.getUUID(), clean);
            if (existing != null) {
                if (existing.pendingDelete()) return "That profile color is still pending deletion.";
                return "You already have a profile named " + clean + ".";
            }
            ProfileLimit limit = limitBlocking(player);
            int liveProfiles = countLiveProfiles(connection, player.getUUID());
            if (liveProfiles >= limit.maxProfiles()) return "You already have the max of " + limit.maxProfiles() + " profiles.";
            ProfileRecord created = createProfile(connection, player, clean, mode, normalizeType(monotypeType), false);
            return "Created profile " + clean + ". Use /profiles to select it.";
        }
        catch (Exception e) { e.printStackTrace(); return "Could not create profile. Check console/database logs."; }
    }

    public static String switchBlocking(ServerPlayer player, String name) {
        if (player == null) return "Could not switch profile.";
        String clean = cleanName(name);
        if (clean == null) return "Invalid profile name.";
        if (!DatabaseManager.isEnabled()) return "Profiles require the SQL database to be enabled.";
        try {
            Connection connection = DatabaseManager.getConnection();
            ProfileRecord target = readByName(connection, player.getUUID(), clean);
            if (target == null) return "No profile named " + clean + ".";
            if (target.pendingDelete()) return "That profile is pending deletion and cannot be loaded.";
            UUID previousProfileId = hasActiveProfile(player) ? activeProfileId(player) : null;
            if (hasActiveProfile(player)) {
                saveActiveLocation(player);
                VanillaProfileStateManager.save(player);
                CobblemonProfileStorageBridge.forceSaveActiveProfileStores(player);
                if (previousProfileId != null) CobblemonProfileStorageBridge.evictProfileStores(previousProfileId);
                ChatPreferenceManager.save(player);
                ProfileSessionLoader.unload(player);
            }
            setActive(connection, player.getUUID(), target.profileId());
            ProfileRecord active = new ProfileRecord(target.profileId(), target.playerUuid(), target.profileName(), target.gameMode(), target.monotypeType(), true, false, null);
            ACTIVE.put(player.getUUID(), active);
            ProfileLobbyManager.leaveLobby(player);
            VanillaProfileStateManager.load(player);
            CobblemonProfileStorageBridge.loadActiveProfileStores(player);
            ProfileSessionLoader.load(player);
            teleportToSavedLocation(player);
            return "Loaded profile " + active.profileName() + " [" + active.gameMode().displayName() + modeSuffix(active) + "].";
        }
        catch (Exception e) { e.printStackTrace(); return "Could not switch profile. Check console/database logs."; }
    }


    public static void switchAsync(ServerPlayer player, String name, Consumer<String> callback) {
        if (player == null) {
            if (callback != null) callback.accept("Could not switch profile.");
            return;
        }
        String clean = cleanName(name);
        if (clean == null) {
            if (callback != null) callback.accept("Invalid profile name.");
            return;
        }
        if (!DatabaseManager.isEnabled()) {
            if (callback != null) callback.accept("Profiles require the SQL database to be enabled.");
            return;
        }

        UUID playerUuid = player.getUUID();
        String playerName = player.getGameProfile().getName();
        UUID previousProfileId = hasActiveProfile(player) ? activeProfileId(player) : null;
        boolean hadActiveProfile = previousProfileId != null && !previousProfileId.equals(playerUuid);

        String saveDimension = null;
        double saveX = 0.0D;
        double saveY = 0.0D;
        double saveZ = 0.0D;
        float saveYaw = 0.0F;
        float savePitch = 0.0F;
        String vanillaSnapshot = null;
        net.minecraft.core.RegistryAccess registryAccess = player.registryAccess();

        if (hadActiveProfile) {
            saveDimension = player.serverLevel().dimension().location().toString();
            saveX = player.getX();
            saveY = player.getY();
            saveZ = player.getZ();
            saveYaw = player.getYRot();
            savePitch = player.getXRot();
            vanillaSnapshot = VanillaProfileStateManager.snapshotSnbt(player);

            CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player);
            ChatPreferenceManager.save(player);
            ProfileSessionLoader.unload(player);
        }

        final UUID previousProfileIdFinal = previousProfileId;
        final boolean hadActiveProfileFinal = hadActiveProfile;
        final String saveDimensionFinal = saveDimension;
        final double saveXFinal = saveX;
        final double saveYFinal = saveY;
        final double saveZFinal = saveZ;
        final float saveYawFinal = saveYaw;
        final float savePitchFinal = savePitch;
        final String vanillaSnapshotFinal = vanillaSnapshot;

        DatabaseManager.runAsync("async profile switch", connection -> {
            ProfileRecord target = readByName(connection, playerUuid, clean);
            if (target == null) throw new IllegalArgumentException("No profile named " + clean + ".");
            if (target.pendingDelete()) throw new IllegalStateException("That profile is pending deletion and cannot be loaded.");

            if (hadActiveProfileFinal && previousProfileIdFinal != null) {
                if (saveDimensionFinal != null && !ProfileLobbyManager.PROFILE_LOBBY_DIMENSION.equals(saveDimensionFinal)) {
                    saveLocationSnapshot(connection, playerName, previousProfileIdFinal, saveDimensionFinal, saveXFinal, saveYFinal, saveZFinal, saveYawFinal, savePitchFinal);
                }
                if (vanillaSnapshotFinal != null && !vanillaSnapshotFinal.isBlank()) {
                    try (var ps = connection.prepareStatement("insert into profile_vanilla_state (profile_id, player_uuid, vanilla_snbt, updated_at) values (?, ?, ?, now()) " +
                            "on conflict (profile_id) do update set vanilla_snbt = excluded.vanilla_snbt, updated_at = now()")) {
                        ps.setObject(1, previousProfileIdFinal);
                        ps.setObject(2, playerUuid);
                        ps.setString(3, vanillaSnapshotFinal);
                        ps.executeUpdate();
                    }
                }
            }

            setActive(connection, playerUuid, target.profileId());
            String targetSnbt = VanillaProfileStateManager.loadSnbt(connection, target.profileId());
            CobblemonProfileStorageBridge.prefetchProfileStores(target.profileId(), playerUuid, registryAccess);
            ProfileRecord active = new ProfileRecord(target.profileId(), target.playerUuid(), target.profileName(), target.gameMode(), target.monotypeType(), true, false, null);

            player.server.execute(() -> {
                if (player.hasDisconnected()) return;
                try {
                    if (hadActiveProfileFinal && previousProfileIdFinal != null) {
                        CobblemonProfileStorageBridge.evictProfileStores(previousProfileIdFinal);
                    }
                    ACTIVE.put(playerUuid, active);
                    ProfileLobbyManager.leaveLobby(player);
                    VanillaProfileStateManager.applySnbt(player, targetSnbt);
                    long cobblemonStart = System.currentTimeMillis();
                    CobblemonProfileStorageBridge.loadActiveProfileStores(player);
                    System.out.println("[PROFILE] loadActiveProfileStores total took " + (System.currentTimeMillis() - cobblemonStart) + "ms for " + player.getGameProfile().getName());

                    long sessionStart = System.currentTimeMillis();
                    ProfileSessionLoader.load(player);
                    System.out.println("[PROFILE] ProfileSessionLoader.load took " + (System.currentTimeMillis() - sessionStart) + "ms for " + player.getGameProfile().getName());

                    teleportToSavedLocation(player);
                    if (callback != null) callback.accept("Loaded profile " + active.profileName() + " [" + active.gameMode().displayName() + modeSuffix(active) + "].");
                } catch (Exception e) {
                    e.printStackTrace();
                    if (callback != null) callback.accept("Could not switch profile. Check console/database logs.");
                }
            });
        }).exceptionally(throwable -> {
            String message = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
            if (message == null || message.isBlank()) message = "Could not switch profile. Check console/database logs.";
            final String finalMessage = message;
            player.server.execute(() -> {
                if (callback != null) callback.accept(finalMessage);
            });
            return null;
        });
    }

    public static String deleteBlocking(ServerPlayer player, String name) {
        if (player == null) return "Could not delete profile.";
        String clean = cleanName(name);
        if (clean == null) return "Invalid profile name.";
        if (!DatabaseManager.isEnabled()) return "Profiles require the SQL database to be enabled.";
        try {
            Connection connection = DatabaseManager.getConnection();
            ProfileRecord target = readByName(connection, player.getUUID(), clean);
            if (target == null) return "No profile named " + clean + ".";
            ProfileLimit limit = limitBlocking(player);
            if (limit.instantDelete()) {
                try (var ps = connection.prepareStatement("update player_profiles set deleted_at = now(), is_pending_delete = false where id = ?")) {
                    ps.setObject(1, target.profileId());
                    ps.executeUpdate();
                }
                if (target.active()) ACTIVE.remove(player.getUUID());
                return "Deleted profile " + target.profileName() + ".";
            }
            try (var ps = connection.prepareStatement("update player_profiles set is_pending_delete = true, delete_available_at = now() + interval '30 minutes' where id = ?")) {
                ps.setObject(1, target.profileId());
                ps.executeUpdate();
            }
            return "Profile " + target.profileName() + " is queued for deletion. It frees the slot in 30 minutes.";
        } catch (Exception e) { e.printStackTrace(); return "Could not delete profile. Check console/database logs."; }
    }

    public static String finalizePendingDeletesBlocking(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return "";
        try {
            Connection connection = DatabaseManager.getConnection();
            try (var ps = connection.prepareStatement("update player_profiles set deleted_at = now(), is_pending_delete = false where player_uuid = ? and is_pending_delete = true and delete_available_at <= now()")) {
            ps.setObject(1, player.getUUID());
            int rows = ps.executeUpdate();
            return rows > 0 ? "Finalized " + rows + " queued profile deletion(s)." : "";
            }
        } catch (Exception e) { e.printStackTrace(); return ""; }
    }


    public static void saveActiveLocation(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled() || !hasActiveProfile(player)) return;
        UUID profileId = activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return;

        String dimension = player.serverLevel().dimension().location().toString();
        if (ProfileLobbyManager.PROFILE_LOBBY_DIMENSION.equals(dimension) || isInMainMenu(player)) {
            return;
        }

        saveLocationSnapshotBlocking(
                player.getGameProfile().getName(),
                profileId,
                dimension,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
    }

    public static void saveActiveLocationAsync(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled() || !hasActiveProfile(player)) return;
        UUID profileId = activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return;

        String dimension = player.serverLevel().dimension().location().toString();
        if (ProfileLobbyManager.PROFILE_LOBBY_DIMENSION.equals(dimension) || isInMainMenu(player)) {
            return;
        }

        String playerName = player.getGameProfile().getName();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        DatabaseManager.executeAsync("save active profile location", connection -> saveLocationSnapshot(connection, playerName, profileId, dimension, x, y, z, yaw, pitch));
    }

    private static void saveLocationSnapshotBlocking(String playerName, UUID profileId, String dimension, double x, double y, double z, float yaw, float pitch) {
        try {
            saveLocationSnapshot(DatabaseManager.getConnection(), playerName, profileId, dimension, x, y, z, yaw, pitch);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save profile location for " + playerName);
            e.printStackTrace();
        }
    }

    private static void saveLocationSnapshot(Connection connection, String playerName, UUID profileId, String dimension, double x, double y, double z, float yaw, float pitch) throws Exception {
        try (var ps = connection.prepareStatement(
                "update player_profiles set last_dimension = ?, last_x = ?, last_y = ?, last_z = ?, last_yaw = ?, last_pitch = ?, last_used_at = now() where id = ?")) {
            ps.setString(1, dimension);
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);
            ps.setFloat(5, yaw);
            ps.setFloat(6, pitch);
            ps.setObject(7, profileId);
            ps.executeUpdate();
        }
    }

    public static void teleportToSavedLocation(ServerPlayer player) {
        if (player == null || player.server == null || !DatabaseManager.isEnabled() || !hasActiveProfile(player)) return;
        UUID profileId = activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return;

        try {
            Connection connection = DatabaseManager.getConnection();
            try (var ps = connection.prepareStatement("select last_dimension, last_x, last_y, last_z, last_yaw, last_pitch from player_profiles where id = ?")) {
                ps.setObject(1, profileId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return;
                    String dimension = rs.getString("last_dimension");
                    if (dimension == null || dimension.isBlank() || ProfileLobbyManager.PROFILE_LOBBY_DIMENSION.equals(dimension)) {
                        teleportToFirstProfileFallback(player);
                        return;
                    }

                    net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.DIMENSION,
                                    net.minecraft.resources.ResourceLocation.parse(dimension)
                            );
                    net.minecraft.server.level.ServerLevel level = player.server.getLevel(key);
                    if (level == null) {
                        player.sendSystemMessage(Component.literal("Saved profile location dimension is missing: " + dimension + ". Sending you to spawn.").withStyle(ChatFormatting.YELLOW));
                        teleportToFirstProfileFallback(player);
                        return;
                    }

                    double x = rs.getDouble("last_x");
                    double y = rs.getDouble("last_y");
                    double z = rs.getDouble("last_z");
                    float yaw = rs.getFloat("last_yaw");
                    float pitch = rs.getFloat("last_pitch");
                    player.teleportTo(level, x, y, z, yaw, pitch);
                    player.setYRot(yaw);
                    player.setYHeadRot(yaw);
                    player.setXRot(pitch);
                    player.resetFallDistance();
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to restore profile location for " + player.getGameProfile().getName());
            e.printStackTrace();
        }
    }

    private static void teleportToFirstProfileFallback(ServerPlayer player) {
        if (player == null || player.server == null) return;

        TeleportLocation configuredSpawn = TeleportConfig.getSpawn();
        if (configuredSpawn != null && TeleportConfig.teleport(player, configuredSpawn)) {
            player.resetFallDistance();
            return;
        }

        net.minecraft.server.level.ServerLevel level = player.server.overworld();
        net.minecraft.core.BlockPos spawn = level.getSharedSpawnPos();
        int x = spawn.getX();
        int z = spawn.getZ();
        int y = Math.max(level.getMinBuildHeight() + 1, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
        SafeTeleportManager.teleportUncheckedNoBack(player, level, x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
        player.resetFallDistance();
        player.sendSystemMessage(Component.literal("No saved location yet, so you were sent to server spawn.").withStyle(ChatFormatting.YELLOW));
    }

    private static void ensurePlayerRow(Connection connection, ServerPlayer player) throws Exception {
        try (var ps = connection.prepareStatement("insert into players (uuid, username, last_seen) values (?, ?, now()) on conflict (uuid) do update set username = excluded.username, last_seen = now()")) {
            ps.setObject(1, player.getUUID());
            ps.setString(2, player.getGameProfile().getName());
            ps.executeUpdate();
        }
    }

    private static void syncLimitFromLuckPerms(Connection connection, ServerPlayer player) throws Exception {
        int max = DEFAULT_MAX_PROFILES;
        boolean instant = false;
        if (LuckPermsHook.hasPermission(player, "champutils.profiles.3")) max = Math.max(max, 3);
        if (LuckPermsHook.hasPermission(player, "champutils.profiles.4")) max = Math.max(max, 4);
        if (LuckPermsHook.hasPermission(player, "champutils.profiles.5")) max = Math.max(max, 5);
        if (LuckPermsHook.hasPermission(player, "champutils.profiles.6")) max = Math.max(max, 6);
        if (LuckPermsHook.hasPermission(player, "champutils.profiles.instant_delete")) instant = true;
        try (var ps = connection.prepareStatement("insert into player_profile_limits (player_uuid, max_profiles, instant_delete, source, updated_at) values (?, ?, ?, 'LUCKPERMS', now()) " +
                "on conflict (player_uuid) do update set max_profiles = excluded.max_profiles, instant_delete = excluded.instant_delete, source = excluded.source, updated_at = now()")) {
            ps.setObject(1, player.getUUID());
            ps.setInt(2, max);
            ps.setBoolean(3, instant);
            ps.executeUpdate();
        }
    }

    private static int countLiveProfiles(Connection connection, UUID playerUuid) throws Exception {
        try (var ps = connection.prepareStatement("select count(*) as total from player_profiles where player_uuid = ? and deleted_at is null and (is_pending_delete = false or delete_available_at > now())")) {
            ps.setObject(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt("total") : 0; }
        }
    }

    private static ProfileRecord readActive(Connection connection, UUID playerUuid) throws Exception {
        try (var ps = connection.prepareStatement("select p.id, p.player_uuid, p.name, p.mode, p.monotype, true as active, p.is_pending_delete, p.delete_available_at " +
                "from player_active_profiles a join player_profiles p on p.id = a.profile_id where a.player_uuid = ? and p.deleted_at is null limit 1")) {
            ps.setObject(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? fromResultSet(rs) : null; }
        }
    }

    private static ProfileRecord readByName(Connection connection, UUID playerUuid, String name) throws Exception {
        try (var ps = connection.prepareStatement("select p.id, p.player_uuid, p.name, p.mode, p.monotype, (a.profile_id is not null) as active, p.is_pending_delete, p.delete_available_at " +
                "from player_profiles p left join player_active_profiles a on a.player_uuid = p.player_uuid and a.profile_id = p.id " +
                "where p.player_uuid = ? and lower(p.name) = lower(?) and p.deleted_at is null limit 1")) {
            ps.setObject(1, playerUuid);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? fromResultSet(rs) : null; }
        }
    }

    private static void setActive(Connection connection, UUID playerUuid, UUID profileId) throws Exception {
        try (var ps = connection.prepareStatement("insert into player_active_profiles (player_uuid, profile_id, updated_at) values (?, ?, now()) on conflict (player_uuid) do update set profile_id = excluded.profile_id, updated_at = now()")) {
            ps.setObject(1, playerUuid);
            ps.setObject(2, profileId);
            ps.executeUpdate();
        }
        try (var ps = connection.prepareStatement("update player_profiles set last_used_at = now() where id = ?")) {
            ps.setObject(1, profileId);
            ps.executeUpdate();
        }
    }


    public static String convertActiveToNormalBlocking(ServerPlayer player) {
        ProfileRecord record = active(player);
        if (player == null || record == null) return "No active profile loaded.";
        if (record.gameMode() == ProfileGameMode.NORMAL) return "This profile is already Normal.";
        if (!DatabaseManager.isEnabled()) return "Profiles require SQL.";
        try (var ps = DatabaseManager.getConnection().prepareStatement("update player_profiles set mode = 'NORMAL', monotype = null, metadata = metadata || jsonb_build_object('converted_to_normal_at', now()::text, 'converted_from_mode', ?) where id = ?")) {
            ps.setString(1, record.gameMode().name());
            ps.setObject(2, record.profileId());
            ps.executeUpdate();
            ACTIVE.put(player.getUUID(), new ProfileRecord(record.profileId(), record.playerUuid(), record.profileName(), ProfileGameMode.NORMAL, null, true, false, null));
            return "Converted " + record.profileName() + " to Normal. This cannot be changed back into a special profile.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Could not convert profile. Check console/database logs.";
        }
    }

    public static ProfileGameMode modeOfProfileIdBlocking(String profileId) {
        if (profileId == null || profileId.isBlank() || !DatabaseManager.isEnabled()) return ProfileGameMode.NORMAL;
        try (var ps = DatabaseManager.getConnection().prepareStatement("select mode from player_profiles where id = ? and deleted_at is null")) {
            ps.setObject(1, java.util.UUID.fromString(profileId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return ProfileGameMode.parse(rs.getString("mode"));
            }
        } catch (Exception ignored) {}
        return ProfileGameMode.NORMAL;
    }

    private static ProfileRecord createProfile(Connection connection, ServerPlayer player, String name, ProfileGameMode mode, String monotypeType, boolean active) throws Exception {
        try (var ps = connection.prepareStatement("insert into player_profiles (player_uuid, name, mode, monotype) values (?, ?, ?, ?) returning id, player_uuid, name, mode, monotype, false as active, is_pending_delete, delete_available_at")) {
            ps.setObject(1, player.getUUID());
            ps.setString(2, name);
            ps.setString(3, mode.name());
            ps.setString(4, mode == ProfileGameMode.MONOTYPE ? normalizeType(monotypeType) : null);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return fromResultSet(rs); }
        }
        throw new IllegalStateException("Profile insert returned no row.");
    }

    private static ProfileRecord fromResultSet(ResultSet rs) throws Exception {
        return new ProfileRecord(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("player_uuid"),
                rs.getString("name"),
                ProfileGameMode.parse(rs.getString("mode")),
                rs.getString("monotype"),
                rs.getBoolean("active"),
                rs.getBoolean("is_pending_delete"),
                rs.getObject("delete_available_at", OffsetDateTime.class)
        );
    }

    private static String cleanName(String raw) {
        if (raw == null) return null;
        String clean = raw.trim();
        if (!clean.matches("[A-Za-z0-9_]{3,16}")) return null;
        return clean;
    }

    private static String normalizeType(String raw) {
        if (raw == null) return null;
        String clean = raw.trim().toLowerCase();
        return clean.isBlank() ? null : clean;
    }

    public static String modeSuffix(ProfileRecord record) {
        if (record != null && record.gameMode() == ProfileGameMode.MONOTYPE && record.monotypeType() != null) return ": " + record.monotypeType();
        return "";
    }
}
