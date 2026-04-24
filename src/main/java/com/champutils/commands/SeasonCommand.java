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

    private static String pendingAction = null;
    private static String pendingSeasonName = null;
    private static int pendingSeasonRemove = -1;

    private static long confirmExpiry = 0;

    private static final long CONFIRM_MS =
            30000; //30 sec



    private static void armConfirm(
            String action,
            String seasonName,
            int seasonNum
    ){
        pendingAction = action;
        pendingSeasonName = seasonName;
        pendingSeasonRemove = seasonNum;

        confirmExpiry =
                System.currentTimeMillis()
                        +CONFIRM_MS;
    }


    private static boolean confirmExpired(){
        return
                System.currentTimeMillis()
                        >confirmExpiry;
    }



    public static void register(){

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, env)->{

                    dispatcher.register(

                            literal("season")



                                    // ========================
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



                                    // ========================
                                    .then(literal("list")
                                            .executes(ctx->{

                                                for(
                                                        int i=1;
                                                        i<=SeasonManager.CURRENT_SEASON;
                                                        i++
                                                ){
                                                    int s=i;

                                                    ctx.getSource().sendSuccess(
                                                            ()->Component.literal(
                                                                    "§eSeason "+s
                                                            ),
                                                            false
                                                    );
                                                }

                                                return 1;
                                            }))



                                    // ========================
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

                                                                        armConfirm(
                                                                                "start",
                                                                                name,
                                                                                -1
                                                                        );

                                                                        ctx.getSource().sendSuccess(
                                                                                ()->Component.literal(
                                                                                        "§cWARNING:\n"
                                                                                                +"Starting a season resets ladder data.\n"
                                                                                                +"Run §e/season confirm §cwithin 30 seconds."
                                                                                ),
                                                                                false
                                                                        );

                                                                        return 1;
                                                                    })
                                                    )
                                    )



                                    // ========================
                                    .then(
                                            literal("rollback")
                                                    .requires(
                                                            s->s.hasPermission(4)
                                                    )

                                                    .executes(ctx->{

                                                        armConfirm(
                                                                "rollback",
                                                                null,
                                                                -1
                                                        );

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§cRollback armed.\n"
                                                                                +"Run §e/season confirm"
                                                                ),
                                                                false
                                                        );

                                                        return 1;
                                                    })
                                    )



                                    // ========================
                                    .then(
                                            literal("removeLast")
                                                    .requires(
                                                            s->s.hasPermission(4)
                                                    )

                                                    .executes(ctx->{

                                                        armConfirm(
                                                                "removeLast",
                                                                null,
                                                                -1
                                                        );

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§cArchive removal armed.\n"
                                                                                +"Run §e/season confirm"
                                                                ),
                                                                false
                                                        );

                                                        return 1;
                                                    })
                                    )



                                    // ========================
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
                                                                                IntegerArgumentType.getInteger(
                                                                                        ctx,
                                                                                        "number"
                                                                                );

                                                                        armConfirm(
                                                                                "remove",
                                                                                null,
                                                                                season
                                                                        );

                                                                        ctx.getSource().sendSuccess(
                                                                                ()->Component.literal(
                                                                                        "§cRemove Season "
                                                                                                +season
                                                                                                +" armed.\n"
                                                                                                +"Run §e/season confirm"
                                                                                ),
                                                                                false
                                                                        );

                                                                        return 1;
                                                                    })
                                                    )
                                    )



                                    // ========================
                                    .then(
                                            literal("confirm")
                                                    .requires(
                                                            s->s.hasPermission(4)
                                                    )

                                                    .executes(ctx->{

                                                        if(
                                                                pendingAction==null
                                                                        ||
                                                                        confirmExpired()
                                                        ){

                                                            pendingAction=null;

                                                            ctx.getSource().sendFailure(
                                                                    Component.literal(
                                                                            "§cNothing pending."
                                                                    )
                                                            );

                                                            return 0;
                                                        }



                                                        switch(pendingAction){

                                                            case "start":

                                                                SeasonManager.startNewSeason(
                                                                        ctx.getSource()
                                                                                .getServer(),
                                                                        pendingSeasonName
                                                                );

                                                                break;



                                                            case "rollback":

                                                                SeasonManager.rollbackSeason(
                                                                        ctx.getSource()
                                                                                .getServer()
                                                                );

                                                                break;



                                                            case "removeLast":

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

                                                                break;



                                                            case "remove":

                                                                File dir2=
                                                                        new File(
                                                                                "config/champutils/seasons"
                                                                        );

                                                                File[] files2=
                                                                        dir2.listFiles(
                                                                                (d,n)->
                                                                                        n.endsWith(".json")
                                                                        );

                                                                if(files2!=null){

                                                                    for(File f:files2){

                                                                        String player=
                                                                                f.getName()
                                                                                        .replace(
                                                                                                ".json",
                                                                                                ""
                                                                                        );

                                                                        SeasonArchiveManager
                                                                                .removeSeason(
                                                                                        player,
                                                                                        pendingSeasonRemove
                                                                                );
                                                                    }
                                                                }

                                                                break;
                                                        }



                                                        pendingAction=null;
                                                        pendingSeasonName=null;
                                                        pendingSeasonRemove=-1;

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§aConfirmed."
                                                                ),
                                                                true
                                                        );

                                                        return 1;
                                                    })
                                    )



                                    // ========================
                                    .then(
                                            literal("cancel")
                                                    .requires(
                                                            s->s.hasPermission(4)
                                                    )

                                                    .executes(ctx->{

                                                        pendingAction=null;
                                                        pendingSeasonName=null;
                                                        pendingSeasonRemove=-1;

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§7Pending action cancelled."
                                                                ),
                                                                false
                                                        );

                                                        return 1;
                                                    })
                                    )

                    );

                });
    }
}