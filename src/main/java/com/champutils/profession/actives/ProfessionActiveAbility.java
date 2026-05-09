package com.champutils.profession.actives;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface ProfessionActiveAbility {

    String id();

    boolean use(
            ServerPlayer player,
            ItemStack stack
    );
}
