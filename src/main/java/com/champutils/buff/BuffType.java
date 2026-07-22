package com.champutils.buff;

import com.champutils.profession.ProfessionType;
import net.minecraft.ChatFormatting;

/**
 * Shared MMO buff types used by every progression/booster system.
 *
 * Guilds, cash shop server boosters, weekend events, ranks, consumables, and
 * future systems should all provide values for these same types instead of
 * creating one-off gameplay hooks.
 */
public enum BuffType {
    MINING_XP("Mining Profession XP", "Increases Mining profession XP earned from legitimate gameplay.", ChatFormatting.DARK_AQUA, ProfessionType.MINING),
    FORESTRY_XP("Forestry Profession XP", "Increases Forestry profession XP earned from legitimate gameplay.", ChatFormatting.GREEN, ProfessionType.FORESTRY),
    FARMING_XP("Farming Profession XP", "Increases Farming profession XP earned from legitimate gameplay.", ChatFormatting.YELLOW, ProfessionType.FARMING),
    BATTLING_XP("Battling Profession XP", "Increases Battling profession XP earned from legitimate gameplay.", ChatFormatting.RED, ProfessionType.BATTLING),
    POKEMON_XP("Pokemon XP", "Increases Pokemon battle experience earned by a relative percent.", ChatFormatting.AQUA, null),
    CHUNK_CHANCE("Chunk Chance", "Increases legitimate profession chunk drop odds by a relative percent.", ChatFormatting.GOLD, null),

    SHINY_CHANCE("Shiny Chance Boost", "Adds a percentage of the base shiny chance for legitimate wild spawns/catches. Multiple boosts stack additively from base.", ChatFormatting.LIGHT_PURPLE, null),
    CATCH_CHANCE("Catch Chance", "Chance for a legitimate wild Poké Ball attempt to become a guaranteed catch.", ChatFormatting.GREEN, null),
    PERFECT_IV_CHANCE("Perfect IV Chance", "Tiny chance for one random IV on a legitimate wild catch to become 31.", ChatFormatting.AQUA, null),
    ADVENTURER_MARKS("Adventurer's Marks", "Increases Adventurer's Marks earned from legitimate guild activities such as hunts, quests, and contracts.", ChatFormatting.AQUA, null);

    public final String displayName;
    public final String description;
    public final ChatFormatting color;
    public final ProfessionType professionType;

    BuffType(String displayName, String description, ChatFormatting color, ProfessionType professionType) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.professionType = professionType;
    }

    public boolean isProfessionXp() {
        return professionType != null;
    }

    public boolean isCatchBuff() {
        return this == SHINY_CHANCE || this == CATCH_CHANCE || this == PERFECT_IV_CHANCE;
    }

    public static BuffType fromProfession(ProfessionType profession) {
        if (profession == null) return null;
        for (BuffType type : values()) {
            if (type.professionType == profession) return type;
        }
        return null;
    }
}
