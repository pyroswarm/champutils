package com.champutils.commands;

import com.champutils.rank.SeasonManager;
import com.champutils.rank.SeasonArchiveManager;
import com.champutils.rank.LeaderboardManager;
import com.champutils.profile.PlayerDataManager;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.io.File;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

public class SeasonCommand {

    private static String pendingAction=null;
    private static String pendingSeasonName=null;
    private static int pendingSeasonRemove=-1;

    private static long confirmExpiry=0;

    private static final long CONFIRM_MS=
            30000;



    private static void armConfirm(
            String action,
            String seasonName,
            int seasonNum
    ){

        pendingAction=action;
        pendingSeasonName=seasonName;
        pendingSeasonRemove=seasonNum;

        confirmExpiry=
                System.currentTimeMillis()
                        +
                        CONFIRM_MS;
    }



    private static boolean confirmExpired(){

        return
                System.currentTimeMillis()
                        >
                        confirmExpiry;
    }



    private static void sendPreview(
            CommandContext<CommandSourceStack> ctx,
            int rp
    ){

        int reset=
                SeasonManager.softReset(
                        rp
                );

        ctx.getSource().sendSuccess(
                ()->Component.literal(
                        "§7"
                                +rp
                                +" → "
                                +reset
                ),
                false
        );
    }



    public static void register(){

        CommandRegistrationCallback.EVENT.register(
                (dispatcher,registry,env)->{

                    dispatcher.register(

                            literal("season")



                                    .then(
                                            literal("info")
                                                    .executes(ctx->{

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§6Season "
                                                                                +SeasonManager.CURRENT_SEASON
                                                                                +" §e"
                                                                                +SeasonManager.CURRENT_NAME
                                                                ),
                                                                false
                                                        );

                                                        return 1;
                                                    })
                                    )



                                    // =====================
                                    // NEW PREVIEW COMMAND
                                    // =====================

                                    .then(
                                            literal("preview")
                                                    .requires(
                                                            s->s.hasPermission(4)
                                                    )

                                                    .executes(ctx->{

                                                        var top=
                                                                LeaderboardManager.getTop(
                                                                        1
                                                                );

                                                        int players=
                                                                PlayerDataManager
                                                                        .getAllPlayers()
                                                                        .size();

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§6--- Season Preview ---"
                                                                ),
                                                                false
                                                        );

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§ePlayers affected: §f"
                                                                                +players
                                                                ),
                                                                false
                                                        );

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§7Soft Reset Examples"
                                                                ),
                                                                false
                                                        );


                                                        sendPreview(
                                                                ctx,
                                                                300
                                                        );

                                                        sendPreview(
                                                                ctx,
                                                                500
                                                        );

                                                        sendPreview(
                                                                ctx,
                                                                1000
                                                        );


                                                        if(
                                                                !top.isEmpty()
                                                        ){

                                                            var p=
                                                                    top.get(0);

                                                            int newRp=
                                                                    SeasonManager.softReset(
                                                                            p.rp
                                                                    );

                                                            ctx.getSource().sendSuccess(
                                                                    ()->Component.literal(
                                                                            "§6Top Player: §f"
                                                                                    +p.playerName
                                                                                    +" "
                                                                                    +p.rp
                                                                                    +" → "
                                                                                    +newRp
                                                                    ),
                                                                    false
                                                            );
                                                        }


                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§aRunning /season start will archive Top100 snapshot."
                                                                ),
                                                                false
                                                        );

                                                        return 1;
                                                    })
                                    )



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
                                                                                        "§cRun /season confirm"
                                                                                ),
                                                                                false
                                                                        );

                                                                        return 1;
                                                                    })
                                                    )
                                    )



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
                                                                        "§cRun /season confirm to rollback."
                                                                ),
                                                                false
                                                        );

                                                        return 1;
                                                    })
                                    )



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
                                                                        "§cRun /season confirm"
                                                                ),
                                                                false
                                                        );

                                                        return 1;
                                                    })
                                    )



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
                                                                                        "§cDeletes Season "
                                                                                                +season
                                                                                                +" archives + top100 snapshot."
                                                                                ),
                                                                                false
                                                                        );

                                                                        ctx.getSource().sendSuccess(
                                                                                ()->Component.literal(
                                                                                        "§cRun /season confirm"
                                                                                ),
                                                                                false
                                                                        );

                                                                        return 1;
                                                                    })
                                                    )
                                    )



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

                                                            ctx.getSource()
                                                                    .sendFailure(
                                                                            Component.literal(
                                                                                    "Nothing pending."
                                                                            )
                                                                    );

                                                            return 0;
                                                        }



                                                        switch(
                                                                pendingAction
                                                        ){

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

                                                                    for(
                                                                            File f :
                                                                            files
                                                                    ){

                                                                        if(
                                                                                f.getName()
                                                                                        .startsWith(
                                                                                                "season_"
                                                                                        )
                                                                        ){
                                                                            continue;
                                                                        }

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

                                                                    for(
                                                                            File f :
                                                                            files2
                                                                    ){

                                                                        if(
                                                                                f.getName()
                                                                                        .startsWith(
                                                                                                "season_"
                                                                                        )
                                                                        ){
                                                                            continue;
                                                                        }

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

                                                                    SeasonArchiveManager
                                                                            .removeSeasonSnapshot(
                                                                                    pendingSeasonRemove
                                                                            );
                                                                }

                                                                break;
                                                        }



                                                        pendingAction=null;
                                                        pendingSeasonName=null;
                                                        pendingSeasonRemove=-1;

                                                        return 1;
                                                    })
                                    )

                    );

                });
    }

}