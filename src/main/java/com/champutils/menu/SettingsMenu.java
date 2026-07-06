package com.champutils.menu;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.scoreboard.PlayerSidebarManager;
import com.champutils.scoreboard.ScoreboardPreferenceManager;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class SettingsMenu {

    public static void open(ServerPlayer player) {
        AdventureGuideManager.increment(player, "settings", 1);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x2, player);
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
                "Repair Confirmation",
                "Opens a UI confirmation before spending materials.",
                ProfessionNotificationSettings.isRepairConfirmationEnabled(player),
                () -> ProfessionNotificationSettings.toggleRepairConfirmation(player),
                player
        );

        setToggle(
                gui,
                5,
                "Auto Repair Tools",
                "Repairs broken profession tools automatically for the normal credit cost.",
                ProfessionNotificationSettings.isAutoRepairEnabled(player),
                () -> ProfessionNotificationSettings.toggleAutoRepair(player),
                player
        );

        setToggle(
                gui,
                6,
                "Trinket Success Messages",
                "Controls important trinket messages in chat.",
                ProfessionNotificationSettings.areTrinketMessagesEnabled(player),
                () -> ProfessionNotificationSettings.toggleTrinketMessages(player),
                player
        );

        setToggle(
                gui,
                7,
                "Scoreboard Display",
                "Shows credits, ranks, dex progress, and skills in the sidebar.",
                ScoreboardPreferenceManager.isEnabled(player.getUUID()),
                () -> toggleScoreboard(player),
                player
        );


        setToggle(
                gui,
                8,
                "Adventure Guide Boss Bar",
                "Shows your current Adventure Guide objective.",
                AdventureGuideManager.isBossBarVisible(player),
                () -> AdventureGuideManager.toggleBossBar(player),
                player
        );

        MenuUtil.addBackButton(gui, 17, () -> MainMenu.open(player));
        gui.open();
    }

    private static void toggleScoreboard(ServerPlayer player) {
        boolean enabled = ScoreboardPreferenceManager.toggle(player.getUUID());

        if (enabled) {
            PlayerSidebarManager.update(player);
            player.sendSystemMessage(Component.literal("§aScoreboard display enabled."));
        } else {
            PlayerSidebarManager.clear(player);
            player.sendSystemMessage(Component.literal("§eScoreboard display disabled."));
        }
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
