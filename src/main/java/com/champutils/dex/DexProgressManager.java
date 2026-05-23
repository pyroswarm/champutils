package com.champutils.dex;

import net.minecraft.server.level.ServerPlayer;

public final class DexProgressManager {

    private DexProgressManager() {
    }

    public static int getCaughtCount(ServerPlayer player) {
        return TrueCaughtDexManager.getCaughtCount(player);
    }

    public static int getTotalPokemon() {
        return Math.max(1, DexRewardConfig.CONFIG.totalPokemon);
    }

    public static double getCompletionPercent(ServerPlayer player) {
        return (getCaughtCount(player) * 100.0D) / getTotalPokemon();
    }

    public static int getUnlockedPercent(ServerPlayer player) {
        double percent = getCompletionPercent(player);
        int step = Math.max(1, DexRewardConfig.CONFIG.tierStepPercent);
        int unlocked = ((int) Math.floor(percent / step)) * step;
        return Math.max(0, Math.min(100, unlocked));
    }

    public static int requiredCaughtForPercent(int percent) {
        return (int) Math.ceil((percent / 100.0D) * getTotalPokemon());
    }
}
