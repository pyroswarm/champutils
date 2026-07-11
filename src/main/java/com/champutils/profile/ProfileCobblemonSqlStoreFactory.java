package com.champutils.profile;

import com.champutils.battle.ServerLifecycleBridge;
import com.champutils.debug.ChampDebugManager;
import com.champutils.database.DatabaseManager;
import com.champutils.hunt.PokemonHuntReflection;
import com.champutils.util.CobblemonEventReflection;
import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.api.storage.StorePosition;
import com.cobblemon.mod.common.api.storage.factory.PokemonStoreFactory;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.block.entity.PCBlockEntity;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

/**
 * SQL-backed Cobblemon party/PC factory for ChampUtils profiles.
 *
 * Cobblemon still asks its normal PokemonStoreManager for stores. The mixin maps
 * account UUID -> active profile_id, and this factory owns those profile UUIDs
 * before Cobblemon's default file factory gets a chance to create file stores.
 */
public final class ProfileCobblemonSqlStoreFactory implements PokemonStoreFactory {
    private final Map<UUID, PlayerPartyStore> partyCache = new ConcurrentHashMap<>();
    private final Map<UUID, PCStore> pcCache = new ConcurrentHashMap<>();
    private final Map<UUID, ServerPlayer> pcHydrationViewers = new ConcurrentHashMap<>();
    private final Set<UUID> hydratedPcCache = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pcLoadsInFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> trackedPartyStores = ConcurrentHashMap.newKeySet();
    private final Set<UUID> trackedPcStores = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingPartyFlushes = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingPcFlushes = ConcurrentHashMap.newKeySet();

    public record StoreSnapshot(
            String partyNbt,
            String pcNbt,
            boolean partyCached,
            boolean pcCached,
            boolean pcHydrated,
            int partySize,
            int pcSize
    ) {
        public boolean hasAnyPayload() {
            return partyNbt != null || pcNbt != null;
        }
    }

    public StoreSnapshot snapshot(UUID profileId, RegistryAccess registryAccess) {
        if (!canOwn(profileId) || registryAccess == null) {
            return new StoreSnapshot(null, null, false, false, false, 0, 0);
        }

        long start = System.currentTimeMillis();
        PlayerPartyStore party = partyCache.get(profileId);
        PCStore pc = hydratedPcCache.contains(profileId) ? pcCache.get(profileId) : null;
        if (party != null) dedupeStore(party);
        if (pc != null) dedupeStore(pc);

        String partyNbt = party == null ? null : safeStoreNbt(party, registryAccess);
        String pcNbt = pc == null ? null : safeStoreNbt(pc, registryAccess);
        int partySize = countStore(party);
        int pcSize = countStore(pc);

        ChampDebugManager.log(ChampDebugManager.Category.PROFILES,
                "[PROFILE-TIMING] ProfileCobblemonSqlStoreFactory.transfer snapshot took "
                        + (System.currentTimeMillis() - start)
                        + "ms for profile=" + profileId
                        + " partyCached=" + (party != null)
                        + " pcCached=" + pcCache.containsKey(profileId)
                        + " pcHydrated=" + hydratedPcCache.contains(profileId)
                        + " partySize=" + partySize
                        + " pcSize=" + pcSize
                        + " partyPayload=" + (partyNbt != null)
                        + " pcPayload=" + (pcNbt != null));

        return new StoreSnapshot(
                partyNbt,
                pcNbt,
                party != null,
                pcCache.containsKey(profileId),
                hydratedPcCache.contains(profileId),
                partySize,
                pcSize
        );
    }

    public void saveSnapshotBlocking(Connection connection, UUID profileId, StoreSnapshot snapshot, String reason) throws Exception {
        if (connection == null || profileId == null || snapshot == null || !snapshot.hasAnyPayload()) return;
        ProfileAtomicSnapshotManager.ensureSchema(connection);
        ProfileAtomicSnapshotManager.saveCobblemonBlocking(connection, profileId, snapshot.partyNbt(), snapshot.pcNbt(), reason);
    }

    public void ensureSchema() {
        if (!DatabaseManager.isEnabled()) return;
        try {
            ensureSchema(DatabaseManager.getConnection());
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to prepare SQL Cobblemon profile storage schema.");
            e.printStackTrace();
        }
    }

    public void ensureSchema(Connection connection) {
        if (!DatabaseManager.isEnabled() || connection == null) return;
        try {
            ensureStorageSchema(connection);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to prepare SQL Cobblemon profile storage schema.");
            e.printStackTrace();
        }
    }

