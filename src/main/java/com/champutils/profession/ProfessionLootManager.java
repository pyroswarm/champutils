package com.champutils.profession;

import net.minecraft.server.level.ServerPlayer;

/**
 * Profession loot has been intentionally simplified.
 * Old item-drop tables are no longer used for normal profession activity.
 * Profession activity now awards digital chunks through ProfessionChunkManager.
 */
public class ProfessionLootManager {
    private ProfessionLootManager() {}

    public static void rollReward(ServerPlayer player, ProfessionType profession) {
        ProfessionChunkManager.rollActivity(player, profession, 1.0D);
    }

    public static void rollReward(ServerPlayer player, ProfessionType profession, double chanceMultiplier) {
        ProfessionChunkManager.rollActivity(player, profession, chanceMultiplier);
    }

    /** Kept for old callers. Direct item rewards are disabled by design. */
    public static void giveReward(ServerPlayer player, String itemId, int amount) {
    }

    public static double effectiveDropChance(ServerPlayer player, ProfessionType profession, double baseChance) {
        int level = ProfessionManager.getLevel(player, profession);
        double bonus = Math.max(0, level - 1) * 0.0015D;
        return Math.min(0.65D, Math.max(0.0D, baseChance + bonus));
    }
}
