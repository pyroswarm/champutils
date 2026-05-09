package com.champutils.profession.actives;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class AutoSmeltToggleAbility implements ProfessionActiveAbility {

    @Override
    public String id() {
        return "auto_smelt_toggle";
    }

    @Override
    public boolean use(
            ServerPlayer player,
            ItemStack stack
    ) {

        ActiveEffectManager.toggleEffect(
                player,
                "auto_smelt",
                "Infernal Core Auto-Smelt",
                stack
        );

        return true;
    }
}
