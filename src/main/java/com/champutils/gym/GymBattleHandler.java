package com.champutils.gym;

import com.champutils.badge.BadgeManager;
import com.champutils.badge.BadgePermissionManager;
import com.champutils.badge.BadgeType;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;

import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class GymBattleHandler {

    public static void register() {

        CobblemonEvents.BATTLE_VICTORY.subscribe(
                event -> {

                    BattleVictoryEvent e =
                            (BattleVictoryEvent) event;

                    handleVictory(
                            e
                    );
                }
        );
    }



    private static void handleVictory(
            BattleVictoryEvent e
    ){

        try{

            ServerPlayer winner =
                    null;

            NPCBattleActor gymNpc =
                    null;



            /*
             * Find winning player
             */

            for(
                    var actor :
                    e.getWinners()
            ){

                if(
                        actor instanceof PlayerBattleActor playerActor
                ){

                    winner =
                            (ServerPlayer)
                                    playerActor.getEntity();

                    break;
                }
            }



            /*
             * Find defeated NPC gym leader
             */

            for(
                    var actor :
                    e.getLosers()
            ){

                if(
                        actor instanceof NPCBattleActor npcActor
                ){

                    gymNpc =
                            npcActor;

                    break;
                }
            }



            if(
                    winner == null
                            ||
                            gymNpc == null
            ){
                return;
            }



            UUID npcUUID =
                    gymNpc.getEntity()
                            .getUUID();



            if(
                    !GymRegistry.isGymNpc(
                            npcUUID
                    )
            ){
                return;
            }



            BadgeType badge =
                    GymRegistry.getBadgeForNpc(
                            npcUUID
                    );


            if(
                    badge == null
            ){
                return;
            }



            boolean awarded =
                    BadgeManager.awardBadge(
                            winner,
                            badge
                    );



            if(
                    awarded
            ){

                winner.sendSystemMessage(
                        Component.literal(
                                "§aGym badge awarded!"
                        )
                );


                /*
                 * Phase 3:
                 * Notify player of newly
                 * unlocked commands
                 */

                BadgePermissionManager.notifyUnlocks(
                        winner
                );


                /*
                 * Optional global broadcast
                 */

                if(
                        winner.getServer()!=null
                ){
                    winner.getServer()
                            .getPlayerList()
                            .broadcastSystemMessage(
                                    Component.literal(
                                            "§6"
                                                    +winner.getName().getString()
                                                    +" earned the "
                                                    +badge.name()
                                                    +" Badge!"
                                    ),
                                    false
                            );
                }

            }
            else{

                winner.sendSystemMessage(
                        Component.literal(
                                "§7You already earned this badge."
                        )
                );
            }

        }
        catch(Exception ex){
            ex.printStackTrace();
        }

    }

}