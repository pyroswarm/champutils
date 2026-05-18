package com.champutils.menu;

import com.champutils.profession.ProfessionNotificationSettings;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class SettingsMenu {

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x1, player);
        gui.setTitle(Component.literal("Settings"));

        setToggle(
                gui,
                0,
                "Profession Popups",
                "Controls profession XP/passive popups.",
                ProfessionNotificationSettings.areProfessionPopupsEnabled(player),
                () -> ProfessionNotificationSettings.toggleProfessionPopups(player),
                player
        );

        setToggle(
                gui,
                1,
                "Sound Effects",
                "Controls reward, proc, queue, and menu sounds.",
                ProfessionNotificationSettings.areSoundEffectsEnabled(player),
                () -> ProfessionNotificationSettings.toggleSoundEffects(player),
                player
        );

        setToggle(
                gui,
                2,
                "Broadcast Messages",
                "Controls global celebration announcements you see.",
                ProfessionNotificationSettings.areBroadcastMessagesEnabled(player),
                () -> ProfessionNotificationSettings.toggleBroadcastMessages(player),
                player
        );

        setToggle(
                gui,
                3,
                "Queue Notifications",
                "Controls PvP queue found popups and alerts.",
                ProfessionNotificationSettings.areQueueNotificationsEnabled(player),
                () -> ProfessionNotificationSettings.toggleQueueNotifications(player),
                player
        );

        setToggle(
                gui,
                4,
                "Dungeon Notifications",
                "Controls dungeon progress notices and alerts.",
                ProfessionNotificationSettings.areDungeonNotificationsEnabled(player),
                () -> ProfessionNotificationSettings.toggleDungeonNotifications(player),
                player
        );

        MenuUtil.addBackButton(gui, 8, () -> MainMenu.open(player));
        gui.open();
    }

    private static void setToggle(
            SimpleGui gui,
            int slot,
            String label,
            String description,
            boolean enabled,
            Runnable toggle,
            ServerPlayer player
    ) {
        Item icon = enabled ? Items.LIME_DYE : Items.GRAY_DYE;

        gui.setSlot(
                slot,
                new GuiElementBuilder(icon)
                        .hideDefaultTooltip()
                        .setName(Component.literal((enabled ? "§a" : "§c") + label + ": " + (enabled ? "ON" : "OFF")))
                        .addLoreLine(Component.literal("§7" + description))
                        .addLoreLine(Component.literal("§eClick to toggle"))
                        .setCallback((i, c, t) -> {
                            toggle.run();
                            open(player);
                        })
        );
    }
}
