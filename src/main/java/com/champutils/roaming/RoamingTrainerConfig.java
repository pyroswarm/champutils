package com.champutils.roaming;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RoamingTrainerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "roaming_trainers.json");

    public static ConfigRoot DATA = defaultConfig();

    private RoamingTrainerConfig() {}

    public static class ConfigRoot {
        public boolean enabled = true;
        public int scanIntervalSeconds = 120;
        public int spawnCheckSeconds = 120;
        public int maxTrainersPerPlayer = 1;
        public int maxNearbyPerPlayer = 1;
        public int maxTrainersPerWorld = 35;
        public int maxWorldTotal = 35;
        public double spawnChancePerScan = 0.08D;
        public double spawnChancePerCheck = 0.08D;
        public boolean movementEnabled = true;
        public int wanderRadiusBlocks = 18;
        public int wanderEverySecondsMin = 8;
        public int wanderEverySecondsMax = 20;
        public double wanderSpeed = 0.8D;
        public int spawnMinDistance = 32;
        public int minimumDistance = 32;
        public int spawnMaxDistance = 96;
        public int maximumDistance = 96;
        public int activePlayerRadius = 96;
        public int despawnAfterNoPlayersSeconds = 1200;
        public int despawnMinutes = 20;
        public int noPlayerNearbyDespawnSeconds = 300;
        public boolean doNotDespawnWhileInBattle = true;
        public boolean requireSolidGround = true;
        public int maxSpawnAttemptsPerPlayer = 12;
        public int islanderSpawnMinDistance = 12;
        public int islanderSpawnMaxDistance = 48;
        public int islanderMaxSpawnAttemptsPerPlayer = 48;
        public List<String> blockedDimensions = new ArrayList<>();
        public boolean allowAllPokemonFromCobblemonRegistry = true;
        public List<String> blacklistedPokemon = new ArrayList<>();
        public Map<String, RaritySettings> rarities = new LinkedHashMap<>();
        public List<String> defaultSpeciesPool = new ArrayList<>();
        public List<String> basicSpeciesPool = new ArrayList<>();
        public List<String> strongSpeciesPool = new ArrayList<>();
        public List<String> eliteSpeciesPool = new ArrayList<>();
        public List<String> legendarySpeciesPool = new ArrayList<>();
        public List<String> ultraBeastSpeciesPool = new ArrayList<>();
        public List<String> paradoxSpeciesPool = new ArrayList<>();
        public List<String> mythicSpeciesPool = new ArrayList<>();
        public List<String> competitiveHeldItems = new ArrayList<>();
        public List<String> competitiveNatures = new ArrayList<>();
        public List<String> randomTrainerSkins = new ArrayList<>();
        public List<String> maleTrainerSkins = new ArrayList<>();
        public List<String> femaleTrainerSkins = new ArrayList<>();
        public boolean allowCompetitiveMoves = true;
    }

    public static class RaritySettings {
        public double weight = 1.0D;
        public int pokemonCount = 1;
        public int levelOffsetMin = 0;
        public int levelOffsetMax = 0;
        public int aiSkill = 3;
        public double evolvedSpeciesChance = 0.0D;
        public double heldItemChance = 0.0D;
        public double competitiveNatureChance = 0.0D;
        public double shinyChance = 0.0D;
        public int legendaryPokemonCount = 0;
        public int fragmentMin = 1;
        public int fragmentMax = 1;
        public List<String> rewardCommands = new ArrayList<>();
        public List<String> speciesPool = new ArrayList<>();
        /** Detailed competitive pool. If present, roaming trainers pull configured Pokemon sets from here first. */
        public List<PokemonPoolEntry> pool = new ArrayList<>();
        public List<String> trainerNames = new ArrayList<>();
        /** Chance to use the full Cobblemon registry pool instead of the curated pool for non-forced slots. */
        public double allPokemonChance = 0.0D;
    }


    public static class PokemonPoolEntry {
        public String species = "eevee";
        public int level = 50;
        public String nature = "jolly";
        public Map<String, Integer> ivs = new LinkedHashMap<>();
        public Map<String, Integer> evs = new LinkedHashMap<>();
        public String ability = "";
        public String heldItem = "";
        public List<String> moves = new ArrayList<>();
        public double weight = 1.0D;
        public List<String> tags = new ArrayList<>();
        public String role = "flex";
    }

    public static synchronized void load() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            if (!FILE.exists()) {
                DATA = defaultConfig();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                ConfigRoot loaded = GSON.fromJson(reader, ConfigRoot.class);
                DATA = loaded == null ? defaultConfig() : loaded;
            }

            sanitize();
            save();
            System.out.println("[ChampUtils] Loaded roaming_trainers.json");
        } catch (Exception e) {
            e.printStackTrace();
            DATA = defaultConfig();
        }
    }

    public static synchronized void save() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(DATA, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sanitize() {
        if (DATA.blockedDimensions == null) DATA.blockedDimensions = new ArrayList<>();
        if (DATA.rarities == null) DATA.rarities = new LinkedHashMap<>();
        if (DATA.blacklistedPokemon == null) DATA.blacklistedPokemon = defaultBlacklistedPokemon();
        if (DATA.defaultSpeciesPool == null || DATA.defaultSpeciesPool.isEmpty()) DATA.defaultSpeciesPool = defaultBasicSpecies();
        if (DATA.basicSpeciesPool == null || DATA.basicSpeciesPool.isEmpty()) DATA.basicSpeciesPool = defaultBasicSpecies();
        if (DATA.strongSpeciesPool == null || DATA.strongSpeciesPool.isEmpty()) DATA.strongSpeciesPool = defaultStrongSpecies();
        if (DATA.eliteSpeciesPool == null || DATA.eliteSpeciesPool.isEmpty()) DATA.eliteSpeciesPool = defaultEliteSpecies();
        if (DATA.legendarySpeciesPool == null || DATA.legendarySpeciesPool.isEmpty()) DATA.legendarySpeciesPool = defaultLegendarySpecies();
        if (DATA.ultraBeastSpeciesPool == null || DATA.ultraBeastSpeciesPool.isEmpty()) DATA.ultraBeastSpeciesPool = defaultUltraBeastSpecies();
        if (DATA.paradoxSpeciesPool == null || DATA.paradoxSpeciesPool.isEmpty()) DATA.paradoxSpeciesPool = defaultParadoxSpecies();
        if (DATA.mythicSpeciesPool == null || DATA.mythicSpeciesPool.isEmpty()) DATA.mythicSpeciesPool = defaultMythicSpecies();
        if (DATA.competitiveHeldItems == null) DATA.competitiveHeldItems = defaultHeldItems();
        if (DATA.competitiveNatures == null) DATA.competitiveNatures = defaultNatures();
        DATA.randomTrainerSkins = cleanTrainerSkins(DATA.randomTrainerSkins);
        DATA.maleTrainerSkins = cleanTrainerSkins(DATA.maleTrainerSkins, defaultMaleTrainerSkins());
        DATA.femaleTrainerSkins = cleanTrainerSkins(DATA.femaleTrainerSkins, defaultFemaleTrainerSkins());
        if (DATA.scanIntervalSeconds == 300 && DATA.spawnCheckSeconds == 300) {
            DATA.scanIntervalSeconds = 60;
            DATA.spawnCheckSeconds = 60;
        }
        if (DATA.scanIntervalSeconds == 120 && DATA.spawnCheckSeconds == 120) {
            DATA.scanIntervalSeconds = 60;
            DATA.spawnCheckSeconds = 60;
        }
        DATA.scanIntervalSeconds = DATA.spawnCheckSeconds > 0 ? DATA.spawnCheckSeconds : DATA.scanIntervalSeconds;
        DATA.spawnCheckSeconds = DATA.scanIntervalSeconds;
        DATA.maxTrainersPerPlayer = DATA.maxNearbyPerPlayer > 0 ? DATA.maxNearbyPerPlayer : DATA.maxTrainersPerPlayer;
        DATA.maxNearbyPerPlayer = DATA.maxTrainersPerPlayer;
        if (DATA.maxTrainersPerWorld == 20 && DATA.maxWorldTotal == 20) {
            DATA.maxTrainersPerWorld = 50;
            DATA.maxWorldTotal = 50;
        }
        if (DATA.maxTrainersPerWorld == 35 && DATA.maxWorldTotal == 35) {
            DATA.maxTrainersPerWorld = 50;
            DATA.maxWorldTotal = 50;
        }
        DATA.maxTrainersPerWorld = DATA.maxWorldTotal > 0 ? DATA.maxWorldTotal : DATA.maxTrainersPerWorld;
        DATA.maxWorldTotal = DATA.maxTrainersPerWorld;
        if (DATA.spawnChancePerScan == 0.01D && DATA.spawnChancePerCheck == 0.01D) {
            DATA.spawnChancePerScan = 0.16D;
            DATA.spawnChancePerCheck = 0.16D;
        }
        if (DATA.spawnChancePerScan == 0.08D && DATA.spawnChancePerCheck == 0.08D) {
            DATA.spawnChancePerScan = 0.16D;
            DATA.spawnChancePerCheck = 0.16D;
        }
        DATA.spawnChancePerScan = DATA.spawnChancePerCheck > 0.0D ? DATA.spawnChancePerCheck : DATA.spawnChancePerScan;
        DATA.spawnChancePerCheck = DATA.spawnChancePerScan;
        DATA.spawnMinDistance = DATA.minimumDistance > 0 ? DATA.minimumDistance : DATA.spawnMinDistance;
        DATA.minimumDistance = DATA.spawnMinDistance;
        DATA.spawnMaxDistance = DATA.maximumDistance > 0 ? DATA.maximumDistance : DATA.spawnMaxDistance;
        DATA.maximumDistance = DATA.spawnMaxDistance;
        if (DATA.despawnAfterNoPlayersSeconds == 900 && DATA.despawnMinutes == 15) {
            DATA.despawnAfterNoPlayersSeconds = 1200;
            DATA.despawnMinutes = 20;
        }
        if (DATA.despawnMinutes > 0) DATA.despawnAfterNoPlayersSeconds = DATA.despawnMinutes * 60;
        DATA.despawnMinutes = Math.max(1, DATA.despawnAfterNoPlayersSeconds / 60);
        if (DATA.scanIntervalSeconds < 5) DATA.scanIntervalSeconds = 5;
        if (DATA.spawnMinDistance < 8) DATA.spawnMinDistance = 8;
        if (DATA.spawnMaxDistance < DATA.spawnMinDistance) DATA.spawnMaxDistance = DATA.spawnMinDistance + 12;
        if (DATA.activePlayerRadius < 16) DATA.activePlayerRadius = 16;
        if (DATA.despawnAfterNoPlayersSeconds < 30) DATA.despawnAfterNoPlayersSeconds = 30;
        if (DATA.maxSpawnAttemptsPerPlayer < 1) DATA.maxSpawnAttemptsPerPlayer = 1;
        if (DATA.islanderSpawnMinDistance < 4) DATA.islanderSpawnMinDistance = 4;
        if (DATA.islanderSpawnMaxDistance < DATA.islanderSpawnMinDistance) DATA.islanderSpawnMaxDistance = DATA.islanderSpawnMinDistance + 12;
        if (DATA.islanderMaxSpawnAttemptsPerPlayer < DATA.maxSpawnAttemptsPerPlayer) DATA.islanderMaxSpawnAttemptsPerPlayer = DATA.maxSpawnAttemptsPerPlayer;
        if (DATA.maxTrainersPerWorld < 1) DATA.maxTrainersPerWorld = 1;
        if (DATA.spawnChancePerScan < 0.0D) DATA.spawnChancePerScan = 0.0D;
        if (DATA.spawnChancePerScan > 1.0D) DATA.spawnChancePerScan = 1.0D;
        if (DATA.wanderRadiusBlocks < 4) DATA.wanderRadiusBlocks = 4;
        if (DATA.wanderRadiusBlocks > 64) DATA.wanderRadiusBlocks = 64;
        if (DATA.wanderEverySecondsMin < 3) DATA.wanderEverySecondsMin = 3;
        if (DATA.wanderEverySecondsMax < DATA.wanderEverySecondsMin) DATA.wanderEverySecondsMax = DATA.wanderEverySecondsMin + 5;
        if (DATA.wanderSpeed <= 0.0D) DATA.wanderSpeed = 0.8D;
        if (DATA.wanderSpeed > 1.5D) DATA.wanderSpeed = 1.5D;

        for (RoamingTrainerRarity rarity : RoamingTrainerRarity.values()) {
            RaritySettings settings = DATA.rarities.computeIfAbsent(rarity.name(), key -> defaultRarity(rarity));
            if (settings.pool == null) settings.pool = new ArrayList<>();
            settings.pokemonCount = desiredPokemonCount(rarity);
            settings.aiSkill = desiredAiSkill(rarity);
            if (settings.allPokemonChance <= 0.0D) settings.allPokemonChance = desiredAllPokemonChance(rarity);
            if (settings.allPokemonChance > 1.0D) settings.allPokemonChance = 1.0D;
            settings.heldItemChance = 1.0D;
            settings.competitiveNatureChance = 1.0D;
            if (settings.trainerNames == null || settings.trainerNames.isEmpty()) {
                settings.trainerNames = defaultTrainerNames(rarity);
            } else {
                settings.trainerNames = cleanTrainerNames(settings.trainerNames, rarity);
            }
        }
    }

    private static int desiredPokemonCount(RoamingTrainerRarity rarity) {
        return switch (rarity) {
            case COMMON, UNCOMMON, RARE -> 3;
            case EPIC -> 4;
            case LEGENDARY -> 5;
            case MYTHIC -> 6;
        };
    }

    private static int desiredAiSkill(RoamingTrainerRarity rarity) {
        // Every roaming trainer rarity should use the strongest available battle AI.
        return 5;
    }

    private static double desiredAllPokemonChance(RoamingTrainerRarity rarity) {
        return switch (rarity) {
            case COMMON -> 0.85D;
            case UNCOMMON -> 0.60D;
            case RARE -> 0.35D;
            case EPIC -> 0.55D;
            case LEGENDARY -> 0.65D;
            case MYTHIC -> 0.70D;
        };
    }

    public static boolean isIslanderDimension(String dimensionId) {
        if (dimensionId == null) return false;
        String normalized = dimensionId.toLowerCase(Locale.ROOT);
        return normalized.equals("multiworld:islander")
                || normalized.equals("islander")
                || normalized.startsWith("multiworld:islander_")
                || normalized.startsWith("islander_")
                || normalized.contains(":islander_")
                || normalized.contains("/islander_");
    }

    public static boolean isBlockedDimension(String dimensionId) {
        if (dimensionId == null) return false;
        String normalized = dimensionId.toLowerCase(Locale.ROOT);

        // Islander worlds are allowed even if the world implementation names them like a territory.
        // This prevents the generic territory block from accidentally disabling roaming trainers there.
        if (isIslanderDimension(normalized)) return false;

        if (normalized.contains("territor")) return true;
        for (String blocked : DATA.blockedDimensions) {
            if (blocked == null || blocked.isBlank()) continue;
            String b = blocked.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals(b) || normalized.endsWith(":" + b) || normalized.endsWith("/" + b)) return true;
        }
        return false;
    }

    public static RaritySettings settings(RoamingTrainerRarity rarity) {
        sanitize();
        return DATA.rarities.getOrDefault(rarity.name(), defaultRarity(rarity));
    }

    private static ConfigRoot defaultConfig() {
        ConfigRoot root = new ConfigRoot();
        root.scanIntervalSeconds = 60;
        root.spawnCheckSeconds = 60;
        root.spawnChancePerScan = 0.16D;
        root.spawnChancePerCheck = 0.16D;
        root.maxTrainersPerPlayer = 2;
        root.maxNearbyPerPlayer = 2;
        root.maxTrainersPerWorld = 50;
        root.maxWorldTotal = 50;
        root.spawnMinDistance = 32;
        root.minimumDistance = 32;
        root.spawnMaxDistance = 96;
        root.maximumDistance = 96;
        root.despawnAfterNoPlayersSeconds = 900;
        root.despawnMinutes = 20;
        root.noPlayerNearbyDespawnSeconds = 300;
        root.movementEnabled = true;
        root.wanderRadiusBlocks = 18;
        root.wanderEverySecondsMin = 8;
        root.wanderEverySecondsMax = 20;
        root.wanderSpeed = 0.8D;
        root.blockedDimensions.add("spawn1");
        root.blockedDimensions.add("multiworld:spawn1");
        root.blacklistedPokemon = defaultBlacklistedPokemon();
        root.defaultSpeciesPool = defaultBasicSpecies();
        root.basicSpeciesPool = defaultBasicSpecies();
        root.strongSpeciesPool = defaultStrongSpecies();
        root.eliteSpeciesPool = defaultEliteSpecies();
        root.legendarySpeciesPool = defaultLegendarySpecies();
        root.ultraBeastSpeciesPool = defaultUltraBeastSpecies();
        root.paradoxSpeciesPool = defaultParadoxSpecies();
        root.mythicSpeciesPool = defaultMythicSpecies();
        root.competitiveHeldItems = defaultHeldItems();
        root.competitiveNatures = defaultNatures();
        root.randomTrainerSkins = defaultTrainerSkins();
        root.maleTrainerSkins = defaultMaleTrainerSkins();
        root.femaleTrainerSkins = defaultFemaleTrainerSkins();
        for (RoamingTrainerRarity rarity : RoamingTrainerRarity.values()) {
            root.rarities.put(rarity.name(), defaultRarity(rarity));
        }
        return root;
    }

    private static RaritySettings defaultRarity(RoamingTrainerRarity rarity) {
        RaritySettings s = new RaritySettings();
        s.trainerNames.addAll(defaultTrainerNames(rarity));
        switch (rarity) {
            case COMMON -> {
                s.weight = 85; s.pokemonCount = 3; s.levelOffsetMin = 5; s.levelOffsetMax = 5; s.aiSkill = 5;
                s.allPokemonChance = 0.85D;
                s.fragmentMin = 1; s.fragmentMax = 2;
                s.rewardCommands.add("eco give %player% 25");
            }
            case UNCOMMON -> {
                s.weight = 12; s.pokemonCount = 3; s.levelOffsetMin = 10; s.levelOffsetMax = 10; s.aiSkill = 5;
                s.allPokemonChance = 0.60D;
                s.evolvedSpeciesChance = 1.0; s.heldItemChance = 1.0; s.competitiveNatureChance = 1.0;
                s.fragmentMin = 1; s.fragmentMax = 3;
                s.rewardCommands.add("eco give %player% 75");
            }
            case RARE -> {
                s.weight = 3; s.pokemonCount = 3; s.levelOffsetMin = 15; s.levelOffsetMax = 15; s.aiSkill = 5;
                s.allPokemonChance = 0.35D;
                s.evolvedSpeciesChance = 1.0; s.heldItemChance = 1.0; s.competitiveNatureChance = 1.0;
                s.fragmentMin = 2; s.fragmentMax = 4;
                s.rewardCommands.add("eco give %player% 175");
            }
            case EPIC -> {
                s.weight = 0.0; s.pokemonCount = 4; s.levelOffsetMin = 20; s.levelOffsetMax = 20; s.aiSkill = 5;
                s.allPokemonChance = 0.55D;
                s.legendaryPokemonCount = 1;
                s.evolvedSpeciesChance = 1.0; s.heldItemChance = 1.0; s.competitiveNatureChance = 1.0;
                s.fragmentMin = 3; s.fragmentMax = 5;
                s.rewardCommands.add("eco give %player% 500");
            }
            case LEGENDARY -> {
                s.weight = 0.0; s.pokemonCount = 5; s.levelOffsetMin = 25; s.levelOffsetMax = 25; s.aiSkill = 5;
                s.allPokemonChance = 0.65D;
                s.legendaryPokemonCount = 1;
                s.evolvedSpeciesChance = 1.0; s.heldItemChance = 1.0; s.competitiveNatureChance = 1.0;
                s.fragmentMin = 4; s.fragmentMax = 7;
                s.rewardCommands.add("eco give %player% 1250");
            }
            case MYTHIC -> {
                s.weight = 0.0; s.pokemonCount = 6; s.levelOffsetMin = 30; s.levelOffsetMax = 30; s.aiSkill = 5;
                s.allPokemonChance = 0.70D;
                s.legendaryPokemonCount = 3;
                s.evolvedSpeciesChance = 1.0; s.heldItemChance = 1.0; s.competitiveNatureChance = 1.0; s.shinyChance = 0.01;
                s.fragmentMin = 5; s.fragmentMax = 9;
                s.rewardCommands.add("eco give %player% 175");
            }
        }
        return s;
    }

    private static List<String> defaultBasicSpecies() {
        return new ArrayList<>(Arrays.asList(
                "bulbasaur", "charmander", "squirtle", "pikachu", "eevee", "pidgey", "spearow", "ekans",
                "sandshrew", "nidoran_f", "nidoran_m", "vulpix", "zubat", "oddish", "paras", "venonat",
                "diglett", "meowth", "psyduck", "mankey", "poliwag", "machop", "bellsprout", "tentacool",
                "geodude", "ponyta", "slowpoke", "magnemite", "doduo", "seel", "grimer", "shellder",
                "gastly", "drowzee", "krabby", "voltorb", "cubone", "horsea"
        ));
    }

    private static List<String> defaultStrongSpecies() {
        return new ArrayList<>(Arrays.asList(
                "ivysaur", "charmeleon", "wartortle", "raichu", "vaporeon", "jolteon", "flareon",
                "pidgeotto", "fearow", "arbok", "sandslash", "clefable", "ninetales", "golbat",
                "vileplume", "parasect", "venomoth", "dugtrio", "persian", "golduck", "primeape",
                "arcanine", "poliwrath", "kadabra", "machoke", "victreebel", "tentacruel", "golem",
                "rapidash", "slowbro", "magneton", "dodrio", "dewgong", "muk", "cloyster", "haunter",
                "hypno", "kingler", "electrode", "exeggutor", "marowak", "weezing", "rhydon", "seadra",
                "starmie", "scyther", "pinsir", "tauros", "gyarados", "lapras", "snorlax", "dragonair"
        ));
    }

    private static List<String> defaultEliteSpecies() {
        return new ArrayList<>(Arrays.asList(
                "venusaur", "charizard", "blastoise", "alakazam", "machamp", "gengar", "dragonite",
                "tyranitar", "metagross", "salamence", "garchomp", "hydreigon", "goodra", "kommo_o",
                "dragapult", "haxorus", "lucario", "gardevoir", "gallade", "milotic", "kingdra",
                "scizor", "heracross", "blissey", "mamoswine", "weavile", "togekiss", "excadrill",
                "volcarona", "greninja", "aegislash", "vikavolt", "toxapex", "mimikyu", "corviknight",
                "grimmsnarl", "duraludon", "baxcalibur", "annihilape", "ceruledge", "armarouge"
        ));
    }

    private static List<String> defaultLegendarySpecies() {
        return new ArrayList<>(Arrays.asList(
                "articuno", "zapdos", "moltres", "mewtwo", "mew", "raikou", "entei", "suicune",
                "lugia", "ho_oh", "celebi", "regirock", "regice", "registeel", "latias", "latios",
                "kyogre", "groudon", "rayquaza", "jirachi", "deoxys", "uxie", "mesprit", "azelf",
                "dialga", "palkia", "heatran", "regigigas", "giratina", "cresselia", "darkrai", "shaymin",
                "arceus", "cobalion", "terrakion", "virizion", "tornadus", "thundurus", "landorus",
                "reshiram", "zekrom", "kyurem", "xerneas", "yveltal", "zygarde", "solgaleo", "lunala",
                "necrozma", "zacian", "zamazenta", "eternatus", "kubfu", "urshifu", "regieleki", "regidrago"
        ));
    }

    private static List<String> defaultUltraBeastSpecies() {
        return new ArrayList<>(Arrays.asList(
                "nihilego", "buzzwole", "pheromosa", "xurkitree", "celesteela", "kartana",
                "guzzlord", "poipole", "naganadel", "stakataka", "blacephalon"
        ));
    }

    private static List<String> defaultParadoxSpecies() {
        return new ArrayList<>(Arrays.asList(
                "great_tusk", "scream_tail", "brute_bonnet", "flutter_mane", "slither_wing", "sandy_shocks",
                "roaring_moon", "walking_wake", "gouging_fire", "raging_bolt", "iron_treads", "iron_bundle",
                "iron_hands", "iron_jugulis", "iron_moth", "iron_thorns", "iron_valiant", "iron_leaves",
                "iron_boulder", "iron_crown"
        ));
    }

    private static List<String> defaultMythicSpecies() {
        return new ArrayList<>(Arrays.asList(
                "mew", "celebi", "jirachi", "deoxys", "phione", "manaphy", "darkrai", "shaymin",
                "arceus", "victini", "keldeo", "meloetta", "genesect", "diancie", "hoopa", "volcanion",
                "magearna", "marshadow", "zeraora", "meltan", "melmetal", "zarude", "pecharunt"
        ));
    }

    private static List<String> defaultBlacklistedPokemon() {
        return new ArrayList<>(Arrays.asList(
                // Kept out by default so roaming trainers do not roll joke/broken picks too often.
                // Remove anything from this list in roaming_trainers.json if you want it included.
                "magikarp", "feebas", "unown", "ditto", "wobbuffet", "wynaut", "shedinja", "smeargle", "delibird", "luvdisc"
        ));
    }

    private static List<String> defaultHeldItems() {
        return new ArrayList<>(Arrays.asList(
                "cobblemon:leftovers", "cobblemon:life_orb", "cobblemon:choice_band", "cobblemon:choice_scarf",
                "cobblemon:choice_specs", "cobblemon:focus_sash", "cobblemon:expert_belt", "cobblemon:muscle_band",
                "cobblemon:wise_glasses", "cobblemon:rocky_helmet"
        ));
    }

    private static List<String> defaultNatures() {
        return new ArrayList<>(Arrays.asList("adamant", "modest", "jolly", "timid", "bold", "calm", "impish", "careful"));
    }

    private static List<String> cleanTrainerNames(List<String> names, RoamingTrainerRarity rarity) {
        List<String> cleaned = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                if (name == null || name.isBlank()) continue;
                String value = name.replace("Roaming Trainer", "Trainer").replace("roaming trainer", "trainer").trim();
                if (!value.isBlank() && !cleaned.contains(value)) cleaned.add(value);
            }
        }
        return cleaned.isEmpty() ? defaultTrainerNames(rarity) : cleaned;
    }

    private static final Set<String> BLOCKED_DEFAULT_SKINS = Set.of(
            "steve", "alex", "mhf_steve", "mhf_alex", "player", "default", "char", "minecraft:steve", "minecraft:alex"
    );

    public static boolean isBlockedDefaultSkin(String skin) {
        if (skin == null) return true;
        String normalized = skin.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return true;
        if (BLOCKED_DEFAULT_SKINS.contains(normalized)) return true;
        return normalized.endsWith("/steve.png")
                || normalized.endsWith("/alex.png")
                || normalized.endsWith("\\steve.png")
                || normalized.endsWith("\\alex.png");
    }

    private static List<String> cleanTrainerSkins(List<String> skins) {
        return cleanTrainerSkins(skins, defaultTrainerSkins());
    }

    private static List<String> cleanTrainerSkins(List<String> skins, List<String> fallbackSkins) {
        List<String> cleaned = new ArrayList<>();
        if (skins != null) {
            for (String skin : skins) {
                if (isBlockedDefaultSkin(skin)) continue;
                String value = skin.trim();
                if (!cleaned.contains(value)) cleaned.add(value);
            }
        }

        // Keep the roaming pool varied even for older configs that only had a few names.
        if (fallbackSkins != null) {
            for (String fallback : fallbackSkins) {
                if (fallback == null || fallback.isBlank() || isBlockedDefaultSkin(fallback)) continue;
                if (!cleaned.contains(fallback)) cleaned.add(fallback);
            }
        }
        return cleaned;
    }

    private static List<String> defaultTrainerSkins() {
        List<String> skins = new ArrayList<>();
        skins.addAll(defaultMaleTrainerSkins());
        skins.addAll(defaultFemaleTrainerSkins());
        return skins;
    }

    private static List<String> defaultMaleTrainerSkins() {
        return new ArrayList<>(Arrays.asList(
                "champ_roamer_male_01.png",
                "champ_roamer_male_02.png",
                "champ_roamer_male_03.png",
                "champ_roamer_male_04.png",
                "champ_roamer_male_05.png",
                "champ_roamer_male_06.png",
                "champ_roamer_male_07.png",
                "champ_roamer_male_08.png",
                "champ_roamer_male_09.png",
                "champ_roamer_male_10.png",
                "champ_roamer_male_11.png",
                "champ_roamer_male_12.png",
                "champ_roamer_male_13.png",
                "champ_roamer_male_14.png",
                "champ_roamer_male_15.png",
                "champ_roamer_male_16.png",
                "champ_roamer_male_17.png"
        ));
    }

    private static List<String> defaultFemaleTrainerSkins() {
        return new ArrayList<>(Arrays.asList(
                "champ_roamer_female_01.png",
                "champ_roamer_female_02.png",
                "champ_roamer_female_03.png",
                "champ_roamer_female_04.png",
                "champ_roamer_female_05.png",
                "champ_roamer_female_06.png",
                "champ_roamer_female_07.png",
                "champ_roamer_female_08.png",
                "champ_roamer_female_09.png",
                "champ_roamer_female_10.png",
                "champ_roamer_female_11.png",
                "champ_roamer_female_12.png",
                "champ_roamer_female_13.png",
                "champ_roamer_female_14.png",
                "champ_roamer_female_15.png"
        ));
    }

    private static List<String> defaultTrainerNames(RoamingTrainerRarity rarity) {
        return switch (rarity) {
            case COMMON -> new ArrayList<>(Arrays.asList("Rookie Trainer", "Youngster", "Camper", "Picnicker", "Bug Catcher"));
            case UNCOMMON -> new ArrayList<>(Arrays.asList("Ace Recruit", "Backpacker", "Hiker", "Rancher", "Pokefan"));
            case RARE -> new ArrayList<>(Arrays.asList("Ace Trainer", "Veteran", "Black Belt", "Hex Maniac", "Ranger"));
            case EPIC -> new ArrayList<>(Arrays.asList("Elite Trainer", "Battle Expert", "Frontier Challenger", "Dragon Tamer"));
            case LEGENDARY -> new ArrayList<>(Arrays.asList("Legend Seeker", "Master Trainer", "Champion's Rival", "Myth Hunter"));
            case MYTHIC -> new ArrayList<>(Arrays.asList("Mythic Challenger", "Apex Trainer", "World Champion", "Grandmaster"));
        };
    }

    private static String formatName(RoamingTrainerRarity rarity) {
        String lower = rarity.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
