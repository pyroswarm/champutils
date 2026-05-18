package com.champutils.profession.actives;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public class ExcavationAbility implements ProfessionActiveAbility {

    private static final int DEFAULT_SECONDS =
            15;

    @Override
    public String id() {
        return "excavation";
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

        ActiveEffectManager.activateExcavation(
                player,
                seconds,
                stack
        );

        player.sendSystemMessage(
                Component.literal(
                        "§6Excavation active: §fYour pickaxe breaks a §e3x3 §farea for §e" +
                                seconds +
                                "s§f."
                )
        );

        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(
                    Component.literal(
                        "§6Excavation active: 3x3 mining for " +
                                seconds +
                                "s"
                ),
                    true
            );
        }

        ProfessionNotificationSettings.playSound(player, 
                SoundEvents.ANVIL_USE,
                SoundSource.PLAYERS,
                0.6F,
                1.35F
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
                        "excavationSeconds"
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
}
