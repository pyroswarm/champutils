package com.champutils.profession;

import com.champutils.buff.BuffContext;
import com.champutils.buff.BuffManager;
import com.champutils.buff.BuffType;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

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

    private static final Map<String, Long> LAST_ACTION_BAR_NOTICE = new ConcurrentHashMap<>();
    private static final long ACTION_BAR_NOTICE_COOLDOWN_MILLIS = 3000L;

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

        double totalBonus = getTotalBonus(player, profession) + ProfessionTrinketManager.professionXpGemBonus(player);
        int boosted = baseAmount;
        int wholeBonus = 0;

        if (totalBonus > 0.0D) {
            String bankKey = bankKey(player, profession);
            double stored = storedBonus(player, profession, bankKey);
            double rawBonus = (baseAmount * totalBonus) + stored;
            wholeBonus = (int) Math.floor(rawBonus);
            double remainder = rawBonus - wholeBonus;

            if (remainder > 0.0D) {
                storeBonus(player, profession, bankKey, remainder);
            } else {
                clearStoredBonus(player, profession, bankKey);
            }

            boosted += Math.max(0, wholeBonus);
        }

        // Profession bonus notifications must never spam chat. Show a throttled action-bar summary instead.
        int actionBarBonus = Math.max(0, wholeBonus);
        if (actionBarBonus > 0 && shouldNotifyActionBar(player, profession)) {
            player.displayClientMessage(Component.literal("+" + actionBarBonus + " bonus " + profession.name() + " XP").withStyle(ChatFormatting.GREEN), true);
        }

        return boosted;
    }

    private static boolean shouldNotifyActionBar(ServerPlayer player, ProfessionType profession) {
        if (player == null || profession == null) return false;
        String key = player.getUUID() + ":" + profession.name();
        long now = System.currentTimeMillis();
        Long last = LAST_ACTION_BAR_NOTICE.get(key);
        if (last != null && now - last < ACTION_BAR_NOTICE_COOLDOWN_MILLIS) return false;
        LAST_ACTION_BAR_NOTICE.put(key, now);
        return true;
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

    private static String bankKey(ServerPlayer player, ProfessionType profession) {
        java.util.UUID profileId = com.champutils.profile.PlayerProfileManager.activeProfileIdOrNull(player.getUUID());
        java.util.UUID id = profileId == null ? player.getUUID() : profileId;
        return id + ":" + profession.name();
    }

    private static double storedBonus(ServerPlayer player, ProfessionType profession, String fallbackKey) {
        try {
            ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
            if (data.xpBonusBank != null) {
                return Math.max(0.0D, data.xpBonusBank.getOrDefault(profession.name(), 0.0D));
            }
        } catch (Exception ignored) {
        }
        return Math.max(0.0D, FRACTION_BANK.getOrDefault(fallbackKey, 0.0D));
    }

    private static void storeBonus(ServerPlayer player, ProfessionType profession, String fallbackKey, double amount) {
        double safe = Math.max(0.0D, Math.min(0.9999D, amount));
        try {
            ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
            if (data.xpBonusBank != null) {
                data.xpBonusBank.put(profession.name(), safe);
                return;
            }
        } catch (Exception ignored) {
        }
        FRACTION_BANK.put(fallbackKey, safe);
    }

    private static void clearStoredBonus(ServerPlayer player, ProfessionType profession, String fallbackKey) {
        try {
            ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
            if (data.xpBonusBank != null) {
                data.xpBonusBank.remove(profession.name());
            }
        } catch (Exception ignored) {
        }
        FRACTION_BANK.remove(fallbackKey);
    }

    private static String formatPercent(double value) {
        double percent = value * 100.0D;
        return String.format(Locale.US, "%.2f%%", percent)
                .replaceAll("0+%$", "%")
                .replace(".%", "%");
    }
}
