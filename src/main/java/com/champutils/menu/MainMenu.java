package com.champutils.menu;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

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
                10,13,16,22
        );


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
                        .setCallback(
                                (i,c,t)->
                                        BattleMenu.open(
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
                        .setCallback(
                                (i,c,t)->
                                        SeasonMenu.open(
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
                                        "§dTrainer Card"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        ProfileMenu.open(
                                                player
                                        )
                        )
        );


        // NEW CHAT LOOKUP BUTTON
        gui.setSlot(
                22,
                new GuiElementBuilder(
                        Items.COMPASS
                )
                        .setName(
                                Component.literal(
                                        "§aPlayer Lookup"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Search player profiles"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to enter name in chat"
                                )
                        )
                        .setCallback(
                                (i,c,t)->{
                                    player.closeContainer();

                                    ProfileLookupManager.beginLookup(
                                            player
                                    );
                                }
                        )
        );

        gui.open();
    }
}