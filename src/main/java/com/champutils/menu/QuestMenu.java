package com.champutils.menu;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.adventurer.AdventurerGuildMenu;
import com.champutils.adventurer.AdventurerGuildManager;
import com.champutils.adventurer.AdventurerGuildConfig;
import com.champutils.adventurer.AdventurerGuildDataManager;
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

public class QuestMenu {

    private enum Tab { DAILY, WEEKLY, GUILD, PVP, CONTRACTS }

    public static void open(ServerPlayer player) { open(player, Tab.DAILY); }

    private static void open(ServerPlayer player, Tab tab) {
        AdventureGuideManager.increment(player, "guild_board", 1);
        QuestDataManager.QuestData data = QuestManager.getData(player);
        QuestManager.refreshIfNeeded(player, data, true);
        QuestDataManager.GuildQuestData guildData = QuestManager.getGuildData(player);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Adventurer Board • " + tab.name()));
        MenuUtil.fillBorders(gui, 0,1,2,3,4,5,6,7,8,45,46,47,48,49,50,51,52,53);
        setTab(gui, 1, Items.CLOCK, "§bDaily", tab == Tab.DAILY, () -> open(player, Tab.DAILY));
        setTab(gui, 2, Items.MAP, "§dWeekly", tab == Tab.WEEKLY, () -> open(player, Tab.WEEKLY));
        setTab(gui, 3, Items.BELL, "§6Guild", tab == Tab.GUILD, () -> open(player, Tab.GUILD));
        setTab(gui, 4, Items.NETHERITE_SWORD, "§cPvP", tab == Tab.PVP, () -> open(player, Tab.PVP));
        setTab(gui, 5, Items.CHEST, "§eContracts", tab == Tab.CONTRACTS, () -> open(player, Tab.CONTRACTS));

        switch (tab) {
            case DAILY -> setSet(gui, 19, player, data, data.daily, "daily", "§bDaily Tasks", "", true);
            case WEEKLY -> setSet(gui, 19, player, data, data.weekly, "weekly", "§dWeekly Tasks", "", false);
            case GUILD -> setGuildWeekly(gui, 19, player, guildData);
            case PVP -> setPvpGuildMissions(gui, 19, player);
            case CONTRACTS -> {
                setActiveContract(gui, 19, player, data);
                gui.setSlot(31, new GuiElementBuilder(Items.CHEST).hideDefaultTooltip()
                        .setName(Component.literal("§6Open Contracts"))
                        .addLoreLine(Component.literal("§eClick to browse and manage contracts"))
                        .setCallback((i,c,a) -> ContractMenu.open(player)));
            }
        }
        gui.setSlot(45, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip()
                .setName(Component.literal("§eBack to Adventurer's Guild"))
                .setCallback((i,c,a) -> AdventurerGuildMenu.open(player)));
        gui.open();
    }

    private static void setTab(SimpleGui gui, int slot, net.minecraft.world.item.Item item, String name, boolean selected, Runnable action) {
        gui.setSlot(slot, new GuiElementBuilder(item).hideDefaultTooltip()
                .setName(Component.literal((selected ? "§a▶ " : "") + name))
                .addLoreLine(Component.literal(selected ? "§aSelected" : "§eClick to open"))
                .setCallback((i,c,a) -> action.run()));
    }

