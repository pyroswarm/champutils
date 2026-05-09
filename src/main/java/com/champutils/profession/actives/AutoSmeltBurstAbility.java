package com.champutils.profession.actives;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public class AutoSmeltBurstAbility implements ProfessionActiveAbility {

    private static final int DEFAULT_SECONDS =
            30;

    @Override
    public String id() {
        return "auto_smelt_burst";
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

        ActiveEffectManager.activateTimed(
                player,
                "auto_smelt",
                "Molten Touch",
                seconds,
                stack
        );

        player.sendSystemMessage(
                Component.literal(
                        "§6Molten Touch active: §fOre drops are smelted for §e" +
                                seconds +
                                "s§f."
                )
        );

        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(
                    Component.literal(
                        "§6Molten Touch active: auto-smelt for " + seconds + "s"
                ),
                    true
            );
        }

        player.playNotifySound(
                SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS,
                0.7F,
                1.2F
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

        if (toolData != null && toolData.activeDurationSeconds > 0) {
            return Math.max(
                    1,
                    toolData.activeDurationSeconds
            );
        }

        double rolledSeconds =
                ProfessionToolUtil.getStat(
                        stack,
                        "autoSmeltSeconds"
                );

        if (rolledSeconds <= 0.0D) {
            return DEFAULT_SECONDS;
        }

        return Math.max(
                1,
                (int) Math.round(rolledSeconds)
        );
    }
}
