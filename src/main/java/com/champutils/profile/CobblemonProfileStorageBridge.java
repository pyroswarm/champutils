package com.champutils.profile;

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
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        long start = System.currentTimeMillis();

        // Profile activation must stay main-thread-light. Hydrate/send the party immediately so
        // throw-out, battles, and party UI work after switching, but DO NOT load/send the PC here.
        // Large PCs are lazily loaded by ProfileCobblemonSqlStoreFactory#getPC/getPCForPlayer on
        // first actual PC access instead of during every profile swap.
        long getPartyStart = System.currentTimeMillis();
        boolean cacheHitBefore = hasSqlCachedParty(profileId);
        var party = Cobblemon.INSTANCE.getStorage().getParty(profileId, player.registryAccess());
        System.out.println("[PROFILE-TIMING] Cobblemon get cached party took " + (System.currentTimeMillis() - getPartyStart) + "ms for " + player.getGameProfile().getName() + " profile=" + profileId + " cacheHitBefore=" + cacheHitBefore);

        long sendStart = System.currentTimeMillis();
        party.sendTo(player);
        System.out.println("[PROFILE-TIMING] Cobblemon party sendTo took " + (System.currentTimeMillis() - sendStart) + "ms for " + player.getGameProfile().getName() + " profile=" + profileId);

        // This Cobblemon sync can cost 100ms+ on the server thread. Keep party.sendTo immediate,
        // but move the broader Cobblemon player-data sync out of the profile activation critical path
        // and away from the same tick as inventory/profile teleport work.
        CompletableFuture.runAsync(() -> player.server.execute(() -> {
            if (player.hasDisconnected()) return;
            long syncStart = System.currentTimeMillis();
            try { Cobblemon.INSTANCE.getStorage().onPlayerDataSync(player); } catch (Throwable ignored) {}
            System.out.println("[PROFILE-TIMING] Cobblemon onPlayerDataSync delayed took " + (System.currentTimeMillis() - syncStart) + "ms for " + player.getGameProfile().getName() + " profile=" + profileId);
        }), CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS));

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[PROFILE-TIMING] Cobblemon party activation total took " + elapsed + "ms for " + player.getGameProfile().getName() + " profile=" + profileId);
    }
}
