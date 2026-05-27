package com.champutils.profile;

import com.champutils.database.DatabaseManager;
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
import kotlin.Unit;
import java.util.concurrent.ConcurrentHashMap;

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
        }
    }

    @Override
    public PlayerPartyStore getPlayerParty(UUID playerID, RegistryAccess registryAccess) {
        if (!canOwn(playerID)) return null;
        return partyCache.computeIfAbsent(playerID, uuid -> {
            UUID accountUuid = CobblemonProfileStorageBridge.accountUuidForProfile(uuid);
            if (accountUuid == null) accountUuid = uuid;

            // Important: playerUUID must be the real Minecraft account UUID so Cobblemon
            // ownership, observers, send-out, and recall all target the online player.
            // storageUUID remains the profile UUID so SQL/file ownership stays profile-scoped.
            PlayerPartyStore store = new PlayerPartyStore(accountUuid, uuid);
            loadStore(uuid, true, store, registryAccess);
            store.initialize();
            return store;
        });
    }

    @Override
    public PCStore getPC(UUID playerID, RegistryAccess registryAccess) {
        if (!canOwn(playerID)) return null;
        return pcCache.computeIfAbsent(playerID, uuid -> {
            PCStore store = new PCStore(uuid);
            try {
            store.resize(
                com.cobblemon.mod.common.Cobblemon.INSTANCE.getConfig().getDefaultBoxCount(),
                false,
                pokemon -> kotlin.Unit.INSTANCE
            );
        } catch (Throwable ignored) {}
            loadStore(uuid, false, store, registryAccess);
            store.initialize();
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
        saveAll(registryAccess);
        partyCache.clear();
        pcCache.clear();
    }

    @Override
    public void onPlayerDisconnect(ServerPlayer player) {
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        save(profileId, player.registryAccess());
        partyCache.remove(profileId);
        pcCache.remove(profileId);
    }

    public void save(UUID profileId, RegistryAccess registryAccess) {
        if (!canOwn(profileId)) return;
        PlayerPartyStore party = partyCache.get(profileId);
        PCStore pc = pcCache.get(profileId);
        if (party == null && pc == null) return;
        upsert(profileId, party, pc, registryAccess);
    }

    public void saveAll(RegistryAccess registryAccess) {
        for (UUID profileId : partyCache.keySet()) save(profileId, registryAccess);
        for (UUID profileId : pcCache.keySet()) save(profileId, registryAccess);
    }

    public void saveAsync(UUID profileId, RegistryAccess registryAccess) {
        if (!canOwn(profileId)) return;
        PlayerPartyStore party = partyCache.get(profileId);
        PCStore pc = pcCache.get(profileId);
        if (party == null && pc == null) return;

        String partyNbt = party == null ? null : party.saveToNBT(new CompoundTag(), registryAccess).toString();
        String pcNbt = pc == null ? null : pc.saveToNBT(new CompoundTag(), registryAccess).toString();
        DatabaseManager.executeAsync("save SQL Cobblemon profile stores", connection -> upsertSnapshot(connection, profileId, partyNbt, pcNbt));
    }

    public void evict(UUID profileId) {
        if (profileId == null) return;
        partyCache.remove(profileId);
        pcCache.remove(profileId);
    }

    public boolean hasCachedStores(UUID profileId) {
        return profileId != null && (partyCache.containsKey(profileId) || pcCache.containsKey(profileId));
    }

    private boolean canOwn(UUID uuid) {
        return uuid != null && DatabaseManager.isEnabled() && CobblemonProfileStorageBridge.isKnownProfileStorageKey(uuid);
    }

    private void loadStore(UUID profileId, boolean party, PokemonStore<?> store, RegistryAccess registryAccess) {
        if (!DatabaseManager.isEnabled()) return;
        try {
            Connection connection = DatabaseManager.getConnection();
            try (var ps = connection.prepareStatement("select party_nbt, pc_nbt from profile_cobblemon_storage where profile_id = ?")) {
                ps.setObject(1, profileId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return;
                    String raw = rs.getString(party ? "party_nbt" : "pc_nbt");
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
            String partyNbt = party == null ? null : party.saveToNBT(new CompoundTag(), registryAccess).toString();
            String pcNbt = pc == null ? null : pc.saveToNBT(new CompoundTag(), registryAccess).toString();
            upsertSnapshot(DatabaseManager.getConnection(), profileId, partyNbt, pcNbt);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save SQL Cobblemon stores for profile " + profileId + ".");
            e.printStackTrace();
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
