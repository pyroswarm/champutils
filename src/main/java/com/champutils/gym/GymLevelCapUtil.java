package com.champutils.gym;

import com.champutils.badge.BadgeManager;
import com.champutils.badge.BadgeType;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Single source of truth for progression-based wild Pokemon level caps.
 *
 * Wild Pokemon should never appear above the player's current progression cap:
 * - before Cascade: Cascade cap
 * - after each gym: next unbeaten gym cap
 * - after gym 8: 100
 */
public final class GymLevelCapUtil {
    private GymLevelCapUtil() {}

    public static int currentWildCap(ServerPlayer player) {
        if (player == null) return 0;
        try {
            Set<BadgeType> earned = BadgeManager.getBadges(player);
            List<GymStep> gyms = configuredGymSteps();
            if (gyms.isEmpty()) return 100;

            for (GymStep step : gyms) {
                if (!earned.contains(step.badge)) {
                    return clampLevel(step.levelCap);
                }
            }

            return 100;
        } catch (Throwable ignored) {
            return 30;
        }
    }

    private static List<GymStep> configuredGymSteps() {
        List<GymStep> gyms = new ArrayList<>();
        for (BadgeType badge : BadgeType.values()) {
            if (!isMainGymBadge(badge)) continue;
            GymConfig.GymDefinition gym = GymConfig.getGym(badge);
            if (gym == null || gym.levelCap <= 0) continue;
            gyms.add(new GymStep(badge, clampLevel(gym.levelCap)));
        }
        gyms.sort(Comparator.comparingInt((GymStep step) -> step.levelCap).thenComparingInt(step -> mainGymOrder(step.badge)));
        return gyms;
    }

    private static boolean isMainGymBadge(BadgeType badge) {
        return switch (badge) {
            case CASCADE, MARSH, EARTH, BOULDER, THUNDER, RAINBOW, SOUL, VOLCANO -> true;
            default -> false;
        };
    }

    private static int mainGymOrder(BadgeType badge) {
        return switch (badge) {
            case CASCADE -> 1;
            case MARSH -> 2;
            case EARTH -> 3;
            case BOULDER -> 4;
            case THUNDER -> 5;
            case RAINBOW -> 6;
            case SOUL -> 7;
            case VOLCANO -> 8;
            default -> 999;
        };
    }

    private static int clampLevel(int level) {
        return Math.max(1, Math.min(100, level));
    }

    private record GymStep(BadgeType badge, int levelCap) {}
}
