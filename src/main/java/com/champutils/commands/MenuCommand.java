package com.champutils.commands;

import com.champutils.matchmaking.MatchmakingManager;
import com.champutils.validation.TeamValidator;
import com.champutils.profile.ProfileManager;
import com.champutils.rank.SeasonManager;
import com.champutils.rank.SeasonArchiveManager;
import com.champutils.rank.LeaderboardManager;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.List;

import static net.minecraft.commands.Commands.literal;

public class MenuCommand {

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, env) -> {

                    dispatcher.register(
                            literal("menu")
                                    .executes(ctx -> {

                                        ServerPlayer player =
                                                ctx.getSource()
                                                        .getPlayerOrException();

                                        openMainMenu(player);
                                        return 1;
                                    })
                    );


                    dispatcher.register(
                            literal("queue")

                                    .then(
                                            literal("ranked")
                                                    .executes(ctx -> {

                                                        ServerPlayer player =
                                                                ctx.getSource()
                                                                        .getPlayerOrException();

                                                        String error =
                                                                TeamValidator.validate(
                                                                        player,
                                                                        "ranked"
                                                                );

                                                        if(error != null){

                                                            sendTitle(
                                                                    player,
                                                                    "§cINVALID TEAM",
                                                                    "§e"+error
                                                            );

                                                            return 0;
                                                        }

                                                        MatchmakingManager.joinQueue(
                                                                player,
                                                                "ranked"
                                                        );

                                                        return 1;

                                                    })
                                    )

                                    .then(
                                            literal("casual")
                                                    .executes(ctx -> {

                                                        ServerPlayer player =
                                                                ctx.getSource()
                                                                        .getPlayerOrException();

                                                        MatchmakingManager.joinQueue(
                                                                player,
                                                                "casual"
                                                        );

                                                        return 1;
                                                    })
                                    )

                                    .then(
                                            literal("leave")
                                                    .executes(ctx -> {

                                                        ServerPlayer player =
                                                                ctx.getSource()
                                                                        .getPlayerOrException();

                                                        MatchmakingManager.leaveQueue(
                                                                player
                                                        );

                                                        return 1;
                                                    })
                                    )
                    );

                });
    }



/* =========================
MAIN MENU
========================= */

    private static void openMainMenu(
            ServerPlayer player
    ){

        SimpleGui gui=
                new SimpleGui(
                        MenuType.GENERIC_9x3,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Main Menu"
                )
        );

        fillBorders(gui);


        gui.setSlot(
                10,
                new GuiElementBuilder(
                        CobblemonItems.POKE_BALL
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Battles"
                                )
                        )
                        .addLoreLine(Component.literal(
                                "§7Queue for competitive battles"
                        ))
                        .addLoreLine(Component.literal(
                                "§7Ranked and casual matchmaking"
                        ))
                        .setCallback(
                                (i,c,t)->
                                        openBattleMenu(
                                                player
                                        )
                        )
        );


        gui.setSlot(
                13,
                new GuiElementBuilder(
                        CobblemonItems.ICE_GEM
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§bSeasons"
                                )
                        )
                        .addLoreLine(Component.literal(
                                "§7Current season rankings"
                        ))
                        .addLoreLine(Component.literal(
                                "§7History and leaderboards"
                        ))
                        .setCallback(
                                (i,c,t)->
                                        openSeasonMenu(
                                                player
                                        )
                        )
        );


        gui.setSlot(
                16,
                new GuiElementBuilder(
                        CobblemonItems.POKEDEX_RED
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§dProfile"
                                )
                        )
                        .addLoreLine(Component.literal(
                                "§7View rank and trainer card"
                        ))
                        .setCallback(
                                (i,c,t)->
                                        openProfileMenu(
                                                player
                                        )
                        )
        );

        gui.open();
    }



/* =========================
BATTLE MENU
========================= */

    private static void openBattleMenu(
            ServerPlayer player
    ){

        SimpleGui gui=
                new SimpleGui(
                        MenuType.GENERIC_9x3,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Battle Queues"
                )
        );

        fillBorders(gui);


        gui.setSlot(
                10,
                new GuiElementBuilder(
                        CobblemonItems.ULTRA_BALL
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§cRanked Queue"
                        ))
                        .addLoreLine(Component.literal(
                                "§7Climb the competitive ladder"
                        ))
                        .addLoreLine(Component.literal(
                                "§6Earn RP and rewards"
                        ))
                        .setCallback((i,c,t)->{

                            String error=
                                    TeamValidator.validate(
                                            player,
                                            "ranked"
                                    );

                            if(error!=null){
                                sendTitle(
                                        player,
                                        "§cINVALID TEAM",
                                        "§e"+error
                                );
                                return;
                            }

                            MatchmakingManager.joinQueue(
                                    player,
                                    "ranked"
                            );

                            player.closeContainer();

                        })
        );


        gui.setSlot(
                13,
                new GuiElementBuilder(
                        CobblemonItems.GREAT_BALL
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§aCasual Queue"
                        ))
                        .addLoreLine(Component.literal(
                                "§7Practice battles"
                        ))
                        .addLoreLine(Component.literal(
                                "§aNo RP changes"
                        ))
                        .setCallback((i,c,t)->{

                            MatchmakingManager.joinQueue(
                                    player,
                                    "casual"
                            );

                            player.closeContainer();

                        })
        );


        gui.setSlot(
                16,
                new GuiElementBuilder(
                        Items.BARRIER
                )
                        .setName(Component.literal(
                                "§cLeave Queue"
                        ))
                        .setCallback((i,c,t)->{

                            MatchmakingManager.leaveQueue(
                                    player
                            );

                            player.closeContainer();

                        })
        );


        addBackButton(
                gui,
                player,
                ()->openMainMenu(player)
        );

        gui.open();
    }



