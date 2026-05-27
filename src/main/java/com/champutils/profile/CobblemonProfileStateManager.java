package com.champutils.profile;

import net.minecraft.server.level.ServerPlayer;

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
        CobblemonProfileStorageBridge.forceSaveActiveProfileStores(player);
    }

    public static void load(ServerPlayer player) {
        CobblemonProfileStorageBridge.loadActiveProfileStores(player);
    }

    public static void clearLive(ServerPlayer player) {
        // No-op by design. The active profile SQL store is selected before
        // Cobblemon loads, so there is no profile-copy cleanup step anymore.
    }
}
