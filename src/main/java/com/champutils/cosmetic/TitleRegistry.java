package com.champutils.cosmetic;

import com.champutils.battle.BattleContextManager;
import com.champutils.profession.ProfessionType;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TitleRegistry {
    private TitleRegistry() {}

    public static Map<String, String> defaults() {
        Map<String, String> out = new LinkedHashMap<>();
        for (TitleConfig.TitleDef def : TitleConfig.titles()) out.put(def.id, def.display);
        return out;
    }

    public static String defaultDisplay(String id) {
        return TitleConfig.display(id);
    }

    public static void unlockProfileStarter(ServerPlayer player) {
        // Profile mode is an emoji chat tag, not a placeholder title.
    }

    public static void handleBattleWin(ServerPlayer player, BattleContextManager.BattleType type) {
        TitleConfig.handleBattleWin(player, type);
    }

    public static void handleCatch(ServerPlayer player) {
        TitleConfig.handleCatch(player);
    }

    public static void handleBoss(ServerPlayer player) {
        TitleConfig.handleBoss(player);
    }

    public static void handleProfessionLevel(ServerPlayer player, ProfessionType profession, int level) {
        TitleConfig.handleProfessionLevel(player, profession, level);
    }
}
