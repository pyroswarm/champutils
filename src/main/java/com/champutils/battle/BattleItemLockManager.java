package com.champutils.battle;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BattleItemLockManager {

    private static final Set<UUID> LOCKED =
            new HashSet<>();

    public static void lock(
            ServerPlayer player
    ){
        LOCKED.add(
                player.getUUID()
        );
    }

    public static void unlock(
            ServerPlayer player
    ){
        LOCKED.remove(
                player.getUUID()
        );
    }

    public static boolean blocked(
            ServerPlayer player
    ){
        return LOCKED.contains(
                player.getUUID()
        );
    }
}