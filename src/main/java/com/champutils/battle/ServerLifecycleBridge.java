package com.champutils.battle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class ServerLifecycleBridge {

    private static MinecraftServer SERVER;



    public static void setServer(
            MinecraftServer server
    ){
        SERVER=server;
    }



    public static MinecraftServer getServer(){
        return SERVER;
    }



    public static ServerPlayer getPlayer(
            UUID uuid
    ){

        if(
                SERVER==null
        ){
            return null;
        }

        return SERVER
                .getPlayerList()
                .getPlayer(
                        uuid
                );
    }

}