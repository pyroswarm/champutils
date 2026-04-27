package com.champutils.menu;

import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.PlayerDataManager.PlayerData;
import com.champutils.rank.RankManager;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class PlayerProfileMenu {

    public static void open(
            ServerPlayer viewer,
            String targetName
    ){

        PlayerData data=null;

        for(
                var entry :
                PlayerDataManager.getAllPlayers()
        ){
            if(
                    entry.name.equalsIgnoreCase(
                            targetName
                    )
            ){
                data=entry.data;
                break;
            }
        }

        if(data==null){
            viewer.sendSystemMessage(
                    Component.literal(
                            "§cPlayer profile not found."
                    )
            );
            return;
        }


        SimpleGui gui=
                new SimpleGui(
                        MenuType.GENERIC_9x6,
                        viewer,
                        false
                );

        gui.setTitle(
                Component.literal(
                        targetName+"'s Trainer Card"
                )
        );

        MenuUtil.fillBorders(
                gui,
                4,
                10,12,14,16,
                29,31,33,
                49
        );


        /* HEADER */

        gui.setSlot(
                4,
                new GuiElementBuilder(
                        CobblemonItems.POKEDEX_RED
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§bTrainer Profile"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Trainer: §f"+data.name
                                )
                        )
        );


        String currentRank=
                RankManager.getRank(
                        data.rp
                ).name;

        String peakRank=
                RankManager.getRank(
                        data.peakRp
                ).name;


        /* RANK */

        gui.setSlot(
                10,
                new GuiElementBuilder(
                        CobblemonItems.WHITE_PLAQUE
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Ranks"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eCurrent: §f"+currentRank
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§ePeak: §f"+peakRank
                                )
                        )
        );


        /* RP */

        gui.setSlot(
                12,
                new GuiElementBuilder(
                        CobblemonItems.FIRE_GEM
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§cRating"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eRP: §f"+data.rp
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§ePeak RP: §f"+data.peakRp
                                )
                        )
        );


        /* RECORD */

        int total=
                data.rankedWins+
                        data.rankedLosses;

        double wr=
                total==0
                        ?0
                        :(((double)data.rankedWins/total)*100);


        gui.setSlot(
                14,
                new GuiElementBuilder(
                        Items.NETHER_STAR
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§bRanked Record"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§aWins: §f"+data.rankedWins
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§cLosses: §f"+data.rankedLosses
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        String.format(
                                                "§eWin Rate: §f%.1f%%",
                                                wr
                                        )
                                )
                        )
        );


        /* STREAKS */

        gui.setSlot(
                16,
                new GuiElementBuilder(
                        Items.BLAZE_POWDER
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Streaks"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eCurrent: §f"+data.currentStreak
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eBest: §f"+data.bestStreak
                                )
                        )
        );


        /* UPSET WINS */

        gui.setSlot(
                29,
                new GuiElementBuilder(
                        Items.DIAMOND_SWORD
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§dUpset Wins"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§f"+data.upsetWins
                                )
                        )
        );


        /* SEASONS */

        gui.setSlot(
                31,
                new GuiElementBuilder(
                        Items.CLOCK
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Seasons Played"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§f"+data.seasonsPlayed
                                )
                        )
        );


        /* HISTORY */

        gui.setSlot(
                33,
                new GuiElementBuilder(
                        Items.WRITABLE_BOOK
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§bSeason History"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Click to view archive"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        SeasonHistoryMenu.open(
                                                viewer,
                                                targetName
                                        )
                        )
        );


        /* CLOSE */

        gui.setSlot(
                49,
                new GuiElementBuilder(
                        Items.BARRIER
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§cClose"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        viewer.closeContainer()
                        )
        );


        gui.open();
    }
}