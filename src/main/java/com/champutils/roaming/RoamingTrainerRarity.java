package com.champutils.roaming;

import net.minecraft.ChatFormatting;

public enum RoamingTrainerRarity {
    COMMON(ChatFormatting.WHITE),
    UNCOMMON(ChatFormatting.GREEN),
    RARE(ChatFormatting.BLUE),
    EPIC(ChatFormatting.DARK_PURPLE),
    LEGENDARY(ChatFormatting.GOLD),
    MYTHIC(ChatFormatting.LIGHT_PURPLE);

    public final ChatFormatting color;

    RoamingTrainerRarity(ChatFormatting color) {
        this.color = color;
    }

    public boolean alertsPlayers() {
        return false;
    }

    public static RoamingTrainerRarity parse(String value, RoamingTrainerRarity fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return RoamingTrainerRarity.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
