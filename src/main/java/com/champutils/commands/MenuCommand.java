package com.champutils.commands;

import com.champutils.matchmaking.MatchmakingManager;
import com.champutils.validation.TeamValidator;
import com.champutils.profile.ProfileManager;
import com.champutils.rank.SeasonManager;
import com.champutils.rank.SeasonArchiveManager;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.literal;

public class MenuCommand {

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, env)->{


                    dispatcher.register(
                            literal("menu")
                                    .executes(ctx->{

                                        ServerPlayer player=
                                                ctx.getSource()
                                                        .getPlayerOrException();

                                        openMainMenu(
                                                player
                                        );

                                        return 1;
                                    })
                    );


                    dispatcher.register(
                            literal("queue")

                                    .then(literal("ranked")
                                            .executes(ctx->{

                                                ServerPlayer player=
                                                        ctx.getSource()
                                                                .getPlayerOrException();

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

                                                    return 0;
                                                }

                                                MatchmakingManager.joinQueue(
                                                        player,
                                                        "ranked"
                                                );

                                                return 1;
                                            }))


                                    .then(literal("casual")
                                            .executes(ctx->{

                                                ServerPlayer player=
                                                        ctx.getSource()
                                                                .getPlayerOrException();

                                                MatchmakingManager.joinQueue(
                                                        player,
                                                        "casual"
                                                );

                                                return 1;
                                            }))


                                    .then(literal("leave")
                                            .executes(ctx->{

                                                ServerPlayer player=
                                                        ctx.getSource()
                                                                .getPlayerOrException();

                                                MatchmakingManager.leaveQueue(
                                                        player
                                                );

                                                return 1;
                                            }))
                    );
                });
    }



//==================================
// MAIN MENU
//==================================

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
                        "ChampUtils Menu"
                )
        );

        fillBorders(gui);


        gui.setSlot(
                10,
                new GuiElementBuilder(
                        Items.DIAMOND_SWORD
                )
                        .setName(
                                Component.literal(
                                        "§6Battles"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        openBattleMenu(player)
                        )
        );


        gui.setSlot(
                13,
                new GuiElementBuilder(
                        Items.CLOCK
                )
                        .setName(
                                Component.literal(
                                        "§dSeasons"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        openSeasonMenu(player)
                        )
        );


        gui.setSlot(
                16,
                new GuiElementBuilder(
                        Items.PLAYER_HEAD
                )
                        .setName(
                                Component.literal(
                                        "§bProfile"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        openProfileMenu(player)
                        )
        );


        gui.open();
    }



//==================================
// BATTLE MENU
//==================================

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
                        "Battle Menu"
                ));

        fillBorders(gui);


        gui.setSlot(
                10,
                new GuiElementBuilder(
                        Items.NETHERITE_SWORD
                )
                        .setName(
                                Component.literal(
                                        "§cRanked Queue"
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
                        }));


        gui.setSlot(
                13,
                new GuiElementBuilder(
                        Items.IRON_SWORD
                )
                        .setName(
                                Component.literal(
                                        "§aCasual Queue"
                                ))
                        .setCallback((i,c,t)->{

                            MatchmakingManager.joinQueue(
                                    player,
                                    "casual"
                            );

                            player.closeContainer();
                        }));


        gui.setSlot(
                16,
                new GuiElementBuilder(
                        Items.BARRIER
                )
                        .setName(
                                Component.literal(
                                        "§cLeave Queue"
                                ))
                        .setCallback((i,c,t)->{

                            MatchmakingManager.leaveQueue(
                                    player
                            );

                            player.closeContainer();
                        }));


        addBackButton(
                gui,
                player,
                ()->openMainMenu(player)
        );

        gui.open();
    }



