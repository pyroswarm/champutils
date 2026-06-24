package com.champutils.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class IslanderSpawningConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/islander_spawning.json");

    public static Config CONFIG = defaults();

    private IslanderSpawningConfig() {}

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
            CONFIG = defaults();
            System.err.println("[ChampUtils] Failed to load islander_spawning.json; using defaults.");
            e.printStackTrace();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(CONFIG, writer);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save islander_spawning.json.");
            e.printStackTrace();
        }
    }

    public static Tier tierForPlaytime(long playtimeSeconds) {
        Tier best = null;
        for (Tier tier : CONFIG.progressionTiers) {
            if (tier == null || !tier.enabled) continue;
            if (playtimeSeconds >= Math.max(0L, tier.minPlaytimeSeconds)) best = tier;
        }
        if (best != null) return best;
        return defaults().progressionTiers.get(0);
    }

    public static boolean isExcludedSpecies(String species) {
        String n = normalize(species);
        if (n.isBlank()) return true;
        if (CONFIG.excludeLegendary && contains(CONFIG.legendarySpecies, n)) return true;
        if (CONFIG.excludeMythical && contains(CONFIG.mythicalSpecies, n)) return true;
        if (CONFIG.excludeUltraBeast && contains(CONFIG.ultraBeastSpecies, n)) return true;
        if (CONFIG.excludeParadox && contains(CONFIG.paradoxSpecies, n)) return true;
        return contains(CONFIG.customSpecialSpawnOnlySpecies, n);
    }

    public static boolean isAllowedByTier(String species, int evolutionStage, Tier tier) {
        if (tier == null) return false;
        String n = normalize(species);
        if (contains(tier.denySpecies, n)) return false;
        if (tier.allowSpecies != null && !tier.allowSpecies.isEmpty()) return contains(tier.allowSpecies, n);
        return evolutionStage <= Math.max(0, tier.maxEvolutionStage);
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        int colon = s.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < s.length()) s = s.substring(colon + 1);
        return s.replace('-', '_').replace(' ', '_').replace(".", "_").replaceAll("[^a-z0-9_]", "");
    }

    private static boolean contains(Set<String> values, String normalized) {
        if (values == null) return false;
        for (String value : values) if (normalize(value).equals(normalized)) return true;
        return false;
    }

    private static Config merge(Config c) {
        Config d = defaults();
        if (c == null) return d;
        if (c.worldPrefix == null || c.worldPrefix.isBlank()) c.worldPrefix = d.worldPrefix;
        if (c.spawnablePositionTypes == null || c.spawnablePositionTypes.isEmpty()) c.spawnablePositionTypes = d.spawnablePositionTypes;
        if (c.legendarySpecies == null || c.legendarySpecies.isEmpty()) c.legendarySpecies = d.legendarySpecies;
        if (c.mythicalSpecies == null || c.mythicalSpecies.isEmpty()) c.mythicalSpecies = d.mythicalSpecies;
        if (c.ultraBeastSpecies == null || c.ultraBeastSpecies.isEmpty()) c.ultraBeastSpecies = d.ultraBeastSpecies;
        if (c.paradoxSpecies == null || c.paradoxSpecies.isEmpty()) c.paradoxSpecies = d.paradoxSpecies;
        if (c.customSpecialSpawnOnlySpecies == null) c.customSpecialSpawnOnlySpecies = d.customSpecialSpawnOnlySpecies;
        if (c.weight <= 0) c.weight = d.weight;
        if (c.closeSpawnerMinDistance < 1) c.closeSpawnerMinDistance = d.closeSpawnerMinDistance;
        if (c.closeSpawnerMaxDistance < c.closeSpawnerMinDistance) c.closeSpawnerMaxDistance = d.closeSpawnerMaxDistance;
        if (c.closeSpawnerZoneDiameter < 8) c.closeSpawnerZoneDiameter = d.closeSpawnerZoneDiameter;
        if (c.islanderMaxNearbyPokemon <= 0) c.islanderMaxNearbyPokemon = d.islanderMaxNearbyPokemon;
        if (c.closeSpawnerZoneHeight < 8) c.closeSpawnerZoneHeight = d.closeSpawnerZoneHeight;
        if (c.progressionTiers == null || c.progressionTiers.isEmpty()) c.progressionTiers = d.progressionTiers;
        for (Tier tier : c.progressionTiers) {
            if (tier == null) continue;
            if (tier.id == null || tier.id.isBlank()) tier.id = "tier";
            if (tier.displayName == null || tier.displayName.isBlank()) tier.displayName = tier.id;
            if (tier.minLevel <= 0) tier.minLevel = 1;
            if (tier.maxLevel < tier.minLevel) tier.maxLevel = tier.minLevel;
            if (tier.maxEvolutionStage < 0) tier.maxEvolutionStage = 0;
            if (tier.allowSpecies == null) tier.allowSpecies = new LinkedHashSet<>();
            if (tier.denySpecies == null) tier.denySpecies = new LinkedHashSet<>();
            if (tier.weightMultiplier <= 0) tier.weightMultiplier = 1.0F;
        }
        return c;
    }

    private static Config defaults() {
        Config c = new Config();
        c.enabled = true;
        c.worldPrefix = "multiworld:islander_";
        c.excludeLegendary = true;
        c.excludeMythical = true;
        c.excludeUltraBeast = true;
        c.excludeParadox = true;
        c.weight = 10.0F;
        c.closeSpawnerEnabled = true;
        c.closeSpawnerMinDistance = 1;
        c.closeSpawnerMaxDistance = 32;
        c.closeSpawnerZoneDiameter = 32;
        c.islanderMaxNearbyPokemon = 16;
        c.closeSpawnerZoneHeight = 24;
        c.spawnablePositionTypes = set("grounded", "surface", "submerged", "seafloor");
        c.progressionTiers = new ArrayList<>();
        c.progressionTiers.add(tier("hour_0", "Islander Hour 0", 0L * 3600L, 1, 10, 0, 1.00F));
        c.progressionTiers.add(tier("hour_1", "Islander Hour 1", 1L * 3600L, 1, 20, 0, 1.00F));
        c.progressionTiers.add(tier("hour_2", "Islander Hour 2", 2L * 3600L, 1, 30, 1, 1.00F));
        c.progressionTiers.add(tier("hour_3", "Islander Hour 3", 3L * 3600L, 1, 40, 1, 1.00F));
        c.progressionTiers.add(tier("hour_4", "Islander Hour 4", 4L * 3600L, 1, 50, 2, 1.00F));
        c.progressionTiers.add(tier("hour_5", "Islander Hour 5", 5L * 3600L, 1, 60, 2, 1.00F));
        c.progressionTiers.add(tier("hour_6", "Islander Hour 6", 6L * 3600L, 1, 70, 2, 1.00F));
        c.progressionTiers.add(tier("hour_7", "Islander Hour 7", 7L * 3600L, 1, 80, 2, 1.00F));
        c.progressionTiers.add(tier("hour_8", "Islander Hour 8", 8L * 3600L, 1, 90, 2, 1.00F));
        c.progressionTiers.add(tier("hour_9", "Islander Hour 9", 9L * 3600L, 1, 100, 2, 1.00F));
        c.legendarySpecies = set("articuno","zapdos","moltres","mewtwo","raikou","entei","suicune","lugia","ho_oh","hooh","regirock","regice","registeel","latias","latios","kyogre","groudon","rayquaza","uxie","mesprit","azelf","dialga","palkia","heatran","regigigas","giratina","cresselia","cobalion","terrakion","virizion","tornadus","thundurus","reshiram","zekrom","landorus","kyurem","xerneas","yveltal","zygarde","type_null","typenull","silvally","tapu_koko","tapukoko","tapu_lele","tapulele","tapu_bulu","tapubulu","tapu_fini","tapufini","cosmog","cosmoem","solgaleo","lunala","necrozma","zacian","zamazenta","eternatus","kubfu","urshifu","regieleki","regidrago","glastrier","spectrier","calyrex","enamorus","wo_chien","wochien","chien_pao","chienpao","ting_lu","tinglu","chi_yu","chiyu","okidogi","munkidori","fezandipiti","ogerpon","terapagos","koraidon","miraidon");
        c.mythicalSpecies = set("mew","celebi","jirachi","deoxys","phione","manaphy","darkrai","shaymin","arceus","victini","keldeo","meloetta","genesect","diancie","hoopa","volcanion","magearna","marshadow","zeraora","meltan","melmetal","zarude","pecharunt");
        c.ultraBeastSpecies = set("nihilego","buzzwole","pheromosa","xurkitree","celesteela","kartana","guzzlord","poipole","naganadel","stakataka","blacephalon");
        c.paradoxSpecies = set("great_tusk","greattusk","scream_tail","screamtail","brute_bonnet","brutebonnet","flutter_mane","fluttermane","slither_wing","slitherwing","sandy_shocks","sandyshocks","roaring_moon","roaringmoon","walking_wake","walkingwake","gouging_fire","gougingfire","raging_bolt","ragingbolt","iron_treads","irontreads","iron_bundle","ironbundle","iron_hands","ironhands","iron_jugulis","ironjugulis","iron_moth","ironmoth","iron_thorns","ironthorns","iron_valiant","ironvaliant","iron_leaves","ironleaves","iron_boulder","ironboulder","iron_crown","ironcrown");
        c.customSpecialSpawnOnlySpecies = new LinkedHashSet<>();
        return c;
    }

    private static Tier tier(String id, String displayName, long seconds, int minLevel, int maxLevel, int maxStage, float weightMultiplier) {
        Tier t = new Tier();
        t.enabled = true;
        t.id = id;
        t.displayName = displayName;
        t.minPlaytimeSeconds = seconds;
        t.minLevel = minLevel;
        t.maxLevel = maxLevel;
        t.maxEvolutionStage = maxStage;
        t.weightMultiplier = weightMultiplier;
        t.allowSpecies = new LinkedHashSet<>();
        t.denySpecies = new LinkedHashSet<>();
        return t;
    }

    private static Set<String> set(String... values) {
        Set<String> out = new LinkedHashSet<>();
        for (String value : values) out.add(value);
        return out;
    }

    public static final class Config {
        public boolean enabled;
        public String worldPrefix;
        public boolean excludeLegendary;
        public boolean excludeMythical;
        public boolean excludeUltraBeast;
        public boolean excludeParadox;
        public float weight;
        public boolean closeSpawnerEnabled;
        public int closeSpawnerMinDistance;
        public int closeSpawnerMaxDistance;
        public int closeSpawnerZoneDiameter;
        public int closeSpawnerZoneHeight;
        public int islanderMaxNearbyPokemon;
        public Set<String> spawnablePositionTypes = new LinkedHashSet<>();
        public List<Tier> progressionTiers = new ArrayList<>();
        public Set<String> legendarySpecies = new LinkedHashSet<>();
        public Set<String> mythicalSpecies = new LinkedHashSet<>();
        public Set<String> ultraBeastSpecies = new LinkedHashSet<>();
        public Set<String> paradoxSpecies = new LinkedHashSet<>();
        public Set<String> customSpecialSpawnOnlySpecies = new LinkedHashSet<>();
    }

    public static final class Tier {
        public boolean enabled = true;
        public String id;
        public String displayName;
        public long minPlaytimeSeconds;
        public int minLevel;
        public int maxLevel;
        /** 0 = base species only, 1 = first evolutions too, 2+ = later evolutions too. */
        public int maxEvolutionStage;
        public float weightMultiplier = 1.0F;
        /** Optional override. If non-empty, only these species can spawn in this tier. */
        public Set<String> allowSpecies = new LinkedHashSet<>();
        /** Optional manual denylist for this tier. */
        public Set<String> denySpecies = new LinkedHashSet<>();
    }
}
