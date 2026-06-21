package com.champutils.gym;

import com.champutils.badge.BadgeType;
import com.champutils.badge.BadgeManager;
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
import net.minecraft.ChatFormatting;
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


            if (GymCooldownManager.isOnCooldown(player)) {

                BattleStateManager.setInBattle(
                        player,
                        false
                );

                player.sendSystemMessage(
                        Component.literal(
                                "§cYou must wait "
                                        + GymCooldownManager.formatRemaining(player)
                                        + " before challenging another gym."
                        )
                );

                ServerPlayer p = player;

                p.server.execute(
                        () -> p.closeContainer()
                );

                pre.cancel();

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

            /*
             * Do not rebuild the NPC party here. Cobblemon has already created the
             * NPCBattleActor by the time BATTLE_STARTED_PRE fires, so changing the
             * NPC's stored party at this point can affect the next challenge instead
             * of the current one. ChampTrainerInteractionListener rebuilds the gym
             * team immediately before BattleBuilder.pvn(), which is the correct time.
             */


            BadgeType requiredBadge =
                    requiredBadge(
                            badge
                    );


            if(
                    requiredBadge != null &&
                    !BadgeManager.hasBadge(
                            player,
                            requiredBadge
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



    private static BadgeType requiredBadge(
            BadgeType badge
    ){

        return switch(badge){

            case CASCADE -> null;
            case MARSH -> BadgeType.CASCADE;
            case EARTH -> BadgeType.MARSH;
            case BOULDER -> BadgeType.EARTH;
            case THUNDER -> BadgeType.BOULDER;
            case RAINBOW -> BadgeType.THUNDER;
            case SOUL -> BadgeType.RAINBOW;
            case VOLCANO -> BadgeType.SOUL;
            case LORELEI -> BadgeType.VOLCANO;
            case BRUNO -> BadgeType.LORELEI;
            case AGATHA -> BadgeType.BRUNO;
            case LANCE -> BadgeType.AGATHA;
            case CHAMPION -> BadgeType.LANCE;
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

            case CASCADE -> "§cThis is the first gym.";
            case MARSH -> "§cDefeat Misty first.";
            case EARTH -> "§cDefeat Sabrina first.";
            case BOULDER -> "§cDefeat Giovanni first.";
            case THUNDER -> "§cDefeat Brock first.";
            case RAINBOW -> "§cDefeat Lt. Surge first.";
            case SOUL -> "§cDefeat Erika first.";
            case VOLCANO -> "§cDefeat Koga first.";
            case LORELEI -> "§cDefeat Blaine first.";
            case BRUNO -> "§cDefeat Lorelei first.";
            case AGATHA -> "§cDefeat Bruno first.";
            case LANCE -> "§cDefeat Agatha first.";
            case CHAMPION -> "§cDefeat Lance first.";
            default -> "§cThis challenge is locked.";
        };
    }
}