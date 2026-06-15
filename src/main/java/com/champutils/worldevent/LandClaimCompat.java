package com.champutils.worldevent;

import com.champutils.claims.LandClaimRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Prevents world events from spawning inside or directly beside player land claims. */
public final class LandClaimCompat {
    private LandClaimCompat() {}

    public static boolean isAreaUnclaimed(ServerLevel level, BlockPos center, int radius) {
        if (level == null || center == null) return true;
        int safeRadius = Math.max(0, radius);
        return !LandClaimRepository.overlapsCached(
                level,
                center.getX() - safeRadius,
                center.getX() + safeRadius,
                center.getZ() - safeRadius,
                center.getZ() + safeRadius
        );
    }
}
