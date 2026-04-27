package com.champutils.permissions;

import com.champutils.badge.BadgeType;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

import net.luckperms.api.model.user.User;
import net.luckperms.api.track.Track;

import net.minecraft.server.level.ServerPlayer;

public class LuckPermsHook {

    private static final String TRACK_NAME =
            "gymprogress";



    public static void promoteForBadge(
            ServerPlayer player,
            BadgeType badge
    ){

        if(
                badge == null
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
                            )
                            .join();

            String before =
                    user.getPrimaryGroup();


            Track track =
                    lp.getTrackManager()
                            .getTrack(
                                    TRACK_NAME
                            );

            if(
                    track == null
            ){
                System.out.println(
                        "[ChampUtils] Missing LP track "
                                + TRACK_NAME
                );
                return;
            }


            track.promote(
                    user,
                    user.getQueryOptions()
                            .context()
            );


            lp.getUserManager()
                    .saveUser(
                            user
                    );


            user =
                    lp.getUserManager()
                            .loadUser(
                                    player.getUUID()
                            )
                            .join();

            String after =
                    user.getPrimaryGroup();


            if(
                    !before.equalsIgnoreCase(
                            after
                    )
            ){
                System.out.println(
                        "[ChampUtils] Rank advanced "
                                + before
                                + " -> "
                                + after
                );
            }
            else{
                System.out.println(
                        "[ChampUtils] Promotion did not advance rank."
                );
            }

        }
        catch(Exception e){
            e.printStackTrace();
        }

    }

}