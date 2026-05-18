package com.champutils.menu;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class GearAppraiserMenu {

    private GearAppraiserMenu() {
    }

    public static void open(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x1, player, false);
        gui.setTitle(Component.literal("Gear Appraiser"));

        addCommandButton(
                gui,
                player,
                3,
                Items.NAME_TAG,
                "§bIdentify Gear",
                "§7Identify the unknown gear in your hand.",
                "§7Reveals the gear's stats and rolls.",
                "itemroll identify",
                true
        );

        addCommandButton(
                gui,
                player,
                5,
                Items.AMETHYST_SHARD,
                "§dReroll Gear",
                "§7Reroll the profession gear in your hand.",
                "§7Costs coins based on rarity.",
                "itemroll reroll",
                true
        );

        gui.open();
    }

    private static void addCommandButton(
            SimpleGui gui,
            ServerPlayer player,
            int slot,
            Item icon,
            String name,
            String loreOne,
            String loreTwo,
            String command,
            boolean closeFirst
    ) {
        gui.setSlot(
                slot,
                new GuiElementBuilder(icon)
                        .hideDefaultTooltip()
                        .setName(Component.literal(name))
                        .addLoreLine(Component.literal(loreOne))
                        .addLoreLine(Component.literal(loreTwo))
                        .addLoreLine(Component.literal("§eClick to continue"))
                        .setCallback((i, c, t) -> {
                            if (closeFirst) {
                                player.closeContainer();
                            }

                            player.getServer()
                                    .getCommands()
                                    .performPrefixedCommand(
                                            player.createCommandSourceStack(),
                                            command
                                    );
                        })
        );
    }
}
