package com.champutils.profile;

import net.minecraft.server.level.ServerPlayer;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;

/**
 * Compatibility wrapper kept so older ChampUtils call sites still compile.
 *
 * The old implementation snapshotted/copy-restored party and PC data after a
 * profile swap. That is intentionally retired. CobblemonProfileStorageBridge is
 * now the single routing point and SQL-backed storage factory.
 */
@Deprecated
public final class CobblemonProfileStateManager {
    private CobblemonProfileStateManager() {}

    public static void ensureSchemaAsync() {
        CobblemonProfileStorageBridge.ensureSchemaAsync();
    }

    public static void save(ServerPlayer player) {
        CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player);
    }

    public static void load(ServerPlayer player) {
        CobblemonProfileStorageBridge.loadActiveProfileStores(player);
    }

    public static void clearLive(ServerPlayer player) {
        if (player == null) return;
        try {
            PlayerPartyStore empty = new PlayerPartyStore(player.getUUID());
            empty.initialize();
            empty.sendTo(player);
        } catch (Throwable ignored) {}
    }
}
