package com.champutils.menu;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class ItemMenu {

    public static void open(
            ServerPlayer player
    ) {
        SimpleGui gui =
                new SimpleGui(
                        MenuType.GENERIC_9x6,
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
                4,
                10,12,14,16,
                28,30,32,34,
                49
        );

        gui.setSlot(
                4,
                new GuiElementBuilder(
                        CobblemonItems.LUCKY_EGG
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Items"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Identify, improve, repair, salvage,"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7and manage profession fragments."
                                )
                        )
        );

        addCommandButton(
                gui,
                player,
                10,
                Items.NAME_TAG,
                "§bIdentify Held Item",
                "§7Reveal an unknown item's stats.",
                "§7Hold the item you want to identify.",
                "itemroll identify",
                true
        );

        addCommandButton(
                gui,
                player,
                12,
                Items.AMETHYST_SHARD,
                "§dReroll Held Item",
                "§7Rerolls your current held item.",
                "§7Costs coins based on rarity.",
                "itemroll reroll",
                true
        );

        addCommandButton(
                gui,
                player,
                14,
                Items.ANVIL,
                "§aRepair Held Item",
                "§7Restore durability on this item.",
                "§7Consumes the configured repair materials.",
                "itemroll repair",
                true
        );

        addCommandButton(
                gui,
                player,
                16,
                Items.BOOK,
                "§eHeld Item Info",
                "§7Inspect this item's stored data.",
                "§7Useful for testing and support.",
                "itemroll iteminfo",
                true
        );

        addCommandButton(
                gui,
                player,
                28,
                Items.GRINDSTONE,
                "§cSalvage Held Item",
                "§7Salvages your current held item.",
                "§7Returns physical fragments based on rarity.",
                "salvage",
                true
        );

        addCommandButton(
                gui,
                player,
                30,
                Items.PRISMARINE_SHARD,
                "§6Fragment Storage",
                "§7View your stored fragment balances.",
                "§7Right-click fragments to deposit them.",
                "fragments list",
                true
        );

        addCommandButton(
                gui,
                player,
                32,
                Items.EMERALD,
                "§aFragment Crafting",
                "§7Upgrade fragments into higher tiers.",
                "§7Craft random unidentified gear.",
                "fragments list",
                true
        );

        addCommandButton(
                gui,
                player,
                34,
                Items.PAPER,
                "§bShow Held Item",
                "§7Share your held item in chat.",
                "§7Other players can hover to inspect it.",
                "showitem",
                true
        );

        MenuUtil.addBackButton(
                gui,
                49,
                () -> MainMenu.open(player)
        );

        gui.open();
    }

    private static void addCommandButton(
            SimpleGui gui,
            ServerPlayer player,
            int slot,
            net.minecraft.world.item.Item icon,
            String name,
            String loreOne,
            String loreTwo,
            String command,
            boolean closeFirst
    ) {
        gui.setSlot(
                slot,
                new GuiElementBuilder(
                        icon
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        name
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        loreOne
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        loreTwo
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to continue"
                                )
                        )
                        .setCallback(
                                (i,c,t)->{
                                    if (closeFirst) {
                                        player.closeContainer();
                                    }

                                    player.getServer()
                                            .getCommands()
                                            .performPrefixedCommand(
                                                    player.createCommandSourceStack(),
                                                    command
                                            );
                                }
                        )
        );
    }
}
