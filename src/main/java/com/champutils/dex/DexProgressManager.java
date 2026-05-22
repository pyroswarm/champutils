package com.champutils.dex;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public final class DexProgressManager {

    private DexProgressManager() {
    }

    public static int getCaughtCount(ServerPlayer player) {
        if (player == null) {
            return 0;
        }

        try {
            PokedexManager pokedex = Cobblemon.playerDataManager.getPokedexData(player);
            if (pokedex == null || pokedex.getSpeciesRecords() == null) {
                return 0;
            }

            int count = 0;
            for (Object value : ((Map<?, ?>) pokedex.getSpeciesRecords()).values()) {
                if (value instanceof SpeciesDexRecord record && record.hasAtLeast(PokedexEntryProgress.CAUGHT)) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
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
