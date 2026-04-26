package com.champutils.gym;

import com.champutils.badge.BadgeType;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class GymBattleStartHandler {

    public static void register() {

        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {

            BattleStartedEvent.Pre pre =
                    (BattleStartedEvent.Pre) event;

            ServerPlayer player = null;
            NPCBattleActor gymNpc = null;


/* =========================
 FIND ACTORS
========================= */

            for (
                    var actor :
                    pre.getBattle().getActors()
            ) {

                if (
                        actor instanceof PlayerBattleActor p
                ) {
                    player =
                            p.getEntity();
                }

                if (
                        actor instanceof NPCBattleActor npc
                ) {
                    gymNpc = npc;
                }

            }


            if (
                    player == null
                            ||
                            gymNpc == null
            ) {
                return;
            }


/* =========================
 ONLY REGISTERED GYM NPCS
========================= */

            if (
                    !GymRegistry.isGymNpc(
                            gymNpc.getNpc()
                                    .getUUID()
                    )
            ) {
                return;
            }


            BadgeType badge =
                    GymRegistry.getBadgeForNpc(
                            gymNpc.getNpc()
                                    .getUUID()
                    );

            GymConfig.GymDefinition gym =
                    GymConfig.getGym(
                            badge
                    );

            if (
                    gym == null
            ) {
                return;
            }


            System.out.println(
                    "[ChampUtils] Checking level cap for "
                            + badge.name()
                            + " cap="
                            + gym.levelCap
            );


/* =========================
 LEVEL CAP CHECK
========================= */

            for (
                    Pokemon mon :
                    PlayerExtensionsKt.party(
                            player
                    )
            ) {

                if (
                        mon == null
                ) {
                    continue;
                }


                System.out.println(
                        "[ChampUtils] "
                                + mon.getSpecies()
                                .getName()
                                + " lvl "
                                + mon.getLevel()
                );


                if (
                        mon.getLevel()
                                >
                                gym.levelCap
                ) {

                    player.sendSystemMessage(
                            Component.literal(
                                    "§cThis gym has a level cap of "
                                            + gym.levelCap
                            )
                    );

                    ServerPlayer p = player;

                    p.server.execute(
                            () -> p.closeContainer()
                    );

                    pre.cancel();

                    System.out.println(
                            "[ChampUtils] Battle blocked by level cap."
                    );

                    return;
                }

            }

        });

    }

}