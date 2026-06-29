package com.champutils.menu;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/**
 * Legacy item hub kept as a clean fallback.
 * The intended player-facing flow is now two separate NPCs:
 * - Gear Workshop: crafting / salvage / repair
 * - Gear Workshop: identify / reroll now live here too
 */
public final class ItemsMenu {

    private ItemsMenu() {
    }

    public static void open(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x1, player, false);
        gui.setTitle(Component.literal("Gear"));

        gui.setSlot(
                3,
                new GuiElementBuilder(Items.SMITHING_TABLE)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§6Gear Workshop"))
                        .addLoreLine(Component.literal("§7Craft, salvage, and repair gear."))
                        .addLoreLine(Component.literal("§eClick to open"))
                        .setCallback((i, c, t) -> GearWorkshopMenu.open(player))
        );

        gui.setSlot(
                5,
                new GuiElementBuilder(Items.AMETHYST_SHARD)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§dIdentify / Reroll Gear"))
                        .addLoreLine(Component.literal("§7Now handled inside Gear Workshop."))
                        .addLoreLine(Component.literal("§eClick to open"))
                        .setCallback((i, c, t) -> GearWorkshopMenu.open(player))
        );

        gui.setSlot(
                7,
                new GuiElementBuilder(Items.NETHER_STAR)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§bEmblem Crafting"))
                        .addLoreLine(Component.literal("§7Craft shiny and megastone emblems."))
                        .addLoreLine(Component.literal("§eClick to open"))
                        .setCallback((i, c, t) -> EmblemMenu.open(player))
        );

        gui.open();
    }
}
