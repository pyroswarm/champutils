package com.champutils.profession.actives;

import com.champutils.profession.ProfessionActiveDuration;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public class ExcavationAbility implements ProfessionActiveAbility {

    private static final int DEFAULT_SECONDS = 15;

    @Override
    public String id() {
        return "excavation";
    }

    @Override
    public boolean use(ServerPlayer player, ItemStack stack) {
        double seconds = getDurationSeconds(player, stack);
        String secondsText = ProfessionActiveDuration.formatSeconds(seconds);

        ActiveEffectManager.activateExcavation(player, seconds, stack);

        player.sendSystemMessage(Component.literal("§6Excavation active: §fYour tool breaks a §e3x3 §farea for §e" + secondsText + "s§f."));
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§6Excavation active: 3x3 excavation for " + secondsText + "s"), true);
        }

        ProfessionNotificationSettings.playSound(player, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.35F);
        return true;
    }

    private double getDurationSeconds(ServerPlayer player, ItemStack stack) {
        double rolledSeconds = ProfessionToolUtil.getStat(stack, "excavationSeconds");
        double fallback = rolledSeconds <= 0.0D ? DEFAULT_SECONDS : rolledSeconds;
        return ActiveEffectManager.extendedActiveDurationSeconds(
                ProfessionActiveDuration.durationSeconds(player, stack, fallback, null)
        );
    }
}
