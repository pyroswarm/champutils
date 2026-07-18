package com.champutils.profile;

import com.champutils.debug.ChampDebugManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.net.messages.client.storage.party.SetPartyReferencePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
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
        Cobblemon.INSTANCE.getStorage().registerFactory(Priority.HIGHEST, sqlFactory);
        registered = true;
        ensureSchemaAsync();
        System.out.println("[ChampUtils] Registered SQL-backed Cobblemon profile storage factory.");
    }

    public static synchronized void ensureSchemaAsync() {
        if (sqlFactory != null) {
            com.champutils.database.DatabaseManager.executeAsync("ensure Cobblemon SQL storage schema", connection -> sqlFactory.ensureSchema(connection));
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

    public static ProfileCobblemonSqlStoreFactory.StoreSnapshot snapshotActiveProfileStores(ServerPlayer player) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player) || sqlFactory == null) {
            return null;
        }
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) return null;
        return sqlFactory.snapshot(profileId, player.registryAccess());
    }

    public static void saveSnapshotBlocking(java.sql.Connection connection, UUID profileId, ProfileCobblemonSqlStoreFactory.StoreSnapshot snapshot, String reason) throws Exception {
        if (connection == null || profileId == null || snapshot == null || sqlFactory == null) return;
        sqlFactory.saveSnapshotBlocking(connection, profileId, snapshot, reason == null ? "profile-transfer" : reason);
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

    public static void prefetchProfilePartyFromRaw(UUID profileId, UUID accountUuid, net.minecraft.core.RegistryAccess registryAccess, String partyNbt) {
        if (profileId == null || accountUuid == null || registryAccess == null || sqlFactory == null) return;
        sqlFactory.prefetchPartyRaw(profileId, accountUuid, registryAccess, partyNbt);
    }

    public static void evictProfileStores(UUID profileId) {
        if (sqlFactory != null) sqlFactory.evict(profileId);
    }

    public static void prefetchActiveProfilePcAsync(ServerPlayer player) {
        if (player == null || sqlFactory == null || !PlayerProfileManager.hasActiveProfile(player)) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) return;
        sqlFactory.prefetchPcAsync(profileId, player.getUUID(), player.registryAccess());
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
        PlayerPartyStore party = activeRuntimeParty(player, profileId);
        ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Cobblemon get cached party took " + (System.currentTimeMillis() - getPartyStart) + "ms for " + player.getGameProfile().getName() + " profile=" + profileId + " cacheHitBefore=" + cacheHitBefore + " partySize=" + cachedPartySize(profileId));

        sendActivePartySnapshot(player, profileId, party, true, "initial");

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
                // Cobblemon's normal player data sync is needed for client party/summary screens.
                // The SQL PC factory now returns a lightweight shell on the server thread and
                // hydrates the real PC asynchronously, so this no longer turns profile activation
                // into a blocking PC SQL load.
                safeCobblemonPlayerDataSync(player, profileId, "activation");
                schedulePartyVerify(player, profileId, 250L, true);
                schedulePartyVerify(player, profileId, 1_000L, false);
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        }), CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS));

        long elapsed = System.currentTimeMillis() - start;
        ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Cobblemon party activation total took " + elapsed + "ms for " + player.getGameProfile().getName() + " profile=" + profileId);
        return done;
    }

    private static int cachedPartySize(UUID profileId) {
        return profileId == null || sqlFactory == null ? -1 : sqlFactory.cachedPartySize(profileId);
    }

    private static PlayerPartyStore activeRuntimeParty(ServerPlayer player, UUID profileId) {
        if (player == null) return null;
        try {
            return Cobblemon.INSTANCE.getStorage().getParty(player);
        } catch (Throwable ignored) {
        }
        try {
            return Cobblemon.INSTANCE.getStorage().getParty(profileId, player.registryAccess());
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void resyncActiveProfileParty(ServerPlayer player, String reason) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        PlayerPartyStore party = activeRuntimeParty(player, profileId);
        sendActivePartySnapshot(player, profileId, party, true, reason == null ? "manual" : reason);
        schedulePartyVerify(player, profileId, 250L, true);
        schedulePartyVerify(player, profileId, 1_000L, false);
    }

    private static void schedulePartyVerify(ServerPlayer player, UUID profileId, long delayMillis, boolean includePlayerDataSync) {
        if (player == null || player.server == null || profileId == null) return;
        CompletableFuture
                .runAsync(() -> {}, CompletableFuture.delayedExecutor(Math.max(50L, delayMillis), TimeUnit.MILLISECONDS))
                .thenRun(() -> player.server.execute(() -> {
                    if (player.hasDisconnected()) return;
                    if (!profileId.equals(PlayerProfileManager.activeProfileId(player))) return;
                    PlayerPartyStore party = activeRuntimeParty(player, profileId);
                    sendActivePartySnapshot(player, profileId, party, includePlayerDataSync, "verify-" + delayMillis + "ms");
                    if (delayMillis >= 1_000L) prefetchActiveProfilePcAsync(player);
                }));
    }

    /**
     * Sends a full party snapshot and then re-selects that store as the player's active
     * client party. Cobblemon's PartyStore#sendTo intentionally sends
     * InitializePartyPacket(false, ...). If we do not immediately follow it with
     * SetPartyReferencePacket, the client can keep rendering an older ClientParty
     * instance even though the server-side party and the partyStores map were updated.
     */
    public static void sendPartyToPlayerAndSelect(ServerPlayer player, PlayerPartyStore party, UUID profileId, String reason) {
        if (player == null || party == null) return;
        long sendStart = System.currentTimeMillis();
        boolean pcLinked = isPlayerPcLinked(player);
        try {
            // Cobblemon's PC screen keeps references to the ClientParty/ClientPC objects that
            // existed when the screen opened. Re-sending InitializePartyPacket while the PC is
            // open replaces the client party object in CobblemonClient.storage, leaving the open
            // PC GUI rendering a stale party. While linked to a PC, the native PC move packets
            // already mutate the current objects correctly, so only re-select the active party.
            if (!pcLinked) {
                party.sendTo(player);
            }
            CobblemonNetwork.INSTANCE.sendPacketToPlayer(player, new SetPartyReferencePacket(party.getUuid()));
        } catch (Throwable throwable) {
            System.err.println("[ChampUtils] Failed to send/select Cobblemon party for " + player.getGameProfile().getName() + " during " + reason + ".");
            throwable.printStackTrace();
            return;
        }
        ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Cobblemon party send/select took " + (System.currentTimeMillis() - sendStart) + "ms for " + player.getGameProfile().getName() + " profile=" + profileId + " reason=" + reason + " partySize=" + cachedPartySize(profileId) + " pcLinked=" + pcLinked);
    }

    private static void sendActivePartySnapshot(ServerPlayer player, UUID profileId, PlayerPartyStore party, boolean includePlayerDataSync, String reason) {
        if (player == null || party == null) return;
        boolean pcLinked = isPlayerPcLinked(player);
        sendPartyToPlayerAndSelect(player, party, profileId, reason);
        if (includePlayerDataSync && !pcLinked) {
            safeCobblemonPlayerDataSync(player, profileId, reason);
        } else if (includePlayerDataSync) {
            ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Skipped Cobblemon player-data full resync while PC is open for " + player.getGameProfile().getName() + " profile=" + profileId + " reason=" + reason);
        }
    }

    public static boolean isPlayerPcLinked(ServerPlayer player) {
        if (player == null) return false;
        try {
            Class<?> managerClass = Class.forName("com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager");
            Object manager = managerClass.getField("INSTANCE").get(null);
            Method getLink = managerClass.getMethod("getLink", UUID.class);
            return getLink.invoke(manager, player.getUUID()) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void safeCobblemonPlayerDataSync(ServerPlayer player, UUID profileId, String reason) {
        if (player == null || player.server == null || player.hasDisconnected()) return;
        if (profileId != null && !profileId.equals(PlayerProfileManager.activeProfileId(player))) return;
        if (isPlayerPcLinked(player)) {
            ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Skipped Cobblemon onPlayerDataSync while PC is open for " + player.getGameProfile().getName() + " profile=" + profileId + " reason=" + reason);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            Object storage = Cobblemon.INSTANCE.getStorage();
            Method sync = null;
            for (Method method : storage.getClass().getMethods()) {
                if (!method.getName().equals("onPlayerDataSync") || method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(ServerPlayer.class)) continue;
                sync = method;
                break;
            }
            if (sync == null) return;
            sync.invoke(storage, player);
            try {
                com.cobblemon.mod.common.CobblemonNetwork.INSTANCE.sendPacketToPlayer(
                        player,
                        new com.cobblemon.mod.common.net.messages.client.SetClientPlayerDataPacket(
                                com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes.INSTANCE.getPOKEDEX(),
                                com.cobblemon.mod.common.Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(profileId).toClientData(),
                                false
                        )
                );
            } catch (Throwable dexError) {
                System.err.println("[ChampUtils] Failed to sync profile-bound Cobblemon Pokedex for " + player.getGameProfile().getName() + ".");
                dexError.printStackTrace();
            }
            ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Cobblemon onPlayerDataSync took " + (System.currentTimeMillis() - start) + "ms for " + player.getGameProfile().getName() + " profile=" + profileId + " reason=" + reason);
        } catch (Throwable throwable) {
            System.err.println("[ChampUtils] Failed to run Cobblemon player data sync for " + player.getGameProfile().getName() + " during " + reason + ".");
            throwable.printStackTrace();
        }
    }
}
