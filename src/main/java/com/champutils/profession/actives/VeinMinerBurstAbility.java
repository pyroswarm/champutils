package com.champutils.profession.actives;

import com.champutils.profession.ProfessionActiveDuration;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public class VeinMinerBurstAbility implements ProfessionActiveAbility {

    private static final int DEFAULT_SECONDS = 15;

    @Override
    public String id() {
        return "vein_miner_burst";
    }

    @Override
    public boolean use(ServerPlayer player, ItemStack stack) {
        double seconds = getDurationSeconds(player, stack);
        String secondsText = ProfessionActiveDuration.formatSeconds(seconds);

        ActiveEffectManager.activateTimed(player, "vein_miner_burst", "Vein Miner Burst", seconds, stack);

        player.sendSystemMessage(Component.literal("§6Vein Miner Burst active: §fConnected natural ore veins will chain mine for §e" + secondsText + "s§f."));
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§6Vein Miner Burst active: chain ore mining for " + secondsText + "s"), true);
        }

        ProfessionNotificationSettings.playSound(player, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.55F);
        return true;
    }

    private double getDurationSeconds(ServerPlayer player, ItemStack stack) {
        double rolledSeconds = ProfessionToolUtil.getStat(stack, "veinMinerSeconds");
        double fallback = rolledSeconds <= 0.0D ? DEFAULT_SECONDS : rolledSeconds;
        return ActiveEffectManager.extendedActiveDurationSeconds(
                ProfessionActiveDuration.durationSeconds(player, stack, fallback, null)
        );
    }
}
