package com.champutils.buff;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Shared registry for global buff metadata and hard safety caps.
 *
 * Provider-specific scaling lives in provider definitions, while this registry
 * owns the final maximum value after every active provider is added together.
 */
public final class BuffRegistry {
    private static final Map<BuffType, Double> HARD_CAPS = new EnumMap<>(BuffType.class);

    static {
        resetDefaults();
    }

    private BuffRegistry() {}

    public static synchronized void resetDefaults() {
        HARD_CAPS.clear();
        HARD_CAPS.put(BuffType.MINING_XP, 1.00D);
        HARD_CAPS.put(BuffType.FORESTRY_XP, 1.00D);
        HARD_CAPS.put(BuffType.FARMING_XP, 1.00D);
        HARD_CAPS.put(BuffType.BATTLING_XP, 1.00D);
        HARD_CAPS.put(BuffType.SHINY_CHANCE, 0.01D);
        HARD_CAPS.put(BuffType.PERFECT_IV_CHANCE, 0.01D);
        HARD_CAPS.put(BuffType.WORLD_EVENT_REWARDS, 1.00D);
        HARD_CAPS.put(BuffType.NPC_MONEY, 1.00D);
    }

    public static synchronized void setHardCap(BuffType type, double hardCap) {
        if (type == null) return;
        HARD_CAPS.put(type, Math.max(0.0D, hardCap));
    }

    public static double hardCap(BuffType type) {
        if (type == null) return 0.0D;
        return HARD_CAPS.getOrDefault(type, 0.0D);
    }

    public static Map<BuffType, Double> hardCaps() {
        return Collections.unmodifiableMap(HARD_CAPS);
    }
}
