package com.champutils.menu;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

public final class ConfirmationMenu {
    private ConfirmationMenu() {}

    public static void open(
            ServerPlayer player,
            String title,
            Item summaryIcon,
            String summaryName,
            List<Component> summaryLore,
            Runnable onConfirm,
            Runnable onCancel
    ) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal(title == null || title.isBlank() ? "Confirm Action" : title));
        MenuUtil.fillBordersForced(gui, 11, 13, 15);

        GuiElementBuilder summary = new GuiElementBuilder(summaryIcon == null ? Items.PAPER : summaryIcon)
                .hideDefaultTooltip()
                .setName(Component.literal(summaryName == null || summaryName.isBlank() ? "§eReview Action" : summaryName));
        if (summaryLore != null) {
            for (Component line : summaryLore) {
                summary.addLoreLine(line == null ? Component.empty() : line);
            }
        }
        gui.setSlot(13, summary);

        gui.setSlot(11, new GuiElementBuilder(Items.GREEN_STAINED_GLASS_PANE)
                .hideDefaultTooltip()
                .setName(Component.literal("§a§lConfirm"))
                .addLoreLine(Component.literal("§7Continue with this action."))
                .setCallback((slot, click, action) -> {
                    player.closeContainer();
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                }));

        gui.setSlot(15, new GuiElementBuilder(Items.RED_STAINED_GLASS_PANE)
                .hideDefaultTooltip()
                .setName(Component.literal("§c§lCancel"))
                .addLoreLine(Component.literal("§7Go back without changes."))
                .setCallback((slot, click, action) -> {
                    if (onCancel != null) {
                        onCancel.run();
                    } else {
                        player.closeContainer();
                    }
                }));

        gui.open();
    }

    public static void open(
            ServerPlayer player,
            String title,
            Item summaryIcon,
            String summaryName,
            String[] summaryLore,
            Runnable onConfirm,
            Runnable onCancel
    ) {
        List<Component> lore = new java.util.ArrayList<>();
        if (summaryLore != null) {
            for (String line : summaryLore) {
                lore.add(Component.literal(line == null ? "" : line));
            }
        }
        open(player, title, summaryIcon, summaryName, lore, onConfirm, onCancel);
    }
}
