package com.champutils.profile;

public enum ProfileGameMode {
    NORMAL,
    IRONMAN,
    MONOTYPE;

    public static ProfileGameMode parse(String raw) {
        if (raw == null) return NORMAL;
        try {
            return ProfileGameMode.valueOf(raw.trim().toUpperCase());
        }
        catch (Exception ignored) {
            return NORMAL;
        }
    }

    public String displayName() {
        return switch (this) {
            case NORMAL -> "Normal";
            case IRONMAN -> "Ironman";
            case MONOTYPE -> "Monotype";
        };
    }
}
