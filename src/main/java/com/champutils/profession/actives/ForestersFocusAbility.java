package com.champutils.profession.actives;

import com.champutils.profession.ProfessionSpecialCelebration;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ForestersFocusAbility implements ProfessionActiveAbility {

    @Override
    public String id() {
        return "foresters_focus";
    }

    @Override
    public boolean use(
            ServerPlayer player,
            ItemStack stack
    ) {

        ProfessionToolConfig.ToolData data =
                ProfessionToolUtil.getToolData(
                        stack
                );

        int duration =
                data == null || data.activeDurationSeconds <= 0
                        ? 20
                        : data.activeDurationSeconds;

        double boostPercent =
                ProfessionToolUtil.getStat(
                        stack,
                        "forestersFocusBoost"
                );

        if (boostPercent <= 0.0D) {
            boostPercent = 75.0D;
        }

        double multiplier =
                1.0D +
                        Math.max(
                                0.0D,
                                boostPercent
                        ) / 100.0D;

        ActiveEffectManager.activateTimedWithMultiplier(
                player,
                "forestry_focus",
                "Forester's Focus",
                duration,
                stack,
                multiplier
        );

        ProfessionSpecialCelebration.celebrateSpecialActive(
                player,
                "§2Forester's Focus!",
                "§fForestry passive chances boosted"
        );

        return true;
    }
}
