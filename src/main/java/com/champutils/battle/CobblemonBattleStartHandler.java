package com.champutils.battle;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;

import com.champutils.matchmaking.MatchmakingManager;
import com.champutils.validation.TeamValidator;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CobblemonBattleStartHandler {

    public static void register() {

        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {

            BattleStartedEvent.Pre e =
                    (BattleStartedEvent.Pre) event;


            List<ServerPlayer> players =
                    new ArrayList<>();


            // =========================
            // MARK ALL PLAYER ACTORS
            // (covers wild battles too)
            // =========================
            for(
                    var actor :
                    e.getBattle().getActors()
            ){

                if(
                        actor instanceof PlayerBattleActor playerActor
                ){

                    ServerPlayer player =
                            (ServerPlayer)
                                    playerActor.getEntity();

                    players.add(
                            player
                    );

                    // IMPORTANT:
                    // mark any battle (wild/gym/pvp)
                    BattleStateManager.setInBattle(
                            player,
                            true
                    );
                }
            }



            // No player somehow
            if(
                    players.isEmpty()
            ){
                return;
            }



            // =========================
            // ONLY RUN RANKED PVP LOGIC
            // IF THERE ARE 2 PLAYERS
            // =========================

            if(
                    players.size() < 2
            ){
                // wild / npc battle
                return;
            }


            ServerPlayer p1 =
                    players.get(0);

            ServerPlayer p2 =
                    players.get(1);



            // =========================
            // ONLY RANKED ENFORCEMENT
            // =========================

            if(
                    !MatchmakingManager
                            .isRankedMatch(
                                    p1
                            )
            ){
                return;
            }



            // =========================
            // LOCK BATTLE ITEMS
            // =========================

            if(
                    BattleItemRules
                            .battleItemsBlocked(
                                    p1,
                                    "ranked"
                            )
            ){

                BattleItemLockManager.lock(
                        p1
                );

                BattleItemLockManager.lock(
                        p2
                );
            }



            // =========================
            // HEAL BOTH TEAMS
            // =========================

            BattlePrepManager.prepareRankedBattle(
                    p1,
                    p2
            );



            // =========================
            // FINAL LEGALITY CHECK
            // =========================

            String err1=
                    TeamValidator.validate(
                            p1,
                            "ranked"
                    );

            String err2=
                    TeamValidator.validate(
                            p2,
                            "ranked"
                    );


            if(
                    err1!=null
                            ||
                            err2!=null
            ){

                cancelMatch(
                        e,
                        p1,
                        p2,

                        err1!=null
                                ? err1
                                : "Opponent has invalid team",

                        err2!=null
                                ? err2
                                : "Opponent has invalid team"
                );
            }

        });
    }




    private static void cancelMatch(
            BattleStartedEvent.Pre event,
            ServerPlayer p1,
            ServerPlayer p2,
            String m1,
            String m2
    ){

        event.cancel();


        BattleStateManager.setInBattle(
                p1,
                false
        );

        BattleStateManager.setInBattle(
                p2,
                false
        );


        BattleItemLockManager.unlock(
                p1
        );

        BattleItemLockManager.unlock(
                p2
        );


        p1.sendSystemMessage(
                Component.literal(
                        "§c"+m1
                )
        );

        p2.sendSystemMessage(
                Component.literal(
                        "§c"+m2
                )
        );


        MatchmakingManager.clearMatch(
                p1
        );

        MatchmakingManager.clearMatch(
                p2
        );
    }
}