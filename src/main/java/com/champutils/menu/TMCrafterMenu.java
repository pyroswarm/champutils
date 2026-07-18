package com.champutils.menu;

import com.champutils.economy.EconomyManager;
import com.champutils.tm.TMConfig;
import com.champutils.tm.TMManager;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;

public final class TMCrafterMenu {
    private static final int[] TYPE_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34
    };
    private static final int[] MOVE_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    private TMCrafterMenu() {}

    public static void open(ServerPlayer player) {
        open(player, MainMenu::open);
    }

    public static void open(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        TMConfig.load();
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal("TM Shop"));
        MenuUtil.fillBorders(gui, TYPE_SLOTS);

        long balance = EconomyManager.getBalance(player);
        gui.setSlot(4, new GuiElementBuilder(Items.MUSIC_DISC_CAT)
                .hideDefaultTooltip()
                .setName(Component.literal("§bTM Shop"))
                .addLoreLine(Component.literal("§7Buy exact TMs with Credits."))
                .addLoreLine(Component.literal("§7Grouped by type and sorted alphabetically."))
                .addLoreLine(Component.literal("§7Balance: §6" + EconomyManager.format(balance))));

        List<String> types = TMManager.moveTypes();
        for (int i = 0; i < types.size() && i < TYPE_SLOTS.length; i++) {
            addType(gui, player, TYPE_SLOTS[i], types.get(i));
        }

        gui.setSlot(49, new GuiElementBuilder(Items.RED_STAINED_GLASS_PANE)
                .hideDefaultTooltip()
                .setName(Component.literal("§cBack"))
                .setCallback((i, c, t) -> backTarget.accept(player)));
        gui.open();
    }

    private static void addType(SimpleGui gui, ServerPlayer player, int slot, String type) {
        List<String> moves = TMManager.movesForType(type);
        gui.setSlot(slot, new GuiElementBuilder(TMManager.iconForType(type))
                .hideDefaultTooltip()
                .setName(Component.literal("§b" + TMManager.prettyType(type) + " TMs"))
                .addLoreLine(Component.literal("§7Available: §e" + moves.size()))
                .addLoreLine(Component.literal("§eClick to browse."))
                .setCallback((i, c, t) -> openMovePicker(player, type, 0)));
    }

    private static void openMovePicker(ServerPlayer player, String type, int page) {
        TMConfig.load();
        String normalized = TMManager.normalizeType(type);
        List<String> moves = TMManager.movesForType(normalized);
        int maxPage = Math.max(0, (moves.size() - 1) / MOVE_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal(TMManager.prettyType(normalized) + " TM Shop"));
        MenuUtil.fillBorders(gui, MOVE_SLOTS);

        int start = safePage * MOVE_SLOTS.length;
        for (int i = 0; i < MOVE_SLOTS.length; i++) {
            int moveIndex = start + i;
            if (moveIndex >= moves.size()) break;
            addMove(gui, player, MOVE_SLOTS[i], moves.get(moveIndex), normalized, safePage);
        }

        if (safePage > 0) {
            gui.setSlot(45, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§ePrevious Page"))
                    .setCallback((i, c, t) -> openMovePicker(player, normalized, safePage - 1)));
        }
        gui.setSlot(49, new GuiElementBuilder(TMManager.iconForType(normalized))
                .hideDefaultTooltip()
                .setName(Component.literal("§b" + TMManager.prettyType(normalized) + " TMs"))
                .addLoreLine(Component.literal("§7Page §f" + (safePage + 1) + "§7/§f" + (maxPage + 1)))
                .addLoreLine(Component.literal("§7Balance: §6" + EconomyManager.format(EconomyManager.getBalance(player)))));
        if (safePage < maxPage) {
            gui.setSlot(53, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§eNext Page"))
                    .setCallback((i, c, t) -> openMovePicker(player, normalized, safePage + 1)));
        }
        gui.setSlot(46, new GuiElementBuilder(Items.RED_STAINED_GLASS_PANE)
                .hideDefaultTooltip()
                .setName(Component.literal("§cBack to Types"))
                .setCallback((i, c, t) -> open(player, MainMenu::open)));
        gui.open();
    }

    private static void addMove(SimpleGui gui, ServerPlayer player, int slot, String moveId, String type, int currentPage) {
        long price = TMManager.priceCentsForMove(moveId);
        boolean canBuy = TMManager.canAfford(player, moveId);
        GuiElementBuilder builder = new GuiElementBuilder(TMManager.iconForMove(moveId))
                .hideDefaultTooltip()
                .setName(Component.literal("§bTM - " + TMManager.prettyMove(moveId)))
                .addLoreLine(Component.literal("§7Type: §f" + TMManager.prettyType(TMManager.typeForMove(moveId))))
                .addLoreLine(Component.literal("§7Cost: §6" + EconomyManager.format(price)))
                .addLoreLine(Component.literal("§7Balance: §6" + EconomyManager.format(EconomyManager.getBalance(player))))
                .addLoreLine(Component.literal(canBuy ? "§eClick to buy this TM." : "§cNot enough Credits."));
        builder.setCallback((i, c, t) -> {
            TMManager.purchaseSpecificAsync(player, moveId).thenAccept(result -> player.server.execute(() -> {
                player.sendSystemMessage(Component.literal((result.success() ? "§a" : "§c") + result.message()));
                openMovePicker(player, type, currentPage);
            }));
        });
        gui.setSlot(slot, builder);
    }
}
