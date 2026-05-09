package com.champutils.profession.actives;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class OreMagnetToggleAbility implements ProfessionActiveAbility {

    @Override
    public String id() {
        return "ore_magnet_toggle";
    }

    @Override
    public boolean use(
            ServerPlayer player,
            ItemStack stack
    ) {

        ActiveEffectManager.toggleEffect(
                player,
                "ore_magnet",
                "Ore Magnet"
        );

        return true;
    }
}
