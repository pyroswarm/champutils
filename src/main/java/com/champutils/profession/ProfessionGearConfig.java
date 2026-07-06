package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfessionGearConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/profession_gear.json");
    public static Config CONFIG = defaults();

    private ProfessionGearConfig() {}

    public static final class Config {
        public boolean enabled = true;
        public Map<String, TierData> tiers = new LinkedHashMap<>();
    }

    public static final class TierData {
        public ArmorStats helmet = new ArmorStats();
        public ArmorStats chestplate = new ArmorStats();
        public ArmorStats leggings = new ArmorStats();
    }

    public static final class ArmorStats {
        public int waterBreathing = 0;
        public int nightVision = 0;
        public int aquaAffinity = 0;
        public int dolphinsGrace = 0;
        public int conduitBoost = 0;
        public int strength = 0;
        public int haste = 0;
        public double blockReach = 0.0D;
        public double damageReduction = 0.0D;
        public double fireReduction = 0.0D;
        public double stepHeight = 0.0D;
        public double knockbackResistance = 0.0D;
    }

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) {
                CONFIG = defaults();
                save();
                return;
            }
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
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(CONFIG, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ArmorStats stats(String rarity, String slot) {
        TierData tier = CONFIG.tiers.get(ProfessionFragmentConfig.normalizeRarity(rarity));
        if (tier == null) tier = defaults().tiers.get(ProfessionFragmentConfig.normalizeRarity(rarity));
        if (tier == null) tier = defaults().tiers.get("F");
        String normalized = slot == null ? "" : slot.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "helmet" -> tier.helmet;
            case "chestplate" -> tier.chestplate;
            case "leggings" -> tier.leggings;
            default -> new ArmorStats();
        };
    }

    private static Config merge(Config c) {
        Config d = defaults();
        if (c.tiers == null) c.tiers = new LinkedHashMap<>();
        for (Map.Entry<String, TierData> entry : d.tiers.entrySet()) {
            c.tiers.putIfAbsent(entry.getKey(), entry.getValue());
            TierData tier = c.tiers.get(entry.getKey());
            if (tier.helmet == null) tier.helmet = entry.getValue().helmet;
            if (tier.chestplate == null) tier.chestplate = entry.getValue().chestplate;
            if (tier.leggings == null) tier.leggings = entry.getValue().leggings;
            if (tier.chestplate.damageReduction <= 0.0D) tier.chestplate.damageReduction = entry.getValue().chestplate.damageReduction;
        }
        return c;
    }

    private static Config defaults() {
        Config c = new Config();
        add(c, "F", helmet(1,0,0,0,0), chest(1,0,0.0,5.0), legs(33,0.0,0.0));
        add(c, "E", helmet(1,1,0,0,0), chest(1,1,0.0,10.0), legs(50,0.6,0.0));
        add(c, "D", helmet(1,1,1,0,0), chest(1,1,1.0,20.0), legs(66,0.6,0.5));
        add(c, "C", helmet(1,1,1,1,0), chest(1,1,1.0,32.5), legs(75,0.6,0.5));
        add(c, "B", helmet(1,1,1,1,1), chest(1,1,1.25,42.5), legs(82,0.6,0.65));
        add(c, "A", helmet(1,1,1,1,1), chest(1,1,1.5,50.0), legs(90,0.6,0.75));
        add(c, "S", helmet(1,1,1,1,1), chest(2,2,2.0,75.0), legs(100,0.8,1.0));
        return c;
    }

    private static void add(Config c, String rarity, ArmorStats helmet, ArmorStats chestplate, ArmorStats leggings) {
        TierData t = new TierData();
        t.helmet = helmet;
        t.chestplate = chestplate;
        t.leggings = leggings;
        c.tiers.put(rarity, t);
    }

    private static ArmorStats helmet(int water, int night, int aqua, int dolphin, int conduit) {
        ArmorStats s = new ArmorStats();
        s.waterBreathing = water;
        s.nightVision = night;
        s.aquaAffinity = aqua;
        s.dolphinsGrace = dolphin;
        s.conduitBoost = conduit;
        return s;
    }

    private static ArmorStats chest(int strength, int haste, double reach, double damageReduction) {
        ArmorStats s = new ArmorStats();
        s.strength = strength;
        s.haste = haste;
        s.blockReach = reach;
        s.damageReduction = damageReduction;
        return s;
    }

    private static ArmorStats legs(double fire, double step, double knockback) {
        ArmorStats s = new ArmorStats();
        s.fireReduction = fire;
        s.stepHeight = step;
        s.knockbackResistance = knockback;
        return s;
    }
}
