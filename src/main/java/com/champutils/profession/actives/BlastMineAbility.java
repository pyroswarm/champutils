package com.champutils.profession.actives;

import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionToolConfig;
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
        int seconds = getDurationSeconds(stack);

        ActiveEffectManager.activateTimed(player, "blast_mine", "Blast Mine", seconds, stack);

        player.sendSystemMessage(Component.literal("§cBlast Mine active: §fYour pickaxe breaks a §e5x5 §farea for §e" + seconds + "s§f."));
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§cBlast Mine active: 5x5 mining for " + seconds + "s"), true);
        }
        player.playNotifySound(SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.55F, 1.55F);
        return true;
    }

    private int getDurationSeconds(ItemStack stack) {
        ProfessionToolConfig.ToolData toolData = ProfessionToolUtil.getToolData(stack);
        if (toolData != null && toolData.activeDurationSeconds > 0) {
            return Math.max(1, toolData.activeDurationSeconds);
        }

        double rolledSeconds = ProfessionToolUtil.getStat(stack, "blastMineSeconds");
        if (rolledSeconds <= 0.0D) {
            return DEFAULT_SECONDS;
        }
        return Math.max(1, (int) Math.round(rolledSeconds));
    }
}
