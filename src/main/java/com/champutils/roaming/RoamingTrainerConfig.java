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
        public int scanIntervalSeconds = 25;
        public int maxTrainersPerPlayer = 2;
        public int spawnMinDistance = 24;
        public int spawnMaxDistance = 48;
        public int activePlayerRadius = 64;
        public int despawnAfterNoPlayersSeconds = 180;
        public boolean doNotDespawnWhileInBattle = true;
        public boolean requireSolidGround = true;
        public int maxSpawnAttemptsPerPlayer = 12;
        public List<String> blockedDimensions = new ArrayList<>();
        public boolean allowAllPokemonFromCobblemonRegistry = true;
        public List<String> blacklistedPokemon = new ArrayList<>();
        public Map<String, RaritySettings> rarities = new LinkedHashMap<>();
        public List<String> defaultSpeciesPool = new ArrayList<>();
        public List<String> basicSpeciesPool = new ArrayList<>();
        public List<String> strongSpeciesPool = new ArrayList<>();
        public List<String> eliteSpeciesPool = new ArrayList<>();
        public List<String> legendarySpeciesPool = new ArrayList<>();
        public List<String> competitiveHeldItems = new ArrayList<>();
        public List<String> competitiveNatures = new ArrayList<>();
        public List<String> randomTrainerSkins = new ArrayList<>();
    }

    public static class RaritySettings {
        public double weight = 1.0D;
        public int pokemonCount = 1;
        public int levelOffsetMin = 0;
        public int levelOffsetMax = 0;
        public int aiSkill = 1;
        public double evolvedSpeciesChance = 0.0D;
        public double heldItemChance = 0.0D;
        public double competitiveNatureChance = 0.0D;
        public double shinyChance = 0.0D;
        public int legendaryPokemonCount = 0;
        public int fragmentMin = 1;
        public int fragmentMax = 1;
        public List<String> rewardCommands = new ArrayList<>();
        public List<String> speciesPool = new ArrayList<>();
        public List<String> trainerNames = new ArrayList<>();
        /** Chance to use the full Cobblemon registry pool instead of the curated pool for non-forced slots. */
        public double allPokemonChance = 0.0D;
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
        if (DATA.competitiveHeldItems == null) DATA.competitiveHeldItems = defaultHeldItems();
        if (DATA.competitiveNatures == null) DATA.competitiveNatures = defaultNatures();
        DATA.randomTrainerSkins = cleanTrainerSkins(DATA.randomTrainerSkins);
        if (DATA.scanIntervalSeconds < 5) DATA.scanIntervalSeconds = 5;
        if (DATA.spawnMinDistance < 8) DATA.spawnMinDistance = 8;
        if (DATA.spawnMaxDistance < DATA.spawnMinDistance) DATA.spawnMaxDistance = DATA.spawnMinDistance + 12;
        if (DATA.activePlayerRadius < 16) DATA.activePlayerRadius = 16;
        if (DATA.despawnAfterNoPlayersSeconds < 30) DATA.despawnAfterNoPlayersSeconds = 30;
        if (DATA.maxSpawnAttemptsPerPlayer < 1) DATA.maxSpawnAttemptsPerPlayer = 1;

        for (RoamingTrainerRarity rarity : RoamingTrainerRarity.values()) {
            RaritySettings settings = DATA.rarities.computeIfAbsent(rarity.name(), key -> defaultRarity(rarity));
            if (settings.trainerNames == null || settings.trainerNames.isEmpty()) {
                settings.trainerNames = defaultTrainerNames(rarity);
            } else {
                settings.trainerNames = cleanTrainerNames(settings.trainerNames, rarity);
            }
        }
    }

    public static boolean isBlockedDimension(String dimensionId) {
        if (dimensionId == null) return false;
        String normalized = dimensionId.toLowerCase(Locale.ROOT);
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
        root.blockedDimensions.add("spawn1");
        root.blockedDimensions.add("multiworld:spawn1");
        root.blacklistedPokemon = defaultBlacklistedPokemon();
        root.defaultSpeciesPool = defaultBasicSpecies();
        root.basicSpeciesPool = defaultBasicSpecies();
        root.strongSpeciesPool = defaultStrongSpecies();
        root.eliteSpeciesPool = defaultEliteSpecies();
        root.legendarySpeciesPool = defaultLegendarySpecies();
        root.competitiveHeldItems = defaultHeldItems();
        root.competitiveNatures = defaultNatures();
        root.randomTrainerSkins = defaultTrainerSkins();
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
                s.weight = 70; s.pokemonCount = 1; s.levelOffsetMin = -3; s.levelOffsetMax = 1; s.aiSkill = 1;
                s.allPokemonChance = 0.85D;
                s.fragmentMin = 1; s.fragmentMax = 2;
                s.rewardCommands.add("eco give %player% 100");
            }
            case UNCOMMON -> {
                s.weight = 20; s.pokemonCount = 2; s.levelOffsetMin = -1; s.levelOffsetMax = 3; s.aiSkill = 2;
                s.allPokemonChance = 0.60D;
                s.evolvedSpeciesChance = 0.15; s.heldItemChance = 0.10; s.competitiveNatureChance = 0.15;
                s.fragmentMin = 1; s.fragmentMax = 3;
                s.rewardCommands.add("eco give %player% 250");
            }
            case RARE -> {
                s.weight = 7; s.pokemonCount = 3; s.levelOffsetMin = 1; s.levelOffsetMax = 5; s.aiSkill = 3;
                s.allPokemonChance = 0.35D;
                s.evolvedSpeciesChance = 0.35; s.heldItemChance = 0.25; s.competitiveNatureChance = 0.35;
                s.fragmentMin = 2; s.fragmentMax = 4;
                s.rewardCommands.add("eco give %player% 750");
            }
            case EPIC -> {
                s.weight = 2; s.pokemonCount = 4; s.levelOffsetMin = 3; s.levelOffsetMax = 8; s.aiSkill = 4;
                s.allPokemonChance = 0.15D;
                s.evolvedSpeciesChance = 0.55; s.heldItemChance = 0.45; s.competitiveNatureChance = 0.55;
                s.fragmentMin = 3; s.fragmentMax = 5;
                s.rewardCommands.add("eco give %player% 2000");
            }
            case LEGENDARY -> {
                s.weight = 0.8; s.pokemonCount = 5; s.levelOffsetMin = 5; s.levelOffsetMax = 10; s.aiSkill = 5;
                s.allPokemonChance = 0.05D;
                s.legendaryPokemonCount = 1;
                s.evolvedSpeciesChance = 0.75; s.heldItemChance = 0.65; s.competitiveNatureChance = 0.75;
                s.fragmentMin = 4; s.fragmentMax = 7;
                s.rewardCommands.add("eco give %player% 5000");
            }
            case MYTHIC -> {
                s.weight = 0.2; s.pokemonCount = 6; s.levelOffsetMin = 8; s.levelOffsetMax = 15; s.aiSkill = 5;
                s.allPokemonChance = 0.0D;
                s.legendaryPokemonCount = 3;
                s.evolvedSpeciesChance = 0.95; s.heldItemChance = 0.90; s.competitiveNatureChance = 0.95; s.shinyChance = 0.01;
                s.fragmentMin = 5; s.fragmentMax = 9;
                s.rewardCommands.add("eco give %player% 10000");
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
        List<String> cleaned = new ArrayList<>();
        if (skins != null) {
            for (String skin : skins) {
                if (isBlockedDefaultSkin(skin)) continue;
                String value = skin.trim();
                if (!cleaned.contains(value)) cleaned.add(value);
            }
        }

        // Keep the roaming pool varied even for older configs that only had a few names.
        for (String fallback : defaultTrainerSkins()) {
            if (cleaned.size() >= 36) break;
            if (!cleaned.contains(fallback)) cleaned.add(fallback);
        }
        return cleaned;
    }

    private static List<String> defaultTrainerSkins() {
        return new ArrayList<>(Arrays.asList(
                "champ_roamer_01.png",
                "champ_roamer_02.png",
                "champ_roamer_03.png",
                "champ_roamer_04.png",
                "champ_roamer_05.png",
                "champ_roamer_06.png",
                "champ_roamer_07.png",
                "champ_roamer_08.png",
                "champ_roamer_09.png",
                "champ_roamer_10.png",
                "champ_roamer_11.png",
                "champ_roamer_12.png",
                "champ_roamer_13.png",
                "champ_roamer_14.png",
                "champ_roamer_15.png",
                "champ_roamer_16.png",
                "champ_roamer_17.png",
                "champ_roamer_18.png",
                "champ_roamer_19.png",
                "champ_roamer_20.png",
                "champ_roamer_21.png",
                "champ_roamer_22.png",
                "champ_roamer_23.png",
                "champ_roamer_24.png",
                "champ_roamer_25.png",
                "champ_roamer_26.png",
                "champ_roamer_27.png",
                "champ_roamer_28.png",
                "champ_roamer_29.png",
                "champ_roamer_30.png",
                "champ_roamer_31.png",
                "champ_roamer_32.png",
                "champ_roamer_33.png",
                "champ_roamer_34.png",
                "champ_roamer_35.png",
                "champ_roamer_36.png"
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
