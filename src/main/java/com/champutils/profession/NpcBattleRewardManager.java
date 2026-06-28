package com.champutils.profession;

import net.minecraft.server.level.ServerPlayer;

/**
 * NPC battle profession loot has been removed.
 * Non-PvP battles now roll digital chunks only.
 */
public class NpcBattleRewardManager {
    public static void rollReward(ServerPlayer player) {
        if (player == null) return;
        ProfessionChunkManager.rollActivity(player, ProfessionType.BATTLING, 1.0D);
    }
}
