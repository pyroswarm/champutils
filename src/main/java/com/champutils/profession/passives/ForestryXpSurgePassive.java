package com.champutils.profession.passives;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.ProfessionBlockTracker;
import com.champutils.profession.ProfessionConfig;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionToolUtil;
import com.champutils.profession.ProfessionType;
import com.champutils.profession.actives.ActiveEffectManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

public class ForestryXpSurgePassive implements ProfessionPassive {

    private static final Random RANDOM =
            new Random();

    @Override
    public void apply(
            ServerPlayer player,
            ItemStack stack,
            ServerLevel level,
            BlockPos pos,
            String blockId
    ) {

        if (
                player == null ||
                        stack == null ||
                        stack.isEmpty() ||
                        level == null ||
                        pos == null ||
                        blockId == null ||
                        blockId.isBlank()
        ) {
            return;
        }

        if (
                ProfessionBlockTracker.isPlayerPlaced(
                        level,
                        pos
                )
        ) {
            return;
        }

        Integer baseXp =
                ProfessionConfig
                        .SETTINGS
                        .forestryXp
                        .get(
                                blockId
                        );

        if (baseXp == null || baseXp <= 0) {
            return;
        }

        double chancePercent =
                ProfessionToolUtil.getStat(
                        stack,
                        "forestryXpSurgeChance"
                );

        if (chancePercent <= 0.0D) {
            chancePercent =
                    ProfessionToolUtil.getStat(
                            stack,
                            "xpSurgeChance"
                    );
        }

        if (chancePercent <= 0.0D) {
            return;
        }

        double multiplier =
                ActiveEffectManager.getForestryPassiveChanceMultiplier(
                        player,
                        stack
                );

        if (
                RANDOM.nextDouble() >=
                        Math.min(
                                100.0D,
                                chancePercent * multiplier
                        ) / 100.0D
        ) {
            return;
        }

        int bonusXp =
                Math.max(
                        1,
                        baseXp
                );

        ProfessionManager.addXp(
                player,
                ProfessionType.FORESTRY,
                bonusXp
        );

        player.displayClientMessage(
                Component.literal(
                        "§aXP Surge: §f+" +
                                bonusXp +
                                " bonus FORESTRY XP!"
                ),
                true
        );


        ProfessionNotificationSettings.playSound(player, 
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.35F,
                1.35F
        );
    }
}
