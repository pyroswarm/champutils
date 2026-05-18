package com.champutils.menu;

import com.champutils.rank.SeasonArchiveManager;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

public class SeasonDetailMenu {

    public static void open(
            ServerPlayer player,
            SeasonArchiveManager.SeasonRecord s
    ){

        SimpleGui gui=
                MenuUtil.createGui(MenuType.GENERIC_9x3, player);

        gui.setTitle(
                Component.literal(
                        "Season "+s.season
                )
        );


        gui.setSlot(
                11,
                new GuiElementBuilder(
                        Items.NETHER_STAR
                )
                        .setName(
                                Component.literal(
                                        "§6Season Stats"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Final RP: "+s.finalRp
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Peak RP: "+s.peakRp
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Final Rank: "
                                                +s.finishRank
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Peak Rank: "
                                                +s.peakRank
                                )
                        )
        );


        gui.setSlot(
                15,
                new GuiElementBuilder(
                        Items.DIAMOND
                )
                        .setName(
                                Component.literal(
                                        "§bSeason Top 100"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to view"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        SeasonTop100Menu.open(
                                                player,
                                                s.season
                                        )
                        )
        );


        MenuUtil.addBackButton(
                gui,
                22,
                ()->SeasonHistoryMenu.open(player)
        );

        gui.open();
    }
}