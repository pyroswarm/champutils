package com.champutils.profession.passives;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.ProfessionBlockTracker;
import com.champutils.profession.ProfessionToolUtil;
import com.champutils.profession.actives.ActiveEffectManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

public class DropMultiplierPassive implements ProfessionPassive {

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

        /*
         * Safety check: drop multiplier passives are economy passives.
         * They should only trigger from natural blocks, never player-placed
         * ore blocks. PassiveRegistry already checks this, but keeping the
         * guard here prevents future direct calls from becoming exploitable.
         */
        if (
                ProfessionBlockTracker.isPlayerPlaced(
                        level,
                        pos
                )
        ) {
            return;
        }

        String bonusDrop =
                getBonusDrop(
                        player,
                        blockId
                );

        if (
                bonusDrop == null ||
                        bonusDrop.isBlank()
        ) {
            return;
        }

        int multiplier =
                rollMiningDropMultiplier(
                        player,
                        stack
                );

        if (multiplier <= 1) {
            return;
        }

        int extraAmount =
                multiplier - 1;

        String command =
                "give " +
                        player.getName().getString() +
                        " " +
                        bonusDrop +
                        " " +
                        extraAmount;

        player.getServer()
                .getCommands()
                .performPrefixedCommand(
                        player.getServer()
                                .createCommandSourceStack(),
                        command
                );

        player.displayClientMessage(
                Component.literal(
                        "§bMining passive: §f" +
                                multiplier +
                                "x drops!"
                ),
                true
        );


        ProfessionNotificationSettings.playSound(player, 
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.45F,
                multiplier >= 4 ? 1.75F : 1.35F
        );
    }

    private int rollMiningDropMultiplier(
            ServerPlayer player,
            ItemStack stack
    ) {

        /*
         * Roll from rarest/biggest result down to smallest result.
         * This keeps the passive exclusive, so one block break cannot roll
         * double + triple + quadruple all at the same time.
         *
         * Config values are percentages:
         *   5.0 = 5% chance
         *   0.5 = 0.5% chance
         */
        if (
                rollChance(
                        player,
                        stack,
                        "quintupleDropChance"
                )
        ) {
            return 5;
        }

        if (
                rollChance(
                        player,
                        stack,
                        "quadrupleDropChance"
                )
        ) {
            return 4;
        }

        if (
                rollChance(
                        player,
                        stack,
                        "tripleDropChance"
                )
        ) {
            return 3;
        }

        if (
                rollChance(
                        player,
                        stack,
                        "doubleDropChance"
                )
        ) {
            return 2;
        }

        return 1;
    }

    private boolean rollChance(
            ServerPlayer player,
            ItemStack stack,
            String statId
    ) {

        double chancePercent =
                ProfessionToolUtil.getStat(
                        stack,
                        statId
                );

        if (chancePercent <= 0.0D) {
            return false;
        }

        double focusMultiplier =
                ActiveEffectManager.getMiningPassiveChanceMultiplier(
                        player,
                        stack
                );

        return RANDOM.nextDouble() <
                Math.min(
                        100.0D,
                        chancePercent * focusMultiplier
                ) / 100.0D;
    }

    private String getBonusDrop(
            ServerPlayer player,
            String blockId
    ) {

        if (ActiveEffectManager.hasAutoSmelt(player, player.getMainHandItem())) {
            String smeltedDrop =
                    getSmeltedBonusDrop(
                            blockId
                    );

            if (smeltedDrop != null) {
                return smeltedDrop;
            }
        }

        return switch (blockId) {

            case "minecraft:coal_ore",
                 "minecraft:deepslate_coal_ore" ->
                    "minecraft:coal";

            case "minecraft:iron_ore",
                 "minecraft:deepslate_iron_ore" ->
                    "minecraft:raw_iron";

            case "minecraft:gold_ore",
                 "minecraft:deepslate_gold_ore" ->
                    "minecraft:raw_gold";

            case "minecraft:copper_ore",
                 "minecraft:deepslate_copper_ore" ->
                    "minecraft:raw_copper";

            case "minecraft:diamond_ore",
                 "minecraft:deepslate_diamond_ore" ->
                    "minecraft:diamond";

            case "minecraft:emerald_ore",
                 "minecraft:deepslate_emerald_ore" ->
                    "minecraft:emerald";

            case "minecraft:redstone_ore",
                 "minecraft:deepslate_redstone_ore" ->
                    "minecraft:redstone";

            case "minecraft:lapis_ore",
                 "minecraft:deepslate_lapis_ore" ->
                    "minecraft:lapis_lazuli";

            case "minecraft:ancient_debris" ->
                    "minecraft:ancient_debris";

            default ->
                    null;
        };
    }

    private String getSmeltedBonusDrop(
            String blockId
    ) {

        return switch (blockId) {

            case "minecraft:iron_ore",
                 "minecraft:deepslate_iron_ore" ->
                    "minecraft:iron_ingot";

            case "minecraft:gold_ore",
                 "minecraft:deepslate_gold_ore",
                 "minecraft:nether_gold_ore" ->
                    "minecraft:gold_ingot";

            case "minecraft:copper_ore",
                 "minecraft:deepslate_copper_ore" ->
                    "minecraft:copper_ingot";

            case "minecraft:ancient_debris" ->
                    "minecraft:netherite_scrap";

            default ->
                    null;
        };
    }

}
