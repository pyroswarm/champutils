package com.champutils.menu;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.adventurer.AdventurerGuildMenu;
import com.champutils.economy.EconomyManager;
import com.champutils.quest.QuestConfig;
import com.champutils.quest.QuestDataManager;
import com.champutils.quest.QuestManager;
import com.champutils.quest.QuestTrackerManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.Locale;

public final class ContractMenu {
    private ContractMenu() {}

    public static void open(ServerPlayer player) {
        QuestDataManager.QuestData data = QuestManager.getData(player);
        QuestManager.refreshIfNeeded(player, data, true);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Adventurer Contracts"));
        MenuUtil.fillBorders(gui, 4, 10, 11, 12, 13, 14, 15, 16, 28, 29, 30, 31, 32, 33, 34, 45, 49);

        gui.setSlot(4, new GuiElementBuilder(Items.CLOCK)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Adventurer Contracts"))
                .addLoreLine(Component.literal("§7Timed tasks from the Adventurer's Guild."))
                .addLoreLine(Component.literal("§7Max active: §f" + QuestConfig.SETTINGS.maxActiveContracts)));

        setActiveContracts(gui, player, data);
        setAvailableContracts(gui, player);
        gui.setSlot(45, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eBack to Adventurer's Guild")).setCallback((slot, click, action) -> AdventurerGuildMenu.open(player)));
        gui.open();
    }

    private static void setActiveContracts(SimpleGui gui, ServerPlayer player, QuestDataManager.QuestData data) {
        gui.setSlot(10, new GuiElementBuilder(Items.MAP)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Active Contracts"))
                .addLoreLine(Component.literal("§7Click a completed contract to claim.")));
        if (data == null || data.contracts == null || data.contracts.isEmpty()) {
            gui.setSlot(11, new GuiElementBuilder(Items.GRAY_DYE).hideDefaultTooltip().setName(Component.literal("§7No active contracts")));
            return;
        }
        int[] slots = {11, 12, 13, 14, 15, 16};
        int offset = 0;
        long now = System.currentTimeMillis();
        for (QuestDataManager.Contract c : data.contracts) {
            if (c == null || c.completed || now >= c.expiresAtMillis) continue;
            if (offset >= slots.length) break;
            boolean done = c.progress >= c.required;
            GuiElementBuilder item = new GuiElementBuilder(done ? Items.LIME_DYE : Items.PAPER)
                    .hideDefaultTooltip()
                    .setName(Component.literal((done ? "§a" : "§e") + c.description))
                    .addLoreLine(Component.literal("§7Contract Rank: §f" + com.champutils.quest.QuestConfig.rankForDifficulty(c.difficulty) + " Rank"))
                    .addLoreLine(Component.literal("§7Progress: §f" + Math.min(c.progress, c.required) + "§7/§f" + c.required))
                    .addLoreLine(Component.literal("§7Time left: §f" + QuestManager.timeLeftText(c)))
                    .addLoreLine(Component.literal("§6Rewards:"));
            addLore(item, QuestManager.contractRewardLore(c.rewardCommands, c.rewardCredits, c.difficulty));
            item.addLoreLine(Component.literal(QuestTrackerManager.isTracked(data, "contract", c) ? "§aTracked" : (done ? "§eLeft click to claim" : "§eLeft click to track")));
            item.addLoreLine(Component.literal("§cRight click to abandon"));
            item.addLoreLine(Component.literal("§7No refund; all progress is lost."));
            item.setCallback((index, click, action) -> {
                if (isRightClick(click)) openAbandonConfirm(player, c);
                else { if (done) QuestManager.completeContract(player); else QuestTrackerManager.toggle(player, "contract", c); open(player); }
            });
            gui.setSlot(slots[offset++], item);
        }
    }

    private static void setAvailableContracts(SimpleGui gui, ServerPlayer player) {
        List<QuestConfig.ContractTemplate> contracts = QuestManager.eligibleContracts(player);
        gui.setSlot(28, new GuiElementBuilder(Items.GOLD_INGOT)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Create Contract"))
                .addLoreLine(Component.literal("§7Pick a contract to start.")));
        int[] slots = {29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        if (contracts == null || contracts.isEmpty()) {
            gui.setSlot(29, new GuiElementBuilder(Items.GRAY_DYE)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§7No eligible contracts"))
                    .addLoreLine(Component.literal("§7Finish more Adventurer tasks"))
                    .addLoreLine(Component.literal("§7or level professions to unlock more.")));
            return;
        }
        for (int i = 0; i < Math.min(slots.length, contracts.size()); i++) {
            QuestConfig.ContractTemplate c = contracts.get(i);
            GuiElementBuilder item = new GuiElementBuilder(Items.WRITABLE_BOOK)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§e" + c.description))
                    .addLoreLine(Component.literal("§7Cost: §6" + EconomyManager.formatWholeCredits(c.creditCost)))
                    .addLoreLine(Component.literal("§7Time: §f" + c.durationHours + "h"))
                    .addLoreLine(Component.literal("§7Contract Rank: §f" + com.champutils.quest.QuestConfig.rankForDifficulty(c.difficulty) + " Rank"))
                    .addLoreLine(Component.literal("§7Adventurer Rank Required: §f" + c.minAdventurerRank + " Rank"))
                    .addLoreLine(Component.literal("§7Profession: §f" + c.profession))
                    .addLoreLine(Component.literal("§6Rewards:"));
            addLore(item, QuestManager.contractRewardLore(c.rewardCommands, c.rewardCredits, c.difficulty));
            item.addLoreLine(Component.literal("§eClick to create"));
            item.setCallback((index, click, action) -> { QuestManager.buyContract(player, c.id); open(player); });
            gui.setSlot(slots[i], item);
        }
    }

    private static void openAbandonConfirm(ServerPlayer player, QuestDataManager.Contract contract) {
        if (player == null || contract == null) return;
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Abandon Contract?"));
        gui.setSlot(4, new GuiElementBuilder(Items.PAPER).hideDefaultTooltip()
                .setName(Component.literal("§e" + contract.description))
                .addLoreLine(Component.literal("§7Progress: §f" + Math.min(contract.progress, contract.required) + "§7/§f" + contract.required))
                .addLoreLine(Component.literal("§cThe purchase cost will not be refunded."))
                .addLoreLine(Component.literal("§cAll current progress will be lost.")));
        gui.setSlot(11, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip()
                .setName(Component.literal("§cAbandon Contract"))
                .addLoreLine(Component.literal("§7Permanently remove this contract."))
                .addLoreLine(Component.literal("§eClick to confirm"))
                .setCallback((slot, click, action) -> {
                    QuestManager.abandonContract(player, contract.purchasedAtMillis);
                    open(player);
                }));
        gui.setSlot(15, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip()
                .setName(Component.literal("§aKeep Contract"))
                .addLoreLine(Component.literal("§7Return without losing progress."))
                .setCallback((slot, click, action) -> open(player)));
        gui.open();
    }

    private static boolean isRightClick(Object clickType) {
        String text = String.valueOf(clickType).toLowerCase(Locale.ROOT);
        return text.contains("right") || text.equals("1");
    }

    private static void addLore(GuiElementBuilder builder, List<Component> lore) {
        if (builder == null || lore == null) return;
        for (Component line : lore) builder.addLoreLine(line);
    }
}
