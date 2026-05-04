package com.champutils.battle;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;

import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;

import net.minecraft.server.level.ServerPlayer;

public class CobblemonBattleHandler {

    public static void register() {

        /*
         =========================
         TRACK BATTLE STATE
         =========================
         */
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> {

            BattleStartedEvent e =
                    (BattleStartedEvent) event;

            for (
                    Object actor :
                    e.getBattle().getActors()
            ) {

                if (
                        actor instanceof PlayerBattleActor p
                ) {

                    ServerPlayer player =
                            (ServerPlayer) p.getEntity();

                    BattleStateManager.setInBattle(
                            player,
                            true
                    );

                    BattleStateManager.setBattle(
                            player,
                            e.getBattle()
                    );
                }
            }
        });



        /*
         =========================
         BATTLE END
         =========================
         */
        CobblemonEvents.BATTLE_VICTORY.subscribe(event -> {

            BattleVictoryEvent e =
                    (BattleVictoryEvent) event;

            clearPlayersFromBattle(
                    e.getBattle()
                            .getActors()
            );

            ServerPlayer winner = null;
            ServerPlayer loser = null;

            /*
             Find player winner
             */
            for (
                    var actor :
                    e.getWinners()
            ) {

                if (
                        actor instanceof PlayerBattleActor p
                ) {

                    winner =
                            (ServerPlayer) p.getEntity();

                    break;
                }
            }

            /*
             Find player loser (if one exists)
             */
            for (
                    var actor :
                    e.getLosers()
            ) {

                if (
                        actor instanceof PlayerBattleActor p
                ) {

                    loser =
                            (ServerPlayer) p.getEntity();

                    break;
                }
            }

            /*
             IMPORTANT FIX:

             Wild battles:
             - winner exists
             - loser is wild pokemon

             NPC battles:
             - winner exists
             - loser may be NPC

             We still want profession XP.
             */
            if (winner != null) {

                BattleListener.onBattleEnd(
                        winner,
                        loser
                );
            }
        });



        /*
         =========================
         SAFETY CLEANUP
         =========================
         */
        CobblemonEvents.BATTLE_FAINTED.subscribe(event -> {

            BattleFaintedEvent e =
                    (BattleFaintedEvent) event;

            try {

                clearPlayersFromBattle(
                        e.getBattle()
                                .getActors()
                );

            } catch (Exception ignored) {
            }
        });
    }



    private static void clearPlayersFromBattle(
            Iterable<?> actors
    ) {

        for (
                Object actor :
                actors
        ) {

            if (
                    actor instanceof PlayerBattleActor p
            ) {

                ServerPlayer player =
                        (ServerPlayer) p.getEntity();

                BattleStateManager.setInBattle(
                        player,
                        false
                );

                BattleStateManager.clearBattle(
                        player
                );
            }
        }
    }
}