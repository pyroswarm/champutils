package com.champutils.profile;

import com.champutils.debug.ChampDebugManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Single routing point for ChampUtils profile-aware Cobblemon storage.
 *
 * Account UUIDs are only used to resolve the active ChampUtils profile. The
 * owner key for Cobblemon party/PC persistence is always profile_id.
 */
public final class CobblemonProfileStorageBridge {
    private static ProfileCobblemonSqlStoreFactory sqlFactory;
    private static boolean registered;

    private CobblemonProfileStorageBridge() {}

    public static synchronized void registerSqlFactory(MinecraftServer server) {
        if (registered) return;
        sqlFactory = new ProfileCobblemonSqlStoreFactory();
        sqlFactory.ensureSchema();
        Cobblemon.INSTANCE.getStorage().registerFactory(Priority.HIGHEST, sqlFactory);
        registered = true;
        System.out.println("[ChampUtils] Registered SQL-backed Cobblemon profile storage factory.");
    }

    public static synchronized void ensureSchemaAsync() {
        if (sqlFactory != null) {
            com.champutils.database.DatabaseManager.executeAsync("ensure Cobblemon SQL storage schema", connection -> sqlFactory.ensureSchema());
        }
    }

    public static UUID storageKey(UUID playerUuid) {
        if (playerUuid == null) return null;
        UUID profileId = PlayerProfileManager.activeProfileId(playerUuid);
        return profileId == null ? playerUuid : profileId;
    }

    public static boolean shouldRedirect(UUID requestedUuid) {
        UUID mapped = storageKey(requestedUuid);
        return mapped != null && requestedUuid != null && !Objects.equals(mapped, requestedUuid);
    }

    public static boolean isKnownProfileStorageKey(UUID uuid) {
        if (uuid == null) return false;
        for (PlayerProfileManager.ProfileRecord record : PlayerProfileManager.activeProfilesSnapshot()) {
            if (record != null && uuid.equals(record.profileId()) && !uuid.equals(record.playerUuid())) return true;
        }
        return false;
    }

    public static UUID accountUuidForProfile(UUID profileId) {
        if (profileId == null) return null;
        for (PlayerProfileManager.ProfileRecord record : PlayerProfileManager.activeProfilesSnapshot()) {
            if (record != null && profileId.equals(record.profileId())) {
                return record.playerUuid();
            }
        }
        return null;
    }

