package com.champutils.commands;

import com.champutils.rank.SeasonManager;
import com.champutils.rank.SeasonArchiveManager;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import java.io.File;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

public class SeasonCommand {

    public static void register(){

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, env)->{

                    dispatcher.register(

                            literal("season")


                                    // ======================
                                    .then(literal("info")
                                            .executes(ctx->{

                                                ctx.getSource().sendSuccess(
                                                        ()->Component.literal(
                                                                "§6Season "
                                                                        +SeasonManager.CURRENT_SEASON
                                                                        +" §7- §e"
                                                                        +SeasonManager.CURRENT_NAME
                                                        ),
                                                        false
                                                );

                                                return 1;
                                            }))



                                    // ======================
                                    .then(literal("list")
                                            .executes(ctx->{

                                                ctx.getSource().sendSuccess(
                                                        ()->Component.literal(
                                                                "§6--- Seasons ---"
                                                        ),
                                                        false
                                                );

                                                for(
                                                        int i=1;
                                                        i<=SeasonManager.CURRENT_SEASON;
                                                        i++
                                                ){

                                                    final int s=i;

                                                    ctx.getSource().sendSuccess(
                                                            ()->Component.literal(
                                                                    "§eSeason "
                                                                            +s
                                                            ),
                                                            false
                                                    );
                                                }

                                                return 1;
                                            }))



                                    // ======================
                                    .then(
                                            literal("start")
                                                    .requires(
                                                            s->s.hasPermission(4)
                                                    )

                                                    .then(
                                                            argument(
                                                                    "name",
                                                                    StringArgumentType.greedyString()
                                                            )

                                                                    .executes(ctx->{

                                                                        String name=
                                                                                StringArgumentType.getString(
                                                                                        ctx,
                                                                                        "name"
                                                                                );

                                                                        SeasonManager.startNewSeason(
                                                                                ctx.getSource()
                                                                                        .getServer(),
                                                                                name
                                                                        );

                                                                        return 1;
                                                                    })
                                                    )
                                    )



                                    // ======================
                                    .then(
                                            literal("rollback")
                                                    .requires(
                                                            s->s.hasPermission(4)
                                                    )

                                                    .executes(ctx->{

                                                        SeasonManager.rollbackSeason(
                                                                ctx.getSource()
                                                                        .getServer()
                                                        );

                                                        return 1;
                                                    })
                                    )



                                    // ======================
                                    // emergency only
                                    .then(
                                            literal("removeLast")
                                                    .requires(
                                                            s->s.hasPermission(4)
                                                    )

                                                    .executes(ctx->{

                                                        File dir=
                                                                new File(
                                                                        "config/champutils/seasons"
                                                                );

                                                        File[] files=
                                                                dir.listFiles(
                                                                        (d,n)->
                                                                                n.endsWith(".json")
                                                                );

                                                        if(files!=null){

                                                            for(File f:files){

                                                                String player=
                                                                        f.getName()
                                                                                .replace(
                                                                                        ".json",
                                                                                        ""
                                                                                );

                                                                SeasonArchiveManager
                                                                        .removeLastSeason(
                                                                                player
                                                                        );
                                                            }
                                                        }

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§cRemoved last season archives."
                                                                ),
                                                                true
                                                        );

                                                        return 1;
                                                    })
                                    )



                                    // ======================
                                    // emergency only
                                    .then(
                                            literal("remove")
                                                    .requires(
                                                            s->s.hasPermission(4)
                                                    )

                                                    .then(
                                                            argument(
                                                                    "number",
                                                                    IntegerArgumentType.integer()
                                                            )

                                                                    .executes(ctx->{

                                                                        int season=
                                                                                IntegerArgumentType
                                                                                        .getInteger(
                                                                                                ctx,
                                                                                                "number"
                                                                                        );

                                                                        File dir=
                                                                                new File(
                                                                                        "config/champutils/seasons"
                                                                                );

                                                                        File[] files=
                                                                                dir.listFiles(
                                                                                        (d,n)->
                                                                                                n.endsWith(".json")
                                                                                );

                                                                        if(files!=null){

                                                                            for(File f:files){

                                                                                String player=
                                                                                        f.getName()
                                                                                                .replace(
                                                                                                        ".json",
                                                                                                        ""
                                                                                                );

                                                                                SeasonArchiveManager
                                                                                        .removeSeason(
                                                                                                player,
                                                                                                season
                                                                                        );
                                                                            }
                                                                        }

                                                                        ctx.getSource().sendSuccess(
                                                                                ()->Component.literal(
                                                                                        "§cRemoved season "
                                                                                                +season
                                                                                ),
                                                                                true
                                                                        );

                                                                        return 1;
                                                                    })
                                                    )
                                    )



                                    // ======================
                                    .then(
                                            literal("wipehistory")
                                                    .requires(
                                                            s->s.hasPermission(4)
                                                    )

                                                    .then(
                                                            argument(
                                                                    "player",
                                                                    StringArgumentType.word()
                                                            )

                                                                    .executes(ctx->{

                                                                        String name=
                                                                                StringArgumentType
                                                                                        .getString(
                                                                                                ctx,
                                                                                                "player"
                                                                                        );

                                                                        SeasonArchiveManager
                                                                                .wipeHistory(
                                                                                        name
                                                                                );

                                                                        return 1;
                                                                    })
                                                    )
                                    )
                    );

                });
    }

}