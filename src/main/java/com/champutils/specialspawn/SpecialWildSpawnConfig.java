package com.champutils.specialspawn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public final class SpecialWildSpawnConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/special_wild_spawns.json");
    public static Data DATA = defaults();

    private SpecialWildSpawnConfig() {}

    public static void load() {
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            if (!FILE.exists()) { DATA = defaults(); save(); return; }
            try (FileReader reader = new FileReader(FILE)) { DATA = GSON.fromJson(reader, Data.class); }
            if (DATA == null) DATA = defaults();
            normalize();
            save();
        } catch (Exception e) {
            DATA = defaults();
            e.printStackTrace();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(DATA, writer); } catch (Exception e) { e.printStackTrace(); }
    }

    private static void normalize() {
        Data d = defaults();
        if (DATA.legendarySpawns == null) DATA.legendarySpawns = d.legendarySpawns;
        if (DATA.paradoxSpawns == null) DATA.paradoxSpawns = d.paradoxSpawns;
        if (DATA.ultraBeastSpawns == null) DATA.ultraBeastSpawns = d.ultraBeastSpawns;
        if (DATA.disabledDimensions == null) DATA.disabledDimensions = d.disabledDimensions;
        if (DATA.checkIntervalTicks <= 0) DATA.checkIntervalTicks = d.checkIntervalTicks;
        if (DATA.minDistanceFromPlayer < 8) DATA.minDistanceFromPlayer = d.minDistanceFromPlayer;
        if (DATA.maxDistanceFromPlayer < DATA.minDistanceFromPlayer) DATA.maxDistanceFromPlayer = d.maxDistanceFromPlayer;
        if (DATA.maxAliveSpecialWildPokemon <= 0) DATA.maxAliveSpecialWildPokemon = d.maxAliveSpecialWildPokemon;
    }

    private static Data defaults() {
        Data root = new Data();
        root.enabled = true;
        root.disableVanillaAndAllTheMonsSpecialSpawns = true;
        root.broadcastLegendarySpawns = true;
        root.broadcastParadoxAndUltraBeastSpawns = false;
        root.checkIntervalTicks = 12000; // 10 minutes
        root.legendaryChancePerCheck = 0.0025; // about 0.25% per check/player candidate
        root.paradoxChancePerCheck = 0.0080;
        root.ultraBeastChancePerCheck = 0.0080;
        root.minDistanceFromPlayer = 48;
        root.maxDistanceFromPlayer = 96;
        root.maxAliveSpecialWildPokemon = 2;
        root.levelRangeLegendary = "60-80";
        root.levelRangeParadox = "45-65";
        root.levelRangeUltraBeast = "50-70";
        root.disabledDimensions = new ArrayList<>(List.of("multiworld:spawn1", "multiworld:spawn", "minecraft:the_end"));

        root.legendarySpawns = new ArrayList<>(List.of(
                entry("articuno", tags("#cobblemon:is_freezing", "#cobblemon:is_mountain"), times("night", "dawn")),
                entry("zapdos", tags("#cobblemon:is_plains", "#cobblemon:is_savanna"), times("day", "dusk")),
                entry("moltres", tags("#cobblemon:is_nether", "#cobblemon:is_volcanic", "#cobblemon:is_badlands"), times("day", "dusk")),
                entry("mewtwo", tags("#cobblemon:is_deep_dark", "#cobblemon:is_end"), times("night")),
                entry("lugia", tags("#cobblemon:is_deep_ocean", "#cobblemon:is_ocean"), times("night")),
                entry("ho_oh", tags("#cobblemon:is_mountain", "#cobblemon:is_sky"), times("dawn", "day")),
                entry("raikou", tags("#cobblemon:is_savanna", "#cobblemon:is_plains"), times("day")),
                entry("entei", tags("#cobblemon:is_badlands", "#cobblemon:is_nether"), times("day")),
                entry("suicune", tags("#cobblemon:is_river", "#cobblemon:is_lake", "#cobblemon:is_ocean"), times("night", "dawn")),
                entry("kyogre", tags("#cobblemon:is_deep_ocean", "#cobblemon:is_ocean"), times("night")),
                entry("groudon", tags("#cobblemon:is_desert", "#cobblemon:is_badlands"), times("day")),
                entry("rayquaza", tags("#cobblemon:is_mountain", "#cobblemon:is_sky"), times("day")),
                entry("dialga", tags("#cobblemon:is_mountain", "#cobblemon:is_deep_dark"), times("night")),
                entry("palkia", tags("#cobblemon:is_ocean", "#cobblemon:is_deep_dark"), times("night")),
                entry("giratina", tags("#cobblemon:is_deep_dark", "#cobblemon:is_nether"), times("night")),
                entry("reshiram", tags("#cobblemon:is_mountain", "#cobblemon:is_plains"), times("day")),
                entry("zekrom", tags("#cobblemon:is_mountain", "#cobblemon:is_plains"), times("night")),
                entry("xerneas", tags("#cobblemon:is_forest", "#cobblemon:is_floral"), times("dawn", "day")),
                entry("yveltal", tags("#cobblemon:is_dark_forest", "#cobblemon:is_mountain"), times("night")),
                entry("zygarde", tags("#cobblemon:is_cave", "#cobblemon:is_mountain"), times("day", "night")),
                entry("solgaleo", tags("#cobblemon:is_savanna", "#cobblemon:is_desert"), times("day")),
                entry("lunala", tags("#cobblemon:is_dark_forest", "#cobblemon:is_mountain"), times("night")),
                entry("necrozma", tags("#cobblemon:is_desert", "#cobblemon:is_deep_dark"), times("dusk", "night")),
                entry("zacian", tags("#cobblemon:is_forest", "#cobblemon:is_plains"), times("day")),
                entry("zamazenta", tags("#cobblemon:is_forest", "#cobblemon:is_mountain"), times("day")),
                entry("eternatus", tags("#cobblemon:is_deep_dark", "#cobblemon:is_nether"), times("night")),
                entry("koraidon", tags("#cobblemon:is_mountain", "#cobblemon:is_badlands"), times("day")),
                entry("miraidon", tags("#cobblemon:is_mountain", "#cobblemon:is_plains"), times("night"))
        ));

        root.paradoxSpawns = new ArrayList<>(List.of(
                entry("great_tusk", tags("#cobblemon:is_desert", "#cobblemon:is_badlands"), times("day")),
                entry("scream_tail", tags("#cobblemon:is_floral", "#cobblemon:is_forest"), times("dawn", "night")),
                entry("brute_bonnet", tags("#cobblemon:is_mushroom", "#cobblemon:is_forest"), times("night")),
                entry("flutter_mane", tags("#cobblemon:is_dark_forest", "#cobblemon:is_cave"), times("night")),
                entry("slither_wing", tags("#cobblemon:is_jungle", "#cobblemon:is_forest"), times("day")),
                entry("sandy_shocks", tags("#cobblemon:is_desert", "#cobblemon:is_badlands"), times("dusk", "day")),
                entry("roaring_moon", tags("#cobblemon:is_mountain", "#cobblemon:is_cave"), times("night")),
                entry("walking_wake", tags("#cobblemon:is_river", "#cobblemon:is_lake"), times("dawn", "day")),
                entry("iron_treads", tags("#cobblemon:is_desert", "#cobblemon:is_badlands"), times("night")),
                entry("iron_bundle", tags("#cobblemon:is_freezing", "#cobblemon:is_snowy"), times("night")),
                entry("iron_hands", tags("#cobblemon:is_mountain", "#cobblemon:is_plains"), times("night")),
                entry("iron_jugulis", tags("#cobblemon:is_dark_forest", "#cobblemon:is_mountain"), times("night")),
                entry("iron_moth", tags("#cobblemon:is_desert", "#cobblemon:is_badlands"), times("day")),
                entry("iron_thorns", tags("#cobblemon:is_mountain", "#cobblemon:is_badlands"), times("night")),
                entry("iron_valiant", tags("#cobblemon:is_floral", "#cobblemon:is_mountain"), times("dawn", "night")),
                entry("iron_leaves", tags("#cobblemon:is_forest", "#cobblemon:is_floral"), times("day")),
                entry("iron_crown", tags("#cobblemon:is_mountain", "#cobblemon:is_deep_dark"), times("night"))
        ));

        root.ultraBeastSpawns = new ArrayList<>(List.of(
                entry("nihilego", tags("#cobblemon:is_cave", "#cobblemon:is_deep_dark"), times("night")),
                entry("buzzwole", tags("#cobblemon:is_jungle", "#cobblemon:is_forest"), times("day")),
                entry("pheromosa", tags("#cobblemon:is_desert", "#cobblemon:is_beach"), times("day")),
                entry("xurkitree", tags("#cobblemon:is_plains", "#cobblemon:is_savanna"), times("night")),
                entry("celesteela", tags("#cobblemon:is_bamboo", "#cobblemon:is_jungle"), times("night")),
                entry("kartana", tags("#cobblemon:is_bamboo", "#cobblemon:is_forest"), times("day")),
                entry("guzzlord", tags("#cobblemon:is_deep_dark", "#cobblemon:is_nether"), times("night")),
                entry("poipole", tags("#cobblemon:is_swamp", "#cobblemon:is_cave"), times("night")),
                entry("naganadel", tags("#cobblemon:is_swamp", "#cobblemon:is_cave"), times("night")),
                entry("stakataka", tags("#cobblemon:is_mountain", "#cobblemon:is_cave"), times("night")),
                entry("blacephalon", tags("#cobblemon:is_dark_forest", "#cobblemon:is_nether"), times("night"))
        ));
        return root;
    }

    private static List<String> tags(String... values) { return new ArrayList<>(List.of(values)); }
    private static List<String> times(String... values) { return new ArrayList<>(List.of(values)); }
    private static SpawnEntry entry(String species, List<String> biomes, List<String> times) {
        SpawnEntry e = new SpawnEntry(); e.species = species; e.biomes = biomes; e.times = times; e.weight = 1.0; return e;
    }

    public static final class Data {
        public boolean enabled;
        public boolean disableVanillaAndAllTheMonsSpecialSpawns;
        public boolean broadcastLegendarySpawns;
        public boolean broadcastParadoxAndUltraBeastSpawns;
        public int checkIntervalTicks;
        public double legendaryChancePerCheck;
        public double paradoxChancePerCheck;
        public double ultraBeastChancePerCheck;
        public int minDistanceFromPlayer;
        public int maxDistanceFromPlayer;
        public int maxAliveSpecialWildPokemon;
        public String levelRangeLegendary;
        public String levelRangeParadox;
        public String levelRangeUltraBeast;
        public List<String> disabledDimensions;
        public List<SpawnEntry> legendarySpawns;
        public List<SpawnEntry> paradoxSpawns;
        public List<SpawnEntry> ultraBeastSpawns;
    }

    public static final class SpawnEntry {
        public String species;
        public List<String> biomes;
        public List<String> times;
        public double weight = 1.0;
    }
}
