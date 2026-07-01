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
        if (DATA.legendarySpawns == null) DATA.legendarySpawns = new ArrayList<>();
        if (DATA.mythicalSpawns == null) DATA.mythicalSpawns = new ArrayList<>();
        if (DATA.paradoxSpawns == null) DATA.paradoxSpawns = new ArrayList<>();
        if (DATA.ultraBeastSpawns == null) DATA.ultraBeastSpawns = new ArrayList<>();
        mergeMissingSpawns(DATA.legendarySpawns, d.legendarySpawns);
        mergeMissingSpawns(DATA.mythicalSpawns, d.mythicalSpawns);
        mergeMissingSpawns(DATA.paradoxSpawns, d.paradoxSpawns);
        mergeMissingSpawns(DATA.ultraBeastSpawns, d.ultraBeastSpawns);
        if (DATA.disabledDimensions == null) DATA.disabledDimensions = d.disabledDimensions;
        if (DATA.checkIntervalTicks <= 0) DATA.checkIntervalTicks = d.checkIntervalTicks;
        if (DATA.targetAverageSpawnMinutes <= 0.0D) DATA.targetAverageSpawnMinutes = d.targetAverageSpawnMinutes;
        if (DATA.minimumTargetAverageSpawnMinutes <= 0.0D) DATA.minimumTargetAverageSpawnMinutes = d.minimumTargetAverageSpawnMinutes;
        if (DATA.baseChanceMultiplier <= 0.0D) DATA.baseChanceMultiplier = d.baseChanceMultiplier;
        if (DATA.pityChanceIncreasePerTargetWindow < 0.0D) DATA.pityChanceIncreasePerTargetWindow = d.pityChanceIncreasePerTargetWindow;
        if (DATA.maxPityMultiplier <= 0.0D) DATA.maxPityMultiplier = d.maxPityMultiplier;
        if (DATA.paradoxCheckIntervalTicks <= 0) DATA.paradoxCheckIntervalTicks = d.paradoxCheckIntervalTicks;
        if (DATA.paradoxTargetAverageSpawnMinutes <= 0.0D) DATA.paradoxTargetAverageSpawnMinutes = d.paradoxTargetAverageSpawnMinutes;
        if (DATA.islanderParadoxTargetAverageSpawnMinutes <= 0.0D) DATA.islanderParadoxTargetAverageSpawnMinutes = d.islanderParadoxTargetAverageSpawnMinutes;
        if (DATA.minimumParadoxTargetAverageSpawnMinutes <= 0.0D) DATA.minimumParadoxTargetAverageSpawnMinutes = d.minimumParadoxTargetAverageSpawnMinutes;
        if (DATA.paradoxBaseChanceMultiplier <= 0.0D) DATA.paradoxBaseChanceMultiplier = d.paradoxBaseChanceMultiplier;
        if (DATA.paradoxPityChanceIncreasePerTargetWindow < 0.0D) DATA.paradoxPityChanceIncreasePerTargetWindow = d.paradoxPityChanceIncreasePerTargetWindow;
        if (DATA.paradoxMaxPityMultiplier <= 0.0D) DATA.paradoxMaxPityMultiplier = d.paradoxMaxPityMultiplier;
        if (DATA.maxAliveParadoxWildPokemon <= 0) DATA.maxAliveParadoxWildPokemon = d.maxAliveParadoxWildPokemon;
        if (DATA.ultraBeastCheckIntervalTicks <= 0) DATA.ultraBeastCheckIntervalTicks = d.ultraBeastCheckIntervalTicks;
        if (DATA.ultraBeastTargetAverageSpawnMinutes <= 0.0D) DATA.ultraBeastTargetAverageSpawnMinutes = d.ultraBeastTargetAverageSpawnMinutes;
        if (DATA.islanderUltraBeastTargetAverageSpawnMinutes <= 0.0D) DATA.islanderUltraBeastTargetAverageSpawnMinutes = d.islanderUltraBeastTargetAverageSpawnMinutes;
        if (DATA.minimumUltraBeastTargetAverageSpawnMinutes <= 0.0D) DATA.minimumUltraBeastTargetAverageSpawnMinutes = d.minimumUltraBeastTargetAverageSpawnMinutes;
        if (DATA.ultraBeastBaseChanceMultiplier <= 0.0D) DATA.ultraBeastBaseChanceMultiplier = d.ultraBeastBaseChanceMultiplier;
        if (DATA.ultraBeastPityChanceIncreasePerTargetWindow < 0.0D) DATA.ultraBeastPityChanceIncreasePerTargetWindow = d.ultraBeastPityChanceIncreasePerTargetWindow;
        if (DATA.ultraBeastMaxPityMultiplier <= 0.0D) DATA.ultraBeastMaxPityMultiplier = d.ultraBeastMaxPityMultiplier;
        if (DATA.maxAliveUltraBeastWildPokemon <= 0) DATA.maxAliveUltraBeastWildPokemon = d.maxAliveUltraBeastWildPokemon;
        if (DATA.playerScalingStartPlayers <= 0) DATA.playerScalingStartPlayers = d.playerScalingStartPlayers;
        if (DATA.playerScalingMaxPlayers <= DATA.playerScalingStartPlayers) DATA.playerScalingMaxPlayers = d.playerScalingMaxPlayers;
        if (DATA.minDistanceFromPlayer < 8) DATA.minDistanceFromPlayer = d.minDistanceFromPlayer;
        if (DATA.maxDistanceFromPlayer < DATA.minDistanceFromPlayer) DATA.maxDistanceFromPlayer = d.maxDistanceFromPlayer;
        if (DATA.maxAliveSpecialWildPokemon <= 0) DATA.maxAliveSpecialWildPokemon = d.maxAliveSpecialWildPokemon;
        if (DATA.levelRangeLegendary == null || DATA.levelRangeLegendary.isBlank()) DATA.levelRangeLegendary = d.levelRangeLegendary;
        if (DATA.levelRangeParadox == null || DATA.levelRangeParadox.isBlank()) DATA.levelRangeParadox = d.levelRangeParadox;
        if (DATA.levelRangeUltraBeast == null || DATA.levelRangeUltraBeast.isBlank()) DATA.levelRangeUltraBeast = d.levelRangeUltraBeast;
        if (DATA.levelRangeMythical == null || DATA.levelRangeMythical.isBlank()) DATA.levelRangeMythical = d.levelRangeMythical;
        if (DATA.islanderWorldPrefix == null || DATA.islanderWorldPrefix.isBlank()) DATA.islanderWorldPrefix = d.islanderWorldPrefix;
        if (DATA.islanderMinimumProfilePlaytimeSeconds < 0L) DATA.islanderMinimumProfilePlaytimeSeconds = d.islanderMinimumProfilePlaytimeSeconds;
        if (DATA.islanderTargetAverageSpawnMinutes <= 0.0D) DATA.islanderTargetAverageSpawnMinutes = d.islanderTargetAverageSpawnMinutes;
        if (DATA.islanderLegendaryChancePerCheck <= 0.0D) DATA.islanderLegendaryChancePerCheck = d.islanderLegendaryChancePerCheck;
        if (DATA.islanderParadoxChancePerCheck < 0.0D) DATA.islanderParadoxChancePerCheck = d.islanderParadoxChancePerCheck;
        if (DATA.islanderUltraBeastChancePerCheck <= 0.0D) DATA.islanderUltraBeastChancePerCheck = d.islanderUltraBeastChancePerCheck;
        if (DATA.islanderMythicalChancePerCheck <= 0.0D) DATA.islanderMythicalChancePerCheck = d.islanderMythicalChancePerCheck;
        DATA.removeBiomeRequirements = true;
        DATA.rareTripleSpawnEventEnabled = false;
        clearSpawnRequirements(DATA.legendarySpawns, DATA.mythicalSpawns, DATA.paradoxSpawns, DATA.ultraBeastSpawns);
        DATA.islanderLegendarySpawns = copyClean(DATA.legendarySpawns);
        DATA.islanderMythicalSpawns = copyClean(DATA.mythicalSpawns);
        DATA.islanderParadoxSpawns = copyClean(DATA.paradoxSpawns);
        DATA.islanderUltraBeastSpawns = copyClean(DATA.ultraBeastSpawns);
    }

    private static Data defaults() {
        Data root = new Data();
        root.enabled = true;
        root.disableVanillaAndAllTheMonsSpecialSpawns = true;
        root.broadcastLegendarySpawns = true;
        root.broadcastParadoxAndUltraBeastSpawns = false;
        root.debugSpecialSpawnRolls = false;
        root.checkIntervalTicks = 1200;
        root.targetAverageSpawnMinutes = 180.0D;
        root.minimumTargetAverageSpawnMinutes = 60.0D;
        root.baseChanceMultiplier = 1.0D;
        root.pityChanceIncreasePerTargetWindow = 1.0D;
        root.maxPityMultiplier = 6.0D;
        root.paradoxOnlySpawnsEnabled = true;
        root.paradoxCheckIntervalTicks = 1200;
        root.paradoxTargetAverageSpawnMinutes = 60.0D;
        root.islanderParadoxTargetAverageSpawnMinutes = 60.0D;
        root.minimumParadoxTargetAverageSpawnMinutes = 15.0D;
        root.paradoxBaseChanceMultiplier = 1.0D;
        root.paradoxPityChanceIncreasePerTargetWindow = 1.0D;
        root.paradoxMaxPityMultiplier = 6.0D;
        root.maxAliveParadoxWildPokemon = 2;
        root.ultraBeastCheckIntervalTicks = 1200;
        root.ultraBeastTargetAverageSpawnMinutes = 120.0D;
        root.islanderUltraBeastTargetAverageSpawnMinutes = 120.0D;
        root.minimumUltraBeastTargetAverageSpawnMinutes = 30.0D;
        root.ultraBeastBaseChanceMultiplier = 1.0D;
        root.ultraBeastPityChanceIncreasePerTargetWindow = 1.0D;
        root.ultraBeastMaxPityMultiplier = 6.0D;
        root.maxAliveUltraBeastWildPokemon = 2;
        root.playerScalingStartPlayers = 10;
        root.playerScalingMaxPlayers = 100;
        root.removeBiomeRequirements = true;
        root.rareTripleSpawnEventEnabled = false;
        root.rareTripleSpawnEventChance = 0.0D;
        root.rareTripleSpawnEventSpawnCount = 1;
        root.rareTripleSpawnEventMessage = "";
        root.legendaryChancePerCheck = 1.0D;
        root.paradoxChancePerCheck = 1.0D;
        root.ultraBeastChancePerCheck = 1.0D;
        root.mythicalChancePerCheck = 1.0D;
        root.minDistanceFromPlayer = 48;
        root.maxDistanceFromPlayer = 96;
        root.maxAliveSpecialWildPokemon = 3;
        root.levelRangeLegendary = "60-80";
        root.levelRangeParadox = "45-65";
        root.levelRangeUltraBeast = "50-70";
        root.levelRangeMythical = "50-70";
        root.islanderSpecialSpawnsEnabled = true;
        root.islanderWorldPrefix = "islander_";
        root.islanderOnlyNotifyIslanders = true;
        root.islanderMinimumProfilePlaytimeSeconds = 10L * 60L * 60L;
        root.islanderTargetAverageSpawnMinutes = 180.0D;
        root.islanderLegendaryChancePerCheck = 1.0D;
        root.islanderParadoxChancePerCheck = 1.0D;
        root.islanderUltraBeastChancePerCheck = 1.0D;
        root.islanderMythicalChancePerCheck = 1.0D;
        root.disabledDimensions = new ArrayList<>(List.of("multiworld:spawn1", "multiworld:spawn", "minecraft:the_end"));
        root.legendarySpawns = new ArrayList<>(List.of(
                entry("articuno"),
                entry("zapdos"),
                entry("moltres"),
                entry("mewtwo"),
                entry("raikou"),
                entry("entei"),
                entry("suicune"),
                entry("lugia"),
                entry("ho_oh"),
                entry("regirock"),
                entry("regice"),
                entry("registeel"),
                entry("latias"),
                entry("latios"),
                entry("kyogre"),
                entry("groudon"),
                entry("rayquaza"),
                entry("uxie"),
                entry("mesprit"),
                entry("azelf"),
                entry("dialga"),
                entry("palkia"),
                entry("heatran"),
                entry("regigigas"),
                entry("giratina"),
                entry("cresselia"),
                entry("cobalion"),
                entry("terrakion"),
                entry("virizion"),
                entry("tornadus"),
                entry("thundurus"),
                entry("reshiram"),
                entry("zekrom"),
                entry("landorus"),
                entry("kyurem"),
                entry("xerneas"),
                entry("yveltal"),
                entry("zygarde"),
                entry("type_null"),
                entry("silvally"),
                entry("tapu_koko"),
                entry("tapu_lele"),
                entry("tapu_bulu"),
                entry("tapu_fini"),
                entry("cosmog"),
                entry("cosmoem"),
                entry("solgaleo"),
                entry("lunala"),
                entry("necrozma"),
                entry("zacian"),
                entry("zamazenta"),
                entry("eternatus"),
                entry("kubfu"),
                entry("urshifu"),
                entry("regieleki"),
                entry("regidrago"),
                entry("glastrier"),
                entry("spectrier"),
                entry("calyrex"),
                entry("enamorus"),
                entry("wo_chien"),
                entry("chien_pao"),
                entry("ting_lu"),
                entry("chi_yu"),
                entry("koraidon"),
                entry("miraidon"),
                entry("okidogi"),
                entry("munkidori"),
                entry("fezandipiti"),
                entry("ogerpon"),
                entry("terapagos")
        ));
        root.mythicalSpawns = new ArrayList<>(List.of(
                entry("mew"),
                entry("celebi"),
                entry("jirachi"),
                entry("deoxys"),
                entry("phione"),
                entry("manaphy"),
                entry("darkrai"),
                entry("shaymin"),
                entry("arceus"),
                entry("victini"),
                entry("keldeo"),
                entry("meloetta"),
                entry("genesect"),
                entry("diancie"),
                entry("hoopa"),
                entry("volcanion"),
                entry("magearna"),
                entry("marshadow"),
                entry("zeraora"),
                entry("meltan"),
                entry("melmetal"),
                entry("zarude"),
                entry("pecharunt")
        ));
        root.paradoxSpawns = new ArrayList<>(List.of(
                entry("great_tusk"),
                entry("scream_tail"),
                entry("brute_bonnet"),
                entry("flutter_mane"),
                entry("slither_wing"),
                entry("sandy_shocks"),
                entry("roaring_moon"),
                entry("walking_wake"),
                entry("gouging_fire"),
                entry("raging_bolt"),
                entry("iron_treads"),
                entry("iron_bundle"),
                entry("iron_hands"),
                entry("iron_jugulis"),
                entry("iron_moth"),
                entry("iron_thorns"),
                entry("iron_valiant"),
                entry("iron_leaves"),
                entry("iron_crown"),
                entry("iron_boulder")
        ));
        root.ultraBeastSpawns = new ArrayList<>(List.of(
                entry("nihilego"),
                entry("buzzwole"),
                entry("pheromosa"),
                entry("xurkitree"),
                entry("celesteela"),
                entry("kartana"),
                entry("guzzlord"),
                entry("poipole"),
                entry("naganadel"),
                entry("stakataka"),
                entry("blacephalon")
        ));
        root.islanderLegendarySpawns = copyClean(root.legendarySpawns);
        root.islanderMythicalSpawns = copyClean(root.mythicalSpawns);
        root.islanderParadoxSpawns = copyClean(root.paradoxSpawns);
        root.islanderUltraBeastSpawns = copyClean(root.ultraBeastSpawns);
        return root;
    }

    @SafeVarargs
    private static void clearSpawnRequirements(List<SpawnEntry>... lists) {
        for (List<SpawnEntry> list : lists) {
            if (list == null) continue;
            for (SpawnEntry entry : list) {
                if (entry == null) continue;
                entry.biomes = new ArrayList<>();
                entry.times = new ArrayList<>();
                if (entry.weight <= 0.0D) entry.weight = 1.0D;
            }
        }
    }

    private static void mergeMissingSpawns(List<SpawnEntry> target, List<SpawnEntry> defaults) {
        Set<String> existing = new HashSet<>();
        for (SpawnEntry entry : target) {
            if (entry != null && entry.species != null) existing.add(normalizeSpecies(entry.species));
        }
        for (SpawnEntry entry : defaults) {
            if (entry == null || entry.species == null) continue;
            if (existing.add(normalizeSpecies(entry.species))) target.add(copy(entry));
        }
    }

    private static String normalizeSpecies(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int namespace = value.lastIndexOf(':');
        if (namespace >= 0 && namespace + 1 < value.length()) value = value.substring(namespace + 1);
        return value.replace(' ', '_').replace('-', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static List<SpawnEntry> copyClean(List<SpawnEntry> entries) {
        List<SpawnEntry> copy = new ArrayList<>();
        if (entries == null) return copy;
        for (SpawnEntry entry : entries) copy.add(copy(entry));
        clearSpawnRequirements(copy);
        return copy;
    }

    private static SpawnEntry copy(SpawnEntry entry) {
        SpawnEntry e = new SpawnEntry();
        e.species = entry == null ? "" : entry.species;
        e.biomes = new ArrayList<>();
        e.times = new ArrayList<>();
        e.weight = entry == null || entry.weight <= 0.0D ? 1.0D : entry.weight;
        return e;
    }

    private static SpawnEntry entry(String species) {
        SpawnEntry e = new SpawnEntry();
        e.species = species;
        e.biomes = new ArrayList<>();
        e.times = new ArrayList<>();
        e.weight = 1.0D;
        return e;
    }

    public static final class Data {
        public boolean enabled;
        public boolean disableVanillaAndAllTheMonsSpecialSpawns;
        public boolean broadcastLegendarySpawns;
        public boolean broadcastParadoxAndUltraBeastSpawns;
        public boolean debugSpecialSpawnRolls;
        public int checkIntervalTicks;
        public double targetAverageSpawnMinutes;
        public double minimumTargetAverageSpawnMinutes;
        public double baseChanceMultiplier;
        public double pityChanceIncreasePerTargetWindow;
        public double maxPityMultiplier;
        public boolean paradoxOnlySpawnsEnabled;
        public int paradoxCheckIntervalTicks;
        public double paradoxTargetAverageSpawnMinutes;
        public double islanderParadoxTargetAverageSpawnMinutes;
        public double minimumParadoxTargetAverageSpawnMinutes;
        public double paradoxBaseChanceMultiplier;
        public double paradoxPityChanceIncreasePerTargetWindow;
        public double paradoxMaxPityMultiplier;
        public int maxAliveParadoxWildPokemon;
        public int ultraBeastCheckIntervalTicks;
        public double ultraBeastTargetAverageSpawnMinutes;
        public double islanderUltraBeastTargetAverageSpawnMinutes;
        public double minimumUltraBeastTargetAverageSpawnMinutes;
        public double ultraBeastBaseChanceMultiplier;
        public double ultraBeastPityChanceIncreasePerTargetWindow;
        public double ultraBeastMaxPityMultiplier;
        public int maxAliveUltraBeastWildPokemon;
        public int playerScalingStartPlayers;
        public int playerScalingMaxPlayers;
        public boolean removeBiomeRequirements;
        public boolean rareTripleSpawnEventEnabled;
        public double rareTripleSpawnEventChance;
        public int rareTripleSpawnEventSpawnCount;
        public String rareTripleSpawnEventMessage;
        public double legendaryChancePerCheck;
        public double paradoxChancePerCheck;
        public double ultraBeastChancePerCheck;
        public double mythicalChancePerCheck;
        public int minDistanceFromPlayer;
        public int maxDistanceFromPlayer;
        public int maxAliveSpecialWildPokemon;
        public String levelRangeLegendary;
        public String levelRangeParadox;
        public String levelRangeUltraBeast;
        public String levelRangeMythical;
        public boolean islanderSpecialSpawnsEnabled;
        public String islanderWorldPrefix;
        public boolean islanderOnlyNotifyIslanders;
        public long islanderMinimumProfilePlaytimeSeconds;
        public double islanderTargetAverageSpawnMinutes;
        public double islanderLegendaryChancePerCheck;
        public double islanderParadoxChancePerCheck;
        public double islanderUltraBeastChancePerCheck;
        public double islanderMythicalChancePerCheck;
        public List<String> disabledDimensions;
        public List<SpawnEntry> legendarySpawns;
        public List<SpawnEntry> paradoxSpawns;
        public List<SpawnEntry> ultraBeastSpawns;
        public List<SpawnEntry> mythicalSpawns;
        public List<SpawnEntry> islanderLegendarySpawns;
        public List<SpawnEntry> islanderParadoxSpawns;
        public List<SpawnEntry> islanderUltraBeastSpawns;
        public List<SpawnEntry> islanderMythicalSpawns;
    }

    public static final class SpawnEntry {
        public String species;
        public List<String> biomes;
        public List<String> times;
        public double weight = 1.0D;
    }
}