/* =========================
TRAINER CARD
========================= */

    private static void openProfileMenu(
            ServerPlayer player
    ){

        SimpleGui gui=
                new SimpleGui(
                        MenuType.GENERIC_9x6,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Trainer Card"
                )
        );

        GuiElementBuilder filler=
                new GuiElementBuilder(
                        Items.GRAY_STAINED_GLASS_PANE
                ).setName(
                        Component.literal(" ")
                );

        for(int i=0;i<54;i++){
            gui.setSlot(i,filler);
        }

        gui.setSlot(
                4,
                new GuiElementBuilder(
                        CobblemonItems.POKEDEX_RED
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§bTrainer Card"
                        ))
                        .addLoreLine(Component.literal(
                                "§7Trainer: §f"+
                                        player.getName().getString()
                        ))
                        .addLoreLine(Component.literal(
                                "§7Season: §f"+
                                        SeasonManager.CURRENT_NAME
                        ))
        );


        gui.setSlot(
                19,
                new GuiElementBuilder(
                        Items.BEACON
                )
                        .setName(Component.literal(
                                "§6Rank"
                        ))
                        .addLoreLine(Component.literal(
                                "§eCurrent: §f"+
                                        ProfileManager.getCurrentRankName(player)
                        ))
        );


        gui.setSlot(
                21,
                new GuiElementBuilder(
                        Items.EMERALD
                )
                        .setName(Component.literal(
                                "§aRating"
                        ))
                        .addLoreLine(Component.literal(
                                "§eRP: §f"+
                                        ProfileManager.getCurrentRp(player)
                        ))
                        .addLoreLine(Component.literal(
                                "§ePeak: §f"+
                                        ProfileManager.getPeakRp(player)
                        ))
        );


        gui.setSlot(
                23,
                new GuiElementBuilder(
                        Items.NETHER_STAR
                )
                        .setName(Component.literal(
                                "§6Record"
                        ))
                        .addLoreLine(Component.literal(
                                "§aWins: §f"+
                                        ProfileManager.getRankedWins(player)
                        ))
                        .addLoreLine(Component.literal(
                                "§cLosses: §f"+
                                        ProfileManager.getRankedLosses(player)
                        ))
        );


        gui.setSlot(
                25,
                new GuiElementBuilder(
                        Items.BLAZE_POWDER
                )
                        .setName(Component.literal(
                                "§6Streaks"
                        ))
                        .addLoreLine(Component.literal(
                                "§eCurrent: §f"+
                                        ProfileManager.getCurrentStreak(player)
                        ))
                        .addLoreLine(Component.literal(
                                "§eBest: §f"+
                                        ProfileManager.getBestStreak(player)
                        ))
        );


        gui.setSlot(
                49,
                new GuiElementBuilder(
                        Items.ARROW
                )
                        .setName(Component.literal(
                                "§eBack"
                        ))
                        .setCallback((i,c,t)->
                                openMainMenu(player)
                        )
        );

        gui.open();
    }



