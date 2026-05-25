package com.champutils.chat;

import net.minecraft.ChatFormatting;

public enum ChatMode {
    LOCAL("local", "Local", "L", ChatFormatting.GREEN),
    GLOBAL("global", "Global", "G", ChatFormatting.AQUA),
    PARTY("party", "Party", "P", ChatFormatting.LIGHT_PURPLE),
    GUILD("guild", "Guild", "Guild", ChatFormatting.DARK_AQUA);

    public final String id;
    public final String displayName;
    public final String prefix;
    public final ChatFormatting color;

    ChatMode(String id, String displayName, String prefix, ChatFormatting color) {
        this.id = id;
        this.displayName = displayName;
        this.prefix = prefix;
        this.color = color;
    }

    public static ChatMode parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toLowerCase();
        for (ChatMode mode : values()) {
            if (mode.id.equals(value) || mode.displayName.toLowerCase().equals(value) || mode.prefix.toLowerCase().equals(value)) return mode;
        }
        if (value.equals("l")) return LOCAL;
        if (value.equals("g") || value.equals("all")) return GLOBAL;
        if (value.equals("p")) return PARTY;
        if (value.equals("gc")) return GUILD;
        return null;
    }
}
