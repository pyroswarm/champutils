package com.champutils.battle;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class BattleStateManager {

    private static final Map<UUID,Boolean> IN_BATTLE =
            new ConcurrentHashMap<>();

    private static final Map<UUID,Object> ACTIVE_BATTLES =
            new ConcurrentHashMap<>();



    public static void setInBattle(
            ServerPlayer player,
            boolean inBattle
    ){

        if(inBattle){
            IN_BATTLE.put(
                    player.getUUID(),
                    true
            );
        }else{
            IN_BATTLE.remove(
                    player.getUUID()
            );

            ACTIVE_BATTLES.remove(
                    player.getUUID()
            );
        }
    }



    public static boolean isInBattle(
            ServerPlayer player
    ){
        return IN_BATTLE.containsKey(
                player.getUUID()
        );
    }



    public static void setBattle(
            ServerPlayer player,
            Object battle
    ){
        ACTIVE_BATTLES.put(
                player.getUUID(),
                battle
        );
    }



    public static Object getBattle(
            ServerPlayer player
    ){
        return ACTIVE_BATTLES.get(
                player.getUUID()
        );
    }



    public static void clearBattle(
            ServerPlayer player
    ){
        ACTIVE_BATTLES.remove(
                player.getUUID()
        );
    }
}