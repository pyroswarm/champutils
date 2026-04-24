package com.champutils.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.literal;

public class LeaderboardCommand {

    public static void register(){

        CommandRegistrationCallback.EVENT.register(
                (dispatcher,r,e)->{

                    dispatcher.register(
                            literal("leaderboard")

                                    .executes(ctx->{

                                        List<ServerPlayer> players =
                                                new ArrayList<>(
                                                        ctx.getSource()
                                                                .getServer()
                                                                .getPlayerList()
                                                                .getPlayers()
                                                );

                                        players.sort(
                                                (a,b)->
                                                        Integer.compare(
                                                                getRp(b),
                                                                getRp(a)
                                                        )
                                        );

                                        ctx.getSource().sendSuccess(
                                                ()->Component.literal(
                                                        "§6--- Top Ladder ---"
                                                ),
                                                false
                                        );

                                        for(
                                                int i=0;
                                                i<Math.min(
                                                        10,
                                                        players.size()
                                                );
                                                i++
                                        ){

                                            ServerPlayer p=
                                                    players.get(i);

                                            int pos=i+1;

                                            ctx.getSource().sendSuccess(
                                                    ()->Component.literal(
                                                            "§e#"
                                                                    +pos
                                                                    +" §f"
                                                                    +p.getName()
                                                                    .getString()
                                                                    +" §7- §6"
                                                                    +getRp(p)
                                                    ),
                                                    false
                                            );
                                        }

                                        return 1;
                                    })
                    );
                });
    }


    private static int getRp(
            ServerPlayer p
    ){

        var sb=
                p.getScoreboard();

        var obj=
                sb.getObjective(
                        "elo"
                );

        if(obj==null)
            return 0;

        return sb
                .getOrCreatePlayerScore(
                        p,
                        obj
                )
                .get();
    }
}