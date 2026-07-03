package com.champutils.profession.actives;

import com.champutils.profession.ProfessionActiveDuration;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionType;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public abstract class TimedBuffAbility implements ProfessionActiveAbility {

    protected abstract String effectId();
    protected abstract String displayName();
    protected abstract int defaultSeconds();
    protected abstract String message(String secondsText);

    /**
     * Timed abilities can opt into profession-level duration scaling.
     * The configured base duration is always used first, then this adds
     * activeDurationSecondsPerLevel * (profession level - 1).
     */
    protected ProfessionType durationScalingProfession() {
        return null;
    }

    @Override
    public boolean use(ServerPlayer player, ItemStack stack) {
        double seconds = getDurationSeconds(player, stack);
        ActiveEffectManager.activateTimed(player, effectId(), displayName(), seconds, stack);
        Component message = Component.literal(message(ProfessionActiveDuration.formatSeconds(seconds)));
        player.sendSystemMessage(message);
        player.displayClientMessage(message, true);
        ProfessionNotificationSettings.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 1.35F);
        return true;
    }

    protected double getDurationSeconds(ServerPlayer player, ItemStack stack) {
        return ActiveEffectManager.extendedActiveDurationSeconds(
                ProfessionActiveDuration.durationSeconds(player, stack, defaultSeconds(), durationScalingProfession())
        );
    }
}
