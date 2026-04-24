package com.champutils.badge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.*;

public class BadgePermissionManager {

    private static final Map<Integer,List<String>> unlocks =
            new HashMap<>();


    static{

        unlocks.put(
                1,
                List.of(
                        "pc"
                )
        );

        unlocks.put(
                3,
                List.of(
                        "pokeheal"
                )
        );

        unlocks.put(
                5,
                List.of(
                        "ivs"
                )
        );

        unlocks.put(
                8,
                List.of(
                        "evs"
                )
        );
    }



    public static boolean hasCommandUnlocked(
            ServerPlayer player,
            String command
    ){

        int badges =
                BadgeManager
                        .getBadges(player)
                        .size();

        for(
                var entry :
                unlocks.entrySet()
        ){

            if(
                    badges >= entry.getKey()
                            &&
                            entry.getValue().contains(
                                    command.toLowerCase()
                            )
            ){
                return true;
            }
        }

        return false;
    }



    public static void notifyUnlocks(
            ServerPlayer player
    ){

        int badges=
                BadgeManager
                        .getBadges(player)
                        .size();

        if(
                unlocks.containsKey(
                        badges
                )
        ){

            for(
                    String cmd :
                    unlocks.get(
                            badges
                    )
            ){

                player.sendSystemMessage(
                        Component.literal(
                                "§6Unlocked command: /"
                                        +cmd
                        )
                );
            }
        }
    }

}