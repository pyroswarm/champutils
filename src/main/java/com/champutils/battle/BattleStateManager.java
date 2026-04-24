package com.champutils.battle;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BattleStateManager {

    private static final Set<UUID> IN_BATTLE = new HashSet<>();

    public static void setInBattle(ServerPlayer player, boolean value) {
        if (value) {
            IN_BATTLE.add(player.getUUID());
        } else {
            IN_BATTLE.remove(player.getUUID());
        }
    }

    public static boolean isInBattle(ServerPlayer player) {
        return IN_BATTLE.contains(player.getUUID());
    }
}