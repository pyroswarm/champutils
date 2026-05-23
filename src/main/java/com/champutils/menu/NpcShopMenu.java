package com.champutils.menu;

import com.champutils.economy.EconomyManager;
import com.champutils.shop.NpcShopConfig;
import com.champutils.shop.NpcShopService;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Set;

public final class NpcShopMenu {

    private NpcShopMenu() {
    }

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal(NpcShopConfig.CONFIG.title == null ? "Essentials Shop" : NpcShopConfig.CONFIG.title));

        MenuUtil.fillBorders(gui);

        gui.setSlot(
                4,
                new GuiElementBuilder(Items.EMERALD)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§aYour Balance"))
                        .addLoreLine(Component.literal("§7" + EconomyManager.format(EconomyManager.getBalance(player))))
                        .addLoreLine(Component.literal("§8This shop is intentionally small."))
                        .addLoreLine(Component.literal("§8Most trading should stay player-driven."))
        );

        Set<Integer> used = new HashSet<>();
        int nextSlot = 9;

        for (NpcShopConfig.ShopEntry entry : NpcShopConfig.CONFIG.entries) {
            if (entry == null) continue;

            int slot = entry.slot >= 0 && entry.slot < gui.getSize() ? entry.slot : nextOpenSlot(used, nextSlot, gui.getSize());
            if (slot < 0 || slot >= gui.getSize()) {
                continue;
            }

            used.add(slot);
            nextSlot = slot + 1;

            Item icon = NpcShopService.resolveItem(entry.icon);
            if (icon == Items.AIR) {
                icon = Items.CHEST;
            }

            GuiElementBuilder builder = new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal(entry.displayName == null ? "§fShop Item" : entry.displayName));

            if (entry.lore != null) {
                for (String line : entry.lore) {
                    builder.addLoreLine(Component.literal(line));
                }
            }

            builder.addLoreLine(Component.literal(""));
            builder.addLoreLine(Component.literal("§6Price: §f" + EconomyManager.format(Math.max(0L, entry.price))));
            builder.addLoreLine(Component.literal("§eClick to buy"));

            builder.setCallback((index, clickType, actionType) -> {
                boolean isPokemonCrate = entry.type != null && "pokemon_crate".equalsIgnoreCase(entry.type.trim());
                NpcShopService.buy(player, entry);

                // Normal shop purchases reopen the shop so the balance refreshes.
                // Pokemon crates open their own CS2-style roulette GUI, so reopening the shop here
                // would instantly replace/kill the roulette UI.
                if (!isPokemonCrate) {
                    open(player);
                }
            });

            gui.setSlot(slot, builder);
        }

        gui.open();
    }

    private static int nextOpenSlot(Set<Integer> used, int start, int size) {
        for (int i = Math.max(0, start); i < size; i++) {
            if (!used.contains(i)) {
                return i;
            }
        }
        return -1;
    }
}
