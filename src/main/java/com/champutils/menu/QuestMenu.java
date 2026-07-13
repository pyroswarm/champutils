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

public class QuestMenu {

    public static void open(ServerPlayer player) {
        AdventureGuideManager.increment(player, "guild_board", 1);
        QuestDataManager.QuestData data = QuestManager.getData(player);
        QuestManager.refreshIfNeeded(player, data, true);
        QuestDataManager.GuildQuestData guildData = QuestManager.getGuildData(player);

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Adventurer Board"));
        MenuUtil.fillBorders(gui, 4, 10, 11, 12, 14, 15, 16, 19, 20, 21, 22, 23, 24, 31, 40, 49);

        gui.setSlot(4, new GuiElementBuilder(Items.BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Adventurer Board"))
                .addLoreLine(Component.literal("§7Daily, weekly, contract,"))
                .addLoreLine(Component.literal("§7guild, and PvP quests.")));

        setSet(gui, 10, player, data, data.daily, "daily", "§bDaily Tasks", "", true);
        setSet(gui, 14, player, data, data.weekly, "weekly", "§dWeekly Tasks", "", false);
        setPvpGuildMissions(gui, 19, player);
        setGuildWeekly(gui, 28, player, guildData);

        GuiElementBuilder dailyClaim = new GuiElementBuilder(Items.EMERALD)
                .hideDefaultTooltip()
                .setName(Component.literal("§aClaim Daily"))
                .addLoreLine(Component.literal("§7Ready: " + readyText(data.daily)))
                .addLoreLine(Component.literal("§6Rewards:"));
        addLore(dailyClaim, QuestManager.rewardLore(true));
        dailyClaim.addLoreLine(Component.literal("§eClick to claim"));
        dailyClaim.setCallback((index, click, action) -> {
            QuestManager.complete(player, true);
            open(player);
        });
        gui.setSlot(22, dailyClaim);

        GuiElementBuilder weeklyClaim = new GuiElementBuilder(Items.NETHER_STAR)
                .hideDefaultTooltip()
                .setName(Component.literal("§dClaim Weekly"))
                .addLoreLine(Component.literal("§7Ready: " + readyText(data.weekly)))
                .addLoreLine(Component.literal("§6Rewards:"));
        addLore(weeklyClaim, QuestManager.rewardLore(false));
        weeklyClaim.addLoreLine(Component.literal("§eClick to claim"));
        weeklyClaim.setCallback((index, click, action) -> {
            QuestManager.complete(player, false);
            open(player);
        });
        gui.setSlot(23, weeklyClaim);

        GuiElementBuilder guildClaim = new GuiElementBuilder(Items.TRIAL_KEY)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Claim Player Guild Weekly"))
                .addLoreLine(Component.literal("§7Ready: " + readyText(guildData == null ? null : guildData.weekly)))
                .addLoreLine(Component.literal("§7Claimed: " + (QuestManager.hasClaimedGuildWeekly(player) ? "§aYes" : "§cNo")))
                .addLoreLine(Component.literal("§6Rewards:"));
        addLore(guildClaim, QuestManager.guildRewardLore());
        guildClaim.addLoreLine(Component.literal("§eClick to claim"));
        guildClaim.setCallback((index, click, action) -> {
            QuestManager.completeGuildWeekly(player);
            open(player);
        });
        gui.setSlot(24, guildClaim);

        gui.setSlot(40, new GuiElementBuilder(Items.NETHERITE_SWORD)
                .hideDefaultTooltip()
                .setName(Component.literal("§cBattle Queues"))
                .addLoreLine(Component.literal("§7Queue ranked or casual PvP."))
                .addLoreLine(Component.literal("§7Ranked is the main progression path."))
                .addLoreLine(Component.literal("§eClick to open"))
                .setCallback((index, click, action) -> BattleMenu.open(player)));

        gui.setSlot(45, new GuiElementBuilder(Items.ARROW)
                .hideDefaultTooltip()
                .setName(Component.literal("§eBack to Adventurer's Guild"))
                .setCallback((index, click, action) -> AdventurerGuildMenu.open(player)));

        gui.setSlot(49, new GuiElementBuilder(Items.CHEST)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Open Contracts"))
                .addLoreLine(Component.literal("§7Contracts are part of the Adventurer's Guild."))
                .addLoreLine(Component.literal("§eClick to open"))
                .setCallback((index, click, action) -> ContractMenu.open(player)));

        gui.open();
    }

    private static void setSet(SimpleGui gui, int start, ServerPlayer player, QuestDataManager.QuestData data, QuestDataManager.QuestSet set, String kind, String title, String claimCommand, boolean daily) {
        GuiElementBuilder main = new GuiElementBuilder(Items.WRITABLE_BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal(title))
                .addLoreLine(Component.literal("§7Status: " + (set != null && set.completed ? "§aClaimed" : QuestManager.isReady(set) ? "§6Ready" : "§fIn Progress")))
                .addLoreLine(Component.literal("§6Rewards:"));
        addLore(main, QuestManager.rewardLore(daily));
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
                    .setCallback((index, click, action) -> { QuestTrackerManager.track(player, kind, o); open(player); }));
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
            int personal = o.playerProgress == null ? 0 : o.playerProgress.getOrDefault(player.getUUID().toString(), 0);
            gui.setSlot(start + 1 + i, new GuiElementBuilder(done ? Items.LIME_DYE : Items.MAP)
                    .hideDefaultTooltip()
                    .setName(Component.literal((done ? "§a" : "§e") + o.description))
                    .addLoreLine(Component.literal("§7Guild progress: §f" + Math.min(o.progress, Math.max(1, o.requiredPlayers)) + "§7/§f" + Math.max(1, o.requiredPlayers) + " members"))
                    .addLoreLine(Component.literal("§7Your progress: §f" + Math.min(personal, o.required) + "§7/§f" + o.required))
                    .addLoreLine(Component.literal("§7Profession: §f" + o.profession))
                    .addLoreLine(Component.literal(done ? "§aComplete for the guild" : "§7Help your guild finish it.")));
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
            item.addLoreLine(Component.literal(QuestTrackerManager.isTracked(data, "contract", c) ? "§aTracked" : (done ? "§eClick to claim" : "§eClick to track")));
            item.setCallback((index, click, action) -> {
                if (done) QuestManager.completeContract(player); else QuestTrackerManager.track(player, "contract", c);
                open(player);
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
