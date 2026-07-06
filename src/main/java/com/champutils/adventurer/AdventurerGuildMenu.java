package com.champutils.adventurer;

import com.champutils.economy.EconomyManager;
import com.champutils.menu.ContractMenu;
import com.champutils.menu.MenuUtil;
import com.champutils.menu.QuestMenu;
import com.champutils.menu.RankedShopMenu;
import com.champutils.roaming.RoamingTrainerRarity;
import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.adventureguide.AdventureGuideMenu;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class AdventurerGuildMenu {
    private AdventurerGuildMenu() {}

    public static void open(ServerPlayer player) {
        if (AdventureGuideManager.handleAdventurerNpcOpen(player)) {
            return;
        }
        AdventurerGuildDataManager.PlayerData data = AdventurerGuildManager.getData(player);
        AdventurerGuildConfig.RankDefinition rank = AdventurerGuildConfig.currentRank(data.renown);
        String rankId = rank == null || rank.id == null ? "F" : rank.id;
        AdventurerGuildConfig.RankDefinition next = AdventurerGuildConfig.nextRank(data.renown);

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Adventurer's Guild"));
        MenuUtil.fillBorders(gui, 4, 10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34, 49);

        GuiElementBuilder header = new GuiElementBuilder(Items.BELL)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Adventurer's Guild"))
                .addLoreLine(Component.literal("§7Rank: §f" + AdventurerRankUtil.displayRank(rankId)))
                .addLoreLine(Component.literal("§7Adventurer XP: §e" + data.renown))
                .addLoreLine(Component.literal("§7Adventurer's Marks: §b" + data.guildMarks))
                .addLoreLine(Component.literal("§7Tower Floor: §f" + data.towerFloor + "§7/§f" + AdventurerGuildConfig.SETTINGS.battleTowerMaxFloor));
        if (next != null) {
            header.addLoreLine(Component.literal("§7Next Rank: §f" + AdventurerRankUtil.displayRank(next.id) + " §8(" + data.renown + "§7/§e" + next.renownRequired + "§8)"));
        } else {
            header.addLoreLine(Component.literal("§6Max Adventurer Rank reached."));
        }
        gui.setSlot(4, header);

        MenuUtil.addOpenButton(gui, 10, Items.WRITABLE_BOOK, "§bAdventurer Board", () -> QuestMenu.open(player),
                "§7Daily, weekly, and PvP quests.", "§7The main Guild task board.");
        MenuUtil.addOpenButton(gui, 12, Items.CHEST, "§6Profession Contracts", () -> ContractMenu.open(player),
                "§7Timed profession tasks.", "§7Earn Adventurer XP and Marks.");
        MenuUtil.addOpenButton(gui, 14, Items.DIAMOND_SWORD, "§dBattle Tower", () -> openBattleTower(player),
                "§7A PvE trainer gauntlet.", "§7Win floors for Adventure rewards.");
        MenuUtil.addOpenButton(gui, 16, Items.COMPASS, "§aAdventurer Requests", () -> openRoamingLeague(player),
                "§7Summon a PvE trainer challenge.", "§7Higher ranks unlock stronger requests.");

        MenuUtil.addOpenButton(gui, 19, Items.BOOK, "§aAdventure Guide", () -> AdventureGuideMenu.open(player),
                "§7One-time guide missions that", "§7teach every Cobble Champs system.");

        MenuUtil.addOpenButton(gui, 21, Items.TRIAL_KEY, "§6Claim Rank Reward", () -> {
                    AdventurerGuildManager.claimNextRankReward(player);
                    open(player);
                },
                "§7Claim the next Adventurer Rank", "§7reward you have unlocked.");

        MenuUtil.addOpenButton(gui, 23, Items.NETHER_STAR, "§dPvP Token Shop", () -> RankedShopMenu.open(player),
                "§7Spend Ranked Tokens from PvP.", "§7Ranked wins are the main source.");

        MenuUtil.addOpenButton(gui, 30, Items.EMERALD, "§aAdventurer Pokémon Shop", () -> AdventurerGuildShopMenu.open(player),
                "§7Spend Adventurer's Marks and Credits", "§7on rank-gated base-form Pokémon.");

        MenuUtil.addOpenButton(gui, 25, Items.MAP, "§eExpeditions", () -> com.champutils.expeditions.ExpeditionMenu.open(player),
                "§7Open expedition content from the", "§7guild hub.");

        MenuUtil.addOpenButton(gui, 32, Items.WRITABLE_BOOK, "§ePlayer Contracts", () -> com.champutils.contracts.PlayerContractMenu.open(player),
                "§7Find item and Pokémon jobs.", "§7Complete jobs for Credits.");

        gui.setSlot(49, new GuiElementBuilder(Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal("§cClose"))
                .addLoreLine(Component.literal("§7Close this menu."))
                .setCallback((slot, click, action) -> player.closeContainer()));
        gui.open();
    }

    public static void openBattleTower(ServerPlayer player) {
        AdventurerGuildDataManager.PlayerData data = AdventurerGuildManager.getData(player);
        AdventurerGuildConfig.BattleTowerFloor floor = AdventurerGuildConfig.floor(data.towerFloor);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x5, player);
        gui.setTitle(Component.literal("Battle Tower"));
        MenuUtil.fillBorders(gui, 4, 10, 12, 14, 16, 19, 21, 23, 25, 31, 36, 44);

        gui.setSlot(4, new GuiElementBuilder(Items.DIAMOND_SWORD)
                .hideDefaultTooltip()
                .setName(Component.literal("§d" + floor.displayName))
                .addLoreLine(Component.literal("§7Current Floor: §f" + data.towerFloor + "§7/§f" + AdventurerGuildConfig.SETTINGS.battleTowerMaxFloor))
                .addLoreLine(Component.literal("§7Trainer Rarity: §f" + AdventurerRankUtil.displayRank(AdventurerRankUtil.fromRarity(RoamingTrainerRarity.parse(floor.rarity, AdventurerGuildConfig.rarityForFloor(data.towerFloor))))))
                .addLoreLine(Component.literal("§7Best Floor: §f" + data.bestTowerFloor))
                .addLoreLine(Component.literal("§7Full Clears: §f" + data.towerClears))
                .addLoreLine(Component.literal("§7Cooldown: §f" + AdventurerGuildManager.timeUntilTowerReady(data)))
                .addLoreLine(Component.literal("§7Checkpoints: §f1, 3, 6, 9"))
                .addLoreLine(Component.literal("§8Rewards are paid only when you reach a new checkpoint.")));

        gui.setSlot(20, new GuiElementBuilder(Items.LIME_STAINED_GLASS_PANE)
                .hideDefaultTooltip()
                .setName(Component.literal("§aStart Floor " + data.towerFloor))
                .addLoreLine(Component.literal("§7Enter the Battle Tower arena."))
                .addLoreLine(Component.literal("§7A trainer appears nearby and the battle starts."))
                .addLoreLine(Component.literal("§cNo healing or Pokémon storage during an attempt."))
                .addLoreLine(Component.literal("§eClick to start"))
                .setCallback((slot, click, action) -> AdventurerGuildManager.startBattleTowerFloor(player)));

        int[] checkpointSlots = {10, 12, 14, 16};
        int[] checkpoints = {1, 3, 6, 9};
        for (int i = 0; i < checkpoints.length; i++) {
            int checkpoint = checkpoints[i];
            boolean unlocked = AdventurerGuildManager.isCheckpointUnlocked(data, checkpoint);
            gui.setSlot(checkpointSlots[i], new GuiElementBuilder(unlocked ? Items.ENDER_EYE : Items.GRAY_DYE)
                    .hideDefaultTooltip()
                    .setName(Component.literal((unlocked ? "§a" : "§7") + "Checkpoint Floor " + checkpoint))
                    .addLoreLine(Component.literal(unlocked ? "§7Start future attempts here." : "§cReach this checkpoint to unlock it."))
                    .addLoreLine(Component.literal("§eClick to select"))
                    .setCallback((slot, click, action) -> { AdventurerGuildManager.selectTowerCheckpoint(player, checkpoint); openBattleTower(player); }));
        }

        gui.setSlot(24, new GuiElementBuilder(Items.RED_STAINED_GLASS_PANE)
                .hideDefaultTooltip()
                .setName(Component.literal("§cBack to Adventurer's Guild"))
                .addLoreLine(Component.literal("§7Return to the Adventurer's Guild."))
                .setCallback((slot, click, action) -> open(player)));

        MenuUtil.addBackButton(gui, 36, () -> open(player));
        gui.open();
    }

    public static void openPvpMissions(ServerPlayer player) {
        AdventurerGuildDataManager.PlayerData data = AdventurerGuildManager.getData(player);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x4, player);
        gui.setTitle(Component.literal("PvP Quests"));
        MenuUtil.fillBorders(gui, 10, 12, 14, 16, 31);

        gui.setSlot(10, new GuiElementBuilder(AdventurerGuildManager.isDailyPvpReady(data) ? Items.LIME_DYE : Items.PAPER)
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
                .setCallback((slot, click, action) -> {
                    AdventurerGuildManager.claimDailyPvp(player);
                    openPvpMissions(player);
                }));

        gui.setSlot(14, new GuiElementBuilder(AdventurerGuildManager.isWeeklyPvpReady(data) ? Items.LIME_DYE : Items.MAP)
                .hideDefaultTooltip()
                .setName(Component.literal("§cWeekly PvP Quest"))
                .addLoreLine(Component.literal("§7Play and win queued PvP battles."))
                .addLoreLine(Component.literal("§7Ranked is the best way to progress."))
                .addLoreLine(Component.literal("§7Matches: §f" + data.weeklyPvpMatches + "§7/§f" + AdventurerGuildConfig.SETTINGS.pvpWeeklyRequiredMatches))
                .addLoreLine(Component.literal("§7Wins: §f" + data.weeklyPvpWins + "§7/§f" + AdventurerGuildConfig.SETTINGS.pvpWeeklyRequiredWins))
                .addLoreLine(Component.literal("§7Ranked Wins: §f" + data.weeklyRankedWins))
                .addLoreLine(Component.literal("§7Claimed: " + (data.weeklyPvpClaimed ? "§aYes" : "§cNo")))
                .addLoreLine(Component.literal("§6Rewards:"))
                .addLoreLine(Component.literal("§7• §6" + EconomyManager.formatWholeCredits(AdventurerGuildConfig.SETTINGS.pvpWeeklyRewardCredits)))
                .addLoreLine(Component.literal("§7• §e" + AdventurerGuildConfig.SETTINGS.pvpWeeklyRewardRenown + " Adventurer XP"))
                .addLoreLine(Component.literal("§7• §b" + AdventurerGuildConfig.SETTINGS.pvpWeeklyRewardMarks + " Adventurer's Marks"))
                .addLoreLine(Component.literal("§eClick to claim"))
                .setCallback((slot, click, action) -> {
                    AdventurerGuildManager.claimWeeklyPvp(player);
                    openPvpMissions(player);
                }));

        gui.setSlot(16, new GuiElementBuilder(Items.NETHERITE_SWORD)
                .hideDefaultTooltip()
                .setName(Component.literal("§6PvP Rewards"))
                .addLoreLine(Component.literal("§7Ranked gives Credits, RP,"))
                .addLoreLine(Component.literal("§7Ranked Tokens, and Track XP."))
                .addLoreLine(Component.literal("§eOpen Battles from /menu to queue.")));

        MenuUtil.addBackButton(gui, 31, () -> open(player));
        gui.open();
    }

    public static void openRoamingLeague(ServerPlayer player) {
        AdventurerGuildDataManager.PlayerData data = AdventurerGuildManager.getData(player);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x4, player);
        gui.setTitle(Component.literal("Adventurer Requests"));
        MenuUtil.fillBorders(gui, 4, 10, 11, 12, 14, 15, 16, 31);

        gui.setSlot(4, new GuiElementBuilder(Items.COMPASS)
                .hideDefaultTooltip()
                .setName(Component.literal("§aAdventurer Requests"))
                .addLoreLine(Component.literal("§7Cooldown: §f" + AdventurerGuildManager.timeUntilRoamingReady(data)))
                .addLoreLine(Component.literal("§7Daily free requests: §f" + data.roamingLeagueDailySpawns + "§7/§f" + AdventurerGuildConfig.SETTINGS.roamingLeagueDailyFreeSpawns))
                .addLoreLine(Component.literal("§7Adventurer XP: §e" + data.renown)));

        int[] slots = {10, 11, 12, 14, 15, 16};
        RoamingTrainerRarity[] rarities = RoamingTrainerRarity.values();
        for (int i = 0; i < Math.min(slots.length, rarities.length); i++) {
            RoamingTrainerRarity rarity = rarities[i];
            AdventurerGuildConfig.RoamingLeagueEntry entry = AdventurerGuildConfig.roamingEntry(rarity);
            boolean unlocked = data.renown >= Math.max(0, entry.minRenown);
            boolean free = data.roamingLeagueDailySpawns < Math.max(0, AdventurerGuildConfig.SETTINGS.roamingLeagueDailyFreeSpawns);
            GuiElementBuilder item = new GuiElementBuilder(unlocked ? Items.MAP : Items.GRAY_DYE)
                    .hideDefaultTooltip()
                    .setName(Component.literal((unlocked ? "§a" : "§7") + AdventurerRankUtil.trainerLabel(AdventurerRankUtil.fromRarity(rarity))))
                    .addLoreLine(Component.literal("§7Required Adventurer XP: §e" + entry.minRenown))
                    .addLoreLine(Component.literal("§7Cost: §6" + (free ? "Daily Free Request" : EconomyManager.formatWholeCredits(entry.creditCost))))
                    .addLoreLine(Component.literal("§6Adventure Rewards:"))
                    .addLoreLine(Component.literal("§7• §e" + entry.rewardRenown + " Adventurer XP"))
                    .addLoreLine(Component.literal("§7• §b" + entry.rewardMarks + " Adventurer's Marks"));
            item.addLoreLine(Component.literal(unlocked ? "§eClick to request Adventurer" : "§cLocked"));
            if (unlocked) {
                item.setCallback((slot, click, action) -> AdventurerGuildManager.startRoamingLeague(player, rarity));
            }
            gui.setSlot(slots[i], item);
        }

        MenuUtil.addBackButton(gui, 31, () -> open(player));
        gui.open();
    }

    private static String readySuffix(boolean ready) {
        return ready ? " §aReady" : "";
    }
}
