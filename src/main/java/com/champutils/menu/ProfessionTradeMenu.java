package com.champutils.menu;

import com.champutils.profession.ProfessionBackpackConfig;
import com.champutils.profession.ProfessionBackpackManager;
import com.champutils.profession.ProfessionType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ProfessionTradeMenu {
    private static final int[] CONTENT = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private ProfessionTradeMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Profession Trade"));
        MenuUtil.fillBorders(gui, 4,20,22,24,49);
        gui.setSlot(4, new GuiElementBuilder(Items.EMERALD).hideDefaultTooltip()
                .setName(Component.literal("§aProfession Trade"))
                .addLoreLine(Component.literal("§7Trade backpack materials for rare rewards.")));
        gui.setSlot(20, new GuiElementBuilder(Items.IRON_PICKAXE).hideDefaultTooltip().setName(Component.literal("§bMining Trades")).setCallback((i,c,t) -> openProfession(player, ProfessionType.MINING, 0)));
        gui.setSlot(22, new GuiElementBuilder(Items.OAK_LOG).hideDefaultTooltip().setName(Component.literal("§2Forestry Trades")).setCallback((i,c,t) -> openProfession(player, ProfessionType.FORESTRY, 0)));
        gui.setSlot(24, new GuiElementBuilder(Items.WHEAT).hideDefaultTooltip().setName(Component.literal("§aFarming Trades")).setCallback((i,c,t) -> openProfession(player, ProfessionType.FARMING, 0)));
        MenuUtil.addBackButton(gui, 49, () -> ProfessionForemanMenu.open(player));
        gui.open();
    }

    public static void openProfession(ServerPlayer player, ProfessionType profession, int page) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Profession Trade - " + profession.name()));
        MenuUtil.fillBorders(gui, 4,10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43,45,49,53);
        gui.setSlot(4, new GuiElementBuilder(Items.AMETHYST_SHARD).hideDefaultTooltip()
                .setName(Component.literal("§d" + profession.name() + " Trades"))
                .addLoreLine(Component.literal("§7Costs are configurable per item.")));

        List<ProfessionBackpackConfig.ItemData> items = tradeItems(profession, player);
        int maxPage = Math.max(0, (items.size() - 1) / CONTENT.length);
        int fixedPage = Math.max(0, Math.min(page, maxPage));
        int start = fixedPage * CONTENT.length;
        for (int idx = 0; idx < CONTENT.length && start + idx < items.size(); idx++) {
            ProfessionBackpackConfig.ItemData data = items.get(start + idx);
            long have = ProfessionBackpackManager.count(player, data.item);
            gui.setSlot(CONTENT[idx], new GuiElementBuilder(icon(data.item)).hideDefaultTooltip()
                    .setName(Component.literal("§e" + data.displayName))
                    .addLoreLine(Component.literal("§7Stored: §a" + have))
                    .addLoreLine(Component.literal("§7Cost: §6" + data.tradeCost + "x"))
                    .addLoreLine(Component.literal("§7Reward: §d" + data.rewardAmount + "x " + ProfessionBackpackConfig.formatName(data.rewardItem)))
                    .addLoreLine(Component.literal(have >= data.tradeCost ? "§eClick to trade once." : "§cNot enough stored."))
                    .setCallback((slot, click, type) -> {
                        ProfessionBackpackManager.TradeResult result = ProfessionBackpackManager.trade(player, data.item);
                        player.sendSystemMessage(Component.literal((result.success() ? "§a" : "§c") + result.message()));
                        openProfession(player, profession, fixedPage);
                    }));
        }
        if (fixedPage > 0) gui.setSlot(45, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§ePrevious Page")).setCallback((i,c,t) -> openProfession(player, profession, fixedPage - 1)));
        MenuUtil.addBackButton(gui, 49, () -> open(player));
        if (fixedPage < maxPage) gui.setSlot(53, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eNext Page")).setCallback((i,c,t) -> openProfession(player, profession, fixedPage + 1)));
        gui.open();
    }

    private static List<ProfessionBackpackConfig.ItemData> tradeItems(ProfessionType profession, ServerPlayer player) {
        Map<String, Long> balances = ProfessionBackpackManager.balances(player);
        return ProfessionBackpackConfig.CONFIG.items.values().stream()
                .filter(data -> profession.name().equalsIgnoreCase(data.profession))
                .filter(data -> balances.getOrDefault(data.item, 0L) > 0L)
                .filter(data -> data.tradeEnabled)
                .sorted(Comparator.comparingInt((ProfessionBackpackConfig.ItemData d) -> d.sort).thenComparing(d -> d.displayName))
                .toList();
    }

    private static Item icon(String itemId) {
        Item item = ProfessionBackpackManager.item(itemId);
        return item == Items.AIR ? Items.BARRIER : item;
    }
}
