package com.champutils.profession.passives;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.ProfessionBlockTracker;
import com.champutils.profession.ProfessionToolUtil;
import com.champutils.profession.actives.ActiveEffectManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Random;

public class ForestryDropMultiplierPassive implements ProfessionPassive {

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

        if (
                ProfessionBlockTracker.isPlayerPlaced(
                        level,
                        pos
                )
        ) {
            return;
        }

        int multiplier =
                rollForestryDropMultiplier(
                        player,
                        stack
                );

        if (multiplier <= 1) {
            return;
        }

        Item item;

        try {
            item =
                    BuiltInRegistries.ITEM.get(
                            ResourceLocation.parse(
                                    blockId
                            )
                    );
        } catch (Exception ignored) {
            return;
        }

        if (item == null || item == Items.AIR) {
            return;
        }

        ItemStack reward =
                new ItemStack(
                        item,
                        multiplier - 1
                );

        if (!player.getInventory().add(reward)) {
            player.drop(
                    reward,
                    false
            );
        }

        player.displayClientMessage(
                Component.literal(
                        "§2Forestry passive: §f" +
                                multiplier +
                                "x log drops!"
                ),
                true
        );


        ProfessionNotificationSettings.playSound(player, 
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.45F,
                1.4F
        );
    }

    private int rollForestryDropMultiplier(
            ServerPlayer player,
            ItemStack stack
    ) {

        if (
                rollChance(
                        player,
                        stack,
                        "tripleChopChance"
                )
        ) {
            return 3;
        }

        if (
                rollChance(
                        player,
                        stack,
                        "doubleChopChance"
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

        double multiplier =
                ActiveEffectManager.getForestryPassiveChanceMultiplier(
                        player,
                        stack
                );

        return RANDOM.nextDouble() <
                Math.min(
                        100.0D,
                        chancePercent * multiplier
                ) / 100.0D;
    }
}
