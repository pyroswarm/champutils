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

public class ShardFinderPassive implements ProfessionPassive {

    public static final String STAT_ID = "shardFinderChance";

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
         * Economy guard: Shard Finder must only trigger from natural mining
         * blocks. PassiveRegistry already checks this, but this keeps the
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
                !isValidShardSource(
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
                        "shardFinder"
                );

        if (reward.isEmpty()) {
            return;
        }

        /*
         * Inventory#add mutates the passed ItemStack. If it fully inserts the
         * item, the original stack becomes empty/air. Capture the display name
         * before insertion so the popup does not say "Found Air".
         */
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
                            "§dShard Finder: §fFound " +
                                    rewardName +
                                    "!"
                    ),
                    true
            );
        }

        ProfessionSpecialCelebration.celebrateSpecialActive(
                player,
                "§dShard Finder!",
                "§fFound " + rewardName
        );
    }

    private static boolean isValidShardSource(
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
                 "minecraft:ancient_debris",
                 "cobblemon:fire_stone_ore",
                 "cobblemon:deepslate_fire_stone_ore",
                 "cobblemon:nether_fire_stone_ore",
                 "cobblemon:water_stone_ore",
                 "cobblemon:deepslate_water_stone_ore",
                 "cobblemon:thunder_stone_ore",
                 "cobblemon:deepslate_thunder_stone_ore",
                 "cobblemon:leaf_stone_ore",
                 "cobblemon:deepslate_leaf_stone_ore",
                 "cobblemon:ice_stone_ore",
                 "cobblemon:deepslate_ice_stone_ore",
                 "cobblemon:moon_stone_ore",
                 "cobblemon:deepslate_moon_stone_ore",
                 "cobblemon:dripstone_moon_stone_ore",
                 "cobblemon:sun_stone_ore",
                 "cobblemon:deepslate_sun_stone_ore",
                 "cobblemon:terracotta_sun_stone_ore",
                 "cobblemon:dawn_stone_ore",
                 "cobblemon:deepslate_dawn_stone_ore",
                 "cobblemon:dusk_stone_ore",
                 "cobblemon:deepslate_dusk_stone_ore",
                 "cobblemon:shiny_stone_ore",
                 "cobblemon:deepslate_shiny_stone_ore" ->
                    true;

            default ->
                    false;
        };
    }
}
