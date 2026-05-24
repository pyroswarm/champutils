package com.champutils.profession;

import com.champutils.buff.BuffContext;
import com.champutils.buff.BuffManager;
import com.champutils.buff.BuffType;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central profession XP boost pipeline.
 *
 * Future systems should register BuffProvider instances with BuffManager instead of modifying ProfessionManager.
 * Examples: weekend events, rank perks, consumable boosters, cosmetics, server-wide boosts, etc.
 */
public final class ProfessionXpBoostManager {

    /**
     * Safety cap for all profession XP boosts combined.
     * 1.00D = +100% XP maximum after all boost sources are added together.
     */
    private static final double MAX_TOTAL_BONUS = 1.00D;

    private static final List<ProfessionXpBoostSource> SOURCES = new CopyOnWriteArrayList<>();
    private static final Map<String, Double> FRACTION_BANK = new ConcurrentHashMap<>();

    static {
        // Additional temporary profession boosters should now register as BuffProvider
        // instances with BuffManager. This class remains responsible for fractional XP banking.
    }

    private ProfessionXpBoostManager() {}

    public interface ProfessionXpBoostSource {
        String id();

        /**
         * Lower values are evaluated first. This is mostly for predictable debug output.
         */
        default int priority() {
            return 1000;
        }

        /**
         * Return decimal bonus. Example: 0.01D = +1% XP.
         */
        double getBonus(ServerPlayer player, ProfessionType profession);
    }

    public static void registerSource(ProfessionXpBoostSource source) {
        if (source == null || source.id() == null || source.id().isBlank()) {
            return;
        }

        SOURCES.removeIf(existing -> existing.id().equalsIgnoreCase(source.id()));
        SOURCES.add(source);
        SOURCES.sort(Comparator
                .comparingInt(ProfessionXpBoostSource::priority)
                .thenComparing(sourceEntry -> sourceEntry.id().toLowerCase(Locale.ROOT))
        );
    }

    public static void unregisterSource(String id) {
        if (id == null || id.isBlank()) {
            return;
        }

        SOURCES.removeIf(existing -> existing.id().equalsIgnoreCase(id));
    }

    public static int applyBoosts(ServerPlayer player, ProfessionType profession, int baseAmount) {
        if (player == null || profession == null || baseAmount <= 0) {
            return baseAmount;
        }

        double totalBonus = getTotalBonus(player, profession);
        if (totalBonus <= 0.0D) {
            return baseAmount;
        }

        String bankKey = player.getUUID() + ":" + profession.name();
        double rawBonus = (baseAmount * totalBonus) + FRACTION_BANK.getOrDefault(bankKey, 0.0D);
        int wholeBonus = (int) Math.floor(rawBonus);
        double remainder = rawBonus - wholeBonus;

        if (remainder > 0.0D) {
            FRACTION_BANK.put(bankKey, remainder);
        } else {
            FRACTION_BANK.remove(bankKey);
        }

        return baseAmount + Math.max(0, wholeBonus);
    }

    public static double getTotalBonus(ServerPlayer player, ProfessionType profession) {
        if (player == null || profession == null) {
            return 0.0D;
        }

        BuffType type = BuffType.fromProfession(profession);
        if (type == null) {
            return 0.0D;
        }

        double total = BuffManager.getTotalBuff(BuffContext.professionXp(player, profession), type);

        // Legacy local sources are still supported for safety, but new systems should
        // prefer BuffManager.registerProvider(...).
        for (ProfessionXpBoostSource source : SOURCES) {
            try {
                total += Math.max(0.0D, source.getBonus(player, profession));
            } catch (Exception exception) {
                System.err.println("[ChampUtils] Profession XP boost source failed: " + source.id());
                exception.printStackTrace();
            }
        }

        return Math.max(0.0D, Math.min(MAX_TOTAL_BONUS, total));
    }

    public static List<String> debugBreakdown(ServerPlayer player, ProfessionType profession) {
        List<String> lines = new ArrayList<>();
        if (player == null || profession == null) {
            return lines;
        }

        BuffType type = BuffType.fromProfession(profession);
        if (type != null) {
            lines.addAll(BuffManager.debugBreakdown(BuffContext.professionXp(player, profession), type));
        }

        for (ProfessionXpBoostSource source : SOURCES) {
            double bonus = 0.0D;
            try {
                bonus = Math.max(0.0D, source.getBonus(player, profession));
            } catch (Exception ignored) {
                // Keep debug output safe even if a future source breaks.
            }

            if (bonus > 0.0D) {
                lines.add("legacy:" + source.id() + ": +" + formatPercent(bonus));
            }
        }

        double total = getTotalBonus(player, profession);
        if (total > 0.0D) {
            lines.add("total: +" + formatPercent(total));
        }

        return lines;
    }

    public static void clearFractionBank(ServerPlayer player) {
        if (player == null) {
            return;
        }

        clearFractionBank(player.getUUID());
    }

    public static void clearFractionBank(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }

        String prefix = playerUuid + ":";
        FRACTION_BANK.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String formatPercent(double value) {
        double percent = value * 100.0D;
        return String.format(Locale.US, "%.2f%%", percent)
                .replaceAll("0+%$", "%")
                .replace(".%", "%");
    }
}
