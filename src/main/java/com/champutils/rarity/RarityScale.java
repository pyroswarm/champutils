package com.champutils.rarity;

import net.minecraft.ChatFormatting;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Canonical server rarity/rank scale.
 *
 * Backend storage/config IDs are now F/E/D/C/B/A/S. Older words are accepted only
 * as one-time migration inputs and must be normalized before saving or awarding.
 */
public final class RarityScale {
    public static final String F = "F";
    public static final String E = "E";
    public static final String D = "D";
    public static final String C = "C";
    public static final String B = "B";
    public static final String A = "A";
    public static final String S = "S";
    public static final String[] ORDER = {F, E, D, C, B, A, S};
    public static final Set<String> VALID = Set.of(ORDER);

    private static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("COMMON", F), Map.entry("F_RANK", F), Map.entry("F-RANK", F),
            Map.entry("UNCOMMON", E), Map.entry("E_RANK", E), Map.entry("E-RANK", E),
            Map.entry("RARE", D), Map.entry("D_RANK", D), Map.entry("D-RANK", D),
            Map.entry("EPIC", C), Map.entry("C_RANK", C), Map.entry("C-RANK", C),
            Map.entry("B_RANK", B), Map.entry("B-RANK", B),
            Map.entry("LEGENDARY", A), Map.entry("A_RANK", A), Map.entry("A-RANK", A),
            Map.entry("MYTHIC", S), Map.entry("MYTHICAL", S), Map.entry("S_RANK", S), Map.entry("S-RANK", S)
    );

    private RarityScale() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return F;
        String key = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (VALID.contains(key)) return key;
        return LEGACY.getOrDefault(key, F);
    }

    public static boolean isRankLike(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String key = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return VALID.contains(key) || LEGACY.containsKey(key);
    }

    public static String lower(String raw) {
        return normalize(raw).toLowerCase(Locale.ROOT);
    }

    public static int index(String raw) {
        String rank = normalize(raw);
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(rank)) return i;
        return 0;
    }

    public static boolean atLeast(String current, String required) {
        return index(current) >= index(required);
    }

    public static String display(String raw) {
        return normalize(raw) + " Rank";
    }

    public static ChatFormatting color(String raw) {
        return switch (normalize(raw)) {
            case E -> ChatFormatting.GREEN;
            case D -> ChatFormatting.AQUA;
            case C -> ChatFormatting.BLUE;
            case B -> ChatFormatting.LIGHT_PURPLE;
            case A -> ChatFormatting.GOLD;
            case S -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }
}
