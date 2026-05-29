package com.champutils.megaboss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MegaBossConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/mega_bosses.json");
    public static Data DATA = new Data();

    private MegaBossConfig() {}

    public static void load() {
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            if (!FILE.exists()) { DATA = defaults(); save(); return; }
            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                DATA = loaded == null ? defaults() : loaded;
            }
            if (DATA.bosses == null || DATA.bosses.isEmpty()) DATA.bosses = defaults().bosses;

            Data defaultData = defaults();
            boolean upgradedConfig = DATA.configVersion < defaultData.configVersion;
            if (upgradedConfig) {
                DATA.bosses = defaultData.bosses;
                DATA.rarityWeights = defaultData.rarityWeights;
                DATA.maxAliveBosses = defaultData.maxAliveBosses;
                DATA.maxAliveMegaBossesPerNearbyPlayer = defaultData.maxAliveMegaBossesPerNearbyPlayer;
                DATA.nearbyPlayerBossRadius = defaultData.nearbyPlayerBossRadius;
                DATA.maxSpawnedPlayersPerCheck = defaultData.maxSpawnedPlayersPerCheck;
                DATA.nameTagFormat = defaultData.nameTagFormat;
                DATA.configVersion = defaultData.configVersion;
            }
            sanitizeRuntimeDefaults(defaultData);
            DATA.bosses.removeIf(boss -> boss == null || boss.species == null || boss.species.equalsIgnoreCase("rayquaza"));
            save();
        } catch (Exception e) {
            DATA = defaults();
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(DATA, writer); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void sanitizeRuntimeDefaults(Data defaultData) {
        if (DATA.checkIntervalTicks <= 0) DATA.checkIntervalTicks = defaultData.checkIntervalTicks;
        if (DATA.maxAliveMegaBossesPerNearbyPlayer <= 0) DATA.maxAliveMegaBossesPerNearbyPlayer = defaultData.maxAliveMegaBossesPerNearbyPlayer;
        if (DATA.nearbyPlayerBossRadius <= 0) DATA.nearbyPlayerBossRadius = defaultData.nearbyPlayerBossRadius;
        if (DATA.maxSpawnedPlayersPerCheck <= 0) DATA.maxSpawnedPlayersPerCheck = defaultData.maxSpawnedPlayersPerCheck;
        if (DATA.nameTagFormat == null || DATA.nameTagFormat.isBlank()) DATA.nameTagFormat = defaultData.nameTagFormat;
        if (DATA.disabledDimensions == null) DATA.disabledDimensions = defaultData.disabledDimensions;
        if (DATA.rarityWeights == null) DATA.rarityWeights = defaultData.rarityWeights;
    }

    private static Data defaults() {
        Data d = new Data();
        d.bosses = new ArrayList<>(Arrays.asList(
                boss("abomasnow", "COMMON", "mega=true", "snowwarning", "modest", "blizzard", "gigadrain", "earthpower", "iceshard"),
                boss("audino", "COMMON", "mega=true", "healer", "bold", "wish", "protect", "dazzlinggleam", "calmmind"),
                boss("banette", "COMMON", "mega=true", "prankster", "adamant", "shadowsneak", "willowisp", "destinybond", "poltergeist"),
                boss("beedrill", "COMMON", "mega=true", "adaptability", "jolly", "poisonjab", "uturn", "knockoff", "drillrun"),
                boss("camerupt", "COMMON", "mega=true", "sheerforce", "quiet", "fireblast", "earthpower", "flashcannon", "stealthrock"),
                boss("glalie", "COMMON", "mega=true", "refrigerate", "jolly", "doubledge", "earthquake", "explosion", "iceshard"),
                boss("houndoom", "COMMON", "mega=true", "solarpower", "timid", "nastyplot", "fireblast", "darkpulse", "sludgebomb"),
                boss("manectric", "COMMON", "mega=true", "intimidate", "timid", "thunderbolt", "overheat", "voltswitch", "hiddenpowerice"),
                boss("pidgeot", "COMMON", "mega=true", "noguard", "timid", "hurricane", "heatwave", "roost", "uturn"),
                boss("sharpedo", "COMMON", "mega=true", "strongjaw", "adamant", "protect", "crunch", "psychicfangs", "waterfall"),

                boss("absol", "UNCOMMON", "mega=true", "magicbounce", "jolly", "swordsdance", "knockoff", "suckerpunch", "playrough"),
                boss("aerodactyl", "UNCOMMON", "mega=true", "toughclaws", "jolly", "stoneedge", "dualwingbeat", "earthquake", "dragondance"),
                boss("aggron", "UNCOMMON", "mega=true", "filter", "impish", "heavyslam", "bodypress", "earthquake", "stealthrock"),
                boss("altaria", "UNCOMMON", "mega=true", "pixilate", "adamant", "dragondance", "return", "earthquake", "roost"),
                boss("ampharos", "UNCOMMON", "mega=true", "moldbreaker", "modest", "thunderbolt", "dragonpulse", "focusblast", "voltswitch"),
                boss("blastoise", "UNCOMMON", "mega=true", "megalauncher", "modest", "waterpulse", "darkpulse", "aurasphere", "icebeam"),
                boss("sableye", "UNCOMMON", "mega=true", "magicbounce", "careful", "recover", "willowisp", "knockoff", "calmmind"),
                boss("slowbro", "UNCOMMON", "mega=true", "shellarmor", "bold", "calmmind", "scald", "psyshock", "slackoff"),
                boss("steelix", "UNCOMMON", "mega=true", "sandforce", "impish", "earthquake", "heavyslam", "bodypress", "stealthrock"),
                boss("venusaur", "UNCOMMON", "mega=true", "thickfat", "bold", "gigadrain", "sludgebomb", "synthesis", "leechseed"),

                boss("alakazam", "RARE", "mega=true", "trace", "timid", "psychic", "focusblast", "shadowball", "nastyplot"),
                boss("gallade", "RARE", "mega=true", "innerfocus", "jolly", "swordsdance", "closecombat", "psychocut", "knockoff"),
                boss("gardevoir", "RARE", "mega=true", "pixilate", "timid", "hypervoice", "psyshock", "focusblast", "calmmind"),
                boss("heracross", "RARE", "mega=true", "skilllink", "adamant", "pinmissile", "rockblast", "closecombat", "swordsdance"),
                boss("lopunny", "RARE", "mega=true", "scrappy", "jolly", "fakeout", "closecombat", "return", "uturn"),
                boss("medicham", "RARE", "mega=true", "purepower", "jolly", "fakeout", "highjumpkick", "zenheadbutt", "icepunch"),
                boss("pinsir", "RARE", "mega=true", "aerilate", "jolly", "swordsdance", "return", "quickattack", "earthquake"),
                boss("scizor", "RARE", "mega=true", "technician", "adamant", "swordsdance", "bulletpunch", "knockoff", "roost"),

                boss("blaziken", "EPIC", "mega=true", "speedboost", "adamant", "swordsdance", "flareblitz", "closecombat", "protect"),
                boss("charizard", "EPIC", "mega_x=true", "toughclaws", "jolly", "dragondance", "flareblitz", "dragonclaw", "earthquake"),
                boss("charizard", "EPIC", "mega_y=true", "drought", "timid", "fireblast", "solarbeam", "airslash", "focusblast"),
                boss("garchomp", "EPIC", "mega=true", "sandforce", "jolly", "swordsdance", "earthquake", "scaleshot", "stoneedge"),
                boss("gyarados", "EPIC", "mega=true", "moldbreaker", "jolly", "dragondance", "waterfall", "crunch", "earthquake"),
                boss("lucario", "EPIC", "mega=true", "adaptability", "jolly", "swordsdance", "closecombat", "meteormash", "extremespeed"),
                boss("mawile", "EPIC", "mega=true", "hugepower", "adamant", "swordsdance", "playrough", "suckerpunch", "knockoff"),
                boss("sceptile", "EPIC", "mega=true", "lightningrod", "timid", "leafstorm", "dragonpulse", "focusblast", "substitute"),
                boss("swampert", "EPIC", "mega=true", "swiftswim", "adamant", "raindance", "waterfall", "earthquake", "icepunch"),

                boss("diancie", "LEGENDARY", "mega=true", "magicbounce", "naive", "diamondstorm", "moonblast", "earthpower", "stealthrock"),
                boss("gengar", "LEGENDARY", "mega=true", "shadowtag", "timid", "shadowball", "sludgewave", "focusblast", "nastyplot"),
                boss("kangaskhan", "LEGENDARY", "mega=true", "parentalbond", "jolly", "fakeout", "poweruppunch", "return", "suckerpunch"),
                boss("metagross", "LEGENDARY", "mega=true", "toughclaws", "jolly", "meteormash", "zenheadbutt", "earthquake", "agility"),
                boss("salamence", "LEGENDARY", "mega=true", "aerilate", "jolly", "dragondance", "return", "earthquake", "roost"),
                boss("tyranitar", "LEGENDARY", "mega=true", "sandstream", "jolly", "dragondance", "stoneedge", "crunch", "earthquake"),

                boss("latias", "MYTHIC", "mega=true", "levitate", "timid", "calmmind", "storedpower", "aurasphere", "recover"),
                boss("latios", "MYTHIC", "mega=true", "levitate", "timid", "dracometeor", "lusterpurge", "aurasphere", "calmmind"),
                boss("mewtwo", "MYTHIC", "mega_x=true", "steadfast", "jolly", "bulkup", "drainpunch", "psystrike", "icepunch"),
                boss("mewtwo", "MYTHIC", "mega_y=true", "insomnia", "timid", "psystrike", "fireblast", "icebeam", "nastyplot")
        ));
        return d;
    }

    private static BossEntry boss(String species, String rarity, String extra, String ability, String nature, String... moves) {
        BossEntry e = new BossEntry();
        e.species = species;
        e.rarity = rarity;
        e.extraProperties = extra;
        e.ability = ability;
        e.nature = nature;
        e.moves = new ArrayList<>(Arrays.asList(moves));
        return e;
    }

    public static final class Data {
        public int configVersion = 3;
        public boolean enabled = true;
        public int checkIntervalTicks = 1200;
        /**
         * Soft safety cap. Set high enough that megabosses can behave like roaming trainers across the server.
         * The real spawn limiter is maxAliveMegaBossesPerNearbyPlayer below.
         */
        public int maxAliveBosses = 50;

        /**
         * Roaming-trainer-style density cap: each player can only have this many megabosses near them.
         */
        public int maxAliveMegaBossesPerNearbyPlayer = 1;

        /**
         * Radius used for the nearby-player megaboss cap.
         */
        public int nearbyPlayerBossRadius = 128;

        /**
         * Prevents one server tick from spawning around every online player at once.
         * Raise this if you want bigger worlds to fill faster.
         */
        public int maxSpawnedPlayersPerCheck = 3;

        public String nameTagFormat = "§5§lMega Boss §8| §d{species} §7[{rarity}] §fLv.{level}";
        public int minDistanceFromPlayer = 32;
        public int maxDistanceFromPlayer = 96;
        public int levelsAbovePlayerHighest = 5;
        public double scaleModifier = 1.7D;
        public long despawnMinutes = 20L;
        public int battlingXpReward = 350;
        public int fragmentMin = 2;
        public int fragmentMax = 4;
        public double megaStoneBaseChance = 0.01D;
        public double megaStoneChanceAtLevel100 = 0.20D;
        public boolean broadcastSpawns = true;
        public boolean broadcastMegaStoneDrops = true;
        public List<String> disabledDimensions = new ArrayList<>(List.of("minecraft:the_end"));
        public List<BossEntry> bosses = new ArrayList<>();
        public RarityWeights rarityWeights = new RarityWeights();
    }

    public static final class RarityWeights {
        public int COMMON = 3000;
        public int UNCOMMON = 2500;
        public int RARE = 1800;
        public int EPIC = 1200;
        public int LEGENDARY = 700;
        public int MYTHIC = 150;
    }

    public static final class BossEntry {
        public boolean enabled = true;
        public String species = "lucario";
        public String rarity = "EPIC";
        public String megaStoneItem = "";
        public String extraProperties = "mega=true";
        public String ability = "";
        public String nature = "";
        public List<String> moves = new ArrayList<>();
    }
}
