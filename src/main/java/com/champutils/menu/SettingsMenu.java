package com.champutils.menu;

import com.champutils.profession.ProfessionNotificationSettings;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class SettingsMenu {

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Settings"));

        boolean enabled = ProfessionNotificationSettings.areProfessionPopupsEnabled(player);

        gui.setSlot(
                13,
                new GuiElementBuilder(enabled ? Items.LIME_DYE : Items.GRAY_DYE)
                        .hideDefaultTooltip()
                        .setName(Component.literal(enabled ? "§aProfession Popups: ON" : "§cProfession Popups: OFF"))
                        .addLoreLine(Component.literal("§7Controls profession actionbar/chat popups."))
                        .addLoreLine(Component.literal("§7Rewards and passive effects still work."))
                        .addLoreLine(Component.literal("§eClick to toggle"))
                        .setCallback((i, c, t) -> {
                            ProfessionNotificationSettings.toggleProfessionPopups(player);
                            open(player);
                        })
        );

        MenuUtil.addBackButton(gui, 22, () -> MainMenu.open(player));
        gui.open();
    }
}
