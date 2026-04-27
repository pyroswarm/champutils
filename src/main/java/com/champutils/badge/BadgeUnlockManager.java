package com.champutils.badge;

import net.minecraft.server.level.ServerPlayer;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

public class BadgeUnlockManager {

    public static void processUnlocks(
            ServerPlayer player
    ){
        /* progression now handled by LP track */
    }



    public static boolean hasEvTrainingAccess(
            ServerPlayer player
    ){
        return hasGroup(
                player,
                "gym5"
        )
                || hasGroup(
                player,
                "gym8"
        );
    }


    public static boolean hasEliteFourAccess(
            ServerPlayer player
    ){
        return hasGroup(
                player,
                "gym8"
        );
    }



    private static boolean hasGroup(
            ServerPlayer player,
            String group
    ){

        try{

            LuckPerms lp=
                    LuckPermsProvider.get();

            User user=
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
                    g->
                            g.getName()
                                    .equalsIgnoreCase(
                                            group
                                    )
            );

        }
        catch(Exception e){
            return false;
        }

    }

}