package com.champutils.menu;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class GearWorkshopMenu {

    private GearWorkshopMenu() {
    }

    public static void open(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x1, player, false);
        gui.setTitle(Component.literal("Gear Workshop"));

        addCommandButton(
                gui,
                player,
                1,
                Items.ANVIL,
                "§aRepair Gear",
                "§7Repair the profession gear in your hand.",
                "§7Uses the configured repair materials.",
                "itemroll repair",
                true
        );

        addCommandButton(
                gui,
                player,
                3,
                Items.GRINDSTONE,
                "§cSalvage Gear",
                "§7Salvage the profession gear in your hand.",
                "§7Returns fragments based on rarity.",
                "salvage",
                true
        );

        gui.setSlot(
                5,
                new GuiElementBuilder(Items.EMERALD)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§aFragment Crafting"))
                        .addLoreLine(Component.literal("§7Upgrade fragments and craft mystery gear."))
                        .addLoreLine(Component.literal("§7The back button returns here."))
                        .addLoreLine(Component.literal("§eClick to open"))
                        .setCallback((i, c, t) -> FragmentCraftingMenu.open(player, GearWorkshopMenu::open))
        );

        addCommandButton(
                gui,
                player,
                7,
                Items.PRISMARINE_SHARD,
                "§6Fragment Storage",
                "§7View your stored fragment balances.",
                "§7Right-click fragments to deposit them.",
                "fragments list",
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
