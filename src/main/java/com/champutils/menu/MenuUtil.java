package com.champutils.menu;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class MenuUtil {

    private static final boolean ENABLE_FILLERS = false;

    public static SimpleGui createGui(
            MenuType<?> type,
            ServerPlayer player
    ) {
        SimpleGui gui = new SimpleGui(type, player, false);
        gui.setLockPlayerInventory(true);
        return gui;
    }

    public static void fillBorders(
            SimpleGui gui,
            int... skips
    ) {
        if (!ENABLE_FILLERS) {
            return;
        }

        outer:
        for (int i = 0; i < gui.getSize(); i++) {
            for (int s : skips) {
                if (i == s) {
                    continue outer;
                }
            }

            gui.setSlot(
                    i,
                    new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                            .hideTooltip()
                            .setName(Component.literal(" "))
            );
        }
    }

    public static void fillBordersForced(
            SimpleGui gui,
            int... skips
    ) {
        outer:
        for (int i = 0; i < gui.getSize(); i++) {
            for (int s : skips) {
                if (i == s) {
                    continue outer;
                }
            }

            gui.setSlot(
                    i,
                    new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                            .hideTooltip()
                            .setName(Component.literal(" "))
            );
        }
    }

    public static void addBackButton(
            SimpleGui gui,
            int slot,
            Runnable action
    ) {
        gui.setSlot(
                slot,
                new GuiElementBuilder(Items.ARROW)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§eBack"))
                        .addLoreLine(Component.literal("§7Return to the previous menu."))
                        .addLoreLine(Component.literal("§eClick to go back"))
                        .setCallback((i, c, t) -> action.run())
        );
    }

    public static void addInfoCard(
            SimpleGui gui,
            int slot,
            Item icon,
            String name,
            String... lore
    ) {
        GuiElementBuilder builder = new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal(name));

        for (String line : lore) {
            builder.addLoreLine(Component.literal(line));
        }

        gui.setSlot(slot, builder);
    }

    public static void addOpenButton(
            SimpleGui gui,
            int slot,
            Item icon,
            String name,
            Runnable action,
            String... lore
    ) {
        GuiElementBuilder builder = new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal(name));

        for (String line : lore) {
            builder.addLoreLine(Component.literal(line));
        }

        builder.addLoreLine(Component.literal("§eClick to open"));
        builder.setCallback((i, c, t) -> action.run());
        gui.setSlot(slot, builder);
    }
}
