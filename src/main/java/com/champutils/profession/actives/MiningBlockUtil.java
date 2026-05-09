package com.champutils.profession.actives;

import com.champutils.profession.ProfessionConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class MiningBlockUtil {

    private MiningBlockUtil() {
    }

    public static boolean isPickaxeBlock(
            Level level,
            BlockPos pos,
            BlockState state
    ) {

        if (state == null || state.isAir()) {
            return false;
        }

        if (state.getDestroySpeed(
                level,
                pos
        ) < 0.0F) {
            return false;
        }

        if (state.hasBlockEntity()) {
            return false;
        }

        if (state.is(
                BlockTags.MINEABLE_WITH_PICKAXE
        )) {
            return true;
        }

        String blockId =
                state.getBlock()
                        .builtInRegistryHolder()
                        .key()
                        .location()
                        .toString();

        return ProfessionConfig.SETTINGS != null &&
                ProfessionConfig.SETTINGS.miningXp != null &&
                ProfessionConfig.SETTINGS.miningXp.containsKey(
                        blockId
                );
    }
}