    public static void forceSaveActiveProfileStores(ServerPlayer player) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        forceSaveProfileStores(profileId, player);
    }

    public static void forceSaveActiveProfileStoresAsync(ServerPlayer player) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null || sqlFactory == null) return;
        sqlFactory.saveAsync(profileId, player.registryAccess());
    }

    public static void forceSaveProfileStores(UUID profileId, ServerPlayer player) {
        // Historical method name kept for compatibility, but it must be async.
        // Profile switching and profile-menu flows call this path; never do SQL here.
        if (profileId == null || player == null || sqlFactory == null) return;
        sqlFactory.saveAsync(profileId, player.registryAccess());
    }


    public static void prefetchProfileStores(UUID profileId, UUID accountUuid, net.minecraft.core.RegistryAccess registryAccess) {
        if (profileId == null || accountUuid == null || registryAccess == null || sqlFactory == null) return;
        // Profile switching only needs the active party. The PC can be huge, so loading it here
        // turns profile activation into a full PC SQL read + SNBT parse + Cobblemon hydration path.
        // Keep switchAsync fast by warming only the party; getPC/getPCForPlayer will lazily hydrate
        // the PC when the player actually opens/accesses PC storage.
        sqlFactory.prefetchParty(profileId, accountUuid, registryAccess);
    }

    public static void prefetchProfileStores(java.sql.Connection connection, UUID profileId, UUID accountUuid, net.minecraft.core.RegistryAccess registryAccess) {
        if (connection == null || profileId == null || accountUuid == null || registryAccess == null || sqlFactory == null) return;
        sqlFactory.prefetchParty(connection, profileId, accountUuid, registryAccess);
    }

    public static void evictProfileStores(UUID profileId) {
        if (sqlFactory != null) sqlFactory.evict(profileId);
    }


    public static boolean removePokemonFromCachedStores(UUID profileId, UUID pokemonUuid, Object pokemon) {
        return sqlFactory != null && sqlFactory.removePokemonFromCachedStores(profileId, pokemonUuid, pokemon);
    }

    public static boolean activeCachedStoresHaveSpecies(UUID profileId, String species, UUID excludePokemonUuid) {
        return sqlFactory != null && sqlFactory.hasSpeciesInCachedStores(profileId, species, excludePokemonUuid);
    }

    public static boolean hasSqlCachedStores(UUID profileId) {
        return sqlFactory != null && sqlFactory.hasCachedStores(profileId);
    }

    public static boolean hasSqlCachedParty(UUID profileId) {
        return sqlFactory != null && sqlFactory.isPartyLoaded(profileId);
    }

    public static void loadActiveProfileStores(ServerPlayer player) {
        loadActiveProfileStoresAndSync(player);
    }

    /**
     * Hydrates the active party immediately, sends it to the client, then completes only after
     * Cobblemon's broader player data sync has run on the server thread. Profile switching uses
     * the returned future as part of the survival quarantine so gameplay is not unlocked while
     * Cobblemon still sees an old/unknown profile store.
     */
    public static CompletableFuture<Void> loadActiveProfileStoresAndSync(ServerPlayer player) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) {
            done.complete(null);
            return done;
        }
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        long start = System.currentTimeMillis();

        // Profile activation must stay main-thread-light. Hydrate/send the party immediately so
        // throw-out, battles, and party UI work after switching, but DO NOT load/send the PC here.
        // Large PCs are lazily loaded by ProfileCobblemonSqlStoreFactory#getPC/getPCForPlayer on
        // first actual PC access instead of during every profile swap.
        long getPartyStart = System.currentTimeMillis();
        boolean cacheHitBefore = hasSqlCachedParty(profileId);
        var party = Cobblemon.INSTANCE.getStorage().getParty(profileId, player.registryAccess());
        ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Cobblemon get cached party took " + (System.currentTimeMillis() - getPartyStart) + "ms for " + player.getGameProfile().getName() + " profile=" + profileId + " cacheHitBefore=" + cacheHitBefore);

        long sendStart = System.currentTimeMillis();
        party.sendTo(player);
        ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Cobblemon party sendTo took " + (System.currentTimeMillis() - sendStart) + "ms for " + player.getGameProfile().getName() + " profile=" + profileId);

        // This Cobblemon sync can cost 100ms+ on the server thread. Keep party.sendTo immediate,
        // but do not release the profile-loading quarantine until it finishes.
        CompletableFuture.runAsync(() -> player.server.execute(() -> {
            try {
                if (player.hasDisconnected()) {
                    done.complete(null);
                    return;
                }
                if (!profileId.equals(PlayerProfileManager.activeProfileId(player))) {
                    done.complete(null);
                    return;
                }
                // Do not call Cobblemon's full onPlayerDataSync here. In Cobblemon 1.7.3 that path can
                // request the PC store, which lazy-loads a large SQL blob and parses SNBT on the server
                // thread. We already send the active party above, and PC storage will lazy-load only when
                // the player actually opens/uses a PC. This keeps login/profile activation from producing
                // DatabaseManager.getConnection() server-thread warnings and 70ms+ tick stalls.
                CompletableFuture.runAsync(() -> player.server.execute(() -> {
                    try {
                        if (!player.hasDisconnected() && profileId.equals(PlayerProfileManager.activeProfileId(player))) {
                            party.sendTo(player);
                        }
                    } catch (Throwable ignored) {
                    }
                }), CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        }), CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS));

        long elapsed = System.currentTimeMillis() - start;
        ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Cobblemon party activation total took " + elapsed + "ms for " + player.getGameProfile().getName() + " profile=" + profileId);
        return done;
    }
}
