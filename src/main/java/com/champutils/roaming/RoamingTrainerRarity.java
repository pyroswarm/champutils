package com.champutils.roaming;

import com.champutils.rarity.RarityScale;
import net.minecraft.ChatFormatting;

public enum RoamingTrainerRarity {
    F(ChatFormatting.WHITE),
    E(ChatFormatting.GREEN),
    D(ChatFormatting.AQUA),
    C(ChatFormatting.BLUE),
    B(ChatFormatting.LIGHT_PURPLE),
    A(ChatFormatting.GOLD),
    S(ChatFormatting.DARK_PURPLE);

    public final ChatFormatting color;

    RoamingTrainerRarity(ChatFormatting color) {
        this.color = color;
    }

    public boolean alertsPlayers() {
        return ordinal() >= B.ordinal();
    }

    public static RoamingTrainerRarity parse(String value, RoamingTrainerRarity fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return RoamingTrainerRarity.valueOf(RarityScale.normalize(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