    private static void setSet(SimpleGui gui, int start, ServerPlayer player, QuestDataManager.QuestData data, QuestDataManager.QuestSet set, String kind, String title, String claimCommand, boolean daily) {
        GuiElementBuilder main = new GuiElementBuilder(Items.WRITABLE_BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal(title))
                .addLoreLine(Component.literal("§7Status: " + (set != null && set.completed ? "§aClaimed" : QuestManager.isReady(set) ? "§6Ready" : "§fIn Progress")))
                .addLoreLine(Component.literal("§7Resets: §f" + QuestManager.resetTimestampText(daily)))
                .addLoreLine(Component.literal("§6Rewards:"));
        addLore(main, QuestManager.rewardLore(daily));
        main.addLoreLine(Component.literal(QuestManager.isReady(set) && set != null && !set.completed ? "§eClick to claim" : "§7Click completed objectives to track"));
        main.setCallback((index, click, action) -> {
            if (QuestManager.isReady(set) && set != null && !set.completed) QuestManager.complete(player, daily);
            open(player, daily ? Tab.DAILY : Tab.WEEKLY);
        });
        gui.setSlot(start, main);

        if (set == null || set.objectives == null) return;
        for (int i = 0; i < Math.min(3, set.objectives.size()); i++) {
            QuestDataManager.Objective o = set.objectives.get(i);
            int slot = start + 1 + i;
            boolean done = o.progress >= o.required;
            gui.setSlot(slot, new GuiElementBuilder(done ? Items.LIME_DYE : Items.PAPER)
                    .hideDefaultTooltip()
                    .setName(Component.literal((done ? "§a" : "§e") + o.description))
                    .addLoreLine(Component.literal("§7Progress: §f" + Math.min(o.progress, o.required) + "§7/§f" + o.required))
                    .addLoreLine(Component.literal("§7Profession: §f" + o.profession))
                    .addLoreLine(Component.literal(QuestTrackerManager.isTracked(data, kind, o) ? "§aTracked" : (done ? "§aComplete" : "§eClick to track")))
                    .setCallback((index, click, action) -> { if (done) { QuestManager.complete(player, "daily".equalsIgnoreCase(kind)); } else { QuestTrackerManager.toggle(player, kind, o); } open(player, daily ? Tab.DAILY : Tab.WEEKLY); }));
        }
    }

    private static void setPvpGuildMissions(SimpleGui gui, int start, ServerPlayer player) {
        AdventurerGuildDataManager.PlayerData data = AdventurerGuildManager.getData(player);
        gui.setSlot(start, new GuiElementBuilder(Items.NETHERITE_SWORD)
                .hideDefaultTooltip()
                .setName(Component.literal("§cPvP Quests"))
                .addLoreLine(Component.literal("§7Queued battle objectives."))
                .addLoreLine(Component.literal("§7Ranked is the main progression path."))
                .addLoreLine(Component.literal("§7Daily Wins: §f" + data.dailyPvpWins + "§7/§f" + AdventurerGuildConfig.SETTINGS.pvpDailyRequiredWins))
                .addLoreLine(Component.literal("§7Weekly Matches: §f" + data.weeklyPvpMatches + "§7/§f" + AdventurerGuildConfig.SETTINGS.pvpWeeklyRequiredMatches))
                .addLoreLine(Component.literal("§7Weekly Wins: §f" + data.weeklyPvpWins + "§7/§f" + AdventurerGuildConfig.SETTINGS.pvpWeeklyRequiredWins))
                .addLoreLine(Component.literal("§eClick to open battle queues"))
                .setCallback((slot, click, action) -> BattleMenu.open(player)));

        gui.setSlot(start + 1, new GuiElementBuilder(AdventurerGuildManager.isDailyPvpReady(data) ? Items.LIME_DYE : Items.PAPER)
                .hideDefaultTooltip()
                .setName(Component.literal("§cDaily PvP Quest"))
                .addLoreLine(Component.literal("§7Win queued PvP battles."))
                .addLoreLine(Component.literal("§7Ranked and casual wins both count."))
                .addLoreLine(Component.literal("§7Progress: §f" + data.dailyPvpWins + "§7/§f" + AdventurerGuildConfig.SETTINGS.pvpDailyRequiredWins))
                .addLoreLine(Component.literal("§7Claimed: " + (data.dailyPvpClaimed ? "§aYes" : "§cNo")))
                .addLoreLine(Component.literal("§6Rewards:"))
                .addLoreLine(Component.literal("§7• §6" + EconomyManager.formatWholeCredits(AdventurerGuildConfig.SETTINGS.pvpDailyRewardCredits)))
                .addLoreLine(Component.literal("§7• §e" + AdventurerGuildConfig.SETTINGS.pvpDailyRewardRenown + " Adventurer XP"))
                .addLoreLine(Component.literal("§7• §b" + AdventurerGuildConfig.SETTINGS.pvpDailyRewardMarks + " Adventurer's Marks"))
                .addLoreLine(Component.literal("§eClick to claim"))
                .setCallback((slot, click, action) -> { AdventurerGuildManager.claimDailyPvp(player); open(player); }));

        gui.setSlot(start + 2, new GuiElementBuilder(AdventurerGuildManager.isWeeklyPvpReady(data) ? Items.LIME_DYE : Items.MAP)
                .hideDefaultTooltip()
                .setName(Component.literal("§cWeekly PvP Quest"))
                .addLoreLine(Component.literal("§7Play and win queued PvP battles."))
                .addLoreLine(Component.literal("§7Ranked wins are tracked here."))
                .addLoreLine(Component.literal("§7Matches: §f" + data.weeklyPvpMatches + "§7/§f" + AdventurerGuildConfig.SETTINGS.pvpWeeklyRequiredMatches))
                .addLoreLine(Component.literal("§7Wins: §f" + data.weeklyPvpWins + "§7/§f" + AdventurerGuildConfig.SETTINGS.pvpWeeklyRequiredWins))
                .addLoreLine(Component.literal("§7Ranked Wins: §f" + data.weeklyRankedWins))
                .addLoreLine(Component.literal("§7Claimed: " + (data.weeklyPvpClaimed ? "§aYes" : "§cNo")))
                .addLoreLine(Component.literal("§6Rewards:"))
                .addLoreLine(Component.literal("§7• §6" + EconomyManager.formatWholeCredits(AdventurerGuildConfig.SETTINGS.pvpWeeklyRewardCredits)))
                .addLoreLine(Component.literal("§7• §e" + AdventurerGuildConfig.SETTINGS.pvpWeeklyRewardRenown + " Adventurer XP"))
                .addLoreLine(Component.literal("§7• §b" + AdventurerGuildConfig.SETTINGS.pvpWeeklyRewardMarks + " Adventurer's Marks"))
                .addLoreLine(Component.literal("§eClick to claim"))
                .setCallback((slot, click, action) -> { AdventurerGuildManager.claimWeeklyPvp(player); open(player); }));
    }

