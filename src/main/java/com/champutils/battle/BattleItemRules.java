package com.champutils.battle;

import com.champutils.config.Config;
import com.champutils.config.Format;

import net.minecraft.server.level.ServerPlayer;

public class BattleItemRules {

    private BattleItemRules(){}

    // =========================
    // GLOBAL CHECK
    // =========================
    public static boolean canUseBattleItems(
            ServerPlayer player,
            String formatId
    ) {

        if (formatId == null) {
            return true;
        }

        Format format =
                Config.formats.get(
                        formatId.toLowerCase()
                );

        if (format == null) {
            return true;
        }

        return format.allow_battle_items;
    }


    // =========================
    // HELPER
    // =========================
    public static boolean battleItemsBlocked(
            ServerPlayer player,
            String formatId
    ) {
        return !canUseBattleItems(
                player,
                formatId
        );
    }
}