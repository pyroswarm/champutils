package com.champutils.profession.actives;

import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionType;

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
        int seconds = getDurationSeconds(player, stack);
        ActiveEffectManager.activateTimed(player, effectId(), displayName(), seconds, stack);
        Component message = Component.literal(message(seconds));
        player.sendSystemMessage(message);
        player.displayClientMessage(message, true);
        ProfessionNotificationSettings.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 1.35F);
        return true;
    }

    protected int getDurationSeconds(ServerPlayer player, ItemStack stack) {
        ProfessionToolConfig.ToolData data = ProfessionToolUtil.getToolData(stack);
        int baseSeconds = data != null && data.activeDurationSeconds > 0
                ? data.activeDurationSeconds
                : defaultSeconds();

        int perLevelSeconds = data == null ? 0 : Math.max(0, data.activeDurationSecondsPerLevel);
        ProfessionType profession = durationScalingProfession();
        if (player != null && profession != null && perLevelSeconds > 0) {
            int level = Math.max(1, ProfessionManager.getLevel(player, profession));
            baseSeconds += perLevelSeconds * Math.max(0, level - 1);
        }

        return Math.max(1, baseSeconds);
    }
}
