package com.champutils.menu;

import com.champutils.scoreboard.PlayerSidebarManager;
import com.champutils.scoreboard.ScoreboardPreferenceManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ScoreboardSettingsMenu {
    private ScoreboardSettingsMenu() {
    }

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x2, player);
        gui.setTitle(Component.literal("Scoreboard Settings"));

        setToggle(
                gui,
                0,
                "Scoreboard Display",
                "Shows or hides the entire sidebar.",
                ScoreboardPreferenceManager.isEnabled(player.getUUID()),
                () -> {
                    boolean enabled = ScoreboardPreferenceManager.toggle(player.getUUID());
                    if (enabled) PlayerSidebarManager.refresh(player);
                    else PlayerSidebarManager.clear(player);
                },
                player
        );

        ScoreboardPreferenceManager.Line[] lines = ScoreboardPreferenceManager.Line.values();
        for (int index = 0; index < lines.length; index++) {
            ScoreboardPreferenceManager.Line line = lines[index];
            setToggle(
                    gui,
                    index + 1,
                    line.displayName(),
                    line.description(),
                    ScoreboardPreferenceManager.isLineEnabled(player.getUUID(), line),
                    () -> {
                        ScoreboardPreferenceManager.toggleLine(player.getUUID(), line);
                        PlayerSidebarManager.refresh(player);
                    },
                    player
            );
        }

        MenuUtil.addBackButton(gui, 17, () -> SettingsMenu.open(player));
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
