package com.champutils.profession.actives;

import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public class StonebreakerAbility implements ProfessionActiveAbility {

    private static final int DEFAULT_SECONDS = 25;

    @Override
    public String id() {
        return "stonebreaker";
    }

    @Override
    public boolean use(ServerPlayer player, ItemStack stack) {
        int seconds = getDurationSeconds(stack);

        ActiveEffectManager.activateTimed(player, "stonebreaker", "Stonebreaker", seconds, stack);

        player.sendSystemMessage(Component.literal("§7Stonebreaker active: §fNatural stone blocks break in a §e5x5 §farea for §e" + seconds + "s§f."));
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§7Stonebreaker active: 5x5 stone clearing for " + seconds + "s"), true);
        }
        player.playNotifySound(SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 0.9F, 1.75F);
        return true;
    }

    private int getDurationSeconds(ItemStack stack) {
        ProfessionToolConfig.ToolData toolData = ProfessionToolUtil.getToolData(stack);
        if (toolData != null && toolData.activeDurationSeconds > 0) {
            return Math.max(1, toolData.activeDurationSeconds);
        }

        double rolledSeconds = ProfessionToolUtil.getStat(stack, "stonebreakerSeconds");
        if (rolledSeconds <= 0.0D) {
            return DEFAULT_SECONDS;
        }
        return Math.max(1, (int) Math.round(rolledSeconds));
    }
}
