package com.champutils.profession.actives;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ReplantToggleAbility implements ProfessionActiveAbility {

    @Override
    public String id() {
        return "replant_toggle";
    }

    @Override
    public boolean use(
            ServerPlayer player,
            ItemStack stack
    ) {

        ActiveEffectManager.toggleEffect(
                player,
                "forestry_replant",
                "Replant",
                stack
        );

        return true;
    }
}
