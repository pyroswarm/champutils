package com.champutils.gym;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.badge.BadgeManager;
import com.champutils.badge.BadgeType;
import com.champutils.badge.BadgeUnlockManager;

import com.champutils.permissions.LuckPermsHook;
import com.champutils.battle.BattleStateManager;
import com.champutils.worldevent.WorldEventManager;
import com.champutils.worldevent.WorldEventBindingRegistry;

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
            ServerPlayer playerParticipant=null;
            NPCBattleActor gymNpc=null;



/* =========================
 FIND PLAYER PARTICIPANT
 AND WINNER
========================= */

            for(
                    var actor :
                    event.getBattle().getActors()
            ){

                if(
                        actor instanceof PlayerBattleActor playerActor
                ){

                    ServerPlayer p =
                            (ServerPlayer)
                                    playerActor.getEntity();

                    playerParticipant = p;

                    // IMPORTANT:
                    // clear gym battle state for queue pause system
                    BattleStateManager.setInBattle(
                            p,
                            false
                    );
                }
            }


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

            /*
             * If this NPC is bound to a world event, never run gym reward/reset
             * logic for it. This prevents old gym bindings from leaking level
             * caps, badge rewards, or team resets into world-event bosses.
             */
            if(
                    WorldEventBindingRegistry.isBoundNpc(
                            npcUUID
                    )
                            ||
                    WorldEventManager.getByNpc(
                            npcUUID
                    ) != null
            ){
                return;
            }

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
 SILENT LUCKPERMS PROMOTION
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
 BADGE TITLE POPUP
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

            ProfessionNotificationSettings.playSound(winner, 
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS,
                    1f,
                    1f
            );



/* =========================
 PERSONAL MESSAGE
========================= */

            winner.sendSystemMessage(
                    Component.literal(
                            "§eYou earned the "
                                    + badge.getDisplayName()
                                    + " Badge!"
                    )
            );



/* =========================
 GLOBAL BROADCAST
========================= */

            winner.getServer()
                    .getPlayerList()
                    .broadcastSystemMessage(
                            Component.literal(
                                    "§6"
                                            + winner.getName()
                                            .getString()
                                            + " defeated the "
                                            + badge.getDisplayName()
                                            + " gym!"
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

        return switch(
                badge
                ){

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