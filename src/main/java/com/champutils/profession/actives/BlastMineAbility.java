package com.champutils.profession.actives;

import com.champutils.profession.ProfessionActiveDuration;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public class BlastMineAbility implements ProfessionActiveAbility {

    private static final int DEFAULT_SECONDS = 12;

    @Override
    public String id() {
        return "blast_mine";
    }

    @Override
    public boolean use(ServerPlayer player, ItemStack stack) {
        double seconds = getDurationSeconds(player, stack);
        String secondsText = ProfessionActiveDuration.formatSeconds(seconds);

        ActiveEffectManager.activateTimed(player, "blast_mine", "Blast Mine", seconds, stack);

        player.sendSystemMessage(Component.literal("§cBlast Mine active: §fYour pickaxe breaks a §e5x5 §farea for §e" + secondsText + "s§f."));
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§cBlast Mine active: 5x5 mining for " + secondsText + "s"), true);
        }
        ProfessionNotificationSettings.playSound(player, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.55F, 1.55F);
        return true;
    }

    private double getDurationSeconds(ServerPlayer player, ItemStack stack) {
        double rolledSeconds = ProfessionToolUtil.getStat(stack, "blastMineSeconds");
        double fallback = rolledSeconds <= 0.0D ? DEFAULT_SECONDS : rolledSeconds;
        return ActiveEffectManager.extendedActiveDurationSeconds(
                ProfessionActiveDuration.durationSeconds(player, stack, fallback, null)
        );
    }
}
