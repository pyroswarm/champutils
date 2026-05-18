package com.champutils.menu;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class ItemsMenu {

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x5, player);
        gui.setTitle(Component.literal("Items"));

        MenuUtil.fillBorders(gui,
                11, 12, 13, 14, 15,
                20, 21, 22,
                36, 44
        );

        addCommandButton(gui, player, 11, Items.NAME_TAG,
                "§bIdentify Held Item",
                "§7Reveal an unknown item's stats.",
                "§7Hold the item you want to identify.",
                "itemroll identify",
                true
        );

        addCommandButton(gui, player, 12, Items.AMETHYST_SHARD,
                "§dReroll Held Item",
                "§7Reroll your current held item.",
                "§7Costs coins based on rarity.",
                "itemroll reroll",
                true
        );

        addCommandButton(gui, player, 13, Items.ANVIL,
                "§aRepair Held Item",
                "§7Restore durability on this item.",
                "§7Consumes configured repair materials.",
                "itemroll repair",
                true
        );

        addCommandButton(gui, player, 14, Items.BOOK,
                "§eHeld Item Info",
                "§7Inspect this item's stored data.",
                "§7Useful for testing and support.",
                "itemroll iteminfo",
                true
        );

        addCommandButton(gui, player, 15, Items.PAPER,
                "§bShow Held Item",
                "§7Share your held item in chat.",
                "§7Other players can hover to inspect it.",
                "showitem",
                true
        );

        addCommandButton(gui, player, 20, Items.GRINDSTONE,
                "§cSalvage Held Item",
                "§7Salvage your current held item.",
                "§7Returns physical fragments by rarity.",
                "salvage",
                true
        );

        addCommandButton(gui, player, 21, Items.PRISMARINE_SHARD,
                "§6Fragment Storage",
                "§7View stored fragment balances.",
                "§7Right-click fragments to deposit them.",
                "fragments list",
                true
        );

        gui.setSlot(
                22,
                new GuiElementBuilder(Items.EMERALD)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§aFragment Crafting"))
                        .addLoreLine(Component.literal("§7Upgrade fragments and craft mystery tools."))
                        .addLoreLine(Component.literal("§7Costs use profession_fragments.json."))
                        .addLoreLine(Component.literal("§eClick to open"))
                        .setCallback((i, c, t) -> FragmentCraftingMenu.open(player))
        );

        MenuUtil.addInfoCard(
                gui,
                44,
                Items.CHEST,
                "§7Tip",
                "§8Most item actions use your held item.",
                "§8Hold the target item before clicking."
        );

        MenuUtil.addBackButton(gui, 36, () -> MainMenu.open(player));
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
