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
                MenuUtil.createGui(
                        MenuType.GENERIC_9x5,
                        viewer
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
                19,21,23,25,
                40
        );

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

        gui.setSlot(
                19,
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

        gui.setSlot(
                21,
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

        gui.setSlot(
                23,
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

        gui.setSlot(
                25,
                new GuiElementBuilder(
                        Items.DIAMOND_PICKAXE
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§aProfessions"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7View profession progress"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        ProfessionMenu.open(
                                                viewer
                                        )
                        )
        );

        gui.setSlot(
                40,
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
