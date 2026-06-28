package com.champutils.rewardtrack;

import com.champutils.menu.MenuUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class RewardTrackMenu {
    private RewardTrackMenu() {}

    public static void open(ServerPlayer player) { open(player, 0); }

    public static void open(ServerPlayer player, int tab) {
        RewardTrackMissionManager.ensure(player);
        RewardTrackData.Save data = RewardTrackData.get(player);
        int safeTab = Math.max(0, Math.min(5, tab));
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal(safeTab == 0 ? "Reward Track - Daily" : safeTab == 5 ? "Reward Track - Rewards" : "Reward Track - Week " + safeTab));

        int level = RewardTrackCommand.level(data.xp);
        int nextLevelXp = Math.min(RewardTrackConfig.maxLevel(), level + 1) * RewardTrackConfig.xpPerLevel();
        gui.setSlot(4, new GuiElementBuilder(Items.NETHER_STAR)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Ranked Reward Track"))
                .addLoreLine(Component.literal("§7Level: §f" + level + "§7/§f" + RewardTrackConfig.maxLevel()))
                .addLoreLine(Component.literal(level < RewardTrackConfig.maxLevel() ? "§7XP: §f" + data.xp + "§7/§f" + nextLevelXp : "§aMax level reached"))
                .addLoreLine(Component.literal("§7Claimed through: §f" + data.claimedLevel))
                .addLoreLine(Component.literal("§eClick to claim ready rewards"))
                .setCallback((i, c, t) -> { RewardTrackCommand.claim(player); open(player, safeTab); }));

        setTab(gui, player, 18, 0, safeTab, "§bDaily", Items.CLOCK, "New missions every day at the shared 2 AM reset.");
        for (int i = 1; i <= 4; i++) {
            setTab(gui, player, 18 + i, i, safeTab, "§dWeek " + i, Items.BOOK, "Season week " + i + " missions.");
        }
        setTab(gui, player, 23, 5, safeTab, "§6Rewards", Items.CHEST, "Preview every reward tier and claim status.");

        if (safeTab == 5) {
            openRewardPreview(player, gui, data, level, 0);
            gui.open();
            return;
        }

        List<RewardTrackData.Mission> missions = data.missions.stream()
                .filter(m -> m != null && (safeTab == 0 ? m.daily : (!m.daily && RewardTrackMissionManager.seasonWeekIndex(m.week) == safeTab)))
                .sorted(Comparator.comparing((RewardTrackData.Mission m) -> m.claimed).thenComparing(m -> m.id))
                .collect(Collectors.toList());

        int[] slots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        if (missions.isEmpty()) {
            gui.setSlot(31, new GuiElementBuilder(Items.GRAY_DYE)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§7No missions yet"))
                    .addLoreLine(Component.literal(safeTab == 0 ? "§7Daily missions refresh at 2 AM." : "§7This week will unlock during the season.")));
        } else {
            for (int i = 0; i < Math.min(slots.length, missions.size()); i++) {
                RewardTrackData.Mission m = missions.get(i);
                boolean done = m.progress >= Math.max(1, m.target);
                gui.setSlot(slots[i], new GuiElementBuilder(m.claimed ? Items.LIME_DYE : done ? Items.EMERALD : Items.PAPER)
                        .hideDefaultTooltip()
                        .setName(Component.literal((m.claimed ? "§a" : done ? "§6" : "§e") + m.title))
                        .addLoreLine(Component.literal("§7Progress: §f" + Math.min(m.progress, m.target) + "§7/§f" + m.target))
                        .addLoreLine(Component.literal("§7Reward Track XP: §6" + m.xp))
                        .addLoreLine(Component.literal(m.claimed ? "§aComplete" : done ? "§6Complete - XP granted automatically" : "§7Keep playing ranked PvP.")));
            }
        }

        gui.setSlot(49, new GuiElementBuilder(Items.CHEST)
                .hideDefaultTooltip()
                .setName(Component.literal("§aClaim Rewards"))
                .addLoreLine(Component.literal("§7Claims every reward up to your current level."))
                .addLoreLine(Component.literal("§eClick to claim"))
                .setCallback((i, c, t) -> { RewardTrackCommand.claim(player); open(player, safeTab); }));
        gui.open();
    }

    private static void openRewardPreview(ServerPlayer player, SimpleGui gui, RewardTrackData.Save data, int level, int page) {
        int[] slots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        int maxLevel = Math.max(1, RewardTrackConfig.maxLevel());
        int maxPage = Math.max(0, (maxLevel - 1) / slots.length);
        int fixedPage = Math.max(0, Math.min(page, maxPage));
        int startLevel = fixedPage * slots.length + 1;
        for (int i = 0; i < slots.length && startLevel + i <= maxLevel; i++) {
            int rewardLevel = startLevel + i;
            RewardTrackConfig.Reward reward = RewardTrackConfig.reward(rewardLevel);
            boolean reached = level >= rewardLevel;
            boolean claimed = data.claimedLevel >= rewardLevel;
            GuiElementBuilder button = new GuiElementBuilder(claimed ? Items.LIME_DYE : reached ? Items.EMERALD : Items.CHEST)
                    .hideDefaultTooltip()
                    .setName(Component.literal((claimed ? "§a" : reached ? "§6" : "§e") + "Tier " + rewardLevel))
                    .addLoreLine(Component.literal(claimed ? "§aClaimed" : reached ? "§6Ready to claim" : "§7Locked"));
            if (reward != null) {
                if (reward.credits > 0) button.addLoreLine(Component.literal("§7Credits: §e" + reward.credits));
                if (reward.items != null) {
                    for (RewardTrackConfig.ItemReward item : reward.items) {
                        if (item != null && item.id != null) button.addLoreLine(Component.literal("§7Item: §f" + item.count + "x " + item.id));
                    }
                }
            }
            final int rewardPage = fixedPage;
            button.setCallback((i2,c,t)-> { RewardTrackCommand.claim(player); openRewardsPage(player, rewardPage); });
            gui.setSlot(slots[i], button);
        }
        if (fixedPage > 0) gui.setSlot(45, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§ePrevious Reward Page")).setCallback((i,c,t)-> openRewardsPage(player, fixedPage - 1)));
        if (fixedPage < maxPage) gui.setSlot(53, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eNext Reward Page")).setCallback((i,c,t)-> openRewardsPage(player, fixedPage + 1)));
        gui.setSlot(49, new GuiElementBuilder(Items.EMERALD).hideDefaultTooltip().setName(Component.literal("§aClaim Ready Rewards")).addLoreLine(Component.literal("§7Reward Page: §f" + (fixedPage + 1) + "§7/§f" + (maxPage + 1))).addLoreLine(Component.literal("§eClick to claim all unlocked tiers.")).setCallback((i,c,t)-> { RewardTrackCommand.claim(player); openRewardsPage(player, fixedPage); }));
    }

    private static void openRewardsPage(ServerPlayer player, int page) {
        RewardTrackMissionManager.ensure(player);
        RewardTrackData.Save data = RewardTrackData.get(player);
        int level = RewardTrackCommand.level(data.xp);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Reward Track - Rewards"));
        int nextLevelXp = Math.min(RewardTrackConfig.maxLevel(), level + 1) * RewardTrackConfig.xpPerLevel();
        gui.setSlot(4, new GuiElementBuilder(Items.NETHER_STAR)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Ranked Reward Track"))
                .addLoreLine(Component.literal("§7Level: §f" + level + "§7/§f" + RewardTrackConfig.maxLevel()))
                .addLoreLine(Component.literal(level < RewardTrackConfig.maxLevel() ? "§7XP: §f" + data.xp + "§7/§f" + nextLevelXp : "§aMax level reached"))
                .addLoreLine(Component.literal("§7Claimed through: §f" + data.claimedLevel)));
        setTab(gui, player, 18, 0, 5, "§bDaily", Items.CLOCK, "New missions every day at the shared 2 AM reset.");
        for (int i = 1; i <= 4; i++) setTab(gui, player, 18 + i, i, 5, "§dWeek " + i, Items.BOOK, "Season week " + i + " missions.");
        setTab(gui, player, 23, 5, 5, "§6Rewards", Items.CHEST, "Preview every reward tier and claim status.");
        openRewardPreview(player, gui, data, level, page);
        gui.open();
    }

    private static void setTab(SimpleGui gui, ServerPlayer player, int slot, int tab, int active, String name, net.minecraft.world.item.Item icon, String lore) {
        gui.setSlot(slot, new GuiElementBuilder(active == tab ? Items.LIME_DYE : icon)
                .hideDefaultTooltip()
                .setName(Component.literal((active == tab ? "§a" : "§e") + name))
                .addLoreLine(Component.literal("§7" + lore))
                .addLoreLine(Component.literal(active == tab ? "§aCurrently open" : "§eClick to open"))
                .setCallback((i, c, t) -> open(player, tab)));
    }
}