    private static void ensureStorageSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists profile_cobblemon_storage (" +
                    "profile_id uuid primary key references player_profiles(id) on delete cascade, " +
                    "party_nbt text, " +
                    "pc_nbt text, " +
                    "updated_at timestamptz not null default now())");
            statement.executeUpdate("alter table profile_cobblemon_storage add column if not exists party_nbt text");
            statement.executeUpdate("alter table profile_cobblemon_storage add column if not exists pc_nbt text");
            statement.executeUpdate("alter table profile_cobblemon_storage add column if not exists updated_at timestamptz not null default now()");
            statement.executeUpdate("create index if not exists idx_profile_cobblemon_storage_updated_at on profile_cobblemon_storage(updated_at)");
            statement.executeUpdate("create index if not exists idx_profile_cobblemon_storage_profile_updated on profile_cobblemon_storage(profile_id, updated_at)");
            ProfileAtomicSnapshotManager.ensureSchema(connection);
        }
    }

    @Override
    public PlayerPartyStore getPlayerParty(UUID playerID, RegistryAccess registryAccess) {
        if (!canOwn(playerID)) return null;
        return partyCache.computeIfAbsent(playerID, uuid -> {
            UUID accountUuid = CobblemonProfileStorageBridge.accountUuidForProfile(uuid);
            if (accountUuid == null) accountUuid = uuid;

            // Important: BOTH playerUUID and the live PartyStore UUID must be the real
            // Minecraft account UUID. Cobblemon 1.7.3 resolves a player-owned Pokémon's
            // owner entity through store.uuid in Pokemon#getOwnerEntity(), not only through
            // PlayerPartyStore#playerUUID. If this live store UUID is the ChampUtils
            // profile UUID, sent-out Pokémon spawn and then immediately lose valid owner
            // linkage/tether behavior. The SQL row key below is still the profile UUID,
            // so persistence remains profile-scoped without breaking Cobblemon runtime logic.
            PlayerPartyStore store = new PlayerPartyStore(accountUuid);
            loadStore(uuid, true, store, registryAccess);
            rebindRuntimeOwner(store, accountUuid);
            store.initialize();
            trackParty(uuid, store, registryAccess);
            return store;
        });
    }

    @Override
    public PCStore getPC(UUID playerID, RegistryAccess registryAccess) {
        if (!canOwn(playerID)) return null;
        return pcCache.computeIfAbsent(playerID, uuid -> {
            long start = System.currentTimeMillis();
            UUID accountUuid = CobblemonProfileStorageBridge.accountUuidForProfile(uuid);
            if (accountUuid == null) accountUuid = uuid;
            PCStore store = new PCStore(accountUuid);
            try {
                store.resize(
                    com.cobblemon.mod.common.Cobblemon.INSTANCE.getConfig().getDefaultBoxCount(),
                    false,
                    pokemon -> kotlin.Unit.INSTANCE
                );
            } catch (Throwable ignored) {}
            store.initialize();
            if (isServerThread()) {
                rebindRuntimeOwner(store, accountUuid);
                queuePcHydration(uuid, store, registryAccess, null);
                ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Queued async SQL Cobblemon PC load for profile=" + uuid + " from server thread");
            } else {
                loadStore(uuid, false, store, registryAccess);
                rebindRuntimeOwner(store, accountUuid);
                hydratedPcCache.add(uuid);
            }
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("[PROFILE] Lazy SQL Cobblemon PC load took " + elapsed + "ms for profile=" + uuid);
            trackPc(uuid, store, registryAccess);
            return store;
        });
    }

    @Override
    public PCStore getPCForPlayer(ServerPlayer player, PCBlockEntity pcBlockEntity) {
        UUID profileId = CobblemonProfileStorageBridge.storageKey(player.getUUID());
        PCStore store = getPC(profileId, player.registryAccess());
        queuePcHydration(profileId, store, player.registryAccess(), player);
        return store;
    }

    @Override
    public <E extends StorePosition, T extends PokemonStore<E>> T getCustomStore(Class<T> storeClass, UUID uuid, RegistryAccess registryAccess) {
        return null;
    }

    @Override
    public void shutdown(RegistryAccess registryAccess) {
        // Server shutdown is the one place where a blocking flush is acceptable.
        // Profile switching, menu flows, and disconnects must use saveAsync/save().
        saveAllBlocking(registryAccess);
        partyCache.clear();
        pcCache.clear();
        pcHydrationViewers.clear();
        hydratedPcCache.clear();
        pcLoadsInFlight.clear();
        trackedPartyStores.clear();
        trackedPcStores.clear();
        pendingPartyFlushes.clear();
        pendingPcFlushes.clear();
    }

    @Override
    public void onPlayerDisconnect(ServerPlayer player) {
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        // Snapshot now, write later. Do not stall the server thread on player disconnect.
        saveAsync(profileId, player.registryAccess());
        partyCache.remove(profileId);
        pcCache.remove(profileId);
        pcHydrationViewers.remove(profileId);
        hydratedPcCache.remove(profileId);
        pcLoadsInFlight.remove(profileId);
        trackedPartyStores.remove(profileId);
        trackedPcStores.remove(profileId);
        pendingPartyFlushes.remove(profileId);
        pendingPcFlushes.remove(profileId);
    }

    /**
     * Safe default save path. This intentionally does NOT perform SQL on the caller thread.
     * Kept with the old name because older ChampUtils call sites still call save(...).
     */
    public void save(UUID profileId, RegistryAccess registryAccess) {
        saveAsync(profileId, registryAccess);
    }

    private void saveBlocking(UUID profileId, RegistryAccess registryAccess) {
        if (!canOwn(profileId)) return;
        long start = System.currentTimeMillis();
        PlayerPartyStore party = partyCache.get(profileId);
        PCStore pc = hydratedPcCache.contains(profileId) ? pcCache.get(profileId) : null; // never save a non-hydrated async PC shell.
        if (party == null && pc == null) return;
        upsert(profileId, party, pc, registryAccess);
        ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] ProfileCobblemonSqlStoreFactory.saveBlocking took " + (System.currentTimeMillis() - start) + "ms for profile=" + profileId + " partyCached=" + (party != null) + " pcLoaded=" + (pc != null));
    }

    public void saveAllBlocking(RegistryAccess registryAccess) {
        for (UUID profileId : partyCache.keySet()) saveBlocking(profileId, registryAccess);
        for (UUID profileId : pcCache.keySet()) saveBlocking(profileId, registryAccess);
    }

    public void saveAsync(UUID profileId, RegistryAccess registryAccess) {
        if (!canOwn(profileId)) return;
        long start = System.currentTimeMillis();
        PlayerPartyStore party = partyCache.get(profileId);
        PCStore pc = hydratedPcCache.contains(profileId) ? pcCache.get(profileId) : null; // dirty rule: if PC was never loaded/hydrated, do not serialize or write it.
        if (party == null && pc == null) return;

        if (party != null) dedupeStore(party);
        if (pc != null) dedupeStore(pc);
        String partyNbt = party == null ? null : safeStoreNbt(party, registryAccess);
        String pcNbt = pc == null ? null : safeStoreNbt(pc, registryAccess);
        ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] ProfileCobblemonSqlStoreFactory.saveAsync snapshot took " + (System.currentTimeMillis() - start) + "ms for profile=" + profileId + " partyCached=" + (party != null) + " pcLoaded=" + (pc != null) + " pcSnapshot=" + (pcNbt != null));
        ProfileAtomicSnapshotManager.saveCobblemonCoalesced(profileId, partyNbt, pcNbt, "async-save");
    }


    public boolean isPcLoaded(UUID profileId) {
        return profileId != null && hydratedPcCache.contains(profileId) && pcCache.containsKey(profileId);
    }

    public boolean isPartyLoaded(UUID profileId) {
        return profileId != null && partyCache.containsKey(profileId);
    }

    public void prefetchParty(UUID profileId, UUID accountUuid, RegistryAccess registryAccess) {
        if (profileId == null || accountUuid == null || registryAccess == null || !DatabaseManager.isEnabled()) return;
        try {
            prefetchParty(DatabaseManager.getConnection(), profileId, accountUuid, registryAccess);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to prefetch SQL Cobblemon party for profile " + profileId + ".");
            e.printStackTrace();
        }
    }

    /**
     * Prefetch using the caller's already-open database connection. Profile switching calls this
     * from the database executor; using that executor connection avoids touching the shared
     * synchronous JDBC connection during the profile switch hot path.
     */
    public void prefetchParty(Connection connection, UUID profileId, UUID accountUuid, RegistryAccess registryAccess) {
        if (connection == null || profileId == null || accountUuid == null || registryAccess == null || !DatabaseManager.isEnabled()) return;
        if (partyCache.containsKey(profileId)) {
            ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] SQL Cobblemon party prefetch took 0ms for profile=" + profileId + " cacheHit=true");
            return;
        }
        partyCache.computeIfAbsent(profileId, uuid -> {
            long start = System.currentTimeMillis();
            PlayerPartyStore store = new PlayerPartyStore(accountUuid);
            loadStore(connection, uuid, true, store, registryAccess);
            rebindRuntimeOwner(store, accountUuid);
            store.initialize();
            trackParty(uuid, store, registryAccess);
            long elapsed = System.currentTimeMillis() - start;
            ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] SQL Cobblemon party prefetch took " + elapsed + "ms for profile=" + profileId + " cacheHit=false");
            return store;
        });
    }

    public void prefetchPartyRaw(UUID profileId, UUID accountUuid, RegistryAccess registryAccess, String partyNbt) {
        if (profileId == null || accountUuid == null || registryAccess == null) return;
        if (partyCache.containsKey(profileId)) {
            ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] transfer Cobblemon party payload hydrate took 0ms for profile=" + profileId + " cacheHit=true");
            return;
        }
        partyCache.computeIfAbsent(profileId, uuid -> {
            long start = System.currentTimeMillis();
            PlayerPartyStore store = new PlayerPartyStore(accountUuid);
            if (partyNbt != null && !partyNbt.isBlank() && !partyNbt.equals("{}")) {
                try {
                    CompoundTag tag = TagParser.parseTag(partyNbt);
                    store.loadFromNBT(tag, registryAccess);
                } catch (Exception e) {
                    System.err.println("[ChampUtils] Failed to hydrate transfer Cobblemon party payload for profile " + profileId + ". Empty live party will be used.");
                    e.printStackTrace();
                }
            }
            rebindRuntimeOwner(store, accountUuid);
            store.initialize();
            trackParty(uuid, store, registryAccess);
            ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] transfer Cobblemon party payload hydrate took " + (System.currentTimeMillis() - start) + "ms for profile=" + profileId + " partySize=" + countStore(store));
            return store;
        });
    }

    public void prefetchPcAsync(UUID profileId, UUID accountUuid, RegistryAccess registryAccess) {
        if (profileId == null || accountUuid == null || registryAccess == null || !DatabaseManager.isEnabled()) return;
        PCStore existing = pcCache.get(profileId);
        if (existing != null) {
            queuePcHydration(profileId, existing, registryAccess, null);
            return;
        }
        pcCache.computeIfAbsent(profileId, uuid -> {
            PCStore store = new PCStore(accountUuid);
            try {
                store.resize(
                        com.cobblemon.mod.common.Cobblemon.INSTANCE.getConfig().getDefaultBoxCount(),
                        false,
                        pokemon -> kotlin.Unit.INSTANCE
                );
            } catch (Throwable ignored) {}
            store.initialize();
            rebindRuntimeOwner(store, accountUuid);
            trackPc(uuid, store, registryAccess);
            queuePcHydration(uuid, store, registryAccess, null);
            return store;
        });
    }

    public void evict(UUID profileId) {
        if (profileId == null) return;
        partyCache.remove(profileId);
        pcCache.remove(profileId);
        pcHydrationViewers.remove(profileId);
        hydratedPcCache.remove(profileId);
        pcLoadsInFlight.remove(profileId);
        trackedPartyStores.remove(profileId);
        trackedPcStores.remove(profileId);
        pendingPartyFlushes.remove(profileId);
        pendingPcFlushes.remove(profileId);
    }


    public boolean removePokemonFromCachedStores(UUID profileId, UUID pokemonUuid, Object pokemonObject) {
        if (profileId == null && pokemonUuid == null && !(pokemonObject instanceof Pokemon)) return false;
        boolean removed = false;
        PlayerPartyStore party = partyCache.get(profileId);
        if (party != null) removed |= removeFromStore(party, pokemonUuid, pokemonObject);
        PCStore pc = pcCache.get(profileId);
        if (pc != null) removed |= removeFromStore(pc, pokemonUuid, pokemonObject);
        return removed;
    }

    private static boolean removeFromStore(Iterable<Pokemon> store, UUID pokemonUuid, Object pokemonObject) {
        if (store == null) return false;
        try {
            for (Pokemon pokemon : store) {
                if (pokemon == null) continue;
                boolean same = pokemonObject instanceof Pokemon p && pokemon == p;
                if (!same && pokemonUuid != null) {
                    try { same = pokemonUuid.equals(pokemon.getUuid()); } catch (Throwable ignored) {}
                }
                if (!same) continue;

                try {
                    var coordinates = pokemon.getStoreCoordinates().get();
                    if (coordinates != null && coordinates.remove()) return true;
                } catch (Throwable ignored) {}

                for (Method method : store.getClass().getMethods()) {
                    if (!method.getName().equals("remove") || method.getParameterCount() != 1) continue;
                    Class<?> parameter = method.getParameterTypes()[0];
                    if (!parameter.isAssignableFrom(pokemon.getClass())) continue;
                    method.setAccessible(true);
                    Object result = method.invoke(store, pokemon);
                    return !(result instanceof Boolean) || (Boolean) result;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean hasCachedStores(UUID profileId) {
        return profileId != null && (partyCache.containsKey(profileId) || pcCache.containsKey(profileId));
    }

    public int cachedPartySize(UUID profileId) {
        return profileId == null ? 0 : countStore(partyCache.get(profileId));
    }

    public int cachedPcSize(UUID profileId) {
        return profileId == null ? 0 : countStore(pcCache.get(profileId));
    }

    public boolean hasSpeciesInCachedStores(UUID profileId, String species, UUID excludePokemonUuid) {
        if (profileId == null || species == null || species.isBlank()) return false;
        String normalized = PokemonHuntReflection.normalizeId(species);
        PlayerPartyStore party = partyCache.get(profileId);
        if (storeHasSpecies(party, normalized, excludePokemonUuid)) return true;
        PCStore pc = pcCache.get(profileId);
        return storeHasSpecies(pc, normalized, excludePokemonUuid);
    }

    private static boolean storeHasSpecies(Iterable<Pokemon> store, String species, UUID excludePokemonUuid) {
        if (store == null || species == null || species.isBlank()) return false;
        try {
            for (Pokemon pokemon : store) {
                if (pokemon == null) continue;
                if (excludePokemonUuid != null && excludePokemonUuid.equals(pokemon.getUuid())) continue;
                String otherSpecies = PokemonHuntReflection.speciesId(pokemon);
                if (species.equals(PokemonHuntReflection.normalizeId(otherSpecies))) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }


    private void trackParty(UUID profileId, PlayerPartyStore store, RegistryAccess registryAccess) {
        if (profileId == null || store == null || registryAccess == null) return;
        if (!trackedPartyStores.add(profileId)) return;
        CobblemonEventReflection.subscribe(store.getAnyChangeObservable(), ignored -> schedulePartyFlush(profileId, registryAccess));
    }

    private void trackPc(UUID profileId, PCStore store, RegistryAccess registryAccess) {
        if (profileId == null || store == null || registryAccess == null) return;
        if (!trackedPcStores.add(profileId)) return;
        CobblemonEventReflection.subscribe(store.getAnyChangeObservable(), ignored -> schedulePcFlush(profileId, registryAccess));
    }

    private void schedulePartyFlush(UUID profileId, RegistryAccess registryAccess) {
        if (profileId == null || registryAccess == null || !pendingPartyFlushes.add(profileId)) return;
        CompletableFuture
                .runAsync(() -> {}, CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS))
                .thenRun(() -> {
                    MinecraftServer server = ServerLifecycleBridge.getServer();
                    Runnable task = () -> {
                        try {
                            PlayerPartyStore party = partyCache.get(profileId);
                            if (party == null) return;
                            saveAsync(profileId, registryAccess);
                            ServerPlayer player = onlinePlayerForProfile(profileId);
                            if (player != null && profileId.equals(PlayerProfileManager.activeProfileId(player))) {
                                CobblemonProfileStorageBridge.sendPartyToPlayerAndSelect(player, party, profileId, "change-flush");
                            }
                        } catch (Throwable throwable) {
                            System.err.println("[ChampUtils] Failed to flush/resync Cobblemon party change for profile " + profileId + ".");
                            throwable.printStackTrace();
                        } finally {
                            pendingPartyFlushes.remove(profileId);
                        }
                    };
                    if (server != null) server.execute(task);
                    else task.run();
                });
    }

    private void schedulePcFlush(UUID profileId, RegistryAccess registryAccess) {
        if (profileId == null || registryAccess == null || !pendingPcFlushes.add(profileId)) return;
        CompletableFuture
                .runAsync(() -> {}, CompletableFuture.delayedExecutor(750, TimeUnit.MILLISECONDS))
                .thenRun(() -> {
                    MinecraftServer server = ServerLifecycleBridge.getServer();
                    Runnable task = () -> {
                        try {
                            if (!hydratedPcCache.contains(profileId)) return;
                            if (!pcCache.containsKey(profileId)) return;
                            saveAsync(profileId, registryAccess);
                            sendPcToViewer(profileId, pcCache.get(profileId));
                        } catch (Throwable throwable) {
                            System.err.println("[ChampUtils] Failed to flush/resync Cobblemon PC change for profile " + profileId + ".");
                            throwable.printStackTrace();
                        } finally {
                            pendingPcFlushes.remove(profileId);
                        }
                    };
                    if (server != null) server.execute(task);
                    else task.run();
                });
    }

    private static ServerPlayer onlinePlayerForProfile(UUID profileId) {
        MinecraftServer server = ServerLifecycleBridge.getServer();
        UUID accountUuid = CobblemonProfileStorageBridge.accountUuidForProfile(profileId);
        if (server == null || accountUuid == null) return null;
        return server.getPlayerList().getPlayer(accountUuid);
    }

    private boolean canOwn(UUID uuid) {
        return uuid != null && DatabaseManager.isEnabled() && CobblemonProfileStorageBridge.isKnownProfileStorageKey(uuid);
    }

    private void loadStore(UUID profileId, boolean party, PokemonStore<?> store, RegistryAccess registryAccess) {
        if (!DatabaseManager.isEnabled()) return;
        try {
            loadStore(DatabaseManager.getConnection(), profileId, party, store, registryAccess);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load SQL Cobblemon " + (party ? "party" : "PC") + " store for profile " + profileId + ". Empty live store will be used.");
            e.printStackTrace();
        }
    }

    private void loadStore(Connection connection, UUID profileId, boolean party, PokemonStore<?> store, RegistryAccess registryAccess) {
        if (!DatabaseManager.isEnabled() || connection == null) return;
        try {
            String raw = readStoreRaw(connection, profileId, party);
            if (raw == null || raw.isBlank()) return;
            CompoundTag tag = TagParser.parseTag(raw);
            if (!party && store instanceof PCStore pcStore) clearPcBoxes(pcStore);
            store.loadFromNBT(tag, registryAccess);
            store.initialize();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load SQL Cobblemon " + (party ? "party" : "PC") + " store for profile " + profileId + ". Empty live store will be used.");
            e.printStackTrace();
        }
    }

    private static String readStoreRaw(Connection connection, UUID profileId, boolean party) throws Exception {
        String column = party ? "party_nbt" : "pc_nbt";
        // Do not SELECT the PC blob when loading the party. Large pc_nbt values were making
        // party-only profile activation behave like a partial PC load.
        try (var ps = connection.prepareStatement("select " + column + " from profile_cobblemon_storage where profile_id = ?")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return ProfileAtomicSnapshotManager.latestCompletedCobblemon(connection, profileId, party);
                }
                String raw = rs.getString(column);
                return (raw == null || raw.isBlank())
                        ? ProfileAtomicSnapshotManager.latestCompletedCobblemon(connection, profileId, party)
                        : raw;
            }
        }
    }

    private void queuePcHydration(UUID profileId, PCStore store, RegistryAccess registryAccess, ServerPlayer viewer) {
        if (profileId == null || store == null || registryAccess == null || !DatabaseManager.isEnabled()) return;
        if (viewer != null) pcHydrationViewers.put(profileId, viewer);
        if (hydratedPcCache.contains(profileId)) {
            sendPcToViewer(profileId, store);
            return;
        }
        if (!pcLoadsInFlight.add(profileId)) return;
        DatabaseManager.supplyAsync("hydrate Cobblemon PC " + profileId, connection -> {
            String raw = readStoreRaw(connection, profileId, false);
            return raw == null || raw.isBlank() ? null : TagParser.parseTag(raw);
        }).whenComplete((tag, error) -> {
            MinecraftServer server = viewer != null && viewer.server != null ? viewer.server : ServerLifecycleBridge.getServer();
            Runnable applyHydration = () -> {
                try {
                    if (error != null) {
                        System.err.println("[ChampUtils] Failed to hydrate SQL Cobblemon PC for profile " + profileId + ": " + error.getMessage());
                        return;
                    }
                    List<Pokemon> transientPokemon = snapshotPokemon(store);
                    if (tag != null) {
                        clearPcBoxes(store);
                        store.loadFromNBT(tag, registryAccess);
                    }
                    mergeMissingPokemon(store, transientPokemon);
                    store.initialize();
                    UUID accountUuid = CobblemonProfileStorageBridge.accountUuidForProfile(profileId);
                    if (accountUuid != null) rebindRuntimeOwner(store, accountUuid);
                    hydratedPcCache.add(profileId);
                    trackPc(profileId, store, registryAccess);
                    ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Async SQL Cobblemon PC hydration finished on server thread for profile=" + profileId + " pcSize=" + countStore(store));
                    sendPcToViewer(profileId, store);
                } catch (Throwable throwable) {
                    System.err.println("[ChampUtils] Failed to apply SQL Cobblemon PC hydration for profile " + profileId + ".");
                    throwable.printStackTrace();
                } finally {
                    pcLoadsInFlight.remove(profileId);
                }
            };
            if (server != null) server.execute(applyHydration);
            else applyHydration.run();
        });
    }

    private void sendPcToViewer(UUID profileId, PCStore store) {
        ServerPlayer viewer = pcHydrationViewers.get(profileId);
        if (viewer == null || store == null || viewer.server == null) return;
        viewer.server.execute(() -> {
            try {
                if (viewer.hasDisconnected()) return;
                if (!profileId.equals(PlayerProfileManager.activeProfileId(viewer))) return;
                boolean pcLinked = CobblemonProfileStorageBridge.isPlayerPcLinked(viewer);
                if (pcLinked) {
                    // Do not call PCStore#sendTo while the PC screen is already open. That sends
                    // InitializePCPacket, which replaces the ClientPC object that the open GUI is
                    // rendering. Sending each box keeps the same ClientPC instance and updates the
                    // visible slots in-place.
                    sendPcBoxesToViewer(store, viewer);
                    ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Sent hydrated Cobblemon PC boxes to open PC GUI for " + viewer.getGameProfile().getName() + " profile=" + profileId + " pcSize=" + countStore(store));
                } else {
                    Method sendTo = store.getClass().getMethod("sendTo", ServerPlayer.class);
                    sendTo.invoke(store, viewer);
                    ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Sent hydrated Cobblemon PC to " + viewer.getGameProfile().getName() + " profile=" + profileId + " pcSize=" + countStore(store));
                }
            } catch (NoSuchMethodException ignored) {
                ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[PROFILE-TIMING] Cobblemon PCStore has no sendTo(ServerPlayer) method; hydration still applied server-side for profile=" + profileId);
            } catch (Throwable throwable) {
                System.err.println("[ChampUtils] Failed to send hydrated SQL Cobblemon PC for profile " + profileId + ".");
                throwable.printStackTrace();
            }
        });
    }

    private void sendPcBoxesToViewer(PCStore store, ServerPlayer viewer) throws Exception {
        Method getBoxes = store.getClass().getMethod("getBoxes");
        Object boxes = getBoxes.invoke(store);
        if (!(boxes instanceof Iterable<?> iterable)) return;
        for (Object box : iterable) {
            if (box == null) continue;
            Method sendTo = box.getClass().getMethod("sendTo", ServerPlayer.class);
            sendTo.invoke(box, viewer);
        }
    }

    private void upsert(UUID profileId, PlayerPartyStore party, PCStore pc, RegistryAccess registryAccess) {
        if (!DatabaseManager.isEnabled()) return;
        try {
            if (party != null) dedupeStore(party);
            if (pc != null) dedupeStore(pc);
            String partyNbt = party == null ? null : safeStoreNbt(party, registryAccess);
            String pcNbt = pc == null ? null : safeStoreNbt(pc, registryAccess);
            ProfileAtomicSnapshotManager.saveCobblemonBlocking(DatabaseManager.getConnection(), profileId, partyNbt, pcNbt, "blocking-save");
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save SQL Cobblemon stores for profile " + profileId + ".");
            e.printStackTrace();
        }
    }



    private static List<Pokemon> snapshotPokemon(Iterable<Pokemon> store) {
        List<Pokemon> pokemon = new ArrayList<>();
        if (store == null) return pokemon;
        try {
            for (Pokemon p : store) {
                if (p != null) pokemon.add(p);
            }
        } catch (Throwable ignored) {}
        return pokemon;
    }

    private static void mergeMissingPokemon(PCStore store, List<Pokemon> pokemon) {
        if (store == null || pokemon == null || pokemon.isEmpty()) return;
        HashSet<UUID> existing = new HashSet<>();
        try {
            for (Pokemon current : store) {
                if (current != null) existing.add(current.getUuid());
            }
        } catch (Throwable ignored) {}
        for (Pokemon p : pokemon) {
            if (p == null) continue;
            UUID uuid;
            try { uuid = p.getUuid(); } catch (Throwable ignored) { continue; }
            if (uuid == null || existing.contains(uuid)) continue;
            try {
                if (store.add(p)) existing.add(uuid);
            } catch (Throwable ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearPcBoxes(PCStore store) {
        if (store == null) return;
        try {
            Method getBoxes = store.getClass().getMethod("getBoxes");
            Object boxes = getBoxes.invoke(store);
            if (boxes instanceof List<?> list) {
                ((List<Object>) list).clear();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void dedupeStore(Iterable<Pokemon> store) {
        if (store == null) return;
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        java.util.List<Pokemon> duplicates = new java.util.ArrayList<>();
        try {
            for (Pokemon pokemon : store) {
                if (pokemon == null) continue;
                UUID id;
                try { id = pokemon.getUuid(); } catch (Throwable t) { continue; }
                if (id == null) continue;
                if (!seen.add(id)) duplicates.add(pokemon);
            }
            for (Pokemon duplicate : duplicates) {
                try {
                    var coordinates = duplicate.getStoreCoordinates().get();
                    if (coordinates != null && coordinates.remove()) continue;
                } catch (Throwable ignored) {}
                for (Method method : store.getClass().getMethods()) {
                    if (!method.getName().equals("remove") || method.getParameterCount() != 1) continue;
                    if (!method.getParameterTypes()[0].isAssignableFrom(duplicate.getClass())) continue;
                    method.setAccessible(true);
                    method.invoke(store, duplicate);
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static String safeStoreNbt(PokemonStore<?> store, RegistryAccess registryAccess) {
        try {
            CompoundTag tag = store.saveToNBT(new CompoundTag(), registryAccess);
            String raw = tag.toString();
            // An empty serialized store ("{}") is a valid state for a player who moved every
            // Pokémon out of their party, or fully cleared a PC. Do not coalesce it away; the
            // caller already avoids snapshotting non-hydrated PC shells.
            if (raw == null || raw.isBlank()) return null;
            return raw;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static boolean isServerThread() {
        String threadName = Thread.currentThread().getName();
        return threadName != null && threadName.equalsIgnoreCase("Server thread");
    }

    private static int countStore(Iterable<Pokemon> store) {
        if (store == null) return 0;
        int count = 0;
        try {
            for (Pokemon pokemon : store) {
                if (pokemon != null) count++;
            }
        } catch (Throwable ignored) {}
        return count;
    }

    private static void rebindRuntimeOwner(Object store, UUID accountUuid) {
        if (store == null || accountUuid == null) return;
        invokeUuidSetter(store, accountUuid, "setUuid", "setUUID", "setPlayerUuid", "setPlayerUUID", "setPlayerId", "setPlayerID");
        setUuidFields(store, accountUuid, "uuid", "storeUUID", "storeUuid", "playerUUID", "playerUuid", "playerID", "playerId");
    }

    private static void invokeUuidSetter(Object store, UUID accountUuid, String... names) {
        for (String name : names) {
            try {
                Method method = store.getClass().getMethod(name, UUID.class);
                method.setAccessible(true);
                method.invoke(store, accountUuid);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
            }
        }
    }

    private static void setUuidFields(Object store, UUID accountUuid, String... names) {
        Class<?> type = store.getClass();
        while (type != null && type != Object.class) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    if (!UUID.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    Object current = field.get(store);
                    if (!accountUuid.equals(current)) field.set(store, accountUuid);
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    private static void upsertSnapshot(Connection connection, UUID profileId, String partyNbt, String pcNbt) throws Exception {
        try (var ps = connection.prepareStatement("insert into profile_cobblemon_storage (profile_id, party_nbt, pc_nbt, updated_at) values (?, ?, ?, now()) " +
                "on conflict (profile_id) do update set " +
                "party_nbt = coalesce(excluded.party_nbt, profile_cobblemon_storage.party_nbt), " +
                "pc_nbt = coalesce(excluded.pc_nbt, profile_cobblemon_storage.pc_nbt), " +
                "updated_at = now()")) {
            ps.setObject(1, profileId);
            ps.setString(2, partyNbt);
            ps.setString(3, pcNbt);
            ps.executeUpdate();
        }
    }
}
