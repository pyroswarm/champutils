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
        /** Percent chance to add a second base profession XP reward. */
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
            if (t.pokemonXpBonusPercent <= 0) t.pokemonXpBonusPercent = def.pokemonXpBonusPercent;
            if (t.friendshipBonusPercent <= 0) t.friendshipBonusPercent = def.friendshipBonusPercent;
            if (t.levelCharmGymCapPercent <= 0) t.levelCharmGymCapPercent = def.levelCharmGymCapPercent;
            if (t.rarePokemonSpawnBonusPercent <= 0) t.rarePokemonSpawnBonusPercent = def.rarePokemonSpawnBonusPercent;
            if (t.chunkChanceBonusPercent <= 0) t.chunkChanceBonusPercent = def.chunkChanceBonusPercent;
        }
        return c;
    }

    private static Config defaults() {
        Config c = new Config();
        add(c, "F", 16, 1, 0.0025, 2, 0.5, 1, 5, 10, 1, 1);
        add(c, "E", 16, 2, 0.0050, 3, 1.0, 3, 10, 20, 3, 2);
        add(c, "D", 16, 3, 0.0075, 5, 2.0, 5, 15, 30, 5, 4);
        add(c, "C", 16, 4, 0.0125, 7, 3.0, 10, 20, 40, 8, 7);
        add(c, "B", 16, 5, 0.0150, 8, 4.5, 14, 23, 48, 10, 9);
        add(c, "A", 16, 6, 0.0175, 10, 6.5, 18, 27, 58, 12, 13);
        add(c, "S", 16, 7, 0.0250, 15, 10.0, 25, 35, 70, 15, 20);
        return c;
    }

    private static void add(Config c, String rarity, int cost, double magnet, double shiny, int pouch,
                            double professionXp, double pokemonXp, double friendship, double levelCharm,
                            double rarePokemon, double chunkyBrick) {
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
        c.tiers.put(rarity, t);
    }
}
