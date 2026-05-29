package com.champutils.profile;

public enum ProfileGameMode {
    NORMAL,
    IRONMAN,
    MONOTYPE,
    ISLANDER,
    NUZLOCKE;

    public static ProfileGameMode parse(String raw) {
        if (raw == null) return NORMAL;
        try {
            return ProfileGameMode.valueOf(raw.trim().toUpperCase());
        }
        catch (Exception ignored) {
            return NORMAL;
        }
    }

    public boolean usesIronmanRules() {
        return this == IRONMAN || this == NUZLOCKE;
    }

    public boolean blocksAuctionHouse() {
        return this == IRONMAN || this == NUZLOCKE || this == ISLANDER;
    }

    public boolean isSpecialMode() {
        return this != NORMAL;
    }

    public String displayName() {
        return switch (this) {
            case NORMAL -> "Normal";
            case IRONMAN -> "Ironman";
            case MONOTYPE -> "Monotype";
            case ISLANDER -> "Islander";
            case NUZLOCKE -> "Nuzlocke";
        };
    }
}
