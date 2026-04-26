package com.champutils.badge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;

public class BadgeUnlockManager {

    private static final String[] GYM_GROUPS = {
            "gym1",
            "gym3",
            "gym5",
            "gym8"
    };



    public static void processUnlocks(
            ServerPlayer player
    ) {

        int badges =
                BadgeManager.getBadgeCount(
                        player
                );


        if(
                badges >= 8
        ){
            promote(
                    player,
                    "gym8",
                    "Elite Four Access Unlocked"
            );
            return;
        }


        if(
                badges >= 5
        ){
            promote(
                    player,
                    "gym5",
                    "EV Training Access Unlocked"
            );
            return;
        }


        if(
                badges >= 3
        ){
            promote(
                    player,
                    "gym3",
                    "Portable Heal Unlocked"
            );
            return;
        }


        if(
                badges >= 1
        ){
            promote(
                    player,
                    "gym1",
                    "Portable PC Unlocked"
            );
        }

    }



    private static void promote(
            ServerPlayer player,
            String newGroup,
            String unlockMessage
    ){

        try{

            LuckPerms lp =
                    LuckPermsProvider.get();

            User user =
                    lp.getUserManager()
                            .getUser(
                                    player.getUUID()
                            );

            if(
                    user == null
            ){
                return;
            }



            if(
                    hasGroup(
                            user,
                            newGroup
                    )
            ){
                return;
            }



/* =========================
 REMOVE OLD GYM GROUPS ONLY
========================= */

            for(
                    String group :
                    GYM_GROUPS
            ){

                user.data().remove(
                        InheritanceNode.builder(
                                group
                        ).build()
                );
            }



/* =========================
 ADD NEW PROGRESSION GROUP
========================= */

            user.data().add(
                    InheritanceNode.builder(
                            newGroup
                    ).build()
            );


            lp.getUserManager()
                    .saveUser(
                            user
                    );



            player.sendSystemMessage(
                    Component.literal(
                            "§6★ "
                                    + unlockMessage
                    )
            );

        }
        catch(Exception e){
            e.printStackTrace();
        }

    }



    private static boolean hasGroup(
            User user,
            String group
    ){

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



/* =====================================
 ACCESS CHECKS
===================================== */


    public static boolean hasEvTrainingAccess(
            ServerPlayer player
    ){

/*
 Reliable admin/op bypass
 */
        if(
                player.hasPermissions(2)
                        ||
                        player.hasPermissions(4)
                        ||
                        (
                                player.getServer() != null
                                        &&
                                        player.getServer()
                                                .getPlayerList()
                                                .isOp(
                                                        player.getGameProfile()
                                                )
                        )
        ){
            return true;
        }


        return
                hasPlayerGroup(
                        player,
                        "gym5"
                )
                        ||
                        hasPlayerGroup(
                                player,
                                "gym8"
                        );
    }



    public static boolean hasEliteFourAccess(
            ServerPlayer player
    ){

/*
 Reliable admin/op bypass
 */
        if(
                player.hasPermissions(2)
                        ||
                        player.hasPermissions(4)
                        ||
                        (
                                player.getServer() != null
                                        &&
                                        player.getServer()
                                                .getPlayerList()
                                                .isOp(
                                                        player.getGameProfile()
                                                )
                        )
        ){
            return true;
        }


        return hasPlayerGroup(
                player,
                "gym8"
        );
    }



    private static boolean hasPlayerGroup(
            ServerPlayer player,
            String group
    ){

        try{

            LuckPerms lp =
                    LuckPermsProvider.get();

            User user =
                    lp.getUserManager()
                            .getUser(
                                    player.getUUID()
                            );

            if(
                    user == null
            ){
                return false;
            }

            return hasGroup(
                    user,
                    group
            );

        }
        catch(Exception e){
            return false;
        }
    }

}