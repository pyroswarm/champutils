package com.champutils.dungeon;

import net.minecraft.ChatFormatting;

public enum DungeonRarity {
    COMMON(20, ChatFormatting.WHITE),
    UNCOMMON(40, ChatFormatting.GREEN),
    RARE(60, ChatFormatting.BLUE),
    EPIC(80, ChatFormatting.DARK_PURPLE),
    LEGENDARY(90, ChatFormatting.GOLD),
    MYTHIC(100, ChatFormatting.LIGHT_PURPLE);

    private final int pokemonLevel;
    private final ChatFormatting color;

    DungeonRarity(int pokemonLevel, ChatFormatting color) {
        this.pokemonLevel = pokemonLevel;
        this.color = color;
    }

    public int getPokemonLevel() {
        return pokemonLevel;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public static DungeonRarity parse(String value) {
        if (value == null || value.isBlank()) {
            return COMMON;
        }

        try {
            return DungeonRarity.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return COMMON;
        }
    }
}
