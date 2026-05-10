package com.champutils.menu;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class ItemsMenu {

    public static void open(
            ServerPlayer player
    ) {

        SimpleGui gui =
                new SimpleGui(
                        MenuType.GENERIC_9x3,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Items"
                )
        );

        MenuUtil.fillBorders(
                gui,
                10,12,14,16,22
        );

        gui.setSlot(
                10,
                new GuiElementBuilder(
                        CobblemonItems.RELIC_COIN
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§bIdentify"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Identifies your current held item."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Reveals its rolled stats."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to identify"
                                )
                        )
                        .setCallback(
                                (i, c, t) ->
                                        player.getServer()
                                                .getCommands()
                                                .performPrefixedCommand(
                                                        player.createCommandSourceStack(),
                                                        "itemroll identify"
                                                )
                        )
        );

        gui.setSlot(
                12,
                new GuiElementBuilder(
                        CobblemonItems.ABILITY_PATCH
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§dReroll"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Rerolls your current held item."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Costs coins based on rarity."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to reroll"
                                )
                        )
                        .setCallback(
                                (i, c, t) ->
                                        player.getServer()
                                                .getCommands()
                                                .performPrefixedCommand(
                                                        player.createCommandSourceStack(),
                                                        "itemroll reroll"
                                                )
                        )
        );

        gui.setSlot(
                14,
                new GuiElementBuilder(
                        Items.ANVIL
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Salvage"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Salvages your current held item."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Converts unwanted tools into fragments."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to salvage"
                                )
                        )
                        .setCallback(
                                (i, c, t) ->
                                        player.getServer()
                                                .getCommands()
                                                .performPrefixedCommand(
                                                        player.createCommandSourceStack(),
                                                        "salvage"
                                                )
                        )
        );

        gui.setSlot(
                16,
                new GuiElementBuilder(
                        Items.AMETHYST_SHARD
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§5Fragments"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7View your stored fragments."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Upgrade and trade fragments."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to view fragments"
                                )
                        )
                        .setCallback(
                                (i, c, t) ->
                                        player.getServer()
                                                .getCommands()
                                                .performPrefixedCommand(
                                                        player.createCommandSourceStack(),
                                                        "fragments list"
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
