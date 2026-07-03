package com.champutils.spawn;

import com.champutils.profile.IslanderMineManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;

/**
 * Centralized hard-blocks for world-event style spawns.
 * Applies to special wild spawns, mega bosses, and roaming trainers so roof/mine rules stay consistent.
 */
public final class SpawnBlockRules {
    private SpawnBlockRules() {}

    public static boolean isBlockedSpawnLevel(ServerLevel level) {
        if (level == null) return true;
        return IslanderMineManager.isMineWorld(level);
    }

    public static boolean isBlockedSpawnPosition(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return true;
        if (IslanderMineManager.isMineWorld(level)) return true;
        return isNetherRoof(level, pos);
    }

    public static boolean isNetherRoof(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String id = level.dimension().location().toString().toLowerCase(Locale.ROOT);
        return isSurvivalNetherDimension(id) && pos.getY() >= 127;
    }

    public static boolean isSurvivalNetherDimension(ServerLevel level) {
        if (level == null) return false;
        return isSurvivalNetherDimension(level.dimension().location().toString().toLowerCase(Locale.ROOT));
    }

    public static boolean isSurvivalNetherDimension(String id) {
        if (id == null) return false;
        String normalized = id.toLowerCase(Locale.ROOT);
        String path = normalized;
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        return normalized.equals("minecraft:the_nether")
                || normalized.equals("the_nether")
                || path.equals("the_nether")
                || path.equals("survival_nether")
                || path.startsWith("survival_nether_")
                || normalized.contains(":survival_nether")
                || normalized.contains("/survival_nether");
    }
}
