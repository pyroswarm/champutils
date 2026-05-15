package com.champutils.menu;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

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
                10,12,14,16,20,24
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
                12,
                new GuiElementBuilder(
                        Items.CHEST
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§aItems"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Identify, reroll, salvage, and manage fragments"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to open"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        ItemsMenu.open(
                                                player
                                        )
                        )
        );

        gui.setSlot(
                14,
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

        gui.setSlot(
                20,
                new GuiElementBuilder(
                        Items.NETHER_STAR
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Leaderboard"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7View RP and profession rankings"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to open"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        LeaderboardMenu.open(
                                                player
                                        )
                        )
        );

        gui.setSlot(
                24,
                new GuiElementBuilder(
                        Items.COMPASS
                )
                        .hideDefaultTooltip()
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
                                    removeLeakedLookupCompass(
                                            player
                                    );

                                    player.closeContainer();

                                    ProfileLookupManager.beginLookup(
                                            player
                                    );

                                    removeLeakedLookupCompass(
                                            player
                                    );
                                }
                        )
        );

        gui.open();
    }



    private static void removeLeakedLookupCompass(
            ServerPlayer player
    ){

        for(
                int slot=0;
                slot<player.getInventory().getContainerSize();
                slot++
        ){

            ItemStack stack=
                    player.getInventory().getItem(
                            slot
                    );

            if(
                    stack.isEmpty() ||
                            !stack.is(
                                    Items.COMPASS
                            )
            ){
                continue;
            }

            Component customName=
                    stack.get(
                            DataComponents.CUSTOM_NAME
                    );

            if(
                    customName!=null &&
                            "Player Lookup".equals(
                                    customName.getString()
                            )
            ){
                player.getInventory().setItem(
                        slot,
                        ItemStack.EMPTY
                );
            }
        }
    }
}