    private static void setGuildWeekly(SimpleGui gui, int start, ServerPlayer player, QuestDataManager.GuildQuestData guildData) {
        GuiElementBuilder main = new GuiElementBuilder(Items.BELL)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Player Guild Weekly Tasks"))
                .addLoreLine(Component.literal("§7Unique members required: §f" + QuestConfig.SETTINGS.guildWeeklyRequiredPlayers))
                .addLoreLine(Component.literal("§7Status: " + (guildData == null ? "§cNo Guild" : QuestManager.isReady(guildData.weekly) ? "§6Ready" : "§fIn Progress")))
                .addLoreLine(Component.literal("§6Rewards:"));
        addLore(main, QuestManager.guildRewardLore());
        main.addLoreLine(Component.literal(guildData != null && QuestManager.isReady(guildData.weekly) && !QuestManager.hasClaimedGuildWeekly(player) ? "§eClick to claim" : "§7Complete the objectives below"));
        main.setCallback((i,c,a) -> {
            if (guildData != null && QuestManager.isReady(guildData.weekly)) QuestManager.completeGuildWeekly(player);
            open(player, Tab.GUILD);
        });
        gui.setSlot(start, main);

        if (guildData == null || guildData.weekly == null || guildData.weekly.objectives == null) {
            gui.setSlot(start + 1, new GuiElementBuilder(Items.GRAY_DYE)
                .hideDefaultTooltip()
                .setName(Component.literal("§7Join a guild to participate.")));
            return;
        }
        for (int i = 0; i < Math.min(4, guildData.weekly.objectives.size()); i++) {
            QuestDataManager.Objective o = guildData.weekly.objectives.get(i);
            boolean done = o.progress >= Math.max(1, o.requiredPlayers);
            java.util.UUID activeProfileId = com.champutils.profile.PlayerProfileManager.activeProfileId(player);
            String personalKey = activeProfileId == null ? "" : activeProfileId.toString();
            int personal = o.playerProgress == null ? 0 : o.playerProgress.getOrDefault(personalKey, 0);
            gui.setSlot(start + 1 + i, new GuiElementBuilder(done ? Items.LIME_DYE : Items.MAP)
                    .hideDefaultTooltip()
                    .setName(Component.literal((done ? "§a" : "§e") + o.description))
                    .addLoreLine(Component.literal("§7Completed quest lines: §f" + Math.min(o.progress, Math.max(1, o.requiredPlayers)) + "§7/§f" + Math.max(1, o.requiredPlayers) + " members"))
                    .addLoreLine(Component.literal("§7Your progress: §f" + Math.min(personal, o.required) + "§7/§f" + o.required))
                    .addLoreLine(Component.literal("§7Profession: §f" + o.profession))
                    .addLoreLine(Component.literal(personal >= o.required ? "§aYou completed this objective" : "§7Complete every objective to add +1 guild progress.")));
        }
    }

