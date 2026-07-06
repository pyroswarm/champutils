package com.champutils.adventureguide;

import com.champutils.adventurer.AdventurerGuildMenu;
import com.champutils.menu.MenuUtil;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class AdventureGuideMenu {
    private AdventureGuideMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Adventure Guide"));

        AdventureGuideManager.Objective current = AdventureGuideManager.currentObjective(player);
        int progress = AdventureGuideManager.currentProgress(player);
        gui.setSlot(4, new GuiElementBuilder(Items.WRITABLE_BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("§bAdventure Guide"))
                .addLoreLine(Component.literal("§7One-time objectives that teach"))
                .addLoreLine(Component.literal("§7the major Cobble Champs systems."))
                .addLoreLine(Component.literal(""))
                .addLoreLine(Component.literal("§fCurrent: §e" + (current == null ? "Complete" : current.title())))
                .addLoreLine(Component.literal(current == null ? "§aAll done." : "§7Progress: §f" + progress + "§7/§f" + current.target())));

        int slot = 9;
        for (AdventureGuideManager.ObjectiveStatus status : AdventureGuideManager.statuses(player)) {
            if (slot >= 45) break;
            gui.setSlot(slot, objectiveIcon(status));
            slot++;
        }

        gui.setSlot(48, new GuiElementBuilder(AdventureGuideManager.isBossBarVisible(player) ? Items.LIME_DYE : Items.GRAY_DYE)
                .hideDefaultTooltip()
                .setName(Component.literal((AdventureGuideManager.isBossBarVisible(player) ? "§a" : "§c") + "Guide Boss Bar: " + (AdventureGuideManager.isBossBarVisible(player) ? "ON" : "OFF")))
                .addLoreLine(Component.literal("§7Shows your current Adventure Guide"))
                .addLoreLine(Component.literal("§7objective at the top of the screen."))
                .addLoreLine(Component.literal("§eClick to toggle"))
                .setCallback((i, c, t) -> {
                    AdventureGuideManager.toggleBossBar(player);
                    open(player);
                }));

        gui.setSlot(50, new GuiElementBuilder(Items.COMPASS)
                .hideDefaultTooltip()
                .setName(Component.literal("§eCurrent Objective"))
                .addLoreLine(Component.literal(current == null ? "§aAll guide objectives are complete." : "§f" + current.title()))
                .addLoreLine(Component.literal(current == null ? "" : "§7" + current.hint())));

        MenuUtil.addBackButton(gui, 45, () -> AdventurerGuildMenu.open(player));
        gui.open();
    }

    private static GuiElementBuilder objectiveIcon(AdventureGuideManager.ObjectiveStatus status) {
        AdventureGuideManager.Objective objective = status.objective();
        Item item = switch (status.status()) {
            case COMPLETE -> Items.LIME_CONCRETE;
            case CURRENT -> Items.YELLOW_CONCRETE;
            case LOCKED -> Items.GRAY_CONCRETE;
        };
        String prefix = switch (status.status()) {
            case COMPLETE -> "§a✓ ";
            case CURRENT -> "§e➤ ";
            case LOCKED -> "§7Locked: ";
        };
        GuiElementBuilder builder = new GuiElementBuilder(item)
                .hideDefaultTooltip()
                .setName(Component.literal(prefix + objective.title()))
                .addLoreLine(Component.literal("§7" + objective.description()))
                .addLoreLine(Component.literal(""))
                .addLoreLine(Component.literal("§7Progress: §f" + status.progress() + "§7/§f" + objective.target()))
                .addLoreLine(Component.literal("§7Reward: §6" + objective.rewardCredits() + " credits"));
        if (status.status() == AdventureGuideManager.Status.CURRENT) {
            builder.addLoreLine(Component.literal(""));
            builder.addLoreLine(Component.literal("§eHint: §7" + objective.hint()));
        }
        return builder;
    }
}
