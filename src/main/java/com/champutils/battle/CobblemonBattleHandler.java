package com.champutils.battle;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;

import net.minecraft.server.level.ServerPlayer;

public class CobblemonBattleHandler {

    public static void register() {

        CobblemonEvents.BATTLE_VICTORY.subscribe(event -> {

            BattleVictoryEvent e = (BattleVictoryEvent) event;

            for (var actor : e.getBattle().getActors()) {
                if (actor instanceof PlayerBattleActor playerActor) {
                    ServerPlayer player = (ServerPlayer) playerActor.getEntity();
                    BattleStateManager.setInBattle(player, false);
                }
            }

            ServerPlayer winner = null;
            ServerPlayer loser = null;

            for (var actor : e.getWinners()) {
                if (actor instanceof PlayerBattleActor playerActor) {
                    winner = (ServerPlayer) playerActor.getEntity();
                    break;
                }
            }

            for (var actor : e.getLosers()) {
                if (actor instanceof PlayerBattleActor playerActor) {
                    loser = (ServerPlayer) playerActor.getEntity();
                    break;
                }
            }

            if (winner != null && loser != null) {
                BattleListener.onBattleEnd(winner, loser);
            }
        });
    }
}