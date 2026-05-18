package com.champutils.menu;

import com.champutils.rank.SeasonManager;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;

public class SeasonMenu {

    public static void open(
            ServerPlayer player
    ){

        SimpleGui gui =
                MenuUtil.createGui(MenuType.GENERIC_9x3, player);

        gui.setTitle(
                Component.literal(
                        "Season Hub"
                )
        );


        MenuUtil.fillBorders(
                gui,
                11,13,15,22
        );



        // Current Season
        gui.setSlot(
                11,
                new GuiElementBuilder(
                        CobblemonItems.ICE_GEM
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§bCurrent Season"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7"
                                                + SeasonManager.CURRENT_NAME
                                )
                        )
        );



        // Season History GUI
        gui.setSlot(
                13,
                new GuiElementBuilder(
                        CobblemonItems.EXP_SHARE
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§dSeason History"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7View previous season stats"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§8Click to open archive"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        SeasonHistoryMenu.open(
                                                player
                                        )
                        )
        );



        // Leaderboard
        gui.setSlot(
                15,
                new GuiElementBuilder(
                        CobblemonItems.ULTRA_BALL
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Leaderboard"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7View Top Players"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        LeaderboardMenu.open(
                                                player
                                        )
                        )
        );



        MenuUtil.addBackButton(
                gui,
                22,
                () -> MainMenu.open(
                        player
                )
        );

        gui.open();
    }
}