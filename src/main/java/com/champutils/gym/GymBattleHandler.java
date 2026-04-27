package com.champutils.gym;

import com.champutils.badge.BadgeManager;
import com.champutils.badge.BadgeType;
import com.champutils.badge.BadgeUnlockManager;

import com.champutils.permissions.LuckPermsHook;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;

import net.minecraft.network.chat.Component;

import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

import net.minecraft.server.level.ServerPlayer;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;

public class GymBattleHandler {

    public static void register(){

        CobblemonEvents.BATTLE_VICTORY.subscribe(
                GymBattleHandler::handleVictory
        );
    }




    private static void handleVictory(
            BattleVictoryEvent event
    ){

        try{

            ServerPlayer winner=null;
            NPCBattleActor gymNpc=null;


/* =========================
 FIND PLAYER WINNER
========================= */

            for(
                    var actor :
                    event.getWinners()
            ){

                if(
                        actor instanceof PlayerBattleActor playerActor
                ){

                    winner=
                            (ServerPlayer)
                                    playerActor.getEntity();

                    break;
                }

            }



/* =========================
 FIND GYM NPC
========================= */

            for(
                    var actor :
                    event.getLosers()
            ){

                if(
                        actor instanceof NPCBattleActor npcActor
                ){
                    gymNpc=npcActor;
                    break;
                }

            }


            if(
                    gymNpc==null
            ){

                for(
                        var actor :
                        event.getWinners()
                ){

                    if(
                            actor instanceof NPCBattleActor npcActor
                    ){
                        gymNpc=npcActor;
                        break;
                    }

                }

            }


            if(
                    gymNpc==null
            ){
                return;
            }



/* =========================
 REGISTERED GYM?
========================= */

            UUID npcUUID=
                    gymNpc.getEntity()
                            .getUUID();

            if(
                    !GymRegistry.isGymNpc(
                            npcUUID
                    )
            ){
                return;
            }



/* =========================
 GET BADGE
========================= */

            BadgeType badge=
                    GymRegistry.getBadgeForNpc(
                            npcUUID
                    );

            if(
                    badge==null
            ){
                return;
            }



/* =========================
 ALWAYS RESET NPC TEAM
========================= */

            try{

                GymNpcPartyBuilder.applyGymTeam(
                        gymNpc.getEntity(),
                        badge
                );

                System.out.println(
                        "[ChampUtils] Gym NPC healed/reset after battle."
                );

            }
            catch(Exception e){
                e.printStackTrace();
            }



/* =========================
 PLAYER LOST?
========================= */

            if(
                    winner==null
            ){
                return;
            }



/* =========================
 AWARD BADGE
========================= */

            boolean awarded=
                    BadgeManager.awardBadge(
                            winner,
                            badge
                    );

            if(
                    !awarded
            ){

                winner.sendSystemMessage(
                        Component.literal(
                                "§7You already earned this badge."
                        )
                );

                return;
            }



/* =========================
 FEATURE UNLOCKS
========================= */

            BadgeUnlockManager.processUnlocks(
                    winner
            );



/* =========================
 LUCKPERMS TRACK PROMOTION
========================= */

            try{

                LuckPermsHook.promoteForBadge(
                        winner,
                        badge
                );

            }
            catch(Exception e){
                e.printStackTrace();
            }



/* =========================
 BADGE POPUP
========================= */

            winner.connection.send(
                    new ClientboundSetTitlesAnimationPacket(
                            10,
                            70,
                            20
                    )
            );

            winner.connection.send(
                    new ClientboundSetTitleTextPacket(
                            Component.literal(
                                    "§6"
                                            + badge.getDisplayName()
                                            + " DEFEATED!"
                            )
                    )
            );

            winner.connection.send(
                    new ClientboundSetSubtitleTextPacket(
                            Component.literal(
                                    titleSubtitle(
                                            badge
                                    )
                            )
                    )
            );


            winner.playNotifySound(
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS,
                    1f,
                    1f
            );



/* =========================
 GLOBAL ANNOUNCEMENT
========================= */

            winner.getServer()
                    .getPlayerList()
                    .broadcastSystemMessage(
                            Component.literal(
                                    "§6"
                                            + winner.getName().getString()
                                            + " defeated "
                                            + badge.getDisplayName()
                                            + "!"
                            ),
                            false
                    );

        }
        catch(Exception ex){
            ex.printStackTrace();
        }

    }





/* =========================
 SUBTITLE TEXT
========================= */

    private static String titleSubtitle(
            BadgeType badge
    ){

        return switch(badge){

            case BOULDER,
                 CASCADE,
                 THUNDER,
                 RAINBOW,
                 SOUL,
                 MARSH,
                 VOLCANO,
                 EARTH ->
                    "§eGym Leader Defeated";

            case LORELEI,
                 BRUNO,
                 AGATHA,
                 LANCE ->
                    "§dElite Four Defeated";

            case CHAMPION ->
                    "§bChampion Defeated";
        };

    }

}