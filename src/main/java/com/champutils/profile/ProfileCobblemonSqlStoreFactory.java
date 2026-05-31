package com.champutils.profile;

import com.champutils.database.DatabaseManager;
import com.champutils.hunt.PokemonHuntReflection;
import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.api.storage.StorePosition;
import com.cobblemon.mod.common.api.storage.factory.PokemonStoreFactory;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.block.entity.PCBlockEntity;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

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

    public void ensureSchema() {
        if (!DatabaseManager.isEnabled()) return;
        try {
            ensureSchema(DatabaseManager.getConnection());
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to prepare SQL Cobblemon profile storage schema.");
            e.printStackTrace();
        }
    }

    private static void ensureSchema(Connection connection) throws Exception {
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
            store.initialize();
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
            loadStore(uuid, false, store, registryAccess);
            store.initialize();
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("[PROFILE] Lazy SQL Cobblemon PC load took " + elapsed + "ms for profile=" + uuid);
            return store;
        });
    }

    @Override
    public PCStore getPCForPlayer(ServerPlayer player, PCBlockEntity pcBlockEntity) {
        UUID profileId = CobblemonProfileStorageBridge.storageKey(player.getUUID());
        return getPC(profileId, player.registryAccess());
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
    }

    @Override
    public void onPlayerDisconnect(ServerPlayer player) {
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        // Snapshot now, write later. Do not stall the server thread on player disconnect.
        saveAsync(profileId, player.registryAccess());
        partyCache.remove(profileId);
        pcCache.remove(profileId);
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
        PCStore pc = pcCache.get(profileId); // null means PC was never lazy-loaded; never load it just to save.
        if (party == null && pc == null) return;
        upsert(profileId, party, pc, registryAccess);
        System.out.println("[PROFILE-TIMING] ProfileCobblemonSqlStoreFactory.saveBlocking took " + (System.currentTimeMillis() - start) + "ms for profile=" + profileId + " partyCached=" + (party != null) + " pcLoaded=" + (pc != null));
    }

    public void saveAllBlocking(RegistryAccess registryAccess) {
        for (UUID profileId : partyCache.keySet()) saveBlocking(profileId, registryAccess);
        for (UUID profileId : pcCache.keySet()) saveBlocking(profileId, registryAccess);
    }

    public void saveAsync(UUID profileId, RegistryAccess registryAccess) {
        if (!canOwn(profileId)) return;
        long start = System.currentTimeMillis();
        PlayerPartyStore party = partyCache.get(profileId);
        PCStore pc = pcCache.get(profileId); // dirty rule: if PC was never loaded, do not serialize or write it.
        if (party == null && pc == null) return;

        if (party != null) dedupeStore(party);
        if (pc != null) dedupeStore(pc);
        String partyNbt = party == null ? null : safeStoreNbt(party, registryAccess);
        String pcNbt = pc == null ? null : safeStoreNbt(pc, registryAccess);
        System.out.println("[PROFILE-TIMING] ProfileCobblemonSqlStoreFactory.saveAsync snapshot took " + (System.currentTimeMillis() - start) + "ms for profile=" + profileId + " partyCached=" + (party != null) + " pcLoaded=" + (pc != null) + " pcSnapshot=" + (pcNbt != null));
        DatabaseManager.executeAsync("save SQL Cobblemon profile stores", connection -> {
            long sqlStart = System.currentTimeMillis();
            upsertSnapshot(connection, profileId, partyNbt, pcNbt);
            System.out.println("[PROFILE-TIMING] ProfileCobblemonSqlStoreFactory.saveAsync SQL write took " + (System.currentTimeMillis() - sqlStart) + "ms for profile=" + profileId);
        });
    }


    public boolean isPcLoaded(UUID profileId) {
        return profileId != null && pcCache.containsKey(profileId);
    }

    public boolean isPartyLoaded(UUID profileId) {
        return profileId != null && partyCache.containsKey(profileId);
    }

    public void prefetchParty(UUID profileId, UUID accountUuid, RegistryAccess registryAccess) {
        if (profileId == null || accountUuid == null || registryAccess == null || !DatabaseManager.isEnabled()) return;
        if (partyCache.containsKey(profileId)) {
            System.out.println("[PROFILE-TIMING] SQL Cobblemon party prefetch took 0ms for profile=" + profileId + " cacheHit=true");
            return;
        }
        partyCache.computeIfAbsent(profileId, uuid -> {
            long start = System.currentTimeMillis();
            PlayerPartyStore store = new PlayerPartyStore(accountUuid);
            loadStore(uuid, true, store, registryAccess);
            store.initialize();
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("[PROFILE-TIMING] SQL Cobblemon party prefetch took " + elapsed + "ms for profile=" + profileId + " cacheHit=false");
            return store;
        });
    }

    public void evict(UUID profileId) {
        if (profileId == null) return;
        partyCache.remove(profileId);
        pcCache.remove(profileId);
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

    private boolean canOwn(UUID uuid) {
        return uuid != null && DatabaseManager.isEnabled() && CobblemonProfileStorageBridge.isKnownProfileStorageKey(uuid);
    }

    private void loadStore(UUID profileId, boolean party, PokemonStore<?> store, RegistryAccess registryAccess) {
        if (!DatabaseManager.isEnabled()) return;
        try {
            Connection connection = DatabaseManager.getConnection();
            String column = party ? "party_nbt" : "pc_nbt";
            // Do not SELECT the PC blob when loading the party. Large pc_nbt values were making
            // party-only profile activation behave like a partial PC load.
            try (var ps = connection.prepareStatement("select " + column + " from profile_cobblemon_storage where profile_id = ?")) {
                ps.setObject(1, profileId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return;
                    String raw = rs.getString(column);
                    if (raw == null || raw.isBlank()) return;
                    CompoundTag tag = TagParser.parseTag(raw);
                    store.loadFromNBT(tag, registryAccess);
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load SQL Cobblemon " + (party ? "party" : "PC") + " store for profile " + profileId + ". Empty live store will be used.");
            e.printStackTrace();
        }
    }

    private void upsert(UUID profileId, PlayerPartyStore party, PCStore pc, RegistryAccess registryAccess) {
        if (!DatabaseManager.isEnabled()) return;
        try {
            if (party != null) dedupeStore(party);
            if (pc != null) dedupeStore(pc);
            String partyNbt = party == null ? null : safeStoreNbt(party, registryAccess);
            String pcNbt = pc == null ? null : safeStoreNbt(pc, registryAccess);
            upsertSnapshot(DatabaseManager.getConnection(), profileId, partyNbt, pcNbt);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save SQL Cobblemon stores for profile " + profileId + ".");
            e.printStackTrace();
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
            // Never let an accidentally empty live store wipe a previously saved SQL PC/party.
            // Empty NBT can happen during profile swaps or before Cobblemon finishes hydrating a store.
            if (raw == null || raw.isBlank() || raw.equals("{}")) return null;
            return raw;
        } catch (Throwable throwable) {
            return null;
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
