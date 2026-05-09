package com.champutils.profession.passives;

import com.champutils.profession.ProfessionBlockTracker;
import com.champutils.profession.ProfessionConfig;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionSpecialCelebration;
import com.champutils.profession.ProfessionToolUtil;
import com.champutils.profession.actives.ActiveEffectManager;
import com.champutils.profession.ProfessionType;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

public class XpSurgePassive implements ProfessionPassive {

    public static final String STAT_ID = "xpSurgeChance";

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

        /*
         * Economy/progression guard: XP Surge must only trigger from natural
         * mining blocks. PassiveRegistry already checks this, but this keeps
         * the passive safe if it is ever called directly later.
         */
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
                        .miningXp
                        .get(
                                blockId
                        );

        if (
                baseXp == null ||
                        baseXp <= 0
        ) {
            return;
        }

        double chancePercent =
                ProfessionToolUtil.getStat(
                        stack,
                        STAT_ID
                );

        if (chancePercent <= 0.0D) {
            return;
        }

        double focusMultiplier =
                ActiveEffectManager.getMiningPassiveChanceMultiplier(
                        player,
                        stack
                );

        if (
                RANDOM.nextDouble() >=
                        Math.min(
                                100.0D,
                                chancePercent * focusMultiplier
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
                ProfessionType.MINING,
                bonusXp
        );

        player.displayClientMessage(
                Component.literal(
                        "§aXP Surge: §f+" +
                                bonusXp +
                                " bonus MINING XP!"
                ),
                true
        );

        ProfessionSpecialCelebration.celebrateSpecialActive(
                player,
                "§aXP Surge!",
                "§f+" + bonusXp + " bonus Mining XP"
        );
    }
}
