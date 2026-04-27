package com.champutils.permissions;

import com.champutils.badge.BadgeType;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

import net.luckperms.api.context.ImmutableContextSet;

import net.luckperms.api.model.user.User;
import net.luckperms.api.track.Track;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class LuckPermsHook {

    private static final String TRACK_NAME =
            "gymprogress";



/* =========================
 PROMOTE ONE STEP
========================= */

    public static void promoteForBadge(
            ServerPlayer player,
            BadgeType badge
    ){

        /*
         strict linear progression:
         every badge promotes
        */

        if(
                !isPromotionBattle(
                        badge
                )
        ){
            return;
        }

        try{

            LuckPerms lp =
                    LuckPermsProvider.get();

            User user =
                    lp.getUserManager()
                            .loadUser(
                                    player.getUUID()
                            ).join();


            Track track =
                    lp.getTrackManager()
                            .getTrack(
                                    TRACK_NAME
                            );


            if(
                    track == null
            ){

                System.out.println(
                        "[ChampUtils] Missing LuckPerms track: "
                                + TRACK_NAME
                );

                player.sendSystemMessage(
                        Component.literal(
                                "§cGym progression track missing."
                        )
                );

                return;
            }



            var result =
                    track.promote(
                            user,
                            ImmutableContextSet.empty()
                    );



            lp.getUserManager()
                    .saveUser(
                            user
                    );



            System.out.println(
                    "[ChampUtils] LuckPerms promote success="
                            + result.wasSuccessful()
            );



            if(
                    result.wasSuccessful()
            ){

                player.sendSystemMessage(
                        Component.literal(
                                "§aProgression rank advanced!"
                        )
                );

            }
            else{

                player.sendSystemMessage(
                        Component.literal(
                                "§cProgression promotion failed."
                        )
                );
            }

        }
        catch(Exception e){

            e.printStackTrace();

            player.sendSystemMessage(
                    Component.literal(
                            "§cLuckPerms error during promotion."
                    )
            );
        }

    }




/* =========================
 EVERY BADGE PROMOTES
========================= */

    private static boolean isPromotionBattle(
            BadgeType badge
    ){

        return badge != null;
    }




/* =========================
 OPTIONAL DEBUG HELPER
========================= */

    public static String getTrackName(){
        return TRACK_NAME;
    }

}