//==================================
// PROFILE MENU
//==================================

    private static void openProfileMenu(
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
                        "Battle Profile"
                ));

        fillBorders(gui);


        gui.setSlot(
                10,
                new GuiElementBuilder(
                        Items.BEACON
                )
                        .setName(
                                Component.literal(
                                        "§6Competitive Profile"
                                ))
                        .addLoreLine(Component.literal(
                                "§eRank: §f"+
                                        ProfileManager.getCurrentRankName(player)
                        ))
                        .addLoreLine(Component.literal(
                                "§eRP: §f"+
                                        ProfileManager.getCurrentRp(player)
                        ))
                        .addLoreLine(Component.literal(
                                "§ePeak RP: §f"+
                                        ProfileManager.getPeakRp(player)
                        ))
        );


        gui.setSlot(
                12,
                new GuiElementBuilder(
                        Items.NETHER_STAR
                )
                        .setName(
                                Component.literal(
                                        "§6Ranked Record"
                                ))
                        .addLoreLine(Component.literal(
                                "§aWins: §f"+
                                        ProfileManager.getRankedWins(player)
                        ))
                        .addLoreLine(Component.literal(
                                "§cLosses: §f"+
                                        ProfileManager.getRankedLosses(player)
                        ))
                        .addLoreLine(Component.literal(
                                "§eWin Rate: §f"+
                                        (int)ProfileManager
                                                .getWinRate(player)
                                        +"%"
                        ))
        );


        gui.setSlot(
                14,
                new GuiElementBuilder(
                        Items.BLAZE_POWDER
                )
                        .setName(
                                Component.literal(
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
                16,
                new GuiElementBuilder(
                        Items.DRAGON_HEAD
                )
                        .setName(
                                Component.literal(
                                        "§6Achievements"
                                ))
                        .addLoreLine(Component.literal(
                                "§eHighest Rank: §f"+
                                        ProfileManager.getHighestRankName(player)
                        ))
                        .addLoreLine(Component.literal(
                                "§eUpset Wins: §f"+
                                        ProfileManager.getUpsetWins(player)
                        ))
        );


        addBackButton(
                gui,
                player,
                ()->openMainMenu(player)
        );

        gui.open();
    }



//==================================
// SEASON HUB
//==================================

    private static void openSeasonMenu(
            ServerPlayer player
    ){

        SimpleGui gui=
                new SimpleGui(
                        MenuType.GENERIC_9x3,
                        player,
                        false
                );

        fillBorders(gui);

        gui.setTitle(
                Component.literal(
                        "Season Hub"
                ));


        gui.setSlot(
                10,
                new GuiElementBuilder(
                        Items.WRITABLE_BOOK
                )
                        .setName(
                                Component.literal(
                                        "§6Current Season"
                                ))
                        .addLoreLine(Component.literal(
                                "§eSeason "
                                        +SeasonManager.CURRENT_SEASON
                        ))
                        .addLoreLine(Component.literal(
                                "§b"
                                        +SeasonManager.CURRENT_NAME
                        ))
        );


        gui.setSlot(
                13,
                new GuiElementBuilder(
                        Items.WRITTEN_BOOK
                )
                        .setName(
                                Component.literal(
                                        "§dSeason History"
                                ))
                        .setCallback(
                                (i,c,t)->
                                        openHistoryMenu(player)
                        ));


        gui.setSlot(
                16,
                new GuiElementBuilder(
                        Items.BEACON
                )
                        .setName(
                                Component.literal(
                                        "§eLeaderboard"
                                ))
                        .setCallback(
                                (i,c,t)->
                                        openLeaderboardMenu(player)
                        ));


        addBackButton(
                gui,
                player,
                ()->openMainMenu(player)
        );

        gui.open();
    }



//==================================
// HISTORY
//==================================

    private static void openHistoryMenu(
            ServerPlayer player
    ){

        List<
                SeasonArchiveManager
                        .SeasonRecord
                > history=

                SeasonArchiveManager
                        .getHistory(
                                player.getName()
                                        .getString()
                        );


        SimpleGui gui=
                new SimpleGui(
                        MenuType.GENERIC_9x6,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Season History"
                ));


        int slot=0;

        for(
                SeasonArchiveManager.SeasonRecord s
                : history
        ){

            if(slot>=45)
                break;

            gui.setSlot(
                    slot++,

                    new GuiElementBuilder(
                            Items.WRITTEN_BOOK
                    )

                            .setName(
                                    Component.literal(
                                            "§6Season "
                                                    +s.season
                                                    +" §7- §e"
                                                    +s.seasonName
                                    )
                            )

                            .addLoreLine(Component.literal(
                                    "§eRank: §f"
                                            +s.finishRank
                            ))

                            .addLoreLine(Component.literal(
                                    "§ePeak RP: §f"
                                            +s.peakRp
                            ))

                            .addLoreLine(Component.literal(
                                    "§eFinal RP: §f"
                                            +s.finalRp
                            ))

                            .addLoreLine(Component.literal(
                                    "§aRecord: §f"
                                            +s.wins
                                            +"-"
                                            +s.losses
                            ))

                            .addLoreLine(Component.literal(
                                    "§6Best Streak: §f"
                                            +s.bestStreak
                            ))
            );
        }

        addBackButton(
                gui,
                player,
                ()->openSeasonMenu(player)
        );

        gui.open();
    }



//==================================
// LEADERBOARD
//==================================

    private static void openLeaderboardMenu(
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
                        "Top Ladder"
                ));


        List<ServerPlayer> players=
                new ArrayList<>(
                        player.getServer()
                                .getPlayerList()
                                .getPlayers()
                );


        players.sort((a,b)->
                Integer.compare(
                        ProfileManager.getCurrentRp(b),
                        ProfileManager.getCurrentRp(a)
                ));


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

            gui.setSlot(
                    i,

                    new GuiElementBuilder(
                            Items.PLAYER_HEAD
                    )

                            .setName(
                                    Component.literal(
                                            "#"+(i+1)+" "
                                                    +p.getName()
                                                    .getString()
                                    )
                            )

                            .addLoreLine(Component.literal(
                                    "§6RP "
                                            +ProfileManager
                                            .getCurrentRp(p)
                            ))
            );
        }

        addBackButton(
                gui,
                player,
                ()->openSeasonMenu(player)
        );

        gui.open();
    }



//==================================

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
                                ))
                        .setCallback(
                                (i,c,t)->
                                        action.run()
                        ));
    }



//==================================

    private static void fillBorders(
            SimpleGui gui
    ){

        GuiElementBuilder filler=
                new GuiElementBuilder(
                        Items.GRAY_STAINED_GLASS_PANE
                )
                        .setName(
                                Component.literal(" ")
                        );

        for(int i=0;i<27;i++){

            if(
                    i==10||
                            i==13||
                            i==16||
                            i==22||
                            i==11||
                            i==12||
                            i==14||
                            i==15
            ){
                continue;
            }

            gui.setSlot(
                    i,
                    filler
            );
        }
    }



//==================================

    private static void sendTitle(
            ServerPlayer player,
            String title,
            String subtitle
    ){

        player.connection.send(
                new net.minecraft.network.protocol.game
                        .ClientboundSetTitlesAnimationPacket(
                        10,40,10
                ));

        player.connection.send(
                new net.minecraft.network.protocol.game
                        .ClientboundSetTitleTextPacket(
                        Component.literal(title)
                ));


        if(
                subtitle!=null
                        &&
                        !subtitle.isEmpty()
        ){

            player.connection.send(
                    new net.minecraft.network.protocol.game
                            .ClientboundSetSubtitleTextPacket(
                            Component.literal(
                                    subtitle
                            )));
        }

    }

}