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


    public static boolean hasPermission(
            ServerPlayer player,
            String permission
    ){
        if(
                player == null
                        || permission == null
                        || permission.isBlank()
        ){
            return false;
        }

        if(
                player.hasPermissions(4)
        ){
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

            if(
                    user == null
            ){
                user =
                        lp.getUserManager()
                                .loadUser(
                                        player.getUUID()
                                )
                                .join();
            }

            return user.getCachedData()
                    .getPermissionData()
                    .checkPermission(
                            permission
                    )
                    .asBoolean();
        }
        catch(Exception ignored){
            return player.hasPermissions(4);
        }
    }


    public static boolean hasExactPermissionNode(
            ServerPlayer player,
            String permission
    ){
        if (player == null || permission == null || permission.isBlank()) return false;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            if (user == null) user = lp.getUserManager().loadUser(player.getUUID()).join();
            String wanted = permission.trim().toLowerCase(java.util.Locale.ROOT);
            return user.resolveInheritedNodes(user.getQueryOptions()).stream().anyMatch(node ->
                    node.getKey() != null
                            && node.getKey().equalsIgnoreCase(wanted)
                            && node.getValue()
            );
        } catch (Exception ignored) {
            return false;
        }
    }

}