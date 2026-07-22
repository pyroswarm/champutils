package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfessionTrinketConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/profession_trinkets.json");
    public static Config CONFIG = defaults();

    private ProfessionTrinketConfig() {}

    public static class Config {
        public boolean enabled = true;
        public Map<String, Tier> tiers = new LinkedHashMap<>();
    }

    public static class Tier {
        public int sameTierFragmentCost;
        public int sameTierEssenceCost;
        public double magnetRadiusBonus;
        /** Extra flat percent chance. Capped in ProfessionTrinketManager so old configs cannot become overpowered. */
        public double shinyChancePercent;
        public int pouchSlots;
        /** Guaranteed profession XP bonus percent. Retains the legacy field name for config compatibility. */
        public double professionXpDoubleChancePercent;
        /** Decimal bonus, written as percent in config. 25.0 = +25%. */
        public double pokemonXpBonusPercent;
        /** Decimal bonus, written as percent in config. 35.0 = +35%. */
        public double friendshipBonusPercent;
        /** Percent of the current gym cap used as the minimum wild spawn level. */
        public double levelCharmGymCapPercent;
        /** Relative boost to non-special rare spawn weighting. */
        public double rarePokemonSpawnBonusPercent;
        /** Relative boost to existing chunk odds, not a flat chance. */
        public double chunkChanceBonusPercent;
        /** Radius in blocks for Totem of Growth. */
        public int growthRadiusBlocks;
        /** Additional growth speed percent for supported crops, berries, and apricorns. */
        public double growthSpeedBonusPercent;
        /** Percent reduction to player exhaustion/hunger drain. 100 prevents hunger loss. */
        public double hungerReductionPercent;
        /** Extra same-seed placements triggered by Seed Pouch. */
        public int seedPouchExtraPlacements;
        /** Additional breeding cooldown reduction applied after profession reduction. */
        public double incubatorCooldownReductionPercent;
    }

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) { CONFIG = defaults(); save(); return; }
            try (FileReader reader = new FileReader(FILE)) {
                Config loaded = GSON.fromJson(reader, Config.class);
                CONFIG = merge(loaded == null ? defaults() : loaded);
            }
            save();
        } catch (Exception e) {
            e.printStackTrace();
            CONFIG = defaults();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(CONFIG, writer); }
        catch (Exception e) { e.printStackTrace(); }
    }

    public static Tier tier(String rarity) {
        if (CONFIG == null || CONFIG.tiers == null) CONFIG = defaults();
        return CONFIG.tiers.getOrDefault(ProfessionFragmentConfig.normalizeRarity(rarity), defaults().tiers.get("F"));
    }

    private static Config merge(Config c) {
        Config d = defaults();
        if (c.tiers == null) c.tiers = new LinkedHashMap<>();
        for (Map.Entry<String, Tier> e : d.tiers.entrySet()) c.tiers.putIfAbsent(e.getKey(), e.getValue());
        for (Map.Entry<String, Tier> e : c.tiers.entrySet()) {
            Tier t = e.getValue();
            if (t == null) { e.setValue(d.tiers.getOrDefault(e.getKey(), d.tiers.get("F"))); continue; }
            Tier def = d.tiers.getOrDefault(e.getKey(), d.tiers.get("F"));
            if (t.sameTierFragmentCost <= 0 && t.sameTierEssenceCost > 0) t.sameTierFragmentCost = t.sameTierEssenceCost;
            if (t.sameTierFragmentCost <= 0) t.sameTierFragmentCost = def.sameTierFragmentCost;
            t.sameTierEssenceCost = t.sameTierFragmentCost;
            if (t.magnetRadiusBonus < 0) t.magnetRadiusBonus = def.magnetRadiusBonus;
            if (t.shinyChancePercent < 0) t.shinyChancePercent = def.shinyChancePercent;
            if (t.pouchSlots <= 0) t.pouchSlots = def.pouchSlots;
            if (t.professionXpDoubleChancePercent <= 0) t.professionXpDoubleChancePercent = def.professionXpDoubleChancePercent;
            // Enforce the current tier ceiling so legacy configs with the old 10-125% values are safely nerfed.
            t.professionXpDoubleChancePercent = Math.min(t.professionXpDoubleChancePercent, def.professionXpDoubleChancePercent);
            if (t.pokemonXpBonusPercent <= 0) t.pokemonXpBonusPercent = def.pokemonXpBonusPercent;
            if (t.friendshipBonusPercent <= 0) t.friendshipBonusPercent = def.friendshipBonusPercent;
            if (t.levelCharmGymCapPercent <= 0) t.levelCharmGymCapPercent = def.levelCharmGymCapPercent;
            if (t.rarePokemonSpawnBonusPercent <= 0) t.rarePokemonSpawnBonusPercent = def.rarePokemonSpawnBonusPercent;
            if (t.chunkChanceBonusPercent <= 0) t.chunkChanceBonusPercent = def.chunkChanceBonusPercent;
            if (t.growthRadiusBlocks <= 0) t.growthRadiusBlocks = def.growthRadiusBlocks;
            if (t.growthSpeedBonusPercent <= 0) t.growthSpeedBonusPercent = def.growthSpeedBonusPercent;
            if (t.hungerReductionPercent <= 0) t.hungerReductionPercent = def.hungerReductionPercent;
            if (t.seedPouchExtraPlacements <= 0) t.seedPouchExtraPlacements = def.seedPouchExtraPlacements;
            if (t.incubatorCooldownReductionPercent <= 0) t.incubatorCooldownReductionPercent = def.incubatorCooldownReductionPercent;
        }
        return c;
    }

    private static Config defaults() {
        Config c = new Config();
        add(c, "F", 16, 1, 0.0025, 2, 2.5, 1, 5, 10, 1, 10, 2, 25, 10, 1, 5);
        add(c, "E", 16, 2, 0.0050, 3, 5.0, 3, 10, 20, 3, 15, 4, 50, 15, 2, 10);
        add(c, "D", 16, 3, 0.0075, 5, 7.5, 5, 15, 30, 5, 22.5, 8, 100, 25, 4, 15);
        add(c, "C", 16, 4, 0.0125, 7, 10.0, 10, 20, 40, 8, 32.5, 16, 175, 37, 8, 20);
        add(c, "B", 16, 5, 0.0150, 8, 15.0, 14, 23, 48, 10, 45, 32, 300, 50, 16, 30);
        add(c, "A", 16, 6, 0.0175, 10, 20.0, 18, 27, 58, 12, 65, 64, 500, 70, 32, 40);
        add(c, "S", 16, 7, 0.0250, 15, 25.0, 25, 35, 70, 15, 100, 128, 800, 100, 64, 50);
        return c;
    }

    private static void add(Config c, String rarity, int cost, double magnet, double shiny, int pouch,
                            double professionXp, double pokemonXp, double friendship, double levelCharm,
                            double rarePokemon, double chunkyBrick, int growthRadius, double growthSpeed, double hungerReduction,
                            int seedPouchExtraPlacements, double incubatorCooldownReductionPercent) {
        Tier t = new Tier();
        t.sameTierFragmentCost = cost;
        t.sameTierEssenceCost = cost;
        t.magnetRadiusBonus = magnet;
        t.shinyChancePercent = shiny;
        t.pouchSlots = pouch;
        t.professionXpDoubleChancePercent = professionXp;
        t.pokemonXpBonusPercent = pokemonXp;
        t.friendshipBonusPercent = friendship;
        t.levelCharmGymCapPercent = levelCharm;
        t.rarePokemonSpawnBonusPercent = rarePokemon;
        t.chunkChanceBonusPercent = chunkyBrick;
        t.growthRadiusBlocks = growthRadius;
        t.growthSpeedBonusPercent = growthSpeed;
        t.hungerReductionPercent = hungerReduction;
        t.seedPouchExtraPlacements = seedPouchExtraPlacements;
        t.incubatorCooldownReductionPercent = incubatorCooldownReductionPercent;
        c.tiers.put(rarity, t);
    }
}
