package com.champutils.profession.actives;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public class MinersFocusAbility implements ProfessionActiveAbility {

    private static final int DEFAULT_SECONDS =
            20;

    private static final double DEFAULT_BOOST_PERCENT =
            50.0D;

    @Override
    public String id() {
        return "miners_focus";
    }

    @Override
    public boolean use(
            ServerPlayer player,
            ItemStack stack
    ) {

        int seconds =
                getDurationSeconds(
                        stack
                );

        double boostPercent =
                getBoostPercent(
                        stack
                );

        double multiplier =
                1.0D +
                        Math.max(
                                0.0D,
                                boostPercent
                        ) / 100.0D;

        ActiveEffectManager.activateTimedWithMultiplier(
                player,
                "miners_focus",
                "Miner's Focus",
                seconds,
                stack,
                multiplier
        );

        int roundedBoost =
                (int) Math.round(
                        boostPercent
                );

        player.sendSystemMessage(
                Component.literal(
                        "§6Miner's Focus active: §fMining passive chances are boosted by §e" +
                                roundedBoost +
                                "% §ffor §e" +
                                seconds +
                                "s§f."
                )
        );

        player.displayClientMessage(
                Component.literal(
                        "§6Miner's Focus: +" +
                                roundedBoost +
                                "% passive chance for " +
                                seconds +
                                "s"
                ),
                true
        );

        ProfessionNotificationSettings.playSound(player, 
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.8F,
                1.6F
        );

        return true;
    }

    private int getDurationSeconds(
            ItemStack stack
    ) {

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolUtil.getToolData(
                        stack
                );

        if (
                toolData != null &&
                        toolData.activeDurationSeconds > 0
        ) {
            return Math.max(
                    1,
                    toolData.activeDurationSeconds
            );
        }

        double rolledSeconds =
                ProfessionToolUtil.getStat(
                        stack,
                        "minersFocusSeconds"
                );

        if (rolledSeconds <= 0.0D) {
            return DEFAULT_SECONDS;
        }

        return Math.max(
                1,
                (int) Math.round(
                        rolledSeconds
                )
        );
    }

    private double getBoostPercent(
            ItemStack stack
    ) {

        double rolledBoost =
                ProfessionToolUtil.getStat(
                        stack,
                        "minersFocusBoost"
                );

        if (rolledBoost <= 0.0D) {
            return DEFAULT_BOOST_PERCENT;
        }

        return Math.min(
                300.0D,
                rolledBoost
        );
    }
}
