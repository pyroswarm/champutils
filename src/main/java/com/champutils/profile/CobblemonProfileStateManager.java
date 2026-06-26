package com.champutils.profile;

import net.minecraft.server.level.ServerPlayer;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;

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
            PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            if (party != null) {
                for (int i = 0; i < party.size(); i++) {
                    try { party.set(i, null); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }
}
