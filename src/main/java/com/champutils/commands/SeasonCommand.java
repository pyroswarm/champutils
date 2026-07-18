package com.champutils.commands;

import com.champutils.rank.SeasonManager;
import com.champutils.rank.SeasonArchiveManager;
import com.champutils.rank.LeaderboardManager;
import com.champutils.profile.PlayerDataManager;
import com.champutils.menu.ConfirmationMenu;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

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



    private static void openSeasonConfirmation(
            CommandContext<CommandSourceStack> ctx,
            String actionName,
            String detail
    ){
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ConfirmationMenu.open(
                    player,
                    "Confirm Season Action",
                    Items.CLOCK,
                    actionName,
                    new String[]{
                            detail == null ? "§7Review this season action." : detail,
                            "§cAdmin-only season action.",
                            "§cThis may affect many players."
                    },
                    () -> runPendingConfirm(ctx.getSource()),
                    () -> {
                        pendingAction = null;
                        pendingSeasonName = null;
                        pendingSeasonRemove = -1;
                        player.sendSystemMessage(Component.literal("§eSeason action cancelled."));
                    }
            );
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Season confirmations must be completed in-game using the UI."));
        }
    }

    private static int runPendingConfirm(CommandSourceStack source){


                                                        if(
                                                                pendingAction==null
                                                                        ||
                                                                        confirmExpired()
                                                        ){

                                                            pendingAction=null;

                                                            source
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
                                                                        source
                                                                                .getServer(),
                                                                        pendingSeasonName
                                                                );
                                                                break;



                                                            case "rollback":

                                                                SeasonManager.rollbackSeason(
                                                                        source
                                                                                .getServer()
                                                                );
                                                                break;



                                                            case "reset0":

                                                                SeasonManager.resetToSeasonZero(
                                                                        source
                                                                                .getServer()
                                                                );
                                                                break;



                                                            case "preseason":

                                                                SeasonManager.startPreseason(
                                                                        source
                                                                                .getServer()
                                                                );
                                                                break;



                                                            case "set":

                                                                SeasonManager.setCurrentSeason(
                                                                        source
                                                                                .getServer(),
                                                                        pendingSeasonRemove,
                                                                        pendingSeasonName
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
                                                    .requires(s -> s.hasPermission(4))

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
                                                    .requires(s -> s.hasPermission(4))

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

                                                                        openSeasonConfirmation(ctx, "§eStart Season", "§7Start new season: §f" + name);

                                                                        ctx.getSource().sendSuccess(
                                                                                ()->Component.literal(
                                                                                        "§cUse the opened confirmation UI to continue."
                                                                                ),
                                                                                false
                                                                        );

                                                                        return 1;
                                                                    })
                                                    )
                                    )



                                    .then(
                                            literal("rollback")
                                                    .requires(s -> s.hasPermission(4))
                                                    .executes(ctx->{

                                                        armConfirm(
                                                                "rollback",
                                                                null,
                                                                -1
                                                        );

                                                        openSeasonConfirmation(ctx, "§cRollback Season", "§7Rollback the current season state.");

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§cUse the opened confirmation UI to rollback."
                                                                ),
                                                                false
                                                        );

                                                        return 1;
                                                    })
                                    )



                                    .then(
                                            literal("removeLast")
                                                    .requires(s -> s.hasPermission(4))
                                                    .executes(ctx->{

                                                        armConfirm(
                                                                "removeLast",
                                                                null,
                                                                -1
                                                        );

                                                        openSeasonConfirmation(ctx, "§cRemove Last Season", "§7Remove the latest archived season for all players.");

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§cUse the opened confirmation UI to continue."
                                                                ),
                                                                false
                                                        );

                                                        return 1;
                                                    })
                                    )



                                    .then(
                                            literal("remove")
                                                    .requires(s -> s.hasPermission(4))

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

                                                                        openSeasonConfirmation(ctx, "§cRemove Season", "§7Delete Season " + season + " archives and Top100 snapshot.");

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
                                                                                        "§cUse the opened confirmation UI to continue."
                                                                                ),
                                                                                false
                                                                        );

                                                                        return 1;
                                                                    })
                                                    )
                                    )



                                    .then(
                                            literal("reset0")
                                                    .requires(s -> s.hasPermission(4))
                                                    .executes(ctx->{

                                                        armConfirm(
                                                                "reset0",
                                                                null,
                                                                -1
                                                        );

                                                        openSeasonConfirmation(ctx, "§cReset to Season 0", "§7Set active season back to Season 0 Offseason.");

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§cUse the opened confirmation UI to reset to Season 0 Offseason."
                                                                ),
                                                                false
                                                        );

                                                        return 1;
                                                    })
                                    )


                                    .then(
                                            literal("preseason")
                                                    .requires(s -> s.hasPermission(4))
                                                    .executes(ctx->{

                                                        armConfirm(
                                                                "preseason",
                                                                "Preseason",
                                                                0
                                                        );

                                                        openSeasonConfirmation(ctx, "§eStart Preseason", "§7Set active season to Season 0 Preseason without resetting players.");

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§cUse the opened confirmation UI to set Season 0 Preseason without resetting players."
                                                                ),
                                                                false
                                                        );

                                                        return 1;
                                                    })
                                    )


                                    .then(
                                            literal("set")
                                                    .requires(s -> s.hasPermission(4))
                                                    .then(
                                                            argument(
                                                                    "number",
                                                                    IntegerArgumentType.integer(0)
                                                            )
                                                                    .then(
                                                                            argument(
                                                                                    "name",
                                                                                    StringArgumentType.greedyString()
                                                                            )
                                                                                    .executes(ctx->{

                                                                                        int season=
                                                                                                IntegerArgumentType.getInteger(
                                                                                                        ctx,
                                                                                                        "number"
                                                                                                );

                                                                                        String name=
                                                                                                StringArgumentType.getString(
                                                                                                        ctx,
                                                                                                        "name"
                                                                                                );

                                                                                        armConfirm(
                                                                                                "set",
                                                                                                name,
                                                                                                season
                                                                                        );

                                                                                        openSeasonConfirmation(ctx, "§eSet Active Season", "§7Set active season to Season " + season + " " + name + " without resetting players.");

                                                                                        ctx.getSource().sendSuccess(
                                                                                                ()->Component.literal(
                                                                                                        "§cUse the opened confirmation UI to set active Season "
                                                                                                                +season
                                                                                                                +" "
                                                                                                                +name
                                                                                                                +" without resetting players."
                                                                                                ),
                                                                                                false
                                                                                        );

                                                                                        return 1;
                                                                                    })
                                                                    )
                                                    )
                                    )


                                    .then(
                                            literal("confirm")
                                                    .requires(s -> s.hasPermission(4))

                                                    .executes(ctx->{

                                                        return runPendingConfirm(ctx.getSource());
                                                    })
                                    )

                    );

                });
    }

}
