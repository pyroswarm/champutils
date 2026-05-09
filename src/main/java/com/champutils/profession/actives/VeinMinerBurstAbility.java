package com.champutils.profession.actives;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public class VeinMinerBurstAbility implements ProfessionActiveAbility {

    private static final int DEFAULT_SECONDS =
            20;

    @Override
    public String id() {
        return "vein_miner_burst";
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
                "vein_miner_burst",
                "Vein Miner Burst",
                seconds,
                stack
        );

        player.sendSystemMessage(
                Component.literal(
                        "§6Vein Miner Burst active: §fConnected natural ore veins will chain mine for §e" +
                                seconds +
                                "s§f."
                )
        );

        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(
                    Component.literal(
                        "§6Vein Miner Burst active: chain ore mining for " +
                                seconds +
                                "s"
                ),
                    true
            );
        }

        player.playNotifySound(
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.8F,
                1.25F
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
                        "veinMinerSeconds"
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
