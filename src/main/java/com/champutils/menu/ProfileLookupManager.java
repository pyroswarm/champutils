package com.champutils.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ProfileLookupManager {

    private static final Set<UUID> WAITING =
            new HashSet<>();



    public static void beginLookup(
            ServerPlayer player
    ){

        WAITING.add(
                player.getUUID()
        );

        player.sendSystemMessage(
                Component.literal(
                        "§aType a player name in chat to open their profile."
                )
        );

        player.sendSystemMessage(
                Component.literal(
                        "§7Type §ccancel §7to exit."
                )
        );
    }



    public static boolean isWaiting(
            ServerPlayer player
    ){
        return WAITING.contains(
                player.getUUID()
        );
    }



    public static void stopLookup(
            ServerPlayer player
    ){
        WAITING.remove(
                player.getUUID()
        );
    }



    public static void handleChat(
            ServerPlayer player,
            String message
    ){

        if(
                !isWaiting(player)
        ){
            return;
        }

        stopLookup(
                player
        );


        if(
                message.equalsIgnoreCase(
                        "cancel"
                )
        ){

            player.sendSystemMessage(
                    Component.literal(
                            "§7Player lookup cancelled."
                    )
            );

            return;
        }


        PlayerProfileMenu.open(
                player,
                message
        );
    }

}