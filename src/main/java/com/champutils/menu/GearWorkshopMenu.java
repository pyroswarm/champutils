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
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x2, player, false);
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
                Items.AMETHYST_SHARD,
                "§dReroll Gear",
                "§7Reroll the profession gear in your hand.",
                "§7Costs materials based on rarity.",
                "itemroll reroll",
                true
        );

        addCommandButton(
                gui,
                player,
                6,
                Items.GRINDSTONE,
                "§cSalvage Gear",
                "§7Salvage the profession gear in your hand.",
                "§7Returns fragments based on rarity.",
                "salvage",
                true
        );


        gui.setSlot(
                4,
                new GuiElementBuilder(Items.NETHER_STAR)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§dEmblem Crafting"))
                        .addLoreLine(Component.literal("§7Craft shiny and Mega emblems."))
                        .addLoreLine(Component.literal("§7Uses fragments and clear item costs."))
                        .addLoreLine(Component.literal("§eClick to open"))
                        .setCallback((i, c, t) -> EmblemMenu.open(player))
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

        gui.setSlot(
                7,
                new GuiElementBuilder(Items.TRIPWIRE_HOOK)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§6Crate Key Crafting"))
                        .addLoreLine(Component.literal("§7Craft regular crate keys."))
                        .addLoreLine(Component.literal("§7Costs fragments, ores, Cobblemon items,"))
                        .addLoreLine(Component.literal("§7and netherite at high tiers."))
                        .addLoreLine(Component.literal("§eClick to open"))
                        .setCallback((i, c, t) -> CrateKeyCraftingMenu.open(player, GearWorkshopMenu::open))
        );

        addCommandButton(
                gui,
                player,
                15,
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
