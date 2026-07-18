package com.champutils.profession.actives;

import com.champutils.profession.ProfessionActiveDuration;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public class StonebreakerAbility implements ProfessionActiveAbility {

    private static final int DEFAULT_SECONDS = 12;

    @Override
    public String id() {
        return "stonebreaker";
    }

    @Override
    public boolean use(ServerPlayer player, ItemStack stack) {
        double seconds = getDurationSeconds(player, stack);
        String secondsText = ProfessionActiveDuration.formatSeconds(seconds);

        ActiveEffectManager.activateTimed(player, "stonebreaker", "Stonebreaker", seconds, stack);

        player.sendSystemMessage(Component.literal("§7Stonebreaker active: §fNatural stone blocks break in a §e3x3 §farea for §e" + secondsText + "s§f."));
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§7Stonebreaker active: 3x3 stone clearing for " + secondsText + "s"), true);
        }
        ProfessionNotificationSettings.playSound(player, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.65F, 0.9F);
        return true;
    }

    private double getDurationSeconds(ServerPlayer player, ItemStack stack) {
        double rolledSeconds = ProfessionToolUtil.getStat(stack, "stonebreakerSeconds");
        double fallback = rolledSeconds <= 0.0D ? DEFAULT_SECONDS : rolledSeconds;
        return ActiveEffectManager.extendedActiveDurationSeconds(
                ProfessionActiveDuration.durationSeconds(player, stack, fallback, null)
        );
    }
}