    private static void setActiveContract(SimpleGui gui, int start, ServerPlayer player, QuestDataManager.QuestData data) {
        gui.setSlot(start, new GuiElementBuilder(Items.CLOCK)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Active Contracts"))
                .addLoreLine(Component.literal("§7Timed Adventurer's Guild tasks."))
                .addLoreLine(Component.literal("§7Max active: §f" + QuestConfig.SETTINGS.maxActiveContracts)));

        if (data == null || data.contracts == null || data.contracts.isEmpty()) {
            gui.setSlot(start + 1, new GuiElementBuilder(Items.GRAY_DYE)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§7No active contract"))
                    .addLoreLine(Component.literal("§7Buy one below.")));
            return;
        }
        int offset = 1;
        long now = System.currentTimeMillis();
        for (QuestDataManager.Contract c : data.contracts) {
            if (c == null || c.completed || now >= c.expiresAtMillis) continue;
            boolean done = c.progress >= c.required;
            GuiElementBuilder item = new GuiElementBuilder(done ? Items.LIME_DYE : Items.MAP)
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
                else {
                    if (done) QuestManager.completeContract(player); else QuestTrackerManager.toggle(player, "contract", c);
                    open(player);
                }
            });
            gui.setSlot(start + offset, item);
            offset++;
            if (offset > 5) break;
        }
    }

    private static void setAvailableContracts(SimpleGui gui, int start, ServerPlayer player) {
        List<QuestConfig.ContractTemplate> contracts = QuestManager.eligibleContracts(player);
        gui.setSlot(start - 1, new GuiElementBuilder(Items.GOLD_INGOT)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Available Contracts"))
                .addLoreLine(Component.literal("§7Start a contract to earn Adventure rewards."))
                .addLoreLine(Component.literal("§7Rewards are shown on each contract.")));
        for (int i = 0; i < Math.min(8, contracts.size()); i++) {
            QuestConfig.ContractTemplate c = contracts.get(i);
            GuiElementBuilder item = new GuiElementBuilder(Items.PAPER)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§e" + c.description))
                                        .addLoreLine(Component.literal("§7Cost: §6" + EconomyManager.formatWholeCredits(c.creditCost)))
                    .addLoreLine(Component.literal("§7Time: §f" + c.durationHours + "h"))
                    .addLoreLine(Component.literal("§7Contract Rank: §f" + com.champutils.quest.QuestConfig.rankForDifficulty(c.difficulty) + " Rank"))
                    .addLoreLine(Component.literal("§7Profession: §f" + c.profession))
                    .addLoreLine(Component.literal("§6Rewards:"));
            addLore(item, QuestManager.contractRewardLore(c.rewardCommands, c.rewardCredits, c.difficulty));
            item.addLoreLine(Component.literal("§eClick to buy"));
            item.setCallback((index, click, action) -> {
                QuestManager.buyContract(player, c.id);
                open(player);
            });
            gui.setSlot(start + i, item);
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

    private static String readyText(QuestDataManager.QuestSet set) {
        if (set == null) return "§cNo";
        if (set.completed) return "§aClaimed";
        return QuestManager.isReady(set) ? "§6Yes" : "§cNo";
    }
}
