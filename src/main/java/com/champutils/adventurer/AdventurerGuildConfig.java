package com.champutils.adventurer;

import com.champutils.roaming.RoamingTrainerRarity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdventurerGuildConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "adventurers_guild.json");

    public static Settings SETTINGS = defaultSettings();

    private AdventurerGuildConfig() {}

    public static final class Settings {
        public boolean enabled = true;
        public int dailyResetHour = 9;
        public int dailyResetMinute = 0;
        public String weeklyResetDay = "MONDAY";
        public int weeklyResetHour = 9;
        public int weeklyResetMinute = 0;

        public int pvpDailyRequiredWins = 2;
        public int pvpDailyRewardCredits = 750;
        public int pvpDailyRewardRenown = 150;
        public int pvpDailyRewardMarks = 1;
        public List<String> pvpDailyRewardCommands = new ArrayList<>();

        public int pvpWeeklyRequiredMatches = 20;
        public int pvpWeeklyRequiredWins = 7;
        public int pvpWeeklyRewardCredits = 6500;
        public int pvpWeeklyRewardRenown = 1000;
        public int pvpWeeklyRewardMarks = 8;
        public List<String> pvpWeeklyRewardCommands = new ArrayList<>();

        public int rankedWinRenown = 0;
        public int casualWinRenown = 0;
        public int rankedWinMarks = 0;
        public int casualWinMarks = 0;

        public int battleTowerMaxFloor = 12;
        public int battleTowerCooldownSeconds = 45;
        public int battleTowerActiveMinutes = 20;
        public int battleTowerClearBonusCredits = 6000;
        public int battleTowerClearBonusRenown = 1250;
        public int battleTowerClearBonusMarks = 12;
        public List<BattleTowerFloor> battleTowerFloors = new ArrayList<>();

        public int roamingLeagueCooldownMinutes = 30;
        public int roamingLeagueDailyFreeSpawns = 1;
        public List<RoamingLeagueEntry> roamingLeague = new ArrayList<>();

        public List<RankDefinition> ranks = new ArrayList<>();
    }

    public static final class BattleTowerFloor {
        public int floor = 1;
        public String displayName = "Floor 1";
        public String rarity = "F";
        public int rewardCredits = 150;
        public int rewardRenown = 60;
        public int rewardMarks = 1;
        public List<String> rewardCommands = new ArrayList<>();
        /** Optional configured arena location. Set with /adventurer admin settowerfloor <floor>. */
        public boolean locationSet = false;
        public String world = "";
        public double x = 0.0D;
        public double y = 64.0D;
        public double z = 0.0D;
        public float yaw = 0.0F;
        public float pitch = 0.0F;
    }

    public static final class RoamingLeagueEntry {
        public String rarity = "F";
        public int minRenown = 0;
        public int requiredRenown = 0; // migration alias for older generated configs
        public int creditCost = 0;
        public int rewardRenown = 60;
        public int rewardMarks = 1;
        public List<String> rewardCommands = new ArrayList<>();
    }

    public static final class RankDefinition {
        public String id = "F";
        public String displayName = "F Rank Adventurer";
        public int renownRequired = 0;
        public int requiredRenown = 0; // migration alias for older generated configs
        public int rewardCredits = 0;
        public int rewardMarks = 0;
        public List<String> rewardCommands = new ArrayList<>();
    }

    public static synchronized void load() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            if (!FILE.exists()) {
                SETTINGS = defaultSettings();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                Settings loaded = GSON.fromJson(reader, Settings.class);
                SETTINGS = loaded == null ? defaultSettings() : loaded;
            }
            sanitize();
            save();
            System.out.println("[ChampUtils] Loaded adventurers_guild.json");
        } catch (Exception e) {
            e.printStackTrace();
            SETTINGS = defaultSettings();
        }
    }

    public static synchronized void save() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(SETTINGS, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static BattleTowerFloor floor(int floor) {
        int safeFloor = Math.max(1, Math.min(Math.max(1, SETTINGS.battleTowerMaxFloor), floor));
        if (SETTINGS.battleTowerFloors != null) {
            for (BattleTowerFloor entry : SETTINGS.battleTowerFloors) {
                if (entry != null && entry.floor == safeFloor) return entry;
            }
        }
        BattleTowerFloor fallback = new BattleTowerFloor();
        fallback.floor = safeFloor;
        fallback.displayName = "Floor " + safeFloor;
        fallback.rarity = rarityForFloor(safeFloor).name();
        fallback.rewardCredits = 250 + safeFloor * 150;
        fallback.rewardRenown = 90 + safeFloor * 45;
        fallback.rewardMarks = Math.max(1, (safeFloor + 1) / 2);
        return fallback;
    }

    public static BattleTowerFloor ensureFloor(int floorNumber) {
        int safeFloor = Math.max(1, Math.min(Math.max(1, SETTINGS.battleTowerMaxFloor), floorNumber));
        if (SETTINGS.battleTowerFloors == null) SETTINGS.battleTowerFloors = new ArrayList<>();
        for (BattleTowerFloor entry : SETTINGS.battleTowerFloors) {
            if (entry != null && entry.floor == safeFloor) return entry;
        }
        BattleTowerFloor created = floor(safeFloor);
        SETTINGS.battleTowerFloors.add(created);
        return created;
    }

    public static RoamingLeagueEntry roamingEntry(RoamingTrainerRarity rarity) {
        RoamingTrainerRarity safe = rarity == null ? RoamingTrainerRarity.F : rarity;
        if (SETTINGS.roamingLeague != null) {
            for (RoamingLeagueEntry entry : SETTINGS.roamingLeague) {
                if (entry != null && safe.name().equalsIgnoreCase(entry.rarity)) return entry;
            }
        }
        RoamingLeagueEntry fallback = new RoamingLeagueEntry();
        fallback.rarity = safe.name();
        fallback.creditCost = switch (safe) {
            case F -> 0;
            case E -> 100;
            case D -> 250;
            case C -> 500;
            case B -> 750;
            case A -> 1000;
            case S -> 2000;
        };
        fallback.minRenown = switch (safe) { case F -> 0; case E -> 750; case D -> 3_000; case C -> 12_000; case B -> 45_000; case A -> 125_000; case S -> 350_000; };
        fallback.rewardRenown = 120 + safe.ordinal() * 140;
        fallback.rewardMarks = 1 + safe.ordinal() * 2;
        return fallback;
    }

    public static RankDefinition currentRank(long renown) {
        RankDefinition best = null;
        if (SETTINGS.ranks != null) {
            for (RankDefinition rank : SETTINGS.ranks) {
                if (rank == null) continue;
                if (renown >= Math.max(0, rank.renownRequired) && (best == null || rank.renownRequired >= best.renownRequired)) {
                    best = rank;
                }
            }
        }
        if (best != null) return best;
        RankDefinition fallback = new RankDefinition();
        fallback.id = "F";
        fallback.displayName = "F Rank Adventurer";
        fallback.renownRequired = 0;
        return fallback;
    }

    public static RankDefinition nextRank(long renown) {
        RankDefinition next = null;
        if (SETTINGS.ranks != null) {
            for (RankDefinition rank : SETTINGS.ranks) {
                if (rank == null) continue;
                if (rank.renownRequired <= renown) continue;
                if (next == null || rank.renownRequired < next.renownRequired) next = rank;
            }
        }
        return next;
    }

    public static DayOfWeek weeklyResetDay() {
        try {
            return DayOfWeek.valueOf((SETTINGS.weeklyResetDay == null ? "MONDAY" : SETTINGS.weeklyResetDay).trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return DayOfWeek.MONDAY;
        }
    }

    public static RoamingTrainerRarity rarityForFloor(int floor) {
        if (floor <= 2) return RoamingTrainerRarity.F;
        if (floor == 3) return RoamingTrainerRarity.E;
        if (floor == 4) return RoamingTrainerRarity.D;
        if (floor <= 6) return RoamingTrainerRarity.C;
        if (floor <= 8) return RoamingTrainerRarity.B;
        if (floor == 9) return RoamingTrainerRarity.A;
        return RoamingTrainerRarity.S;
    }

    private static void sanitize() {
        if (SETTINGS == null) SETTINGS = defaultSettings();
        SETTINGS.dailyResetHour = clamp(SETTINGS.dailyResetHour, 0, 23);
        SETTINGS.dailyResetMinute = clamp(SETTINGS.dailyResetMinute, 0, 59);
        SETTINGS.weeklyResetHour = clamp(SETTINGS.weeklyResetHour, 0, 23);
        SETTINGS.weeklyResetMinute = clamp(SETTINGS.weeklyResetMinute, 0, 59);
        SETTINGS.pvpDailyRequiredWins = Math.max(1, SETTINGS.pvpDailyRequiredWins);
        SETTINGS.pvpWeeklyRequiredMatches = Math.max(1, SETTINGS.pvpWeeklyRequiredMatches);
        SETTINGS.pvpWeeklyRequiredWins = Math.max(0, SETTINGS.pvpWeeklyRequiredWins);
        SETTINGS.battleTowerMaxFloor = Math.max(12, Math.min(25, SETTINGS.battleTowerMaxFloor));
        SETTINGS.battleTowerCooldownSeconds = Math.max(0, SETTINGS.battleTowerCooldownSeconds);
        SETTINGS.battleTowerActiveMinutes = Math.max(5, SETTINGS.battleTowerActiveMinutes);
        SETTINGS.roamingLeagueCooldownMinutes = Math.max(0, SETTINGS.roamingLeagueCooldownMinutes);
        SETTINGS.roamingLeagueDailyFreeSpawns = Math.max(0, SETTINGS.roamingLeagueDailyFreeSpawns);
        if (SETTINGS.pvpDailyRewardCommands == null) SETTINGS.pvpDailyRewardCommands = new ArrayList<>();
        if (SETTINGS.pvpWeeklyRewardCommands == null) SETTINGS.pvpWeeklyRewardCommands = new ArrayList<>();
        if (SETTINGS.battleTowerFloors == null || SETTINGS.battleTowerFloors.isEmpty()) SETTINGS.battleTowerFloors = defaultTowerFloors();
        for (int i = 1; i <= SETTINGS.battleTowerMaxFloor; i++) ensureFloor(i);
        if (SETTINGS.roamingLeague == null || SETTINGS.roamingLeague.isEmpty()) SETTINGS.roamingLeague = defaultRoamingLeague();
        if (SETTINGS.ranks == null || SETTINGS.ranks.isEmpty()) SETTINGS.ranks = defaultRanks();
        for (BattleTowerFloor floor : SETTINGS.battleTowerFloors) if (floor != null && floor.rewardCommands == null) floor.rewardCommands = new ArrayList<>();
        for (RoamingLeagueEntry entry : SETTINGS.roamingLeague) {
            if (entry == null) continue;
            if (entry.requiredRenown > 0 && entry.minRenown <= 0) entry.minRenown = entry.requiredRenown;
            entry.requiredRenown = 0;
            entry.rarity = com.champutils.rarity.RarityScale.normalize(entry.rarity);
            if (entry.rewardCommands == null) entry.rewardCommands = new ArrayList<>();
        }
        for (RankDefinition rank : SETTINGS.ranks) {
            if (rank == null) continue;
            if (rank.requiredRenown > 0 && rank.renownRequired <= 0) rank.renownRequired = rank.requiredRenown;
            rank.requiredRenown = 0;
            rank.id = com.champutils.rarity.RarityScale.normalize(rank.id);
            if (rank.displayName == null || rank.displayName.isBlank()) rank.displayName = rank.id + " Rank Adventurer";
            if (rank.rewardCommands == null) rank.rewardCommands = new ArrayList<>();
        }
    }

    private static Settings defaultSettings() {
        Settings settings = new Settings();
        settings.pvpDailyRewardCommands.add("opencrates givekey %player% f 1");
        settings.pvpWeeklyRewardCommands.add("opencrates givekey %player% d 1");
        settings.battleTowerFloors = defaultTowerFloors();
        settings.roamingLeague = defaultRoamingLeague();
        settings.ranks = defaultRanks();
        return settings;
    }

    private static List<BattleTowerFloor> defaultTowerFloors() {
        List<BattleTowerFloor> floors = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            BattleTowerFloor floor = new BattleTowerFloor();
            floor.floor = i;
            floor.displayName = "Battle Tower Floor " + i;
            floor.rarity = rarityForFloor(i).name();
            switch (i) {
                case 1 -> { floor.rewardCredits = 400; floor.rewardRenown = 140; floor.rewardMarks = 1; }
                case 2 -> { floor.rewardCredits = 550; floor.rewardRenown = 190; floor.rewardMarks = 2; }
                case 3 -> { floor.rewardCredits = 750; floor.rewardRenown = 260; floor.rewardMarks = 2; }
                case 4 -> { floor.rewardCredits = 1000; floor.rewardRenown = 360; floor.rewardMarks = 3; }
                case 5 -> { floor.rewardCredits = 1300; floor.rewardRenown = 480; floor.rewardMarks = 4; }
                case 6 -> { floor.rewardCredits = 1600; floor.rewardRenown = 600; floor.rewardMarks = 5; }
                case 7 -> { floor.rewardCredits = 2200; floor.rewardRenown = 800; floor.rewardMarks = 7; }
                case 8 -> { floor.rewardCredits = 3000; floor.rewardRenown = 1000; floor.rewardMarks = 9; }
                case 9 -> { floor.rewardCredits = 4500; floor.rewardRenown = 1350; floor.rewardMarks = 12; }
                case 10 -> { floor.rewardCredits = 6000; floor.rewardRenown = 1650; floor.rewardMarks = 15; }
                case 11 -> { floor.rewardCredits = 7500; floor.rewardRenown = 2100; floor.rewardMarks = 20; }
                default -> { floor.rewardCredits = 10000; floor.rewardRenown = 2800; floor.rewardMarks = 28; }
            }
            if (i == 3) floor.rewardCommands.add("opencrates givekey %player% f 1");
            if (i == 6) floor.rewardCommands.add("opencrates givekey %player% d 1");
            if (i == 9) floor.rewardCommands.add("opencrates givekey %player% b 1");
            if (i == 12) floor.rewardCommands.add("opencrates givekey %player% a 1");
            floors.add(floor);
        }
        return floors;
    }

    private static List<RoamingLeagueEntry> defaultRoamingLeague() {
        List<RoamingLeagueEntry> entries = new ArrayList<>();
        for (RoamingTrainerRarity rarity : RoamingTrainerRarity.values()) {
            RoamingLeagueEntry entry = new RoamingLeagueEntry();
            entry.rarity = rarity.name();
            entry.minRenown = switch (rarity) {
                case F -> 0;
                case E -> 750;
                case D -> 3_000;
                case C -> 12_000;
                case B -> 45_000;
                case A -> 125_000;
                case S -> 350_000;
            };
            entry.creditCost = switch (rarity) {
                case F -> 0;
                case E -> 350;
                case D -> 900;
                case C -> 2_250;
                case B -> 6_000;
                case A -> 15_000;
                case S -> 35_000;
            };
            entry.rewardRenown = 120 + rarity.ordinal() * 140;
            entry.rewardMarks = 1 + rarity.ordinal() * 2;
            entries.add(entry);
        }
        return entries;
    }

    private static List<RankDefinition> defaultRanks() {
        List<RankDefinition> ranks = new ArrayList<>();
        ranks.add(rank("F", "F Rank Adventurer", 0, 0, 0));
        ranks.add(rank("E", "E Rank Adventurer", 750, 750, 3));
        ranks.add(rank("D", "D Rank Adventurer", 3_000, 2_500, 6));
        ranks.add(rank("C", "C Rank Adventurer", 12_000, 6_500, 10));
        ranks.add(rank("B", "B Rank Adventurer", 45_000, 15_000, 18));
        ranks.add(rank("A", "A Rank Adventurer", 125_000, 35_000, 30));
        ranks.add(rank("S", "S Rank Adventurer", 350_000, 85_000, 55));
        return ranks;
    }

    private static RankDefinition rank(String id, String display, int required, int credits, int marks) {
        RankDefinition rank = new RankDefinition();
        rank.id = id;
        rank.displayName = display;
        rank.renownRequired = required;
        rank.rewardCredits = credits;
        rank.rewardMarks = marks;
        return rank;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
