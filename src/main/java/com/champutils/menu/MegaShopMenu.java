package com.champutils.menu;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.economy.EconomyManager;
import com.champutils.genesis.MegaShopConfig;
import com.champutils.genesis.MegaShopService;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class MegaShopMenu {

    private static final int[] CONTENT_SLOTS = new int[] {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private MegaShopMenu() {}

    public static void open(ServerPlayer player) {
        AdventureGuideManager.increment(player, "shop", 1);
        openCategories(player);
    }

    private static void openCategories(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal(MegaShopConfig.CONFIG.title == null ? "Mega & Special Item Shop" : MegaShopConfig.CONFIG.title));
        MenuUtil.fillBorders(gui);

        gui.setSlot(4, new GuiElementBuilder(Items.NETHER_STAR)
                .hideDefaultTooltip()
                .setName(Component.literal("§dMega & Special Item Shop"))
                .addLoreLine(Component.literal("§7Balance: §f" + EconomyManager.format(EconomyManager.getBalance(player))))
                .addLoreLine(Component.literal("§8Mega Stones, Genesis Forms, and non-craftable Cobblemon items."))
                .addLoreLine(Component.literal("§8Use categories below to keep the shop clean.")));

        List<String> categories = MegaShopConfig.categories();
        if (categories.isEmpty()) {
            gui.setSlot(22, new GuiElementBuilder(Items.BARRIER)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§cNo special shop items available"))
                    .addLoreLine(Component.literal("§7Check §fconfig/champutils/mega_shop.json§7.")));
            gui.open();
            return;
        }

        int limit = Math.min(categories.size(), CONTENT_SLOTS.length);
        for (int i = 0; i < limit; i++) {
            String category = categories.get(i);
            List<MegaShopConfig.ShopEntry> entries = MegaShopConfig.entriesForCategory(category);
            Item icon = iconFor(entries);
            gui.setSlot(CONTENT_SLOTS[i], new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§d" + category))
                    .addLoreLine(Component.literal("§7Items: §f" + entries.size()))
                    .addLoreLine(Component.literal("§eClick to browse"))
                    .setCallback((slot, clickType, actionType) -> openCategory(player, category, 0)));
        }

        gui.open();
    }

    private static void openCategory(ServerPlayer player, String category, int page) {
        List<MegaShopConfig.ShopEntry> allEntries = MegaShopConfig.entriesForCategory(category);
        int maxPage = Math.max(0, (allEntries.size() - 1) / CONTENT_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * CONTENT_SLOTS.length;
        int end = Math.min(allEntries.size(), start + CONTENT_SLOTS.length);
        List<MegaShopConfig.ShopEntry> pageEntries = start >= end ? new ArrayList<>() : allEntries.subList(start, end);

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Special Shop: " + category + " " + (safePage + 1) + "/" + (maxPage + 1)));
        MenuUtil.fillBorders(gui);

        gui.setSlot(4, new GuiElementBuilder(Items.EMERALD)
                .hideDefaultTooltip()
                .setName(Component.literal("§aYour Balance"))
                .addLoreLine(Component.literal("§7" + EconomyManager.format(EconomyManager.getBalance(player))))
                .addLoreLine(Component.literal("§8Category: §f" + category)));

        for (int i = 0; i < pageEntries.size(); i++) {
            MegaShopConfig.ShopEntry entry = pageEntries.get(i);
            Item icon = MegaShopService.resolveItem(entry.icon == null || entry.icon.isBlank() ? entry.id : entry.icon);
            if (icon == Items.AIR) icon = MegaShopService.resolveItem(entry.id);
            if (icon == Items.AIR) icon = Items.BARRIER;

            GuiElementBuilder builder = new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal(entry.displayName == null ? "§fMega Shop Item" : "§f" + entry.displayName));

            if (entry.lore != null) {
                for (String line : entry.lore) {
                    builder.addLoreLine(Component.literal(line));
                }
            }

            builder.addLoreLine(Component.literal(""));
            builder.addLoreLine(Component.literal("§7ID: §8" + entry.id));
            builder.addLoreLine(Component.literal("§6Price: §f" + EconomyManager.format(EconomyManager.creditsToCents(entry.priceCredits))));
            if (entry.amount > 1) builder.addLoreLine(Component.literal("§7Amount: §f" + entry.amount));
            builder.addLoreLine(Component.literal("§eClick to buy"));
            builder.setCallback((slot, clickType, actionType) -> {
                MegaShopService.buy(player, entry);
                openCategory(player, category, safePage);
            });

            gui.setSlot(CONTENT_SLOTS[i], builder);
        }

        gui.setSlot(45, new GuiElementBuilder(Items.ARROW)
                .hideDefaultTooltip()
                .setName(Component.literal("§eBack to Categories"))
                .addLoreLine(Component.literal("§7Return to the special shop tabs."))
                .setCallback((slot, clickType, actionType) -> openCategories(player)));

        if (safePage > 0) {
            gui.setSlot(48, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§ePrevious Page"))
                    .setCallback((slot, clickType, actionType) -> openCategory(player, category, safePage - 1)));
        }
        if (safePage < maxPage) {
            gui.setSlot(50, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§eNext Page"))
                    .setCallback((slot, clickType, actionType) -> openCategory(player, category, safePage + 1)));
        }

        gui.open();
    }

    private static Item iconFor(List<MegaShopConfig.ShopEntry> entries) {
        if (entries != null) {
            for (MegaShopConfig.ShopEntry entry : entries) {
                if (entry == null) continue;
                Item icon = MegaShopService.resolveItem(entry.icon == null || entry.icon.isBlank() ? entry.id : entry.icon);
                if (icon != Items.AIR) return icon;
                icon = MegaShopService.resolveItem(entry.id);
                if (icon != Items.AIR) return icon;
            }
        }
        return Items.NETHER_STAR;
    }
}
