package com.champutils.profession.passives;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface ProfessionPassive {

    void apply(
            ServerPlayer player,
            ItemStack stack,
            ServerLevel level,
            BlockPos pos,
            String blockId
    );
}
