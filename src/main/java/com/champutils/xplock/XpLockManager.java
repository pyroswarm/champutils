package com.champutils.xplock;

import com.cobblemon.mod.common.pokemon.Pokemon;

public final class XpLockManager {

    public static final String LOCK_KEY = "champutils_xp_locked";

    private XpLockManager() {
    }

    public static boolean isLocked(Pokemon pokemon) {
        if (pokemon == null) {
            return false;
        }

        try {
            return pokemon.getPersistentData().getBoolean(LOCK_KEY);
        } catch (Exception ignored) {
            return false;
        }
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
