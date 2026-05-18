package com.champutils.menu;

import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class HelpMenu {

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Help"));

        MenuUtil.addInfoCard(
                gui,
                13,
                Items.PAPER,
                "§bHelp Coming Soon",
                "§7This menu is intentionally empty for now.",
                "§7Guides and server info can be added later."
        );

        MenuUtil.addBackButton(gui, 22, () -> MainMenu.open(player));
        gui.open();
    }
}
