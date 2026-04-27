package com.champutils.battle;

import com.champutils.matchmaking.MatchmakingManager;
import com.champutils.matchmaking.ArenaManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DisconnectForfeitManager {

    private static final Map<UUID, PendingReturn> PENDING_RETURNS =
            new HashMap<>();


    private static class PendingReturn {

        double x;
        double y;
        double z;

        float yaw;
        float pitch;

        PendingReturn(
                double x,
                double y,
                double z,
                float yaw,
                float pitch
        ){
            this.x=x;
            this.y=y;
            this.z=z;
            this.yaw=yaw;
            this.pitch=pitch;
        }
    }



    public static void handleDisconnect(
            ServerPlayer quitter
    ){

        if(
                quitter==null
        ){
            return;
        }


        ServerPlayer opponent =
                MatchmakingManager.getOpponent(
                        quitter
                );

        if(
                opponent==null
        ){
            return;
        }


        boolean ranked =
                MatchmakingManager.isRankedMatch(
                        quitter
                );



        // save quitter return location
        PENDING_RETURNS.put(
                quitter.getUUID(),

                new PendingReturn(
                        quitter.getX(),
                        quitter.getY(),
                        quitter.getZ(),
                        quitter.getYRot(),
                        quitter.getXRot()
                )
        );



        opponent.sendSystemMessage(
                Component.literal(
                        ranked
                                ? "§aOpponent disconnected. Win by forfeit."
                                : "§eOpponent disconnected. Casual match ended."
                )
        );



        // return opponent from arena
        ArenaManager.returnPlayer(
                opponent
        );

        ArenaManager.releaseArena(
                opponent
        );



        // clear battle flags
        BattleStateManager.setInBattle(
                quitter,
                false
        );

        BattleStateManager.setInBattle(
                opponent,
                false
        );



        if(
                ranked
        ){

            // ranked disconnect loss
            BattleListener.onBattleEnd(
                    opponent,
                    quitter
            );

        } else {

            // casual:
            // no rating changes
            MatchmakingManager.clearMatch(
                    quitter
            );

            MatchmakingManager.clearMatch(
                    opponent
            );
        }

    }



    public static void handleJoin(
            ServerPlayer player
    ){

        PendingReturn loc =
                PENDING_RETURNS.remove(
                        player.getUUID()
                );

        if(
                loc==null
        ){
            return;
        }


        player.teleportTo(
                loc.x,
                loc.y,
                loc.z
        );

        player.connection.teleport(
                loc.x,
                loc.y,
                loc.z,
                loc.yaw,
                loc.pitch
        );


        player.sendSystemMessage(
                Component.literal(
                        "§cDisconnect counted as a loss."
                )
        );
    }
}