package com.champutils.profession.passives;

import com.champutils.profession.ProfessionBlockTracker;
import com.champutils.profession.ProfessionRewardPassiveConfig;
import com.champutils.profession.ProfessionSpecialCelebration;
import com.champutils.profession.ProfessionToolUtil;
import com.champutils.profession.actives.ActiveEffectManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

public class ForestryRewardFinderPassive implements ProfessionPassive {

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

        rollReward(
                player,
                stack,
                level,
                pos,
                "sapFinderChance",
                "forestrySapFinder",
                "§6Sap Finder!",
                "§6Sap Finder: §fFound "
        );

        rollReward(
                player,
                stack,
                level,
                pos,
                "seedFinderChance",
                "forestrySeedFinder",
                "§aSeed Finder!",
                "§aSeed Finder: §fFound "
        );
    }

    private void rollReward(
            ServerPlayer player,
            ItemStack stack,
            ServerLevel level,
            BlockPos pos,
            String statId,
            String tableName,
            String title,
            String chatPrefix
    ) {

        if (
                player == null ||
                        stack == null ||
                        stack.isEmpty() ||
                        level == null ||
                        pos == null
        ) {
            return;
        }

        if (
                ProfessionBlockTracker.isPlayerPlaced(
                        level,
                        pos
                )
        ) {
            return;
        }

        double chancePercent =
                ProfessionToolUtil.getStat(
                        stack,
                        statId
                );

        if (chancePercent <= 0.0D) {
            return;
        }

        double multiplier =
                ActiveEffectManager.getForestryPassiveChanceMultiplier(
                        player,
                        stack
                );

        if (
                RANDOM.nextDouble() >=
                        Math.min(
                                100.0D,
                                chancePercent * multiplier
                        ) / 100.0D
        ) {
            return;
        }

        ItemStack reward =
                ProfessionRewardPassiveConfig.rollReward(
                        tableName
                );

        if (reward.isEmpty()) {
            return;
        }

        String rewardName =
                reward.getHoverName()
                        .getString();

        ItemStack toGive =
                reward.copy();

        if (!player.getInventory().add(toGive)) {
            player.drop(
                    toGive,
                    false
            );
        }

        player.displayClientMessage(
                Component.literal(
                        chatPrefix +
                                rewardName +
                                "!"
                ),
                true
        );

        ProfessionSpecialCelebration.celebrateSpecialActive(
                player,
                title,
                "§fFound " + rewardName
        );
    }
}
