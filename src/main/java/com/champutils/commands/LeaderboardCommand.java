package com.champutils.commands;

import com.champutils.rank.LeaderboardManager;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.network.chat.Component;

import java.util.List;

import static net.minecraft.commands.Commands.literal;

public class LeaderboardCommand {

    public static void register(){

        CommandRegistrationCallback.EVENT.register(
                (dispatcher,r,e)->{

                    dispatcher.register(
                            literal("leaderboard")

                                    .executes(ctx->{

                                        List<LeaderboardManager.Entry> top =
                                                LeaderboardManager.getTop(10);

                                        ctx.getSource().sendSuccess(
                                                ()->Component.literal(
                                                        "§6--- Top Ladder ---"
                                                ),
                                                false
                                        );

                                        for(
                                                int i=0;
                                                i<top.size();
                                                i++
                                        ){

                                            LeaderboardManager.Entry p=
                                                    top.get(i);

                                            int pos=i+1;

                                            ctx.getSource().sendSuccess(
                                                    ()->Component.literal(
                                                            "§e#"
                                                                    +pos
                                                                    +" §f"
                                                                    +p.playerName
                                                                    +" §7- §6"
                                                                    +p.rp
                                                                    +" RP"
                                                    ),
                                                    false
                                            );
                                        }

                                        return 1;

                                    })
                    );

                });
    }
}