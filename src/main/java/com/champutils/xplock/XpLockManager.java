package com.champutils.xplock;

import com.cobblemon.mod.common.pokemon.Pokemon;

public final class XpLockManager {

    public static final String LOCK_KEY = "champutils_xp_locked";
    public static final String LEVEL_CAP_KEY = "champutils_level_cap";

    private XpLockManager() {
    }

    public static boolean isLocked(Pokemon pokemon) {
        if (pokemon == null) {
            return false;
        }

        try {
            // /xplock should only honor the explicit XP lock flag. Legacy /levelcap data
            // is intentionally ignored so players who had /levelcap enabled before removal
            // are not permanently stuck unable to gain experience.
            return pokemon.getPersistentData().getBoolean(LOCK_KEY);
        } catch (Exception ignored) {
            return false;
        }
    }


    public static int getLevelCap(Pokemon pokemon) {
        if (pokemon == null) return 0;
        try {
            return pokemon.getPersistentData().getInt(LEVEL_CAP_KEY);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static void setLevelCap(Pokemon pokemon, int level) {
        if (pokemon == null) return;
        pokemon.getPersistentData().putInt(LEVEL_CAP_KEY, Math.max(1, Math.min(100, level)));
    }

    public static boolean enforceLevelCap(Pokemon pokemon) {
        if (pokemon == null) return false;
        try {
            int cap = getLevelCap(pokemon);
            if (cap > 0 && pokemon.getLevel() > cap) {
                pokemon.setLevel(cap);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void clearLevelCap(Pokemon pokemon) {
        if (pokemon == null) return;
        pokemon.getPersistentData().remove(LEVEL_CAP_KEY);
    }

    public static void lock(Pokemon pokemon) {
        if (pokemon == null) {
            return;
        }

        pokemon.getPersistentData().putBoolean(LOCK_KEY, true);
    }

    public static void unlock(Pokemon pokemon) {
        if (pokemon == null) {
            return;
        }

        pokemon.getPersistentData().remove(LOCK_KEY);
    }

    public static boolean toggle(Pokemon pokemon) {
        if (isLocked(pokemon)) {
            unlock(pokemon);
            return false;
        }

        lock(pokemon);
        return true;
    }
}
