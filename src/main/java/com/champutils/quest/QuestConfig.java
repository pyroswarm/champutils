package com.champutils.quest;

import com.champutils.profession.ProfessionType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class QuestConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Settings SETTINGS = new Settings();

    public static class Settings {
        public int dailyResetHour = 9;
        public int dailyResetMinute = 0;
        public String weeklyResetDay = "MONDAY";
        public int weeklyResetHour = 9;
        public int weeklyResetMinute = 0;
        public int dailyObjectiveCount = 3;
        public int weeklyObjectiveCount = 3;
        public int dailyCompletionCredits = 350;
        public int weeklyCompletionCredits = 2500;
        public int dailyProfessionXpPerObjective = 75;
        public int weeklyProfessionXpPerObjective = 350;
        public int crateCreditChancePercent = 100;
        public String dailyCrateCreditId = "common";
        public String weeklyCrateCreditId = "rare";
        public int guildWeeklyObjectiveCount = 3;
        public int guildWeeklyRequiredPlayers = 10;
        public int guildWeeklyCompletionCredits = 1000;
        public List<String> guildWeeklyRewardCommands = new ArrayList<>();
        public List<Template> guildWeeklyTemplates = new ArrayList<>();
        public int maxActiveContracts = 1;
        public List<Template> dailyTemplates = new ArrayList<>();
        public List<Template> weeklyTemplates = new ArrayList<>();
        public List<ContractTemplate> contractTemplates = new ArrayList<>();
        public List<String> dailyRewardCommands = new ArrayList<>();
        public List<String> weeklyRewardCommands = new ArrayList<>();
    }

    public static class Template {
        public String id;
        public String description;
        public String objectiveType;
        public String profession;
        public String target;
        public int amount;
        public int minLevel;
        public int weight;
    }

    public static class ContractTemplate extends Template {
        public int creditCost;
        public int rewardCredits;
        public int durationHours;
        public String difficulty;
        public List<String> rewardCommands = new ArrayList<>();
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "quests.json");
            if (!file.exists()) createDefault(file);
            try (FileReader reader = new FileReader(file)) {
                Settings loaded = GSON.fromJson(reader, Settings.class);
                SETTINGS = loaded == null ? new Settings() : loaded;
            }
            normalize();
        } catch (Exception e) {
            e.printStackTrace();
            SETTINGS = new Settings();
            normalize();
        }
    }

    private static void normalize() {
        if (SETTINGS.dailyTemplates == null) SETTINGS.dailyTemplates = new ArrayList<>();
        if (SETTINGS.weeklyTemplates == null) SETTINGS.weeklyTemplates = new ArrayList<>();
        if (SETTINGS.contractTemplates == null) SETTINGS.contractTemplates = new ArrayList<>();
        if (SETTINGS.guildWeeklyTemplates == null) SETTINGS.guildWeeklyTemplates = new ArrayList<>();
        if (SETTINGS.guildWeeklyRewardCommands == null) SETTINGS.guildWeeklyRewardCommands = new ArrayList<>();
        if (SETTINGS.guildWeeklyRequiredPlayers <= 0) SETTINGS.guildWeeklyRequiredPlayers = 10;
        if (SETTINGS.guildWeeklyObjectiveCount <= 0) SETTINGS.guildWeeklyObjectiveCount = 3;
        if (SETTINGS.guildWeeklyRewardCommands.isEmpty()) {
            SETTINGS.guildWeeklyRewardCommands.add("opencrates givekey %player% guild 1");
            // Guild quests always give the guild crate credit. Keep other rewards optional in config.
        }
        if (SETTINGS.guildWeeklyTemplates.isEmpty()) addDefaultGuildWeeklyTemplates(SETTINGS);
        if (SETTINGS.dailyRewardCommands == null) SETTINGS.dailyRewardCommands = new ArrayList<>();
        if (SETTINGS.weeklyRewardCommands == null) SETTINGS.weeklyRewardCommands = new ArrayList<>();
        for (ContractTemplate c : SETTINGS.contractTemplates) {
            if (c == null) continue;
            if (c.rewardCommands == null) c.rewardCommands = new ArrayList<>();
            normalizeContractEconomy(c);
        }
        if (SETTINGS.maxActiveContracts <= 0) SETTINGS.maxActiveContracts = 1;
    }

    private static void normalizeContractEconomy(ContractTemplate c) {
        c.difficulty = normalizeDifficulty(c.difficulty);
        int cost = switch (c.difficulty) {
            case "UNCOMMON" -> 50;
            case "RARE" -> 75;
            case "EPIC" -> 125;
            case "LEGENDARY" -> 175;
            case "MYTHIC" -> 250;
            default -> 25;
        };
        int rewardCredits = switch (c.difficulty) {
            case "UNCOMMON" -> 350;
            case "RARE" -> 700;
            case "EPIC" -> 1400;
            case "LEGENDARY" -> 2800;
            case "MYTHIC" -> 6500;
            default -> 150;
        };
        int hours = switch (c.difficulty) {
            case "UNCOMMON" -> 7;
            case "RARE" -> 8;
            case "EPIC" -> 10;
            case "LEGENDARY" -> 11;
            case "MYTHIC" -> 12;
            default -> 6;
        };

        // Keep contracts short enough for normal play sessions. Existing configs with
        // old 24-240h durations are corrected on load before beta.
        c.creditCost = cost;
        c.rewardCredits = Math.max(c.rewardCredits, rewardCredits);
        c.durationHours = hours;
    }

    private static String normalizeDifficulty(String difficulty) {
        String value = difficulty == null ? "" : difficulty.trim().toUpperCase();
        return switch (value) {
            case "MEDIUM" -> "UNCOMMON";
            case "HARD" -> "RARE";
            case "EXPERT" -> "EPIC";
            case "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC" -> value;
            default -> "COMMON";
        };
    }

    private static void createDefault(File file) {
        try {
            Settings s = new Settings();
            s.dailyRewardCommands.add("give %player% cobblemon:poke_ball 8");
            s.weeklyRewardCommands.add("give %player% cobblemon:great_ball 12");
            s.guildWeeklyRewardCommands.add("opencrates givekey %player% guild 1");
            // Guild quests always give the guild crate credit. Keep other rewards optional in config.

            // Daily pool: intentionally wide so players don't see the same quests constantly.
            daily(s, "daily_mine_coal", "Mine 96 coal ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:coal_ore", 96, 1, 10);
            daily(s, "daily_mine_copper", "Mine 96 copper ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:copper_ore", 96, 1, 10);
            daily(s, "daily_mine_iron", "Mine 64 iron ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:iron_ore", 64, 1, 9);
            daily(s, "daily_mine_gold", "Mine 40 gold ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:gold_ore", 40, 5, 8);
            daily(s, "daily_mine_redstone", "Mine 80 redstone ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:redstone_ore", 80, 5, 8);
            daily(s, "daily_mine_lapis", "Mine 48 lapis ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:lapis_ore", 48, 5, 8);
            daily(s, "daily_mine_diamonds", "Mine 8 diamond ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:diamond_ore", 8, 10, 7);
            daily(s, "daily_mine_deepslate", "Mine 128 deepslate ore blocks", "MINE_BLOCK_CONTAINS", ProfessionType.MINING, "deepslate", 128, 10, 6);
            daily(s, "daily_mine_evolution_ore", "Mine 12 Cobblemon evolution stone ores", "MINE_BLOCK_CONTAINS", ProfessionType.MINING, "stone_ore", 12, 5, 6);
            daily(s, "daily_mine_ancient_debris", "Mine 3 ancient debris", "MINE_BLOCK", ProfessionType.MINING, "minecraft:ancient_debris", 3, 35, 3);

            daily(s, "daily_chop_logs", "Chop 128 natural logs", "CHOP_BLOCK_TAG", ProfessionType.FORESTRY, "logs", 128, 1, 10);
            daily(s, "daily_chop_oak", "Chop 80 oak logs", "CHOP_BLOCK_CONTAINS", ProfessionType.FORESTRY, "oak_log", 80, 1, 8);
            daily(s, "daily_chop_spruce", "Chop 80 spruce logs", "CHOP_BLOCK_CONTAINS", ProfessionType.FORESTRY, "spruce_log", 80, 1, 8);
            daily(s, "daily_chop_dark_oak", "Chop 64 dark oak logs", "CHOP_BLOCK_CONTAINS", ProfessionType.FORESTRY, "dark_oak_log", 64, 5, 7);
            daily(s, "daily_chop_cherry", "Chop 48 cherry logs", "CHOP_BLOCK_CONTAINS", ProfessionType.FORESTRY, "cherry_log", 48, 5, 6);
            daily(s, "daily_chop_mangrove", "Chop 48 mangrove logs", "CHOP_BLOCK_CONTAINS", ProfessionType.FORESTRY, "mangrove_log", 48, 10, 5);
            daily(s, "daily_chop_apricorn", "Chop 40 apricorn logs", "CHOP_BLOCK_CONTAINS", ProfessionType.FORESTRY, "apricorn", 40, 5, 6);

            daily(s, "daily_harvest_any", "Harvest 128 fully grown crops", "HARVEST_CROP", ProfessionType.FARMING, "any", 128, 1, 10);
            daily(s, "daily_harvest_wheat", "Harvest 96 wheat", "HARVEST_CROP", ProfessionType.FARMING, "minecraft:wheat", 96, 1, 8);
            daily(s, "daily_harvest_carrots", "Harvest 96 carrots", "HARVEST_CROP", ProfessionType.FARMING, "minecraft:carrots", 96, 1, 8);
            daily(s, "daily_harvest_potatoes", "Harvest 96 potatoes", "HARVEST_CROP", ProfessionType.FARMING, "minecraft:potatoes", 96, 1, 8);
            daily(s, "daily_harvest_beetroot", "Harvest 64 beetroot", "HARVEST_CROP", ProfessionType.FARMING, "minecraft:beetroots", 64, 5, 6);
            daily(s, "daily_harvest_melon", "Harvest 48 melon blocks", "HARVEST_CROP", ProfessionType.FARMING, "minecraft:melon", 48, 10, 5);
            daily(s, "daily_harvest_pumpkin", "Harvest 48 pumpkins", "HARVEST_CROP", ProfessionType.FARMING, "minecraft:pumpkin", 48, 10, 5);

            daily(s, "daily_battle_wild", "Win 24 wild battles", "WIN_BATTLE", ProfessionType.BATTLING, "UNKNOWN", 24, 1, 9);
            daily(s, "daily_battle_npc", "Win 8 NPC trainer battles", "WIN_BATTLE", ProfessionType.BATTLING, "NPC", 8, 1, 8);
            daily(s, "daily_battle_ranked", "Win 3 ranked battles", "WIN_BATTLE", ProfessionType.BATTLING, "RANKED", 3, 10, 5);
            daily(s, "daily_battle_casual", "Win 5 casual battles", "WIN_BATTLE", ProfessionType.BATTLING, "CASUAL", 5, 5, 6);
            String[] types = {"fire","water","grass","electric","ground","rock","bug","flying","ghost","psychic","dark","dragon","fairy","ice","steel","poison","fighting","normal"};
            for (String type : types) daily(s, "daily_defeat_" + type, "Defeat 16 " + cap(type) + "-type Pokémon", "DEFEAT_TYPE", ProfessionType.BATTLING, type, 16, type.equals("dragon") ? 20 : 1, type.equals("dragon") ? 4 : 6);

            // Weekly pool: longer versions plus higher-level goals.
            weekly(s, "weekly_mine_coal", "Mine 800 coal ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:coal_ore", 800, 1, 10);
            weekly(s, "weekly_mine_iron", "Mine 500 iron ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:iron_ore", 500, 1, 10);
            weekly(s, "weekly_mine_diamonds", "Mine 80 diamond ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:diamond_ore", 80, 10, 8);
            weekly(s, "weekly_mine_evolution_ore", "Mine 100 Cobblemon evolution stone ores", "MINE_BLOCK_CONTAINS", ProfessionType.MINING, "stone_ore", 100, 10, 6);
            weekly(s, "weekly_mine_ancient_debris", "Mine 32 ancient debris", "MINE_BLOCK", ProfessionType.MINING, "minecraft:ancient_debris", 32, 35, 4);
            weekly(s, "weekly_chop_logs", "Chop 1200 natural logs", "CHOP_BLOCK_TAG", ProfessionType.FORESTRY, "logs", 1200, 1, 10);
            weekly(s, "weekly_chop_dark_oak", "Chop 700 dark oak logs", "CHOP_BLOCK_CONTAINS", ProfessionType.FORESTRY, "dark_oak_log", 700, 5, 7);
            weekly(s, "weekly_chop_apricorn", "Chop 400 apricorn logs", "CHOP_BLOCK_CONTAINS", ProfessionType.FORESTRY, "apricorn", 400, 10, 6);
            weekly(s, "weekly_harvest_any", "Harvest 1500 fully grown crops", "HARVEST_CROP", ProfessionType.FARMING, "any", 1500, 1, 10);
            weekly(s, "weekly_harvest_wheat", "Harvest 900 wheat", "HARVEST_CROP", ProfessionType.FARMING, "minecraft:wheat", 900, 1, 8);
            weekly(s, "weekly_harvest_roots", "Harvest 900 carrots or potatoes", "HARVEST_CROP_CONTAINS", ProfessionType.FARMING, "to", 900, 5, 6);
            weekly(s, "weekly_ranked_wins", "Win 25 ranked battles", "WIN_BATTLE", ProfessionType.BATTLING, "RANKED", 25, 15, 7);
            weekly(s, "weekly_casual_wins", "Win 45 casual battles", "WIN_BATTLE", ProfessionType.BATTLING, "CASUAL", 45, 10, 7);
            weekly(s, "weekly_npc_wins", "Win 75 NPC trainer battles", "WIN_BATTLE", ProfessionType.BATTLING, "NPC", 75, 1, 8);
            for (String type : types) weekly(s, "weekly_defeat_" + type, "Defeat 120 " + cap(type) + "-type Pokémon", "DEFEAT_TYPE", ProfessionType.BATTLING, type, 120, type.equals("dragon") ? 25 : 1, type.equals("dragon") ? 4 : 6);

            // Guild weekly pool: every member contributes, and each objective requires multiple unique members to finish it.
            guildWeekly(s, "guild_weekly_mine_diamonds", "Guild members mine 64 diamond ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:diamond_ore", 64, 10, 8);
            guildWeekly(s, "guild_weekly_chop_logs", "Guild members chop 900 natural logs", "CHOP_BLOCK_TAG", ProfessionType.FORESTRY, "logs", 900, 1, 10);
            guildWeekly(s, "guild_weekly_harvest_crops", "Guild members harvest 1200 fully grown crops", "HARVEST_CROP", ProfessionType.FARMING, "any", 1200, 1, 10);
            guildWeekly(s, "guild_weekly_ranked_wins", "Guild members win 15 ranked battles", "WIN_BATTLE", ProfessionType.BATTLING, "RANKED", 15, 15, 7);
            guildWeekly(s, "guild_weekly_npc_wins", "Guild members win 40 NPC trainer battles", "WIN_BATTLE", ProfessionType.BATTLING, "NPC", 40, 1, 8);
            guildWeekly(s, "guild_weekly_defeat_dragons", "Guild members defeat 60 Dragon-type Pokémon", "DEFEAT_TYPE", ProfessionType.BATTLING, "dragon", 60, 25, 4);

            // Purchasable single-objective contracts. Only auto-trackable objectives are used.
            // Crate credits and rarity fragments are guaranteed, matching the contract rarity.
            contract(s, "contract_coal_shift", "Mine 160 coal ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:coal_ore", 160, 1, 10, 25, 75, 6, "COMMON", "give %player% cobblemon:poke_ball 10");
            contract(s, "contract_log_shift", "Chop 220 natural logs", "CHOP_BLOCK_TAG", ProfessionType.FORESTRY, "logs", 220, 1, 10, 25, 75, 6, "COMMON", "give %player% cobblemon:oran_berry 8");
            contract(s, "contract_crop_shift", "Harvest 220 fully grown crops", "HARVEST_CROP", ProfessionType.FARMING, "any", 220, 1, 10, 25, 75, 6, "COMMON", "give %player% cobblemon:poke_ball 10");
            contract(s, "contract_trainer_shift", "Win 12 NPC trainer battles", "WIN_BATTLE", ProfessionType.BATTLING, "NPC", 12, 1, 8, 25, 100, 6, "COMMON", "give %player% cobblemon:potion 6");

            contract(s, "contract_iron_run", "Mine 180 iron ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:iron_ore", 180, 1, 9, 50, 150, 7, "UNCOMMON", "give %player% cobblemon:great_ball 8");
            contract(s, "contract_apricorn_run", "Chop 140 apricorn logs", "CHOP_BLOCK_CONTAINS", ProfessionType.FORESTRY, "apricorn", 140, 5, 7, 50, 150, 7, "UNCOMMON", "give %player% cobblemon:friend_ball 4");
            contract(s, "contract_harvest_run", "Harvest 400 fully grown crops", "HARVEST_CROP", ProfessionType.FARMING, "any", 400, 1, 9, 50, 150, 7, "UNCOMMON", "give %player% cobblemon:sitrus_berry 8");
            contract(s, "contract_type_run", "Defeat 45 Fire-type Pokémon", "DEFEAT_TYPE", ProfessionType.BATTLING, "fire", 45, 1, 7, 50, 175, 7, "UNCOMMON", "give %player% cobblemon:dive_ball 6");

            contract(s, "contract_diamond_rush", "Mine 36 diamond ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:diamond_ore", 36, 10, 8, 75, 350, 8, "RARE", "give %player% cobblemon:ultra_ball 6");
            contract(s, "contract_evo_ores", "Mine 70 Cobblemon evolution stone ores", "MINE_BLOCK_CONTAINS", ProfessionType.MINING, "stone_ore", 70, 10, 8, 75, 350, 8, "RARE", "give %player% cobblemon:thunder_stone 1", "give %player% cobblemon:water_stone 1");
            contract(s, "contract_dark_forest", "Chop 500 dark oak logs", "CHOP_BLOCK_CONTAINS", ProfessionType.FORESTRY, "dark_oak_log", 500, 5, 8, 75, 350, 8, "RARE", "give %player% cobblemon:dusk_ball 8");
            contract(s, "contract_ranked_push", "Win 8 ranked battles", "WIN_BATTLE", ProfessionType.BATTLING, "RANKED", 8, 15, 5, 75, 400, 8, "RARE", "give %player% cobblemon:quick_ball 8");

            contract(s, "contract_ancient_debris", "Mine 20 ancient debris", "MINE_BLOCK", ProfessionType.MINING, "minecraft:ancient_debris", 20, 35, 5, 125, 800, 10, "EPIC", "give %player% cobblemon:ultra_ball 10");
            contract(s, "contract_dragon_hunter", "Defeat 75 Dragon-type Pokémon", "DEFEAT_TYPE", ProfessionType.BATTLING, "dragon", 75, 25, 4, 125, 800, 10, "EPIC", "give %player% cobblemon:dragon_fang 1");

            contract(s, "contract_ranked_marathon", "Win 20 ranked battles", "WIN_BATTLE", ProfessionType.BATTLING, "RANKED", 20, 20, 3, 175, 1500, 11, "LEGENDARY", "give %player% cobblemon:focus_sash 1");
            contract(s, "contract_trainer_marathon", "Win 120 NPC trainer battles", "WIN_BATTLE", ProfessionType.BATTLING, "NPC", 120, 1, 4, 175, 1500, 11, "LEGENDARY", "give %player% cobblemon:revive 12");

            contract(s, "contract_master_grind", "Defeat 300 wild Pokémon by type quests", "DEFEAT_TYPE", ProfessionType.BATTLING, "any", 300, 30, 1, 250, 3000, 12, "MYTHIC", "give %player% cobblemon:rare_candy 2");

            try (FileWriter writer = new FileWriter(file)) { GSON.toJson(s, writer); }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addDefaultGuildWeeklyTemplates(Settings s) {
        guildWeekly(s, "guild_weekly_mine_diamonds", "Guild members mine 64 diamond ore", "MINE_BLOCK", ProfessionType.MINING, "minecraft:diamond_ore", 64, 10, 8);
        guildWeekly(s, "guild_weekly_chop_logs", "Guild members chop 900 natural logs", "CHOP_BLOCK_TAG", ProfessionType.FORESTRY, "logs", 900, 1, 10);
        guildWeekly(s, "guild_weekly_harvest_crops", "Guild members harvest 1200 fully grown crops", "HARVEST_CROP", ProfessionType.FARMING, "any", 1200, 1, 10);
        guildWeekly(s, "guild_weekly_ranked_wins", "Guild members win 15 ranked battles", "WIN_BATTLE", ProfessionType.BATTLING, "RANKED", 15, 15, 7);
        guildWeekly(s, "guild_weekly_npc_wins", "Guild members win 40 NPC trainer battles", "WIN_BATTLE", ProfessionType.BATTLING, "NPC", 40, 1, 8);
        guildWeekly(s, "guild_weekly_defeat_dragons", "Guild members defeat 60 Dragon-type Pokémon", "DEFEAT_TYPE", ProfessionType.BATTLING, "dragon", 60, 25, 4);
    }

    private static String cap(String s) { return s == null || s.isEmpty() ? "" : s.substring(0, 1).toUpperCase() + s.substring(1); }

    private static void daily(Settings s, String id, String desc, String type, ProfessionType profession, String target, int amount, int minLevel, int weight) {
        s.dailyTemplates.add(template(id, desc, type, profession, target, amount, minLevel, weight));
    }

    private static void weekly(Settings s, String id, String desc, String type, ProfessionType profession, String target, int amount, int minLevel, int weight) {
        s.weeklyTemplates.add(template(id, desc, type, profession, target, amount, minLevel, weight));
    }

    private static void guildWeekly(Settings s, String id, String desc, String type, ProfessionType profession, String target, int amount, int minLevel, int weight) {
        s.guildWeeklyTemplates.add(template(id, desc, type, profession, target, amount, minLevel, weight));
    }

    private static Template template(String id, String desc, String type, ProfessionType profession, String target, int amount, int minLevel, int weight) {
        Template t = new Template();
        t.id = id;
        t.description = desc;
        t.objectiveType = type;
        t.profession = profession.name();
        t.target = target;
        t.amount = amount;
        t.minLevel = minLevel;
        t.weight = weight;
        return t;
    }

    private static void contract(Settings s, String id, String desc, String type, ProfessionType profession, String target, int amount, int minLevel, int weight, int cost, int rewardCredits, int hours, String difficulty, String... rewards) {
        ContractTemplate t = new ContractTemplate();
        t.id = id;
        t.description = desc;
        t.objectiveType = type;
        t.profession = profession.name();
        t.target = target;
        t.amount = amount;
        t.minLevel = minLevel;
        t.weight = weight;
        t.creditCost = cost;
        t.rewardCredits = rewardCredits;
        t.durationHours = hours;
        t.difficulty = difficulty;
        for (String r : rewards) t.rewardCommands.add(r);
        s.contractTemplates.add(t);
    }
}
