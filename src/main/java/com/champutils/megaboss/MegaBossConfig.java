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
                DATA.spawnChancePerPlayerCheck = defaultData.spawnChancePerPlayerCheck;
                DATA.nameTagFormat = defaultData.nameTagFormat;
                DATA.checkIntervalTicks = defaultData.checkIntervalTicks;
                DATA.despawnMinutes = defaultData.despawnMinutes;
                DATA.configVersion = defaultData.configVersion;
            }
            sanitizeRuntimeDefaults(defaultData);
            DATA.bosses.removeIf(boss -> boss == null || boss.species == null || boss.species.equalsIgnoreCase("rayquaza"));
            ensureRequiredBossEntries(defaultData);
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
        if (DATA.checkIntervalTicks == 300 || DATA.checkIntervalTicks == 600) DATA.checkIntervalTicks = defaultData.checkIntervalTicks;
        if (DATA.checkIntervalTicks <= 0) DATA.checkIntervalTicks = defaultData.checkIntervalTicks;
        if (DATA.maxAliveMegaBossesPerNearbyPlayer <= 0) DATA.maxAliveMegaBossesPerNearbyPlayer = defaultData.maxAliveMegaBossesPerNearbyPlayer;
        if (DATA.nearbyPlayerBossRadius <= 0) DATA.nearbyPlayerBossRadius = defaultData.nearbyPlayerBossRadius;
        if (DATA.maxSpawnedPlayersPerCheck == 4 || DATA.maxSpawnedPlayersPerCheck == 5 || DATA.maxSpawnedPlayersPerCheck == 8) DATA.maxSpawnedPlayersPerCheck = defaultData.maxSpawnedPlayersPerCheck;
        if (DATA.maxSpawnedPlayersPerCheck <= 0) DATA.maxSpawnedPlayersPerCheck = defaultData.maxSpawnedPlayersPerCheck;
        if (DATA.spawnChancePerPlayerCheck <= 0.0D || DATA.spawnChancePerPlayerCheck > 1.0D || DATA.spawnChancePerPlayerCheck == 0.25D) DATA.spawnChancePerPlayerCheck = defaultData.spawnChancePerPlayerCheck;
        if (DATA.levelsAbovePlayerHighest < 0) DATA.levelsAbovePlayerHighest = 0;
        // Mega Stones are intentionally fixed at 50% per megaboss win.
        // Keep deprecated config fields aligned so old configs deserialize safely.
        DATA.megaStoneDropChance = 0.50D;
        DATA.megaStoneBaseChance = 0.50D;
        DATA.megaStoneChanceAtLevel100 = 0.50D;
        if (DATA.nameTagFormat == null || DATA.nameTagFormat.isBlank()) DATA.nameTagFormat = defaultData.nameTagFormat;
        if (DATA.disabledDimensions == null) DATA.disabledDimensions = defaultData.disabledDimensions;
        if (DATA.rarityWeights == null) DATA.rarityWeights = defaultData.rarityWeights;
        if (DATA.fragmentMin <= 0 && DATA.essenceMin > 0) DATA.fragmentMin = DATA.essenceMin;
        if (DATA.fragmentMax <= 0 && DATA.essenceMax > 0) DATA.fragmentMax = DATA.essenceMax;
        DATA.essenceMin = DATA.fragmentMin;
        DATA.essenceMax = DATA.fragmentMax;
    }


    /**
     * Restores required split-form bosses that may be absent from older or manually trimmed configs.
     * Match by both species and Mega aspect so Mega Mewtwo X and Y remain separate entries.
     */
    private static void ensureRequiredBossEntries(Data defaultData) {
        for (BossEntry required : defaultData.bosses) {
            if (!"mewtwo".equalsIgnoreCase(required.species)) continue;
            String requiredAspect = required.extraProperties == null ? "" : required.extraProperties.trim().toLowerCase();
            boolean exists = DATA.bosses.stream().anyMatch(existing ->
                    existing != null
                            && "mewtwo".equalsIgnoreCase(existing.species)
                            && requiredAspect.equals(existing.extraProperties == null ? "" : existing.extraProperties.trim().toLowerCase())
            );
            if (!exists) DATA.bosses.add(required);
        }
    }

    private static Data defaults() {
        Data d = new Data();
        d.bosses = new ArrayList<>(Arrays.asList(
                boss("abomasnow", "F", "mega=true", "snowwarning", "modest", "blizzard", "gigadrain", "earthpower", "iceshard"),
                boss("audino", "F", "mega=true", "healer", "bold", "wish", "protect", "dazzlinggleam", "calmmind"),
                boss("banette", "F", "mega=true", "prankster", "adamant", "shadowsneak", "willowisp", "destinybond", "poltergeist"),
                boss("beedrill", "F", "mega=true", "adaptability", "jolly", "poisonjab", "uturn", "knockoff", "drillrun"),
                boss("camerupt", "F", "mega=true", "sheerforce", "quiet", "fireblast", "earthpower", "flashcannon", "stealthrock"),
                boss("glalie", "F", "mega=true", "refrigerate", "jolly", "doubledge", "earthquake", "explosion", "iceshard"),
                boss("houndoom", "F", "mega=true", "solarpower", "timid", "nastyplot", "fireblast", "darkpulse", "sludgebomb"),
                boss("manectric", "F", "mega=true", "intimidate", "timid", "thunderbolt", "overheat", "voltswitch", "hiddenpowerice"),
                boss("pidgeot", "F", "mega=true", "noguard", "timid", "hurricane", "heatwave", "roost", "uturn"),
                boss("sharpedo", "F", "mega=true", "strongjaw", "adamant", "protect", "crunch", "psychicfangs", "waterfall"),

                boss("absol", "E", "mega=true", "magicbounce", "jolly", "swordsdance", "knockoff", "suckerpunch", "playrough"),
                boss("aerodactyl", "E", "mega=true", "toughclaws", "jolly", "stoneedge", "dualwingbeat", "earthquake", "dragondance"),
                boss("aggron", "E", "mega=true", "filter", "impish", "heavyslam", "bodypress", "earthquake", "stealthrock"),
                boss("altaria", "E", "mega=true", "pixilate", "adamant", "dragondance", "return", "earthquake", "roost"),
                boss("ampharos", "E", "mega=true", "moldbreaker", "modest", "thunderbolt", "dragonpulse", "focusblast", "voltswitch"),
                boss("blastoise", "E", "mega=true", "megalauncher", "modest", "waterpulse", "darkpulse", "aurasphere", "icebeam"),
                boss("sableye", "E", "mega=true", "magicbounce", "careful", "recover", "willowisp", "knockoff", "calmmind"),
                boss("slowbro", "E", "mega=true", "shellarmor", "bold", "calmmind", "scald", "psyshock", "slackoff"),
                boss("steelix", "E", "mega=true", "sandforce", "impish", "earthquake", "heavyslam", "bodypress", "stealthrock"),
                boss("venusaur", "E", "mega=true", "thickfat", "bold", "gigadrain", "sludgebomb", "synthesis", "leechseed"),

                boss("alakazam", "D", "mega=true", "trace", "timid", "psychic", "focusblast", "shadowball", "nastyplot"),
                boss("gallade", "D", "mega=true", "innerfocus", "jolly", "swordsdance", "closecombat", "psychocut", "knockoff"),
                boss("gardevoir", "D", "mega=true", "pixilate", "timid", "hypervoice", "psyshock", "focusblast", "calmmind"),
                boss("heracross", "D", "mega=true", "skilllink", "adamant", "pinmissile", "rockblast", "closecombat", "swordsdance"),
                boss("lopunny", "D", "mega=true", "scrappy", "jolly", "fakeout", "closecombat", "return", "uturn"),
                boss("medicham", "D", "mega=true", "purepower", "jolly", "fakeout", "highjumpkick", "zenheadbutt", "icepunch"),
                boss("pinsir", "D", "mega=true", "aerilate", "jolly", "swordsdance", "return", "quickattack", "earthquake"),
                boss("scizor", "D", "mega=true", "technician", "adamant", "swordsdance", "bulletpunch", "knockoff", "roost"),

                boss("charizard", "C", "mega_x=true", "toughclaws", "jolly", "dragondance", "flareblitz", "dragonclaw", "earthquake"),
                boss("charizard", "C", "mega_y=true", "drought", "timid", "fireblast", "solarbeam", "airslash", "focusblast"),
                boss("lucario", "C", "mega=true", "adaptability", "jolly", "swordsdance", "closecombat", "meteormash", "extremespeed"),

                boss("blaziken", "B", "mega=true", "speedboost", "adamant", "swordsdance", "flareblitz", "closecombat", "protect"),
                boss("garchomp", "B", "mega=true", "sandforce", "jolly", "swordsdance", "earthquake", "scaleshot", "stoneedge"),
                boss("gyarados", "B", "mega=true", "moldbreaker", "jolly", "dragondance", "waterfall", "crunch", "earthquake"),
                boss("mawile", "B", "mega=true", "hugepower", "adamant", "swordsdance", "playrough", "suckerpunch", "knockoff"),
                boss("sceptile", "B", "mega=true", "lightningrod", "timid", "leafstorm", "dragonpulse", "focusblast", "substitute"),
                boss("swampert", "B", "mega=true", "swiftswim", "adamant", "raindance", "waterfall", "earthquake", "icepunch"),

                boss("diancie", "A", "mega=true", "magicbounce", "naive", "diamondstorm", "moonblast", "earthpower", "stealthrock"),
                boss("gengar", "A", "mega=true", "shadowtag", "timid", "shadowball", "sludgewave", "focusblast", "nastyplot"),
                boss("kangaskhan", "A", "mega=true", "parentalbond", "jolly", "fakeout", "poweruppunch", "return", "suckerpunch"),
                boss("metagross", "A", "mega=true", "toughclaws", "jolly", "meteormash", "zenheadbutt", "earthquake", "agility"),
                boss("salamence", "A", "mega=true", "aerilate", "jolly", "dragondance", "return", "earthquake", "roost"),
                boss("tyranitar", "A", "mega=true", "sandstream", "jolly", "dragondance", "stoneedge", "crunch", "earthquake"),

                boss("latias", "S", "mega=true", "levitate", "timid", "calmmind", "storedpower", "aurasphere", "recover"),
                boss("latios", "S", "mega=true", "levitate", "timid", "dracometeor", "lusterpurge", "aurasphere", "calmmind"),
                boss("mewtwo", "S", "mega_x=true", "steadfast", "jolly", "bulkup", "drainpunch", "psystrike", "icepunch"),
                boss("mewtwo", "S", "mega_y=true", "insomnia", "timid", "psystrike", "fireblast", "icebeam", "nastyplot")
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
        if (species.equalsIgnoreCase("diancie")
                || species.equalsIgnoreCase("latias")
                || species.equalsIgnoreCase("latios")
                || species.equalsIgnoreCase("mewtwo")) {
            e.spawnWeight = 0.02D;
        }
        return e;
    }

    public static final class Data {
        public int configVersion = 9;
        public boolean enabled = true;
        public int checkIntervalTicks = 1200;
        /**
         * Soft safety cap. Set high enough that megabosses can behave like roaming trainers across the server.
         * The real spawn limiter is maxAliveMegaBossesPerNearbyPlayer below.
         */
        public int maxAliveBosses = 6;

        /**
         * Roaming-trainer-style density cap: each player can only have this many megabosses near them.
         */
        public int maxAliveMegaBossesPerNearbyPlayer = 1;

        /**
         * Radius used for the nearby-player megaboss cap.
         */
        public int nearbyPlayerBossRadius = 192;

        /**
         * Prevents one server tick from spawning around every online player at once.
         * Raise this if you want bigger worlds to fill faster.
         */
        public int maxSpawnedPlayersPerCheck = 1;

        /**
         * Per eligible player spawn roll each check. Previous behavior was effectively 100% until caps were reached.
         * With a 60 second check interval, 0.125 averages one eligible-player spawn roll success every 8 minutes.
         */
        public double spawnChancePerPlayerCheck = 0.125D;

        public String nameTagFormat = "§5§lMega Boss §8| §d{species} §7[{rarity}] §fLv.{level}";
        public int minDistanceFromPlayer = 32;
        public int maxDistanceFromPlayer = 96;
        public int levelsAbovePlayerHighest = 15;
        public double scaleModifier = 1.7D;
        public long despawnMinutes = 30L;
        public int battlingXpReward = 350;
        /** Deprecated: Mega Bosses no longer grant profession Essence. */
        public int fragmentMin = 0;
        public int essenceMin = 0;
        /** Deprecated: Mega Bosses no longer grant profession Essence. */
        public int fragmentMax = 0;
        public int essenceMax = 0;
        public double megaStoneDropChance = 0.50D;
        /** Deprecated: kept so old configs still deserialize safely. */
        public double megaStoneBaseChance = 0.50D;
        /** Deprecated: kept so old configs still deserialize safely. */
        public double megaStoneChanceAtLevel100 = 0.50D;
        public boolean broadcastSpawns = true;
        public boolean broadcastMegaStoneDrops = true;
        /** Logs one compact line per natural spawn check plus detailed spawn failures. */
        public boolean debugSpawning = false;
        public List<String> disabledDimensions = new ArrayList<>();
        public List<BossEntry> bosses = new ArrayList<>();
        public RarityWeights rarityWeights = new RarityWeights();
    }

    public static final class RarityWeights {
        /**
         * Deprecated: natural megaboss selection no longer uses rarity weights.
         * Rarity is still used for difficulty/reward scaling and /megaboss force.
         */
        public double F = 1.0D;
        public double E = 1.0D;
        public double D = 1.0D;
        public double C = 1.0D;
        public double B = 1.0D;
        public double A = 1.0D;
        public double S = 1.0D;
    }

    public static final class BossEntry {
        public boolean enabled = true;
        public String species = "lucario";
        public String rarity = "C";
        public String megaStoneItem = "";
        public List<String> megaStoneItems = new ArrayList<>();
        public String extraProperties = "mega=true";
        public String ability = "";
        public String nature = "";
        public List<String> moves = new ArrayList<>();
        /** Relative natural-spawn selection weight. 1.0 is normal; 0.02 is roughly 50x rarer. */
        public double spawnWeight = 1.0D;
    }
}
