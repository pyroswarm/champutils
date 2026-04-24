package com.champutils.menu;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;

public class MainMenu {

    public static void open(
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

        MenuUtil.fillBorders(
                gui,
                10,13,16
        );

        gui.setSlot(
                10,
                new GuiElementBuilder(
                        CobblemonItems.POKE_BALL
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§6Battles"
                        ))
                        .setCallback(
                                (i,c,t)->
                                        BattleMenu.open(player)
                        )
        );

        gui.setSlot(
                13,
                new GuiElementBuilder(
                        CobblemonItems.ICE_GEM
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§bSeasons"
                        ))
                        .setCallback(
                                (i,c,t)->
                                        SeasonMenu.open(player)
                        )
        );

        gui.setSlot(
                16,
                new GuiElementBuilder(
                        CobblemonItems.POKEDEX_RED
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§dTrainer Card"
                        ))
                        .setCallback(
                                (i,c,t)->
                                        ProfileMenu.open(player)
                        )
        );

        gui.open();
    }
}