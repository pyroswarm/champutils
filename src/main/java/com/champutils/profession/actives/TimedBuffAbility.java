package com.champutils.profession.actives;

import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public abstract class TimedBuffAbility implements ProfessionActiveAbility {

    protected abstract String effectId();
    protected abstract String displayName();
    protected abstract int defaultSeconds();
    protected abstract String message(int seconds);

    @Override
    public boolean use(ServerPlayer player, ItemStack stack) {
        int seconds = getDurationSeconds(stack);
        ActiveEffectManager.activateTimed(player, effectId(), displayName(), seconds, stack);
        Component message = Component.literal(message(seconds));
        player.sendSystemMessage(message);
        player.displayClientMessage(message, true);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 1.35F);
        return true;
    }

    protected int getDurationSeconds(ItemStack stack) {
        ProfessionToolConfig.ToolData data = ProfessionToolUtil.getToolData(stack);
        if (data != null && data.activeDurationSeconds > 0) return Math.max(1, data.activeDurationSeconds);
        return defaultSeconds();
    }
}
