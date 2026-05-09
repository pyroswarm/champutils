package com.champutils.profession.passives;

import com.champutils.profession.ProfessionBlockTracker;
import com.champutils.profession.ProfessionRewardPassiveConfig;
import com.champutils.profession.ProfessionSpecialCelebration;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

public class TreasurePingPassive implements ProfessionPassive {

    public static final String STAT_ID = "treasurePingChance";

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
         * Economy guard: Treasure Ping must only trigger from natural mining
         * sources. PassiveRegistry already checks this, but this keeps the
         * passive safe if it is ever called directly later.
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
                !isValidTreasureSource(
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
                ProfessionRewardPassiveConfig.rollReward(
                        "treasurePing"
                );

        if (reward.isEmpty()) {
            return;
        }

        int amount =
                reward.getCount();

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


        ProfessionSpecialCelebration.celebrateSpecialActive(
                player,
                "§6Treasure Ping!",
                "§fFound " + amount + "x " + rewardName
        );
    }

    private static boolean isValidTreasureSource(
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
                 "minecraft:smooth_basalt",
                 "minecraft:coal_ore",
                 "minecraft:deepslate_coal_ore",
                 "minecraft:copper_ore",
                 "minecraft:deepslate_copper_ore",
                 "minecraft:iron_ore",
                 "minecraft:deepslate_iron_ore",
                 "minecraft:gold_ore",
                 "minecraft:deepslate_gold_ore",
                 "minecraft:nether_gold_ore",
                 "minecraft:redstone_ore",
                 "minecraft:deepslate_redstone_ore",
                 "minecraft:lapis_ore",
                 "minecraft:deepslate_lapis_ore",
                 "minecraft:emerald_ore",
                 "minecraft:deepslate_emerald_ore",
                 "minecraft:diamond_ore",
                 "minecraft:deepslate_diamond_ore",
                 "minecraft:ancient_debris" ->
                    true;

            default ->
                    false;
        };
    }

}
