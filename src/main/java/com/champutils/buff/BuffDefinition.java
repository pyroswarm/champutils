package com.champutils.buff;

/**
 * Scaling/cap definition for a buff value produced by one provider.
 *
 * Values are decimals: 0.01 = +1%, 0.0025 = +0.25%.
 */
public final class BuffDefinition {
    public final BuffType type;
    public final boolean enabled;
    public final int unlockLevel;
    public final int maxLevel;
    public final double startingBonus;
    public final double perLevelBonus;
    public final double maxBonus;
    public final boolean scaled;

    private BuffDefinition(
            BuffType type,
            boolean enabled,
            int unlockLevel,
            int maxLevel,
            double startingBonus,
            double perLevelBonus,
            double maxBonus,
            boolean scaled
    ) {
        this.type = type;
        this.enabled = enabled;
        this.unlockLevel = Math.max(1, unlockLevel);
        this.maxLevel = Math.max(this.unlockLevel, maxLevel);
        this.startingBonus = Math.max(0.0D, startingBonus);
        this.perLevelBonus = Math.max(0.0D, perLevelBonus);
        this.maxBonus = Math.max(this.startingBonus, maxBonus);
        this.scaled = scaled;
    }

    public static BuffDefinition perLevel(BuffType type, boolean enabled, int unlockLevel, double perLevelBonus, double maxBonus) {
        return new BuffDefinition(type, enabled, unlockLevel, unlockLevel, 0.0D, perLevelBonus, maxBonus, false);
    }

    public static BuffDefinition scaled(BuffType type, boolean enabled, int unlockLevel, int maxLevel, double startingBonus, double maxBonus) {
        return new BuffDefinition(type, enabled, unlockLevel, maxLevel, startingBonus, 0.0D, maxBonus, true);
    }

    public double valueAt(int level) {
        if (!enabled || level < unlockLevel) return 0.0D;
        if (scaled) {
            if (maxLevel <= unlockLevel) return maxBonus;
            double progress = (double) (Math.min(level, maxLevel) - unlockLevel) / (double) (maxLevel - unlockLevel);
            double value = startingBonus + ((maxBonus - startingBonus) * progress);
            return Math.max(0.0D, Math.min(maxBonus, value));
        }

        int effectiveLevels = Math.max(0, level - unlockLevel + 1);
        return Math.max(0.0D, Math.min(maxBonus, effectiveLevels * perLevelBonus));
    }

    public double unlockDisplayValue() {
        return scaled ? startingBonus : perLevelBonus;
    }
}