/* =========================
SEASONS
========================= */

    private static void openSeasonMenu(
            ServerPlayer player
    ){

        SimpleGui gui=
                new SimpleGui(
                        MenuType.GENERIC_9x3,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Season Hub"
                )
        );

        fillBorders(gui);


        gui.setSlot(
                10,
                new GuiElementBuilder(
                        CobblemonItems.RARE_CANDY
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§6Current Season"
                        ))
                        .addLoreLine(Component.literal(
                                "§eSeason "+
                                        SeasonManager.CURRENT_SEASON
                        ))
                        .addLoreLine(Component.literal(
                                "§b"+
                                        SeasonManager.CURRENT_NAME
                        ))
        );


        gui.setSlot(
                13,
                new GuiElementBuilder(
                        CobblemonItems.POKEDEX_RED
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§dSeason History"
                        ))
                        .setCallback((i,c,t)->
                                openHistoryMenu(player)
                        )
        );


        gui.setSlot(
                16,
                new GuiElementBuilder(
                        CobblemonItems.ULTRA_BALL
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§eLeaderboard"
                        ))
                        .setCallback((i,c,t)->
                                openLeaderboardMenu(player)
                        )
        );

        addBackButton(
                gui,
                player,
                ()->openMainMenu(player)
        );

        gui.open();
    }



    /* ========================= */

    private static void openHistoryMenu(
            ServerPlayer player
    ){

        SimpleGui gui=
                new SimpleGui(
                        MenuType.GENERIC_9x6,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Season History"
                )
        );

        int slot=0;

        for(
                SeasonArchiveManager.SeasonRecord s :
                SeasonArchiveManager.getHistory(
                        player.getName().getString()
                )
        ){

            if(slot>=45) break;

            gui.setSlot(
                    slot++,
                    new GuiElementBuilder(
                            Items.WRITTEN_BOOK
                    )
                            .setName(
                                    Component.literal(
                                            "§6Season "+
                                                    s.season+
                                                    " §e"+
                                                    s.seasonName
                                    )
                            )
            );
        }

        addBackButton(
                gui,
                player,
                ()->openSeasonMenu(player)
        );

        gui.open();
    }



/* =========================
NEW OFFLINE LEADERBOARD
========================= */

    private static void openLeaderboardMenu(
            ServerPlayer player
    ){

        SimpleGui gui =
                new SimpleGui(
                        MenuType.GENERIC_9x6,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Top Ladder"
                )
        );

        List<LeaderboardManager.Entry> top =
                LeaderboardManager.getTop(
                        25
                );


        for(
                int i=0;
                i<top.size();
                i++
        ){

            LeaderboardManager.Entry entry =
                    top.get(i);


            com.cobblemon.mod.common.item.PokeBallItem icon; // FIXED TYPE


            if(i==0){

                icon =
                        CobblemonItems.MASTER_BALL;

            }else if(i==1){

                icon =
                        CobblemonItems.ULTRA_BALL;

            }else if(i<=9){

                icon =
                        CobblemonItems.GREAT_BALL;

            }else{

                icon =
                        CobblemonItems.POKE_BALL;
            }



            gui.setSlot(
                    i,

                    new GuiElementBuilder(
                            icon
                    )

                            .hideDefaultTooltip()

                            .setName(
                                    Component.literal(
                                            "§6#"
                                                    +(i+1)
                                                    +" §f"
                                                    +entry.playerName
                                    )
                            )

                            .addLoreLine(
                                    Component.literal(
                                            "§eRP: §f"
                                                    +entry.rp
                                    )
                            )

                            .addLoreLine(
                                    Component.literal(
                                            "§7"
                                                    +getRankTitle(
                                                    entry.rp
                                            )
                                    )
                            )
            );
        }


        addBackButton(
                gui,
                player,
                ()->openSeasonMenu(
                        player
                )
        );

        gui.open();
    }



    private static String getRankTitle(
            int rp
    ){

        if(rp>=1200){
            return "Grand Master";
        }

        if(rp>=1000){
            return "Master";
        }

        if(rp>=800){
            return "Ultra";
        }

        if(rp>=600){
            return "Veteran";
        }

        if(rp>=400){
            return "Ace";
        }

        return "Youngster";
    }



    /* ========================= */

    private static void addBackButton(
            SimpleGui gui,
            ServerPlayer player,
            Runnable action
    ){

        gui.setSlot(
                22,
                new GuiElementBuilder(
                        Items.ARROW
                )
                        .setName(
                                Component.literal(
                                        "§eBack"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        action.run()
                        )
        );
    }



    /* ========================= */

    private static void fillBorders(
            SimpleGui gui
    ){

        GuiElementBuilder filler=
                new GuiElementBuilder(
                        Items.GRAY_STAINED_GLASS_PANE
                ).setName(
                        Component.literal(" ")
                );

        for(int i=0;i<27;i++){

            if(
                    i==10||
                            i==13||
                            i==16||
                            i==22
            ) continue;

            gui.setSlot(
                    i,
                    filler
            );
        }
    }



    /* ========================= */

    private static void sendTitle(
            ServerPlayer player,
            String title,
            String subtitle
    ){

        player.connection.send(
                new net.minecraft.network.protocol.game
                        .ClientboundSetTitlesAnimationPacket(
                        10,40,10
                )
        );

        player.connection.send(
                new net.minecraft.network.protocol.game
                        .ClientboundSetTitleTextPacket(
                        Component.literal(
                                title
                        )
                )
        );

        if(
                subtitle!=null &&
                        !subtitle.isEmpty()
        ){
            player.connection.send(
                    new net.minecraft.network.protocol.game
                            .ClientboundSetSubtitleTextPacket(
                            Component.literal(
                                    subtitle
                            )
                    )
            );
        }
    }

}