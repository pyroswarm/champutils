package com.champutils.profession;

import net.minecraft.server.level.ServerPlayer;

public class WorldBossRewardManager {

    public static void rollReward(
            ServerPlayer player
    ) {
        /*
         World event boss rewards are now handled by
         com.champutils.worldevent.WorldEventBattleHandler so the reward table
         can come from world_events.json and can be tied to the defeated event NPC.
         This method remains for older BattleContextManager.WORLD_BOSS calls.
         */
    }
}
