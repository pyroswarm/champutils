package com.champutils.profile;

import com.champutils.battle.BattleStateManager;
import com.champutils.badge.BadgeManager;
import com.champutils.badge.BadgeType;
import com.champutils.megaboss.MegaBossBattleListener;

import com.champutils.chat.ChatPreferenceManager;
import com.champutils.config.Config;
import com.champutils.claims.LandClaimRepository;
import com.champutils.database.DatabaseManager;
import com.champutils.teleport.SafeTeleportManager;
import com.champutils.teleport.TeleportConfig;
import com.champutils.teleport.TeleportLocation;
import com.champutils.menu.ProfileSelectionMenu;
import com.champutils.permissions.LuckPermsHook;
import com.champutils.territory.TerritoryRegionWipeManager;
import com.champutils.leaderboard.ProfileLeaderboardRepository;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class PlayerProfileManager {
    public static final int DEFAULT_MAX_PROFILES = 2;

    private static final Map<UUID, ProfileRecord> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SWITCHING = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, ProfileRecord>> PROFILE_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, CachedProfileList> PROFILE_LIST_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, ProfileLimit> LIMIT_CACHE = new ConcurrentHashMap<>();
    private static final long PROFILE_LIST_CACHE_TTL_MILLIS = 15_000L;
    private static final long PROFILE_LIMIT_CACHE_TTL_MILLIS = 60_000L;
    private static final Map<UUID, String> VANILLA_STATE_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, SavedLocationSnapshot> SAVED_LOCATION_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Object> CREATE_LOCKS = new ConcurrentHashMap<>();

    private PlayerProfileManager() {}

    private static long timing(String operation, Runnable runnable) {
        long start = System.currentTimeMillis();
        try {
            runnable.run();
        } finally {
            System.out.println("[PROFILE-TIMING] " + operation + " took " + (System.currentTimeMillis() - start) + "ms");
        }
        return System.currentTimeMillis() - start;
    }


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

    private record SavedLocationSnapshot(
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            boolean useFallback
    ) {}

    private record CachedProfileList(
            List<ProfileRecord> profiles,
            long cachedAtMillis
    ) {}

    private static void cacheProfile(ProfileRecord record) {
        if (record == null || record.playerUuid() == null || record.profileName() == null) return;
        PROFILE_CACHE.computeIfAbsent(record.playerUuid(), ignored -> new ConcurrentHashMap<>())
                .put(record.profileName().toLowerCase(), record);
    }

    private static void cacheProfileList(UUID playerUuid, List<ProfileRecord> profiles) {
        if (playerUuid == null || profiles == null) return;
        for (ProfileRecord record : profiles) cacheProfile(record);
        PROFILE_LIST_CACHE.put(playerUuid, new CachedProfileList(List.copyOf(profiles), System.currentTimeMillis()));
    }

    private static List<ProfileRecord> cachedProfileList(UUID playerUuid) {
        CachedProfileList cached = playerUuid == null ? null : PROFILE_LIST_CACHE.get(playerUuid);
        if (cached == null || System.currentTimeMillis() - cached.cachedAtMillis() > PROFILE_LIST_CACHE_TTL_MILLIS) return null;
        return cached.profiles();
    }

    private static void updateCachedActive(UUID playerUuid, UUID activeProfileId) {
        CachedProfileList cached = playerUuid == null ? null : PROFILE_LIST_CACHE.get(playerUuid);
        if (cached == null) return;
        List<ProfileRecord> updated = new ArrayList<>();
        for (ProfileRecord r : cached.profiles()) {
            if (r == null) continue;
            updated.add(new ProfileRecord(r.profileId(), r.playerUuid(), r.profileName(), r.gameMode(), r.monotypeType(), activeProfileId != null && activeProfileId.equals(r.profileId()), r.pendingDelete(), r.deleteAvailableAt()));
        }
        cacheProfileList(playerUuid, updated);
    }

    private static void clearProfileCache(UUID playerUuid) {
        if (playerUuid != null) {
            PROFILE_CACHE.remove(playerUuid);
            PROFILE_LIST_CACHE.remove(playerUuid);
            LIMIT_CACHE.remove(playerUuid);
        }
    }

    static void cacheVanillaState(UUID profileId, String snbt) {
        if (profileId == null) return;
        if (snbt == null || snbt.isBlank()) {
            VANILLA_STATE_CACHE.remove(profileId);
            return;
        }
        VANILLA_STATE_CACHE.put(profileId, snbt);
    }

    static void invalidateVanillaStateCache(UUID profileId) {
        if (profileId != null) VANILLA_STATE_CACHE.remove(profileId);
    }

    private static ProfileRecord cachedProfileByName(UUID playerUuid, String name) {
        if (playerUuid == null || name == null) return null;
        Map<String, ProfileRecord> cache = PROFILE_CACHE.get(playerUuid);
        return cache == null ? null : cache.get(name.toLowerCase());
    }

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
                statement.executeUpdate("alter table player_profile_limits add column if not exists fast_delete boolean not null default false");
                statement.executeUpdate("alter table player_profile_limits add column if not exists deletion_delay_minutes integer not null default 30");
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

                // Profile switch hot-path indexes. These are safe if they already exist and keep
                // profile lookup, active lookup, profile-state lookup, and location lookup indexed.
                statement.executeUpdate("create index if not exists idx_player_profiles_player_uuid on player_profiles(player_uuid)");
                statement.executeUpdate("create index if not exists idx_player_profiles_player_id on player_profiles(player_uuid, id) where deleted_at is null");
                statement.executeUpdate("create index if not exists idx_player_profiles_player_last_used on player_profiles(player_uuid, last_used_at) where deleted_at is null");
                // profile_vanilla_state and profile_cobblemon_storage create their own indexes in their schema managers.
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
                statement.executeUpdate("create table if not exists profile_land_claims (" +
                        "id uuid primary key default gen_random_uuid(), profile_id uuid not null references player_profiles(id) on delete cascade, " +
                        "player_uuid uuid not null references players(uuid) on delete cascade, owner_name text not null, server_id text not null, world_name text not null, world_key text not null, " +
                        "min_x integer not null, max_x integer not null, min_z integer not null, max_z integer not null, settings jsonb not null default '{}'::jsonb, " +
                        "created_at timestamptz not null default now(), updated_at timestamptz not null default now())");
                statement.executeUpdate("create index if not exists idx_profile_land_claims_profile on profile_land_claims(profile_id)");
                statement.executeUpdate("create index if not exists idx_profile_land_claims_world_bounds on profile_land_claims(server_id, world_name, min_x, max_x, min_z, max_z)");

                // Deleted profiles are intentionally hard-deleted so their cascaded data,
                // land claims, and leaderboard rows are fully removed instead of lingering
                // behind a soft-delete flag.
                statement.executeUpdate("delete from player_profiles where deleted_at is not null");
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

    UUID playerUuid = player.getUUID();
    String playerName = player.getGameProfile().getName();
    clearActiveForMenu(player);

    // Player joins used to do ensurePlayerRow, LuckPerms limit sync, profile list, and default
    // creation on the server thread. That made joins/profile-menu opening spike ticks. Keep the
    // network/database prep in the database executor and only touch UI/player state back on the
    // server thread.
    final boolean[] createdDefault = new boolean[] { false };
    DatabaseManager.runAsync("async profile join prep", connection -> {
        long start = System.currentTimeMillis();
        ensurePlayerRow(connection, player);
        syncLimitFromLuckPerms(connection, player);

        if (countLiveProfiles(connection, playerUuid) == 0) {
            createProfile(connection, player, "Default", ProfileGameMode.NORMAL, null, false);
            createdDefault[0] = true;
        }
        readProfiles(connection, playerUuid);
        readLimit(connection, player);
        System.out.println("[PROFILE-TIMING] async join profile prep/cache warm took " + (System.currentTimeMillis() - start) + "ms for " + playerName);
    }).whenComplete((ignored, error) -> player.server.execute(() -> {
        if (player.hasDisconnected()) return;
        if (error != null) {
            error.printStackTrace();
            ACTIVE.put(playerUuid, new ProfileRecord(playerUuid, playerUuid, "Fallback", ProfileGameMode.NORMAL, null, true, false, null));
            player.sendSystemMessage(Component.literal("Could not prepare your SQL profiles. Check console/database logs.").withStyle(ChatFormatting.RED));
            return;
        }
        if (createdDefault[0]) {
            player.sendSystemMessage(Component.literal("Created your first profile: Default.").withStyle(ChatFormatting.GREEN));
        }
        ProfileMainMenuManager.enter(player, false);
        ProfileSelectionMenu.open(player);
    }));
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
        if (player == null || !DatabaseManager.isEnabled()) return new ProfileLimit(DEFAULT_MAX_PROFILES, false, false, 30);
        ProfileLimit cached = LIMIT_CACHE.get(player.getUUID());
        if (cached != null) return cached;
        try {
            return readLimit(DatabaseManager.getConnection(), player);
        } catch (Exception e) { e.printStackTrace(); }
        return new ProfileLimit(DEFAULT_MAX_PROFILES, false, false, 30);
    }

    public record ProfileLimit(int maxProfiles, boolean instantDelete, boolean fastDelete, int deletionDelayMinutes) {}


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
        List<ProfileRecord> cached = cachedProfileList(player.getUUID());
        if (cached != null) return new ArrayList<>(cached);
        try {
            profiles.addAll(readProfiles(DatabaseManager.getConnection(), player.getUUID()));
        }
        catch (Exception e) { e.printStackTrace(); }
        return profiles;
    }

    public static String createBlocking(ServerPlayer player, String name, ProfileGameMode mode, String monotypeType) {
        if (player == null) return "Could not create profile.";

        Object createLock = CREATE_LOCKS.computeIfAbsent(player.getUUID(), ignored -> new Object());
        synchronized (createLock) {
            return createBlockingLocked(player, name, mode, monotypeType);
        }
    }

    private static String createBlockingLocked(ServerPlayer player, String name, ProfileGameMode mode, String monotypeType) {
        String clean = cleanName(name);
        if (clean == null) return "Profile names must be 3-16 letters/numbers/underscore.";
        if (mode == ProfileGameMode.MONOTYPE && (monotypeType == null || monotypeType.isBlank())) return "Monotype profiles need a type, example: /profiles create FireRun monotype fire";
        if (!DatabaseManager.isEnabled()) return "Profiles require the SQL database to be enabled.";
        try {
            long createTotalStart = System.currentTimeMillis();
            long connectionStart = System.currentTimeMillis();
            Connection connection = DatabaseManager.getConnection();
            System.out.println("[PROFILE-TIMING] createBlocking.connection acquisition took " + (System.currentTimeMillis() - connectionStart) + "ms");

            long ensureStart = System.currentTimeMillis();
            ensurePlayerRow(connection, player);
            System.out.println("[PROFILE-TIMING] createBlocking.ensurePlayerRow took " + (System.currentTimeMillis() - ensureStart) + "ms");

            long limitSyncStart = System.currentTimeMillis();
            syncLimitFromLuckPerms(connection, player);
            System.out.println("[PROFILE-TIMING] createBlocking.syncLimitFromLuckPerms took " + (System.currentTimeMillis() - limitSyncStart) + "ms");

            long pendingDeleteStart = System.currentTimeMillis();
            finalizePendingDeletesBlocking(player);
            System.out.println("[PROFILE-TIMING] createBlocking.finalizePendingDeletes took " + (System.currentTimeMillis() - pendingDeleteStart) + "ms");

            long existingStart = System.currentTimeMillis();
            ProfileRecord existing = readByName(connection, player.getUUID(), clean);
            System.out.println("[PROFILE-TIMING] createBlocking.existingProfileLookup took " + (System.currentTimeMillis() - existingStart) + "ms");
            if (existing != null) {
                if (existing.pendingDelete()) return "That profile color is still pending deletion.";
                return "You already have a profile named " + clean + ".";
            }

            long limitStart = System.currentTimeMillis();
            ProfileLimit limit = limitBlocking(player);
            int liveProfiles = countLiveProfiles(connection, player.getUUID());
            System.out.println("[PROFILE-TIMING] createBlocking.limit/count lookup took " + (System.currentTimeMillis() - limitStart) + "ms");
            if (liveProfiles >= limit.maxProfiles()) return "You already have the max of " + limit.maxProfiles() + " profiles.";

            long createTimingStart = System.currentTimeMillis();
            ProfileRecord created = createProfile(connection, player, clean, mode, normalizeType(monotypeType), false);
            System.out.println("[PROFILE-TIMING] createBlocking.ProfileRepository.createProfile insert took " + (System.currentTimeMillis() - createTimingStart) + "ms");

            long defaultCacheStart = System.currentTimeMillis();
            clearProfileCache(player.getUUID());
            cacheProfile(created);
            // Do not cache an empty vanilla state here. A newly created profile may receive
            // starter items or player-earned items later in the same server session, and a
            // stale "{}" cache entry would make future switches clear the live inventory
            // instead of reading the saved profile_vanilla_state row from SQL.
            VANILLA_STATE_CACHE.remove(created.profileId());
            SAVED_LOCATION_CACHE.put(created.profileId(), new SavedLocationSnapshot(null, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, true));
            System.out.println("[PROFILE-TIMING] createBlocking.default profile cache took " + (System.currentTimeMillis() - defaultCacheStart) + "ms");
            System.out.println("[PROFILE-TIMING] createBlocking total took " + (System.currentTimeMillis() - createTotalStart) + "ms");
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
                ProfilePlaytimeManager.flushPlayerAsyncBestEffort(player);
                saveActiveLocationAsync(player);
                VanillaProfileStateManager.saveAsync(player);
                CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player);
                if (previousProfileId != null) CobblemonProfileStorageBridge.evictProfileStores(previousProfileId);
                ChatPreferenceManager.saveAsync(player.getUUID(), ChatPreferenceManager.get(player.getUUID()));
                ProfileSessionLoader.unload(player);
            }
            setActive(connection, player.getUUID(), target.profileId());
            ProfileRecord active = new ProfileRecord(target.profileId(), target.playerUuid(), target.profileName(), target.gameMode(), target.monotypeType(), true, false, null);
            ACTIVE.put(player.getUUID(), active);
            cacheProfile(active);
            updateCachedActive(player.getUUID(), active.profileId());
            ProfilePlaytimeManager.warmCacheAsync(active.profileId());
            ProfilePlaytimeManager.recordCurrentSession(player);
            ProfileLobbyManager.leaveLobby(player);
            VanillaProfileStateManager.load(player);
            CobblemonProfileStorageBridge.loadActiveProfileStores(player);
            ProfileSessionLoader.load(player);
            com.champutils.cosmetic.TitleRegistry.unlockProfileStarter(player);
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
        if (SWITCHING.putIfAbsent(playerUuid, Boolean.TRUE) != null) {
            if (callback != null) callback.accept("Profile switch already in progress. Please wait a moment.");
            return;
        }

        long switchStart = System.currentTimeMillis();
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
            ProfilePlaytimeManager.flushPlayerAsyncBestEffort(player);
            long snapshotStart = System.currentTimeMillis();
            saveDimension = player.serverLevel().dimension().location().toString();
            saveX = player.getX();
            saveY = player.getY();
            saveZ = player.getZ();
            saveYaw = player.getYRot();
            savePitch = player.getXRot();
            vanillaSnapshot = VanillaProfileStateManager.snapshotSnbt(player);
            System.out.println("[PROFILE-TIMING] switchAsync.snapshot old vanilla/location took " + (System.currentTimeMillis() - snapshotStart) + "ms");

            timing("switchAsync.forceSaveProfileStores old profile", () -> CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player));
            timing("switchAsync.ChatPreferenceManager.saveAsync", () -> ChatPreferenceManager.saveAsync(player.getUUID(), ChatPreferenceManager.get(player.getUUID())));
            timing("switchAsync.ProfileSessionLoader.unload", () -> ProfileSessionLoader.unload(player));
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
            long sqlStart = System.currentTimeMillis();
            ProfileRecord target = cachedProfileByName(playerUuid, clean);
            boolean cacheHit = target != null;
            if (target == null) {
                target = readByName(connection, playerUuid, clean);
                cacheProfile(target);
            }
            if (target == null) throw new IllegalArgumentException("No profile named " + clean + ".");
            if (target.pendingDelete()) throw new IllegalStateException("That profile is pending deletion and cannot be loaded.");
            System.out.println("[PROFILE-TIMING] SQL profile lookup took " + (System.currentTimeMillis() - sqlStart) + "ms cacheHit=" + cacheHit);

            if (hadActiveProfileFinal && previousProfileIdFinal != null) {
                long oldSaveStart = System.currentTimeMillis();
                if (saveDimensionFinal != null && !ProfileLobbyManager.PROFILE_LOBBY_DIMENSION.equals(saveDimensionFinal)) {
                    saveLocationSnapshot(connection, playerName, previousProfileIdFinal, saveDimensionFinal, saveXFinal, saveYFinal, saveZFinal, saveYawFinal, savePitchFinal);
                    SAVED_LOCATION_CACHE.put(previousProfileIdFinal, new SavedLocationSnapshot(saveDimensionFinal, saveXFinal, saveYFinal, saveZFinal, saveYawFinal, savePitchFinal, false));
                }
                if (vanillaSnapshotFinal != null && !vanillaSnapshotFinal.isBlank()) {
                    VANILLA_STATE_CACHE.put(previousProfileIdFinal, vanillaSnapshotFinal);
                    try (var ps = connection.prepareStatement("insert into profile_vanilla_state (profile_id, player_uuid, vanilla_snbt, updated_at) values (?, ?, ?, now()) " +
                            "on conflict (profile_id) do update set vanilla_snbt = excluded.vanilla_snbt, updated_at = now()")) {
                        ps.setObject(1, previousProfileIdFinal);
                        ps.setObject(2, playerUuid);
                        ps.setString(3, vanillaSnapshotFinal);
                        ps.executeUpdate();
                    }
                }
                System.out.println("[PROFILE-TIMING] SQL old profile location/vanilla save took " + (System.currentTimeMillis() - oldSaveStart) + "ms");
            }

            long sqlLoadStart = System.currentTimeMillis();
            // Do not block the player's switch on the remote SQL active-profile write. The active
            // profile is applied from the already-resolved target record below, then persisted in
            // a separate DB task. This removes the common 60-80ms WAN/commit delay from switchAsync.
            System.out.println("[PROFILE-TIMING] SQL active profile update skipped critical path; queued async persistence");

            long vanillaLoadStart = System.currentTimeMillis();
            String targetSnbt = VANILLA_STATE_CACHE.get(target.profileId());
            boolean vanillaCacheHit = targetSnbt != null;
            if (!vanillaCacheHit) {
                targetSnbt = VanillaProfileStateManager.loadSnbt(connection, target.profileId());
                if (targetSnbt != null) VANILLA_STATE_CACHE.put(target.profileId(), targetSnbt);
            }
            System.out.println("[PROFILE-TIMING] SQL vanilla inventory/state load took " + (System.currentTimeMillis() - vanillaLoadStart) + "ms cacheHit=" + vanillaCacheHit);

            long locationLoadStart = System.currentTimeMillis();
            SavedLocationSnapshot savedLocationSnapshot = SAVED_LOCATION_CACHE.get(target.profileId());
            boolean locationCacheHit = savedLocationSnapshot != null;
            if (!locationCacheHit) {
                savedLocationSnapshot = loadSavedLocationSnapshot(connection, target.profileId());
                if (savedLocationSnapshot != null) SAVED_LOCATION_CACHE.put(target.profileId(), savedLocationSnapshot);
            }
            System.out.println("[PROFILE-TIMING] SQL saved location load took " + (System.currentTimeMillis() - locationLoadStart) + "ms cacheHit=" + locationCacheHit);
            System.out.println("[PROFILE-TIMING] SQL vanilla/location/party-prep section before party took " + (System.currentTimeMillis() - sqlLoadStart) + "ms");

            long playtimeLoadStart = System.currentTimeMillis();
            ProfilePlaytimeManager.loadCacheBlocking(connection, target.profileId());
            System.out.println("[PROFILE-TIMING] SQL profile playtime preload took " + (System.currentTimeMillis() - playtimeLoadStart) + "ms");

            long partyPrefetchStart = System.currentTimeMillis();
            CobblemonProfileStorageBridge.prefetchProfileStores(connection, target.profileId(), playerUuid, registryAccess);
            System.out.println("[PROFILE-TIMING] party prefetch took " + (System.currentTimeMillis() - partyPrefetchStart) + "ms");

            final String targetSnbtFinal = targetSnbt;
            final SavedLocationSnapshot savedLocationSnapshotFinal = savedLocationSnapshot;
            ProfileRecord active = new ProfileRecord(target.profileId(), target.playerUuid(), target.profileName(), target.gameMode(), target.monotypeType(), true, false, null);
            persistActiveProfileAsync(playerUuid, active.profileId(), playerName);

            player.server.execute(() -> {
                long activationStart = System.currentTimeMillis();
                if (player.hasDisconnected()) {
                    SWITCHING.remove(playerUuid);
                    return;
                }
                try {
                    if (hadActiveProfileFinal && previousProfileIdFinal != null) {
                        timing("server.execute.evict old Cobblemon stores", () -> CobblemonProfileStorageBridge.evictProfileStores(previousProfileIdFinal));
                    }
                    ACTIVE.put(playerUuid, active);
                    cacheProfile(active);
                    updateCachedActive(playerUuid, active.profileId());
                    ProfilePlaytimeManager.warmCacheAsync(active.profileId());
                    ProfilePlaytimeManager.recordCurrentSession(player);
                    timing("server.execute.ProfileLobbyManager.leaveLobby", () -> ProfileLobbyManager.leaveLobby(player));
                    timing("server.execute.VanillaProfileStateManager.applySnbt", () -> VanillaProfileStateManager.applySnbt(player, targetSnbtFinal));
                    timing("server.execute.loadActiveProfileStores", () -> CobblemonProfileStorageBridge.loadActiveProfileStores(player));
                    timing("server.execute.ProfileSessionLoader.loadCritical", () -> ProfileSessionLoader.loadCritical(player));
                    com.champutils.cosmetic.TitleRegistry.unlockProfileStarter(player);
                    timing("server.execute.teleportToSavedLocation", () -> teleportToSavedLocationSnapshot(player, savedLocationSnapshotFinal));

                    System.out.println("[PROFILE-TIMING] server.execute profile activation block took " + (System.currentTimeMillis() - activationStart) + "ms for " + playerName + " profile=" + active.profileId());

                    CompletableFuture
                            .supplyAsync(() -> ProfileSessionLoader.loadBackground(playerUuid, active.profileId(), playerName))
                            .whenComplete((snapshot, error) -> player.server.execute(() -> {
                                try {
                                    if (error != null) {
                                        System.err.println("[ChampUtils] Background profile session load failed for " + playerName + ": " + error.getMessage());
                                        error.printStackTrace();
                                    } else {
                                        ProfileSessionLoader.applyBackground(player, snapshot);
                                    }
                                    ProfileSessionLoader.loadDelayedNonCritical(player);
                                } finally {
                                    SWITCHING.remove(playerUuid);
                                    System.out.println("[PROFILE-TIMING] switchAsync total took " + (System.currentTimeMillis() - switchStart) + "ms for " + playerName);
                                }
                            }));

                    if (callback != null) callback.accept("Loaded profile " + active.profileName() + " [" + active.gameMode().displayName() + modeSuffix(active) + "].");
                } catch (Exception e) {
                    SWITCHING.remove(playerUuid);
                    e.printStackTrace();
                    if (callback != null) callback.accept("Could not switch profile. Check console/database logs.");
                }
            });
        }).exceptionally(throwable -> {
            SWITCHING.remove(playerUuid);
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
                if (target.active()) ProfilePlaytimeManager.flushPlayerBlockingBestEffort(player);
                hardDeleteProfile(connection, player, target.profileId());
                if (target.active()) ACTIVE.remove(player.getUUID());
                clearProfileCache(player.getUUID());
                ProfileLeaderboardRepository.invalidateCache();
                return "Deleted profile " + target.profileName() + ".";
            }
            int delayMinutes = Math.max(1, limit.deletionDelayMinutes());
            try (var ps = connection.prepareStatement("update player_profiles set is_pending_delete = true, delete_available_at = now() + (? * interval '1 minute') where id = ?")) {
                ps.setInt(1, delayMinutes);
                ps.setObject(2, target.profileId());
                ps.executeUpdate();
                clearProfileCache(player.getUUID());
            }
            return "Profile " + target.profileName() + " is queued for deletion. It frees the slot in " + formatMinutes(delayMinutes) + ".";
        } catch (Exception e) { e.printStackTrace(); return "Could not delete profile. Check console/database logs."; }
    }

    public static String cancelDeleteBlocking(ServerPlayer player, String name) {
        if (player == null) return "Could not cancel profile deletion.";
        String clean = cleanName(name);
        if (clean == null) return "Invalid profile name.";
        if (!DatabaseManager.isEnabled()) return "Profiles require the SQL database to be enabled.";
        try {
            Connection connection = DatabaseManager.getConnection();
            ProfileRecord target = readByName(connection, player.getUUID(), clean);
            if (target == null) return "No profile named " + clean + ".";
            if (!target.pendingDelete()) return "Profile " + target.profileName() + " is not queued for deletion.";
            try (var ps = connection.prepareStatement("update player_profiles set is_pending_delete = false, delete_available_at = null where id = ? and deleted_at is null")) {
                ps.setObject(1, target.profileId());
                ps.executeUpdate();
            }
            clearProfileCache(player.getUUID());
            return "Cancelled deletion for profile " + target.profileName() + ".";
        } catch (Exception e) { e.printStackTrace(); return "Could not cancel profile deletion. Check console/database logs."; }
    }

    private static String formatMinutes(int minutes) {
        if (minutes <= 0) return "now";
        if (minutes < 60) return minutes + " minute" + (minutes == 1 ? "" : "s");
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (mins == 0) return hours + " hour" + (hours == 1 ? "" : "s");
        return hours + "h " + mins + "m";
    }

    public static String finalizePendingDeletesBlocking(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return "";
        try {
            Connection connection = DatabaseManager.getConnection();
            List<UUID> finalizedProfileIds = new ArrayList<>();
            try (var select = connection.prepareStatement("select id from player_profiles where player_uuid = ? and is_pending_delete = true and delete_available_at <= now() and deleted_at is null")) {
                select.setObject(1, player.getUUID());
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        UUID profileId = (UUID) rs.getObject("id");
                        if (profileId != null) finalizedProfileIds.add(profileId);
                    }
                }
            }
            if (finalizedProfileIds.isEmpty()) return "";
            int rows = 0;
            ProfilePlaytimeManager.flushPlayerBlockingBestEffort(player);
            for (UUID profileId : finalizedProfileIds) {
                if (hardDeleteProfile(connection, player, profileId)) rows++;
            }
            clearProfileCache(player.getUUID());
            ProfileLeaderboardRepository.invalidateCache();
            return rows > 0 ? "Finalized " + rows + " queued profile deletion(s)." : "";
        } catch (Exception e) { e.printStackTrace(); return ""; }
    }

    private static boolean hardDeleteProfile(Connection connection, ServerPlayer player, UUID profileId) throws Exception {
        if (profileId == null) return false;

        // Remove territory files/regions tied to this profile and remove SQL land claims
        // immediately so protection caches cannot keep ghost claims alive until refresh.
        deletePersonalTerritoryForProfile(player, profileId);
        LandClaimRepository.deleteForProfileBlocking(profileId);

        try (var ps = connection.prepareStatement("delete from player_profiles where id = ?")) {
            ps.setObject(1, profileId);
            return ps.executeUpdate() > 0;
        }
    }

    private static void deletePersonalTerritoryForProfile(ServerPlayer player, UUID profileId) {
        if (player == null || player.server == null || profileId == null) return;
        try {
            TerritoryRegionWipeManager.enqueueDeleteForDeletedProfile(player.server, profileId, player.getUUID());
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to enqueue territory deletion for deleted profile " + profileId + ".");
            e.printStackTrace();
        }
    }


    public static void saveActiveLocation(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled() || !hasActiveProfile(player)) return;
        UUID profileId = activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return;

        if (BattleStateManager.isInBattle(player) || BattleStateManager.hasTrackedState(player) || MegaBossBattleListener.isPlayerInMegaBossBattle(player)) {
            // Never persist the player's temporary battle location as their profile spawn.
            return;
        }

        if (BattleStateManager.isInBattle(player) || BattleStateManager.hasTrackedState(player) || MegaBossBattleListener.isPlayerInMegaBossBattle(player)) {
            // Never persist the player's temporary battle location as their profile spawn.
            return;
        }

        String dimension = player.serverLevel().dimension().location().toString();
        if (ProfileLobbyManager.PROFILE_LOBBY_DIMENSION.equals(dimension) || isInMainMenu(player)) {
            return;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        saveLocationSnapshotBlocking(
                player.getGameProfile().getName(),
                profileId,
                dimension,
                x,
                y,
                z,
                yaw,
                pitch
        );
        SAVED_LOCATION_CACHE.put(profileId, new SavedLocationSnapshot(dimension, x, y, z, yaw, pitch, false));
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
        SAVED_LOCATION_CACHE.put(profileId, new SavedLocationSnapshot(dimension, x, y, z, yaw, pitch, false));
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

    private static SavedLocationSnapshot loadSavedLocationSnapshot(Connection connection, UUID profileId) throws Exception {
        if (connection == null || profileId == null) return null;
        try (var ps = connection.prepareStatement("select last_dimension, last_x, last_y, last_z, last_yaw, last_pitch from player_profiles where id = ?")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String dimension = rs.getString("last_dimension");
                if (dimension == null || dimension.isBlank() || ProfileLobbyManager.PROFILE_LOBBY_DIMENSION.equals(dimension)) {
                    return new SavedLocationSnapshot(null, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, true);
                }
                return new SavedLocationSnapshot(
                        dimension,
                        rs.getDouble("last_x"),
                        rs.getDouble("last_y"),
                        rs.getDouble("last_z"),
                        rs.getFloat("last_yaw"),
                        rs.getFloat("last_pitch"),
                        false
                );
            }
        }
    }

    private static void teleportToSavedLocationSnapshot(ServerPlayer player, SavedLocationSnapshot snapshot) {
        if (player == null || player.server == null || snapshot == null) return;
        if (snapshot.useFallback()) {
            teleportToFirstProfileFallback(player);
            return;
        }
        try {
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.ResourceLocation.parse(snapshot.dimension())
                    );
            net.minecraft.server.level.ServerLevel level = player.server.getLevel(key);
            if (level == null) {
                player.sendSystemMessage(Component.literal("Saved profile location dimension is missing: " + snapshot.dimension() + ". Sending you to spawn.").withStyle(ChatFormatting.YELLOW));
                teleportToFirstProfileFallback(player);
                return;
            }
            player.teleportTo(level, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch());
            player.setYRot(snapshot.yaw());
            player.setYHeadRot(snapshot.yaw());
            player.setXRot(snapshot.pitch());
            player.resetFallDistance();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to restore profile location snapshot for " + player.getGameProfile().getName());
            e.printStackTrace();
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
        if (LuckPermsHook.hasPermission(player, "champutils.profiles.vip")) max = Math.max(max, 3);
        if (LuckPermsHook.hasPermission(player, "champutils.profiles.vipplus")) max = Math.max(max, 4);
        if (LuckPermsHook.hasPermission(player, "champutils.profiles.3")) max = Math.max(max, 3);
        if (LuckPermsHook.hasPermission(player, "champutils.profiles.4")) max = Math.max(max, 4);
        if (LuckPermsHook.hasPermission(player, "champutils.profiles.5")) max = Math.max(max, 5);
        if (LuckPermsHook.hasPermission(player, "champutils.profiles.6")) max = Math.max(max, 6);
        boolean fast = LuckPermsHook.hasPermission(player, "champutils.profiles.fast_delete");
        if (LuckPermsHook.hasPermission(player, "champutils.profiles.vip") || LuckPermsHook.hasPermission(player, "champutils.profiles.vipplus") || LuckPermsHook.hasPermission(player, "champutils.profiles.instant_delete")) instant = true;
        int delayMinutes = instant ? 0 : (fast ? 5 : 30);
        try (var ps = connection.prepareStatement("insert into player_profile_limits (player_uuid, max_profiles, instant_delete, fast_delete, deletion_delay_minutes, source, updated_at) values (?, ?, ?, ?, ?, 'LUCKPERMS', now()) " +
                "on conflict (player_uuid) do update set max_profiles = excluded.max_profiles, instant_delete = excluded.instant_delete, fast_delete = excluded.fast_delete, deletion_delay_minutes = excluded.deletion_delay_minutes, source = excluded.source, updated_at = now()")) {
            ps.setObject(1, player.getUUID());
            ps.setInt(2, max);
            ps.setBoolean(3, instant);
            ps.setBoolean(4, fast);
            ps.setInt(5, delayMinutes);
            ps.executeUpdate();
        }
    }

    private static int countLiveProfiles(Connection connection, UUID playerUuid) throws Exception {
        try (var ps = connection.prepareStatement("select count(*) as total from player_profiles where player_uuid = ? and deleted_at is null and (is_pending_delete = false or delete_available_at > now())")) {
            ps.setObject(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt("total") : 0; }
        }
    }

    private static List<ProfileRecord> readProfiles(Connection connection, UUID playerUuid) throws Exception {
        List<ProfileRecord> profiles = new ArrayList<>();
        try (var statement = connection.prepareStatement(
                "select p.id, p.player_uuid, p.name, p.mode, p.monotype, (a.profile_id is not null) as active, p.is_pending_delete, p.delete_available_at " +
                        "from player_profiles p left join player_active_profiles a on a.player_uuid = p.player_uuid and a.profile_id = p.id " +
                        "where p.player_uuid = ? and p.deleted_at is null order by p.created_at asc")) {
            statement.setObject(1, playerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) profiles.add(fromResultSet(rs));
            }
        }
        cacheProfileList(playerUuid, profiles);
        return profiles;
    }

    private static ProfileLimit readLimit(Connection connection, ServerPlayer player) throws Exception {
        ensurePlayerRow(connection, player);
        syncLimitFromLuckPerms(connection, player);
        try (var ps = connection.prepareStatement("select max_profiles, instant_delete, fast_delete, deletion_delay_minutes from player_profile_limits where player_uuid = ?")) {
            ps.setObject(1, player.getUUID());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean instant = rs.getBoolean("instant_delete");
                    boolean fast = rs.getBoolean("fast_delete");
                    int delay = instant ? 0 : Math.max(1, rs.getInt("deletion_delay_minutes"));
                    ProfileLimit limit = new ProfileLimit(Math.max(DEFAULT_MAX_PROFILES, rs.getInt("max_profiles")), instant, fast, delay);
                    LIMIT_CACHE.put(player.getUUID(), limit);
                    return limit;
                }
            }
        }
        ProfileLimit fallback = new ProfileLimit(DEFAULT_MAX_PROFILES, false, false, 30);
        LIMIT_CACHE.put(player.getUUID(), fallback);
        return fallback;
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

    private static void persistActiveProfileAsync(UUID playerUuid, UUID profileId, String playerName) {
        if (playerUuid == null || profileId == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("persist active profile", connection -> {
            long start = System.currentTimeMillis();
            setActive(connection, playerUuid, profileId);
            System.out.println("[PROFILE-TIMING] async SQL active profile persist took " + (System.currentTimeMillis() - start) + "ms for " + playerName);
        });
    }

    private static void setActive(Connection connection, UUID playerUuid, UUID profileId) throws Exception {
        long connectionReady = System.currentTimeMillis();
        boolean oldAutoCommit = connection.getAutoCommit();
        long beginStart = System.currentTimeMillis();
        connection.setAutoCommit(false);
        System.out.println("[PROFILE-TIMING] setActive.transaction begin took " + (System.currentTimeMillis() - beginStart) + "ms");
        try {
            long upsertStart = System.currentTimeMillis();
            try (var ps = connection.prepareStatement("insert into player_active_profiles (player_uuid, profile_id, updated_at) values (?, ?, now()) " +
                    "on conflict (player_uuid) do update set profile_id = excluded.profile_id, updated_at = now()")) {
                ps.setObject(1, playerUuid);
                ps.setObject(2, profileId);
                ps.executeUpdate();
            }
            System.out.println("[PROFILE-TIMING] setActive.active upsert took " + (System.currentTimeMillis() - upsertStart) + "ms");

            long lastUsedStart = System.currentTimeMillis();
            try (var ps = connection.prepareStatement("update player_profiles set last_used_at = now() where id = ?")) {
                ps.setObject(1, profileId);
                ps.executeUpdate();
            }
            System.out.println("[PROFILE-TIMING] setActive.last_used update took " + (System.currentTimeMillis() - lastUsedStart) + "ms");

            long commitStart = System.currentTimeMillis();
            connection.commit();
            System.out.println("[PROFILE-TIMING] setActive.commit took " + (System.currentTimeMillis() - commitStart) + "ms");
        } catch (Exception e) {
            try { connection.rollback(); } catch (Exception ignored) {}
            throw e;
        } finally {
            try { connection.setAutoCommit(oldAutoCommit); } catch (Exception ignored) {}
        }
    }


    public static String convertActiveToNormalBlocking(ServerPlayer player) {
        ProfileRecord record = active(player);
        if (player == null || record == null) return "No active profile loaded.";
        if (record.gameMode() == ProfileGameMode.NORMAL) return "This profile is already Normal.";
        if (record.gameMode() == ProfileGameMode.ISLANDER && !hasCompletedIslanderConversionProgression(player)) {
            return "Islander profiles can only convert after all 8 gyms and the Elite 4 Champion are completed.";
        }
        if (!DatabaseManager.isEnabled()) return "Profiles require SQL.";

        int requiredDays = Config.profileConversion == null ? 3 : Config.profileConversion.minAgeDaysBeforeNormal;
        if (requiredDays < 0) requiredDays = 0;

        try (Connection connection = DatabaseManager.getConnection()) {
            if (requiredDays > 0) {
                try (var agePs = connection.prepareStatement("select created_at from player_profiles where id = ? and deleted_at is null limit 1")) {
                    agePs.setObject(1, record.profileId());
                    try (ResultSet rs = agePs.executeQuery()) {
                        if (!rs.next()) return "Could not find this profile in SQL.";

                        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
                        if (createdAt == null) return "Could not verify this profile's age. Conversion blocked for safety.";

                        OffsetDateTime now = OffsetDateTime.now(createdAt.getOffset());
                        OffsetDateTime eligibleAt = createdAt.plusDays(requiredDays);
                        if (now.isBefore(eligibleAt)) {
                            long hoursLeft = Math.max(1, ChronoUnit.HOURS.between(now, eligibleAt));
                            long daysLeft = hoursLeft / 24;
                            long remainderHours = hoursLeft % 24;
                            String remaining = daysLeft > 0
                                    ? daysLeft + "d " + remainderHours + "h"
                                    : hoursLeft + "h";
                            return "This " + record.gameMode().displayName() + " profile is too new to convert. It must exist for at least " + requiredDays + " day" + (requiredDays == 1 ? "" : "s") + ". Try again in about " + remaining + ".";
                        }
                    }
                }
            }

            try (var ps = connection.prepareStatement("update player_profiles set mode = 'NORMAL', monotype = null, metadata = metadata || jsonb_build_object('converted_to_normal_at', now()::text, 'converted_from_mode', ?, 'conversion_min_age_days', ?) where id = ?")) {
                ps.setString(1, record.gameMode().name());
                ps.setInt(2, requiredDays);
                ps.setObject(3, record.profileId());
                ps.executeUpdate();
                ACTIVE.put(player.getUUID(), new ProfileRecord(record.profileId(), record.playerUuid(), record.profileName(), ProfileGameMode.NORMAL, null, true, false, null));
                clearProfileCache(player.getUUID());
                return "Converted " + record.profileName() + " to Normal. This cannot be changed back into a special profile.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Could not convert profile. Check console/database logs.";
        }
    }

    private static boolean hasCompletedIslanderConversionProgression(ServerPlayer player) {
        if (player == null) return false;
        for (BadgeType badge : BadgeType.values()) {
            if (!BadgeManager.hasBadge(player, badge)) {
                return false;
            }
        }
        return true;
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
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ProfileRecord record = fromResultSet(rs);
                    cacheProfile(record);
                    return record;
                }
            }
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
