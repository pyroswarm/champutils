package com.champutils.adventurer;

import com.champutils.rarity.RarityScale;
import com.champutils.roaming.RoamingTrainerRarity;
import net.minecraft.ChatFormatting;

public final class AdventurerRankUtil {
    private AdventurerRankUtil() {}

    public static String normalizeRank(String raw) { return RarityScale.normalize(raw); }
    public static int rankIndex(String raw) { return RarityScale.index(raw); }
    public static boolean atLeast(String current, String required) { return RarityScale.atLeast(current, required); }
    public static String displayRank(String raw) { return RarityScale.display(raw); }
    public static String trainerLabel(String raw) { return RarityScale.display(raw) + " Adventurer"; }
    public static ChatFormatting color(String raw) { return RarityScale.color(raw); }

    public static RoamingTrainerRarity toRarity(String rank) {
        return RoamingTrainerRarity.parse(normalizeRank(rank), RoamingTrainerRarity.F);
    }

    public static String fromRarity(RoamingTrainerRarity rarity) {
        return rarity == null ? "F" : normalizeRank(rarity.name());
    }
}
