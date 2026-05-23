package com.champutils.dex;

import com.champutils.menu.MenuUtil;
import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class DexRewardsMenu {

    private static final int[] TIER_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19
    };

    private DexRewardsMenu() {
    }

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("True Caught Dex Rewards"));

        int caught = DexProgressManager.getCaughtCount(player);
        int total = DexProgressManager.getTotalPokemon();
        double exactPercent = DexProgressManager.getCompletionPercent(player);
        int unlockedPercent = DexProgressManager.getUnlockedPercent(player);

        int index = 0;
        int step = Math.max(1, DexRewardConfig.CONFIG.tierStepPercent);
        for (int percent = step; percent <= 100 && index < TIER_SLOTS.length; percent += step) {
            int slot = TIER_SLOTS[index++];
            boolean unlocked = unlockedPercent >= percent;
            boolean claimed = DexRewardClaimData.hasClaimed(player.getUUID(), percent);
            int required = DexProgressManager.requiredCaughtForPercent(percent);

            ChatFormatting color = claimed ? ChatFormatting.GREEN : unlocked ? ChatFormatting.GOLD : ChatFormatting.RED;
            String status = claimed ? "Claimed" : unlocked ? "Ready to Claim" : "Locked";

            GuiElementBuilder button = new GuiElementBuilder(CobblemonItems.POKE_BALL)
                    .hideDefaultTooltip()
                    .setName(Component.literal(color + String.valueOf(percent) + "% Reward"))
                    .addLoreLine(Component.literal("§7Status: " + color + status))
                    .addLoreLine(Component.literal("§7Progress: §f" + caught + "§7/§f" + total + " §8(" + String.format("%.2f", exactPercent) + "%)"))
                    .addLoreLine(Component.literal("§7Required: §f" + required + " unique caught"));

            if (!claimed && unlocked) {
                button.addLoreLine(Component.literal("§eClick to claim"));
                int claimPercent = percent;
                button.setCallback((i, c, t) -> {
                    DexRewardManager.claim(player, claimPercent);
                    open(player);
                });
            }

            gui.setSlot(slot, button);
        }

        gui.setSlot(26, new GuiElementBuilder(Items.BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("§bOpen True Caught Dex"))
                .addLoreLine(Component.literal("§7See exactly what counts for these rewards."))
                .addLoreLine(Component.literal("§7Only real wild catches count."))
                .setCallback((i, c, t) -> TrueDexMenu.open(player)));

        gui.open();
    }
}
