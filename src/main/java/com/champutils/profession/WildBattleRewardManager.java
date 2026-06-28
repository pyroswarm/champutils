package com.champutils.profession;

import net.minecraft.server.level.ServerPlayer;

/**
 * Battle profession no longer pays guaranteed money or random item loot.
 * Non-PvP battle wins only roll digital chunks. PvP callers should not use this manager.
 */
public class WildBattleRewardManager {
    public static void rollReward(ServerPlayer player) {
        if (player == null) return;
        ProfessionChunkManager.rollActivity(player, ProfessionType.BATTLING, 1.0D);
    }

    public static void rollRewardNoMoney(ServerPlayer player) {
        rollReward(player);
    }
}
