package com.champutils.profession;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Centralized duration math for profession active abilities.
 * Keeps tooltips, cooldown padding, and actual effect timers in sync.
 */
public final class ProfessionActiveDuration {

    public static final double DEFAULT_BASE_SECONDS = 10.0D;
    public static final double DEFAULT_SECONDS_PER_LEVEL = 0.1D;

    private ProfessionActiveDuration() {
    }

    public static double durationSeconds(ServerPlayer player, ItemStack stack, double fallbackSeconds, ProfessionType fallbackProfession) {
        return durationSeconds(player, ProfessionToolUtil.getToolData(stack), fallbackSeconds, fallbackProfession);
    }

    public static double durationSeconds(ServerPlayer player, ProfessionToolConfig.ToolData data, double fallbackSeconds, ProfessionType fallbackProfession) {
        double baseSeconds = data != null && data.activeDurationSeconds > 0
                ? data.activeDurationSeconds
                : Math.max(1.0D, fallbackSeconds);

        double perLevelSeconds = data == null
                ? 0.0D
                : Math.max(0.0D, data.activeDurationSecondsPerLevel);

        ProfessionType profession = professionFromData(data);
        if (profession == null) {
            profession = fallbackProfession;
        }

        if (player != null && profession != null && perLevelSeconds > 0.0D) {
            int level = Math.max(1, ProfessionManager.getBenefitLevel(player, profession));
            baseSeconds += Math.max(0, level - 1) * perLevelSeconds;
        }

        return Math.max(1.0D, baseSeconds);
    }

    public static String formatSeconds(double seconds) {
        double rounded = Math.round(seconds * 10.0D) / 10.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001D) {
            return String.valueOf((int) Math.rint(rounded));
        }
        return String.format(Locale.US, "%.1f", rounded);
    }

    public static int cooldownPaddingSeconds(ServerPlayer player, ProfessionToolConfig.ToolData data) {
        double duration = durationSeconds(player, data, 0.0D, professionFromData(data));
        return (int) Math.ceil(duration);
    }

    public static ProfessionType professionFromData(ProfessionToolConfig.ToolData data) {
        if (data == null || data.profession == null || data.profession.isBlank()) {
            return null;
        }

        try {
            return ProfessionType.valueOf(data.profession.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }
}
