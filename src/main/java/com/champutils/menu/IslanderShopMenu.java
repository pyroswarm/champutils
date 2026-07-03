package com.champutils.menu;

import com.champutils.economy.EconomyManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.shop.IslanderShopConfig;
import com.champutils.shop.NpcShopConfig;
import com.champutils.shop.NpcShopService;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Set;

public final class IslanderShopMenu {
    private IslanderShopMenu() {}

    public static void open(ServerPlayer player) {
        if (player == null) return;
        if (!PlayerProfileManager.isIslander(player)) {
            player.sendSystemMessage(Component.literal("Only Islander profiles can use the Islander Resource Shop.").withStyle(ChatFormatting.RED));
            return;
        }
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal(IslanderShopConfig.CONFIG.title == null ? "Islander Resource Shop" : IslanderShopConfig.CONFIG.title));
        MenuUtil.fillBorders(gui);
        gui.setSlot(4, new GuiElementBuilder(Items.WATER_BUCKET).hideDefaultTooltip()
                .setName(Component.literal("§bIslander Resources"))
                .addLoreLine(Component.literal("§7Balance: §f" + EconomyManager.format(EconomyManager.getBalance(player))))
                .addLoreLine(Component.literal("§8Safety-valve materials for skyblock progression.")));

        Set<Integer> used = new HashSet<>();
        used.add(4);
        int nextSlot = 10;
        for (NpcShopConfig.ShopEntry entry : IslanderShopConfig.CONFIG.entries) {
            if (entry == null) continue;
            int slot = entry.slot >= 0 && entry.slot < gui.getSize() && isContentSlot(entry.slot, gui.getSize()) ? entry.slot : nextOpenSlot(used, nextSlot, gui.getSize());
            if (slot < 0) continue;
            used.add(slot);
            nextSlot = slot + 1;
            Item icon = NpcShopService.resolveItem(entry.icon);
            if (icon == Items.AIR) icon = Items.CHEST;
            GuiElementBuilder builder = new GuiElementBuilder(icon).hideDefaultTooltip()
                    .setName(Component.literal(entry.displayName == null ? "§fShop Item" : entry.displayName));
            if (entry.lore != null) for (String line : entry.lore) builder.addLoreLine(Component.literal(line));
            builder.addLoreLine(Component.literal(""));
            builder.addLoreLine(Component.literal("§6Price: §f" + EconomyManager.format(Math.max(0L, entry.price))));
            builder.addLoreLine(Component.literal("§eClick to buy"));
            builder.setCallback((index, clickType, actionType) -> { NpcShopService.buy(player, entry); open(player); });
            gui.setSlot(slot, builder);
        }
        gui.open();
    }

    private static int nextOpenSlot(Set<Integer> used, int start, int size) {
        for (int i = Math.max(0, start); i < size; i++) if (!used.contains(i) && isContentSlot(i, size)) return i;
        return -1;
    }

    private static boolean isContentSlot(int slot, int size) {
        int row = slot / 9;
        int column = slot % 9;
        int rows = size / 9;
        return row > 0 && row < rows - 1 && column > 0 && column < 8;
    }
}
