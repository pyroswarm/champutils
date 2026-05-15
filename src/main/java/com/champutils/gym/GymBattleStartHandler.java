package com.champutils.gym;

import com.champutils.badge.BadgeType;
import com.champutils.battle.BattleStateManager;
import com.champutils.worldevent.WorldEventManager;
import com.champutils.worldevent.WorldEventBindingRegistry;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class GymBattleStartHandler {

    public static void register() {

        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {

            BattleStartedEvent.Pre pre =
                    (BattleStartedEvent.Pre) event;

            ServerPlayer player = null;
            NPCBattleActor gymNpc = null;


            for(
                    var actor :
                    pre.getBattle().getActors()
            ){

                if(
                        actor instanceof PlayerBattleActor p
                ){
                    player = p.getEntity();
                }

                if(
                        actor instanceof NPCBattleActor npc
                ){
                    gymNpc = npc;
                }
            }


            if(
                    player == null
                            ||
                            gymNpc == null
            ){
                return;
            }


            /*
             * World event NPCs can be bound to existing NPCs, including NPCs
             * that were previously gym-bound. Bound world-event NPCs must never
             * run gym logic, even before/after the event is active, otherwise
             * the old gym level cap/reward handler can intercept the click.
             */
            if(
                    WorldEventBindingRegistry.isBoundNpc(
                            gymNpc.getNpc()
                                    .getUUID()
                    )
                            ||
                    WorldEventManager.getByNpc(
                            gymNpc.getNpc()
                                    .getUUID()
                    ) != null
            ){
                return;
            }


            if(
                    !GymRegistry.isGymNpc(
                            gymNpc.getNpc()
                                    .getUUID()
                    )
            ){
                return;
            }


            // IMPORTANT:
            // mark player in battle so queue pauses
            BattleStateManager.setInBattle(
                    player,
                    true
            );


            BadgeType badge =
                    GymRegistry.getBadgeForNpc(
                            gymNpc.getNpc()
                                    .getUUID()
                    );

            GymConfig.GymDefinition gym =
                    GymConfig.getGym(
                            badge
                    );

            if(gym == null){
                return;
            }


            String requiredGroup =
                    requiredGroup(
                            badge
                    );


            if(
                    !hasGroup(
                            player,
                            requiredGroup
                    )
            ){

                BattleStateManager.setInBattle(
                        player,
                        false
                );

                player.sendSystemMessage(
                        Component.literal(
                                lockedMessage(
                                        badge
                                )
                        )
                );

                ServerPlayer p = player;

                p.server.execute(
                        () -> p.closeContainer()
                );

                pre.cancel();

                return;
            }



            for(
                    Pokemon mon :
                    PlayerExtensionsKt.party(
                            player
                    )
            ){

                if(mon==null){
                    continue;
                }


                if(
                        mon.getLevel()
                                >
                                gym.levelCap
                ){

                    BattleStateManager.setInBattle(
                            player,
                            false
                    );

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

                    return;
                }
            }

        });
    }



    private static String requiredGroup(
            BadgeType badge
    ){

        return switch(badge){

            case BOULDER -> null;
            case CASCADE -> "gym1";
            case THUNDER -> "gym2";
            case RAINBOW -> "gym3";
            case SOUL -> "gym4";
            case MARSH -> "gym5";
            case VOLCANO -> "gym6";
            case EARTH -> "gym7";
            case LORELEI -> "gym8";
            case BRUNO -> "elite4-1";
            case AGATHA -> "elite4-2";
            case LANCE -> "elite4-3";
            case CHAMPION -> "elite4-4";
        };
    }



    private static boolean hasGroup(
            ServerPlayer player,
            String group
    ){

        if(group==null){
            return true;
        }

        try{

            LuckPerms lp =
                    LuckPermsProvider.get();

            User user =
                    lp.getUserManager()
                            .getUser(
                                    player.getUUID()
                            );

            if(user==null){
                return false;
            }

            return user.getInheritedGroups(
                    user.getQueryOptions()
            ).stream().anyMatch(
                    g ->
                            g.getName()
                                    .equalsIgnoreCase(
                                            group
                                    )
            );

        }
        catch(Exception e){
            e.printStackTrace();
            return false;
        }
    }



    private static String lockedMessage(
            BadgeType badge
    ){

        return switch(badge){

            case CASCADE -> "§cDefeat Brock first.";
            case THUNDER -> "§cDefeat Misty first.";
            case RAINBOW -> "§cDefeat Lt. Surge first.";
            case SOUL -> "§cDefeat Erika first.";
            case MARSH -> "§cDefeat Koga first.";
            case VOLCANO -> "§cDefeat Sabrina first.";
            case EARTH -> "§cDefeat Blaine first.";
            case LORELEI -> "§cDefeat Giovanni first.";
            case BRUNO -> "§cDefeat Lorelei first.";
            case AGATHA -> "§cDefeat Bruno first.";
            case LANCE -> "§cDefeat Agatha first.";
            case CHAMPION -> "§cDefeat Lance first.";
            default -> "§cThis challenge is locked.";
        };
    }
}