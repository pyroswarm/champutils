package com.champutils.breeding;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/** Permanent per-Pokémon breeding eligibility stored inside Cobblemon persistent NBT. */
public final class PokemonBreedability {
    private static final String ROOT = "champutils_breedability";
    private static final String BREEDABLE = "breedable";
    private static final String REASON = "reason";
    private static final String BASELINE = "iv_baseline";
    private static final List<Stat> STATS = List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED);
    private static final List<String> KEYS = List.of("hp", "attack", "defence", "special_attack", "special_defence", "speed");

    private PokemonBreedability() {}

    public static boolean isBreedable(Pokemon pokemon) {
        if (pokemon == null) return false;
        CompoundTag root = pokemon.getPersistentData().getCompound(ROOT);
        return !root.contains(BREEDABLE) || root.getBoolean(BREEDABLE);
    }

    public static String reason(Pokemon pokemon) {
        if (pokemon == null) return "Unavailable";
        CompoundTag root = pokemon.getPersistentData().getCompound(ROOT);
        return root.getString(REASON);
    }

    public static boolean makeUnbreedable(Pokemon pokemon, String reason) {
        if (pokemon == null) return false;
        CompoundTag persistent = pokemon.getPersistentData();
        CompoundTag root = persistent.getCompound(ROOT);
        if (root.contains(BREEDABLE) && !root.getBoolean(BREEDABLE)) return false;
        root.putBoolean(BREEDABLE, false);
        root.putString(REASON, reason == null || reason.isBlank() ? "Permanently neutered" : reason);
        writeBaseline(root, pokemon);
        persistent.put(ROOT, root);
        return true;
    }

    /**
     * Records natural IVs once, then permanently neuters the Pokémon if a later scan sees an increase.
     * Decreases are accepted and become the new baseline so lowering IVs cannot create false positives.
     */
    public static boolean auditIvChanges(Pokemon pokemon) {
        if (pokemon == null || !isBreedable(pokemon)) return false;
        CompoundTag persistent = pokemon.getPersistentData();
        CompoundTag root = persistent.getCompound(ROOT);
        if (!root.contains(BASELINE)) {
            writeBaseline(root, pokemon);
            persistent.put(ROOT, root);
            return false;
        }
        CompoundTag baseline = root.getCompound(BASELINE);
        boolean increased = false;
        for (int i = 0; i < STATS.size(); i++) {
            int current = pokemon.getIvs().get(STATS.get(i));
            int previous = baseline.getInt(KEYS.get(i));
            if (current > previous) increased = true;
            if (current < previous) baseline.putInt(KEYS.get(i), current);
        }
        if (increased) return makeUnbreedable(pokemon, "IVs were increased unnaturally");
        root.put(BASELINE, baseline);
        persistent.put(ROOT, root);
        return false;
    }

    public static void refreshIvBaseline(Pokemon pokemon) {
        if (pokemon == null) return;
        CompoundTag persistent = pokemon.getPersistentData();
        CompoundTag root = persistent.getCompound(ROOT);
        writeBaseline(root, pokemon);
        persistent.put(ROOT, root);
    }

    private static void writeBaseline(CompoundTag root, Pokemon pokemon) {
        CompoundTag baseline = new CompoundTag();
        for (int i = 0; i < STATS.size(); i++) baseline.putInt(KEYS.get(i), pokemon.getIvs().get(STATS.get(i)));
        root.put(BASELINE, baseline);
    }
}
