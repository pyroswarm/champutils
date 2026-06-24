package com.champutils.menu;

import com.champutils.tm.TMConfig;
import com.champutils.tm.TMManager;
import com.champutils.profession.ProfessionFragmentManager;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class TMCrafterMenu {
    private static final int[] RARITY_SLOTS = {10, 11, 12, 13, 14, 15};
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
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(Component.literal("TM Crafter"));

        MenuUtil.fillBorders(gui, 4, 10,11,12,13,14,15, 22);
        gui.setSlot(4, new GuiElementBuilder(Items.MUSIC_DISC_CAT)
                .hideDefaultTooltip()
                .setName(Component.literal("§bTM Crafter"))
                .addLoreLine(Component.literal("§7Craft TMs from a tier"))
                .addLoreLine(Component.literal("§7or pick the exact TM for a higher targeted cost."))
                .addLoreLine(Component.literal("§8Costs use your stored fragments.")));

        for (int i = 0; i < TMConfig.RARITIES.size() && i < RARITY_SLOTS.length; i++) {
            addRarity(gui, player, RARITY_SLOTS[i], TMConfig.RARITIES.get(i));
        }

                gui.open();
    }

    private static void addRarity(SimpleGui gui, ServerPlayer player, int slot, String rarity) {
        List<String> moves = TMManager.movesForRarity(rarity);
        Map<String, Integer> randomCost = TMManager.randomCostForRarity(rarity);
        Map<String, Integer> specificCost = TMManager.specificCostForRarity(rarity);
        boolean canRandom = canAfford(player, randomCost);

        GuiElementBuilder builder = new GuiElementBuilder(iconFor(rarity))
                .hideDefaultTooltip()
                .setName(Component.literal("§f" + TMManager.prettyRarity(rarity) + " TMs").withStyle(color(rarity)))
                .addLoreLine(Component.literal("§7Available TMs: §e" + moves.size()))
                .addLoreLine(Component.literal("§aRandom Cost: §f" + TMManager.costText(randomCost)))
                .addLoreLine(Component.literal("§bSpecific Cost: §f" + TMManager.costText(specificCost)))
                .addLoreLine(Component.literal(canRandom ? "§eRight-click: random TM" : "§cRight-click: missing random craft fragments"))
                .addLoreLine(Component.literal("§eLeft-click: choose specific TM"));

        builder.setCallback((index, click, action) -> {
            boolean rightClick = click != null && String.valueOf(click).toLowerCase(Locale.ROOT).contains("right");
            if (!rightClick) {
                openMovePicker(player, rarity, 0);
                return;
            }
            TMManager.CraftResult result = TMManager.craftRandom(player, rarity);
            player.sendSystemMessage(Component.literal((result.success() ? "§a" : "§c") + result.message()));
            open(player, MainMenu::open);
        });
        gui.setSlot(slot, builder);
    }

    private static void openMovePicker(ServerPlayer player, String rarity, int page) {
        TMConfig.load();
        String normalized = TMConfig.normalizeRarity(rarity);
        List<String> moves = TMManager.movesForRarity(normalized);
        int maxPage = Math.max(0, (moves.size() - 1) / MOVE_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal("Choose " + TMManager.prettyRarity(normalized) + " TM"));
        MenuUtil.fillBorders(gui, MOVE_SLOTS);

        int start = safePage * MOVE_SLOTS.length;
        for (int i = 0; i < MOVE_SLOTS.length; i++) {
            int moveIndex = start + i;
            if (moveIndex >= moves.size()) break;
            addMove(gui, player, MOVE_SLOTS[i], moves.get(moveIndex), safePage);
        }

        if (safePage > 0) {
            gui.setSlot(45, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§ePrevious Page"))
                    .setCallback((i, c, t) -> openMovePicker(player, normalized, safePage - 1)));
        }
        gui.setSlot(49, new GuiElementBuilder(Items.BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("§bExact TM Selection"))
                .addLoreLine(Component.literal("§7Specific crafting uses the selected TM cost."))
                .addLoreLine(Component.literal("§7Page §f" + (safePage + 1) + "§7/§f" + (maxPage + 1))));
        if (safePage < maxPage) {
            gui.setSlot(53, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§eNext Page"))
                    .setCallback((i, c, t) -> openMovePicker(player, normalized, safePage + 1)));
        }
        gui.setSlot(46, new GuiElementBuilder(Items.RED_STAINED_GLASS_PANE)
                .hideDefaultTooltip()
                .setName(Component.literal("§cBack"))
                .setCallback((i, c, t) -> open(player, MainMenu::open)));
        gui.open();
    }

    private static void addMove(SimpleGui gui, ServerPlayer player, int slot, String moveId, int currentPage) {
        String rarity = TMManager.rarityForMove(moveId);
        Map<String, Integer> cost = TMManager.specificCostForMove(moveId);
        boolean canCraft = canAfford(player, cost);
        GuiElementBuilder builder = new GuiElementBuilder(TMManager.iconForMove(moveId))
                .hideDefaultTooltip()
                .setName(Component.literal("§bTM - " + TMManager.prettyMove(moveId)))
                .addLoreLine(Component.literal("§7Rarity: §f" + TMManager.prettyRarity(rarity)))
                .addLoreLine(Component.literal("§7Cost: §f" + TMManager.costText(cost)))
                .addLoreLine(Component.literal(canCraft ? "§eClick to craft this TM" : "§cMissing fragments"));
        builder.setCallback((i, c, t) -> {
            TMManager.CraftResult result = TMManager.craftSpecific(player, moveId);
            player.sendSystemMessage(Component.literal((result.success() ? "§a" : "§c") + result.message()));
            openMovePicker(player, rarity, currentPage);
        });
        gui.setSlot(slot, builder);
    }

    private static boolean canAfford(ServerPlayer player, Map<String, Integer> cost) {
        if (cost == null || cost.isEmpty()) return true;
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (ProfessionFragmentManager.countFragments(player, entry.getKey()) < Math.max(0, entry.getValue())) return false;
        }
        return true;
    }

    private static Item iconFor(String rarity) {
        return TMManager.iconForRarity(rarity);
    }

    private static ChatFormatting color(String rarity) {
        return switch (TMConfig.normalizeRarity(rarity)) {
            case "UNCOMMON" -> ChatFormatting.GREEN;
            case "RARE" -> ChatFormatting.BLUE;
            case "EPIC" -> ChatFormatting.LIGHT_PURPLE;
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "MYTHIC" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }
}
