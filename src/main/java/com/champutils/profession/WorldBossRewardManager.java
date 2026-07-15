package com.champutils.profession;

import net.minecraft.server.level.ServerPlayer;

/**
 * Legacy hook retained for world-boss battle contexts.
 * World bosses currently award through their own active boss systems.
 */
public final class WorldBossRewardManager {
    private WorldBossRewardManager() {}

    public static void rollReward(ServerPlayer player) {
        // Intentionally empty: active world-boss systems own their reward tables.
    }
}
