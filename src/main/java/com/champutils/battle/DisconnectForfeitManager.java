package com.champutils.battle;

import com.champutils.matchmaking.MatchmakingManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DisconnectForfeitManager {

    /*
    Store original locations in case player disconnects mid-ranked battle
     */
    private static final Map<UUID, SavedLocation> RETURN_LOCATIONS =
            new ConcurrentHashMap<>();


    private static class SavedLocation {

        String dimensionId;
        BlockPos pos;

        SavedLocation(
                String dimensionId,
                BlockPos pos
        ){
            this.dimensionId=dimensionId;
            this.pos=pos;
        }
    }



/* =========================
SAVE LOCATION BEFORE BATTLE
========================= */

    public static void saveReturnLocation(
            ServerPlayer player
    ){

        try{

            RETURN_LOCATIONS.put(
                    player.getUUID(),
                    new SavedLocation(
                            player.level()
                                    .dimension()
                                    .location()
                                    .toString(),

                            player.blockPosition()
                    )
            );

        }
        catch(Exception ignored){
        }
    }



/* =========================
DISCONNECT FORFEIT
========================= */

    public static void handleDisconnect(
            ServerPlayer quitter
    ){

        if(
                quitter==null
        ){
            return;
        }


        if(
                !BattleStateManager.isInBattle(
                        quitter
                )
        ){
            return;
        }


        ServerPlayer opponent=
                MatchmakingManager.getOpponent(
                        quitter
                );

        if(
                opponent==null
        ){
            BattleStateManager.setInBattle(
                    quitter,
                    false
            );

            return;
        }



/* =========================
RP RESULT
========================= */

        BattleListener.onBattleEnd(
                opponent,
                quitter
        );



/* =========================
MESSAGING
========================= */

        opponent.sendSystemMessage(
                Component.literal(
                        "§aOpponent forfeited by disconnect."
                )
        );



/* =========================
CLEAR BATTLE STATE
========================= */

        BattleStateManager.setInBattle(
                quitter,
                false
        );

        BattleStateManager.setInBattle(
                opponent,
                false
        );

        BattleStateManager.clearBattle(
                quitter
        );

        BattleStateManager.clearBattle(
                opponent
        );






/* =========================
CLEAR MATCH DATA
========================= */

        MatchmakingManager.clearMatch(
                quitter
        );

        MatchmakingManager.clearMatch(
                opponent
        );

    }



/* =========================
REJOIN RETURN LOCATION
========================= */

    public static void handleJoin(
            ServerPlayer player
    ){

        SavedLocation saved=
                RETURN_LOCATIONS.remove(
                        player.getUUID()
                );

        if(
                saved==null
        ){
            return;
        }

        try{

            MinecraftServer server=
                    player.server;

            if(
                    server==null
            ){
                return;
            }


            ServerLevel targetLevel=
                    null;

            for(
                    ServerLevel level :
                    server.getAllLevels()
            ){

                if(
                        level.dimension()
                                .location()
                                .toString()
                                .equals(
                                        saved.dimensionId
                                )
                ){
                    targetLevel=level;
                    break;
                }
            }



/*
Return to original location
 */
            if(
                    targetLevel!=null
            ){

                player.teleportTo(
                        targetLevel,
                        saved.pos.getX()+0.5,
                        saved.pos.getY(),
                        saved.pos.getZ()+0.5,
                        player.getYRot(),
                        player.getXRot()
                );

                return;
            }


/*
Fallback:
spawn if original location unavailable
 */
            ServerLevel overworld=
                    server.overworld();

            BlockPos spawn=
                    overworld.getSharedSpawnPos();

            player.teleportTo(
                    overworld,
                    spawn.getX()+0.5,
                    spawn.getY(),
                    spawn.getZ()+0.5,
                    player.getYRot(),
                    player.getXRot()
            );

        }
        catch(Exception ignored){
        }

    }

}