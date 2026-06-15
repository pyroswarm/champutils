package com.champutils.menu;

import com.champutils.economy.EconomyManager;
import com.champutils.quest.QuestConfig;
import com.champutils.quest.QuestDataManager;
import com.champutils.quest.QuestManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import java.util.List;

public final class ContractMenu {
    private ContractMenu() {}

    public static void open(ServerPlayer player) {
        QuestDataManager.QuestData data = QuestManager.getData(player);
        QuestManager.refreshIfNeeded(player, data, true);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Contracts"));
        MenuUtil.fillBorders(gui, 4, 10, 11, 12, 13, 14, 15, 16, 28, 29, 30, 31, 32, 33, 34, 49);

        gui.setSlot(4, new GuiElementBuilder(Items.CLOCK)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Contracts"))
                .addLoreLine(Component.literal("§7Longer objectives with clear fixed rewards."))
                .addLoreLine(Component.literal("§7Max active: §f" + QuestConfig.SETTINGS.maxActiveContracts)));

        setActiveContracts(gui, player, data);
        setAvailableContracts(gui, player);
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
                    .addLoreLine(Component.literal("§7Difficulty: §f" + c.difficulty))
                    .addLoreLine(Component.literal("§7Progress: §f" + Math.min(c.progress, c.required) + "§7/§f" + c.required))
                    .addLoreLine(Component.literal("§7Time left: §f" + QuestManager.timeLeftText(c)))
                    .addLoreLine(Component.literal("§6Rewards:"));
            addLore(item, QuestManager.contractRewardLore(c.rewardCommands, c.rewardCredits, c.difficulty));
            item.addLoreLine(Component.literal(done ? "§eClick to claim" : "§7Complete before it expires."));
            item.setCallback((index, click, action) -> { QuestManager.completeContract(player); open(player); });
            gui.setSlot(slots[offset++], item);
        }
    }

    private static void setAvailableContracts(SimpleGui gui, ServerPlayer player) {
        List<QuestConfig.ContractTemplate> contracts = QuestManager.eligibleContracts(player);
        gui.setSlot(28, new GuiElementBuilder(Items.GOLD_INGOT)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Available Contracts"))
                .addLoreLine(Component.literal("§7Click a contract to buy it.")));
        int[] slots = {29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < Math.min(slots.length, contracts.size()); i++) {
            QuestConfig.ContractTemplate c = contracts.get(i);
            GuiElementBuilder item = new GuiElementBuilder(Items.WRITABLE_BOOK)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§e" + c.description))
                    .addLoreLine(Component.literal("§7Cost: §6" + EconomyManager.formatWholeCredits(c.creditCost)))
                    .addLoreLine(Component.literal("§7Time: §f" + c.durationHours + "h"))
                    .addLoreLine(Component.literal("§7Difficulty: §f" + c.difficulty))
                    .addLoreLine(Component.literal("§7Profession: §f" + c.profession))
                    .addLoreLine(Component.literal("§6Rewards:"));
            addLore(item, QuestManager.contractRewardLore(c.rewardCommands, c.rewardCredits, c.difficulty));
            item.addLoreLine(Component.literal("§eClick to buy"));
            item.setCallback((index, click, action) -> { QuestManager.buyContract(player, c.id); open(player); });
            gui.setSlot(slots[i], item);
        }
    }

    private static void addLore(GuiElementBuilder builder, List<Component> lore) {
        if (builder == null || lore == null) return;
        for (Component line : lore) builder.addLoreLine(line);
    }
}
