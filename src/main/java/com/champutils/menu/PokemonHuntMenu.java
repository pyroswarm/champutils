package com.champutils.menu;

import com.champutils.matchmaking.PokemonIconUtil;
import com.champutils.hunt.PokemonHuntManager;
import com.champutils.hunt.PokemonHuntState;
import com.champutils.economy.EconomyManager;
import com.champutils.hunt.PokemonHuntConfig;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class PokemonHuntMenu {

    private PokemonHuntMenu() {}

    public static void open(ServerPlayer player) {
        PokemonHuntManager.ensureStarted(player.server);

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("§bPokémon Hunts"));
        MenuUtil.fillBordersForced(gui, 10, 11, 12, 14, 15, 16, 22);

        List<PokemonHuntState.HuntEntry> hunts = PokemonHuntManager.sortedHunts();
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < Math.min(slots.length, hunts.size()); i++) {
            PokemonHuntState.HuntEntry hunt = hunts.get(i);
            boolean done = hunt.claimed;
            ItemStack icon = PokemonIconUtil.createPokemonIcon(hunt.species, false, "minecraft:egg", false);
            GuiElementBuilder builder = new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal((done ? "§a" : rarityColor(hunt.difficulty)) + PokemonHuntManager.prettySpecies(hunt.species)))
                    .addLoreLine(Component.literal("§7First player to catch this exact target wins."))
                    .addLoreLine(Component.literal("§7Species: §f" + PokemonHuntManager.prettySpecies(hunt.species)))
                    .addLoreLine(Component.literal("§7Nature: §f" + PokemonHuntManager.prettyNature(hunt.nature)))
                    .addLoreLine(Component.literal("§7Gender: §f" + PokemonHuntManager.prettyGender(hunt.gender)))
                    .addLoreLine(Component.literal("§7Ability: §f" + PokemonHuntManager.prettyAbility(hunt.ability)))
                    .addLoreLine(Component.literal("§7Difficulty: §f" + (hunt.difficulty == null ? "Common" : hunt.difficulty)))
                    .addLoreLine(Component.literal("§6Rewards:"));
            addRewardLore(builder, hunt.rewards, hunt.difficulty);
            builder.addLoreLine(Component.literal(" "));

            if (done) {
                builder.addLoreLine(Component.literal("§aCompleted by §f" + hunt.winnerName));
                if (player.getUUID().toString().equals(hunt.winnerUuid) && !hunt.rewardClaimed) {
                    builder.addLoreLine(Component.literal("§eReward ready: §f/hunts claim"));
                } else if (hunt.rewardClaimed) {
                    builder.addLoreLine(Component.literal("§7Reward claimed."));
                }
            } else {
                builder.addLoreLine(Component.literal("§eStatus: §fAvailable"));
                builder.addLoreLine(Component.literal("§8Trades, evolutions, and Wondertrade do not count."));
            }

            gui.setSlot(slots[i], builder);
        }

        gui.setSlot(22, new GuiElementBuilder(Items.CLOCK)
                .hideDefaultTooltip()
                .setName(Component.literal("§bNext Refresh"))
                .addLoreLine(Component.literal("§7Refreshes in: §f" + formatDuration(PokemonHuntManager.millisUntilRefresh())))
                .addLoreLine(Component.literal("§7Use §e/hunts§7 anytime to check active hunts."))
        );

        gui.open();
    }

    private static String rarityColor(String difficulty) {
        String d = difficulty == null ? "COMMON" : difficulty.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (d) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            default -> "§f";
        };
    }

    private static void addRewardLore(GuiElementBuilder builder, PokemonHuntConfig.Rewards rewards, String difficulty) {
        if (builder == null) return;
        if (rewards == null) {
            builder.addLoreLine(Component.literal("§7- §fRewards vary by target."));
            return;
        }
        long rewardCredits = PokemonHuntConfig.normalizeRewardCredits(rewards.credits, difficulty);
        if (rewardCredits > 0L) {
            builder.addLoreLine(Component.literal("§7- §6" + EconomyManager.format(rewardCredits)));
        }
        if (rewards.items != null && !rewards.items.isEmpty()) {
            int shown = 0;
            for (PokemonHuntConfig.RewardItem item : rewards.items) {
                if (item == null || item.item == null || item.item.isBlank()) continue;
                int amount = Math.max(1, item.min);
                builder.addLoreLine(Component.literal("§7- " + PokemonHuntManager.prettyItemId(item.item) + " ×" + amount));
                if (++shown >= 6) break;
            }
        }
        builder.addLoreLine(Component.literal("§7- §e" + PokemonHuntConfig.DATA.settings.crateCreditChancePercent + "% chance for 1 " + PokemonHuntManager.displayCrateForDifficulty(difficulty)));
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = TimeUnit.SECONDS.toHours(seconds);
        seconds -= TimeUnit.HOURS.toSeconds(hours);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        seconds -= TimeUnit.MINUTES.toSeconds(minutes);
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
