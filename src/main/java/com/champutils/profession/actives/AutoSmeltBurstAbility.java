package com.champutils.profession.actives;

import com.champutils.profession.ProfessionActiveDuration;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public class AutoSmeltBurstAbility implements ProfessionActiveAbility {

    private static final int DEFAULT_SECONDS = 30;

    @Override
    public String id() {
        return "auto_smelt_burst";
    }

    @Override
    public boolean use(ServerPlayer player, ItemStack stack) {
        double seconds = getDurationSeconds(player, stack);
        String secondsText = ProfessionActiveDuration.formatSeconds(seconds);

        ActiveEffectManager.activateTimed(player, "auto_smelt", "Molten Touch", seconds, stack);

        player.sendSystemMessage(Component.literal("§6Molten Touch active: §fOre drops are smelted for §e" + secondsText + "s§f."));
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§6Molten Touch active: auto-smelt for " + secondsText + "s"), true);
        }

        ProfessionNotificationSettings.playSound(player, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.2F);
        return true;
    }

    private double getDurationSeconds(ServerPlayer player, ItemStack stack) {
        double rolledSeconds = ProfessionToolUtil.getStat(stack, "autoSmeltSeconds");
        double fallback = rolledSeconds <= 0.0D ? DEFAULT_SECONDS : rolledSeconds;
        return ActiveEffectManager.extendedActiveDurationSeconds(
                ProfessionActiveDuration.durationSeconds(player, stack, fallback, null)
        );
    }
}
