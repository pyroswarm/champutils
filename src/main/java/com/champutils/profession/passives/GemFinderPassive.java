package com.champutils.profession.passives;

import com.champutils.profession.ProfessionBlockTracker;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionRewardPassiveConfig;
import com.champutils.profession.ProfessionSpecialCelebration;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.Random;

public class GemFinderPassive implements ProfessionPassive {

    public static final String STAT_ID = "gemFinderChance";

    private static final Random RANDOM =
            new Random();

    @Override
    public void apply(
            ServerPlayer player,
            ItemStack stack,
            ServerLevel level,
            BlockPos pos,
            String blockId
    ) {

        if (
                player == null ||
                        stack == null ||
                        stack.isEmpty() ||
                        level == null ||
                        pos == null ||
                        blockId == null ||
                        blockId.isBlank()
        ) {
            return;
        }

        /*
         * Economy guard: Gem Finder must only trigger from natural stone-like
         * mining blocks. PassiveRegistry already checks this, but this keeps
         * the passive safe if it is ever called directly later.
         */
        if (
                ProfessionBlockTracker.isPlayerPlaced(
                        level,
                        pos
                )
        ) {
            return;
        }

        if (
                !isValidGemSource(
                        blockId
                )
        ) {
            return;
        }

        double chancePercent =
                ProfessionToolUtil.getStat(
                        stack,
                        STAT_ID
                );

        if (chancePercent <= 0.0D) {
            return;
        }

        if (
                RANDOM.nextDouble() >=
                        Math.min(
                                100.0D,
                                chancePercent
                        ) / 100.0D
        ) {
            return;
        }

        ItemStack reward =
                rollReward();

        if (reward.isEmpty()) {
            return;
        }

        String rewardName =
                reward.getHoverName()
                        .getString();

        ItemStack rewardToGive =
                reward.copy();

        if (
                !player.getInventory()
                        .add(
                                rewardToGive
                        )
        ) {
            player.drop(
                    rewardToGive,
                    false
            );
        }

        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(
                    Component.literal(
                            "§bGem Finder: §fFound " +
                                    rewardName +
                                    "!"
                    ),
                    true
            );
        }

        ProfessionSpecialCelebration.celebrateSpecialActive(
                player,
                "§bGem Finder!",
                "§fFound " + rewardName
        );
    }

    private static ItemStack rollReward() {

        return ProfessionRewardPassiveConfig.rollReward(
                "gemFinder"
        );
    }

    private static boolean isValidGemSource(
            String blockId
    ) {

        return switch (blockId) {
            case "minecraft:stone",
                 "minecraft:deepslate",
                 "minecraft:granite",
                 "minecraft:diorite",
                 "minecraft:andesite",
                 "minecraft:tuff",
                 "minecraft:calcite",
                 "minecraft:dripstone_block",
                 "minecraft:blackstone",
                 "minecraft:basalt",
                 "minecraft:smooth_basalt" ->
                    true;

            default ->
                    false;
        };
    }
}
