package com.champutils.gym;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.badge.BadgeManager;
import com.champutils.badge.BadgeType;
import com.champutils.badge.BadgeUnlockManager;

import com.champutils.battle.BattleStateManager;
import com.champutils.worldevent.WorldEventManager;
import com.champutils.worldevent.WorldEventBindingRegistry;
import com.champutils.cosmetic.TitleManager;

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
 DISCARD TEMP GYM TEAM
========================= */

            try{

                GymNpcPartyBuilder.clearStoredGymTeam(
                        gymNpc.getEntity()
                );

                System.out.println(
                        "[ChampUtils] Gym NPC temporary team cleared after battle."
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

            if(!awarded){
                winner.sendSystemMessage(Component.literal("§7You already earned this badge."));
            }

            // Always refresh progression/title unlocks after a gym victory. This repairs players who
            // already had the badge before title SQL/config logic was fixed.
            BadgeUnlockManager.processUnlocks(winner);
            GymProgressRepository.recordAttempt(winner, badge, true);
            unlockGymTitles(winner, badge);

            if(!awarded){ return; }



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

    private static void unlockGymTitles(ServerPlayer player, BadgeType badge) {
        if (player == null || badge == null) return;
        TitleManager.unlock(player, "champion_spark");
        switch (badge) {
            case BOULDER -> TitleManager.unlock(player, "boulder_badge");
            case CASCADE -> TitleManager.unlock(player, "cascade_badge");
            case THUNDER -> TitleManager.unlock(player, "thunder_badge");
            case RAINBOW -> TitleManager.unlock(player, "rainbow_badge");
            case SOUL -> TitleManager.unlock(player, "soul_badge");
            case MARSH -> TitleManager.unlock(player, "marsh_badge");
            case VOLCANO -> TitleManager.unlock(player, "volcano_badge");
            case EARTH -> TitleManager.unlock(player, "earth_badge");
            case LORELEI -> TitleManager.unlock(player, "lorelei_badge");
            case BRUNO -> TitleManager.unlock(player, "bruno_badge");
            case AGATHA -> TitleManager.unlock(player, "agatha_badge");
            case LANCE -> TitleManager.unlock(player, "lance_badge");
            case CHAMPION -> TitleManager.unlock(player, "champion");
        }
        if (GymProgressRepository.defeatedCount(player) >= 8) TitleManager.unlock(player, "gym_champion");
    }

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