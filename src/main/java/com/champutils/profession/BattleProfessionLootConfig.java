package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BattleProfessionLootConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean enabled = true;
    public static double baseRollChance = 0.22D;
    public static double rollChancePerBattlingLevel = 0.0012D;
    public static double maxRollChance = 0.38D;
    public static int baseRolls = 1;
    public static int bonusRollEveryLevels = 25;
    public static int maxRolls = 5;
    public static boolean announceRewards = true;

    public static FragmentJackpotSettings fragmentJackpots = new FragmentJackpotSettings();
    public static DungeonKeySettings dungeonKeys = new DungeonKeySettings();
    public static List<LootEntry> rewards = new ArrayList<>();

    private BattleProfessionLootConfig() {
    }

    public static class ConfigRoot {
        public boolean enabled = true;
        public double baseRollChance = 0.22D;
        public double rollChancePerBattlingLevel = 0.0012D;
        public double maxRollChance = 0.38D;
        public int baseRolls = 1;
        public int bonusRollEveryLevels = 25;
        public int maxRolls = 5;
        public boolean announceRewards = true;
        public FragmentJackpotSettings fragmentJackpots = new FragmentJackpotSettings();
        public DungeonKeySettings dungeonKeys = new DungeonKeySettings();
        public List<LootEntry> rewards = new ArrayList<>();
    }

    public static class LootEntry {
        public String itemId = "minecraft:air";
        public int minBattlingLevel = 1;
        public int minAmount = 1;
        public int maxAmount = 1;
        public int weight = 1;
        public int weightPerLevelAboveUnlock = 0;
        public boolean enabled = true;
    }

    public static class FragmentJackpotSettings {
        public boolean enabled = true;
        public double baseChance = 0.001D;
        public double chancePerBattlingLevel = 0.00008D;
        public double maxChance = 0.012D;
        public int minBattlingLevel = 1;
        public boolean allowMythic = false;
        public Map<String, Integer> rarityWeights = new LinkedHashMap<>();

        public FragmentJackpotSettings() {
            rarityWeights.put("COMMON", 1);
            rarityWeights.put("UNCOMMON", 1);
            rarityWeights.put("RARE", 1);
            rarityWeights.put("EPIC", 1);
            rarityWeights.put("LEGENDARY", 1);
        }
    }

    public static class DungeonKeySettings {
        public boolean enabled = true;
        public double chancePerBattleLevelMultiplier = 0.000012D;
        public List<KeyDropEntry> drops = new ArrayList<>();
    }

    public static class KeyDropEntry {
        public String keyId = "common_dungeon_key";
        public int minBattlingLevel = 1;
        public double baseChance = 0.0007D;
        public double maxChance = 0.004D;
        public int minAmount = 1;
        public int maxAmount = 1;
        public boolean enabled = true;
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, "battle_profession_loot.json");
            if (!file.exists()) {
                createDefault(file);
            }

            try (FileReader reader = new FileReader(file)) {
                ConfigRoot root = GSON.fromJson(reader, ConfigRoot.class);
                if (root == null) {
                    root = defaultRoot();
                }

                enabled = root.enabled;
                baseRollChance = root.baseRollChance;
                rollChancePerBattlingLevel = root.rollChancePerBattlingLevel;
                maxRollChance = root.maxRollChance;
                baseRolls = root.baseRolls;
                bonusRollEveryLevels = root.bonusRollEveryLevels;
                maxRolls = root.maxRolls;
                announceRewards = root.announceRewards;
                fragmentJackpots = root.fragmentJackpots == null ? new FragmentJackpotSettings() : root.fragmentJackpots;
                dungeonKeys = root.dungeonKeys == null ? new DungeonKeySettings() : root.dungeonKeys;
                rewards = root.rewards == null ? new ArrayList<>() : root.rewards;
            }

            if (rewards == null || rewards.isEmpty()) {
                rewards = defaultRoot().rewards;
            }

            if (fragmentJackpots.rarityWeights == null || fragmentJackpots.rarityWeights.isEmpty()) {
                fragmentJackpots.rarityWeights = new FragmentJackpotSettings().rarityWeights;
            }

            if (dungeonKeys.drops == null || dungeonKeys.drops.isEmpty()) {
                dungeonKeys.drops = defaultRoot().dungeonKeys.drops;
            }

            System.out.println("[ChampUtils] Loaded battle profession loot: " + rewards.size() + " entries.");
        } catch (Exception e) {
            e.printStackTrace();
            ConfigRoot defaults = defaultRoot();
            enabled = defaults.enabled;
            baseRollChance = defaults.baseRollChance;
            rollChancePerBattlingLevel = defaults.rollChancePerBattlingLevel;
            maxRollChance = defaults.maxRollChance;
            baseRolls = defaults.baseRolls;
            bonusRollEveryLevels = defaults.bonusRollEveryLevels;
            maxRolls = defaults.maxRolls;
            announceRewards = defaults.announceRewards;
            fragmentJackpots = defaults.fragmentJackpots;
            dungeonKeys = defaults.dungeonKeys;
            rewards = defaults.rewards;
        }
    }

    private static void createDefault(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(defaultRoot(), writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ConfigRoot defaultRoot() {
        ConfigRoot root = new ConfigRoot();

        root.dungeonKeys.drops.add(key("common_dungeon_key", 1, 0.00075D, 0.0040D));
        root.dungeonKeys.drops.add(key("uncommon_dungeon_key", 20, 0.00035D, 0.0022D));
        root.dungeonKeys.drops.add(key("rare_dungeon_key", 40, 0.00016D, 0.0010D));
        root.dungeonKeys.drops.add(key("epic_dungeon_key", 60, 0.00007D, 0.00045D));
        root.dungeonKeys.drops.add(key("legendary_dungeon_key", 80, 0.000025D, 0.00018D));
        root.dungeonKeys.drops.add(key("mythic_dungeon_key", 100, 0.000008D, 0.00005D));

        addPokeBalls(root);
        addBattleItems(root);
        addHeldItems(root);

        return root;
    }

    private static KeyDropEntry key(String id, int level, double baseChance, double maxChance) {
        KeyDropEntry entry = new KeyDropEntry();
        entry.keyId = id;
        entry.minBattlingLevel = level;
        entry.baseChance = baseChance;
        entry.maxChance = maxChance;
        entry.minAmount = 1;
        entry.maxAmount = 1;
        return entry;
    }

    private static void addPokeBalls(ConfigRoot root) {
        entry(root, "cobblemon:poke_ball", 1, 2, 5, 120, 1);
        entry(root, "cobblemon:premier_ball", 1, 1, 3, 70, 1);
        entry(root, "cobblemon:heal_ball", 10, 1, 3, 70, 1);
        entry(root, "cobblemon:nest_ball", 10, 1, 2, 55, 1);
        entry(root, "cobblemon:great_ball", 10, 1, 3, 95, 1);
        entry(root, "cobblemon:ancient_poke_ball", 10, 1, 3, 40, 1);
        entry(root, "cobblemon:ancient_great_ball", 20, 1, 3, 45, 1);
        entry(root, "cobblemon:dive_ball", 20, 1, 2, 45, 1);
        entry(root, "cobblemon:net_ball", 20, 1, 2, 45, 1);
        entry(root, "cobblemon:timer_ball", 30, 1, 2, 45, 1);
        entry(root, "cobblemon:quick_ball", 30, 1, 2, 45, 1);
        entry(root, "cobblemon:dusk_ball", 30, 1, 2, 45, 1);
        entry(root, "cobblemon:ultra_ball", 40, 1, 2, 65, 1);
        entry(root, "cobblemon:ancient_ultra_ball", 40, 1, 2, 35, 1);
        entry(root, "cobblemon:repeat_ball", 40, 1, 2, 32, 1);
        entry(root, "cobblemon:luxury_ball", 50, 1, 2, 26, 1);
        entry(root, "cobblemon:level_ball", 50, 1, 2, 22, 1);
        entry(root, "cobblemon:lure_ball", 50, 1, 2, 22, 1);
        entry(root, "cobblemon:moon_ball", 50, 1, 1, 18, 1);
        entry(root, "cobblemon:friend_ball", 60, 1, 1, 18, 1);
        entry(root, "cobblemon:love_ball", 60, 1, 1, 18, 1);
        entry(root, "cobblemon:heavy_ball", 60, 1, 1, 18, 1);
        entry(root, "cobblemon:fast_ball", 60, 1, 1, 18, 1);
        entry(root, "cobblemon:sport_ball", 70, 1, 1, 12, 1);
        entry(root, "cobblemon:safari_ball", 70, 1, 1, 12, 1);
        entry(root, "cobblemon:dream_ball", 80, 1, 1, 8, 1);
        entry(root, "cobblemon:beast_ball", 90, 1, 1, 5, 1);
        entry(root, "cobblemon:cherish_ball", 90, 1, 1, 4, 0);
        entry(root, "cobblemon:master_ball", 100, 1, 1, 1, 0);

        String[] colored = {"azure", "citrine", "ivory", "roseate", "slate", "verdant"};
        for (String color : colored) {
            entry(root, "cobblemon:" + color + "_ball", 20, 1, 2, 24, 1);
            entry(root, "cobblemon:ancient_" + color + "_ball", 30, 1, 2, 16, 1);
        }
        entry(root, "cobblemon:park_ball", 70, 1, 1, 8, 1);
        entry(root, "cobblemon:ancient_heavy_ball", 40, 1, 1, 18, 1);
        entry(root, "cobblemon:ancient_leaden_ball", 50, 1, 1, 14, 1);
        entry(root, "cobblemon:ancient_gigaton_ball", 70, 1, 1, 8, 1);
        entry(root, "cobblemon:ancient_feather_ball", 40, 1, 1, 18, 1);
        entry(root, "cobblemon:ancient_wing_ball", 50, 1, 1, 14, 1);
        entry(root, "cobblemon:ancient_jet_ball", 70, 1, 1, 8, 1);
        entry(root, "cobblemon:ancient_origin_ball", 100, 1, 1, 1, 0);
    }

    private static void addBattleItems(ConfigRoot root) {
        entry(root, "cobblemon:potion", 1, 1, 3, 90, 1);
        entry(root, "cobblemon:antidote", 1, 1, 2, 45, 1);
        entry(root, "cobblemon:awakening", 1, 1, 2, 40, 1);
        entry(root, "cobblemon:burn_heal", 1, 1, 2, 40, 1);
        entry(root, "cobblemon:ice_heal", 1, 1, 2, 40, 1);
        entry(root, "cobblemon:paralyze_heal", 1, 1, 2, 40, 1);
        entry(root, "cobblemon:super_potion", 10, 1, 3, 75, 1);
        entry(root, "cobblemon:remedy", 10, 1, 2, 40, 1);
        entry(root, "cobblemon:ether", 20, 1, 2, 24, 1);
        entry(root, "cobblemon:revive", 20, 1, 2, 45, 1);
        entry(root, "cobblemon:dire_hit", 20, 1, 2, 35, 1);
        entry(root, "cobblemon:guard_spec", 20, 1, 2, 35, 1);
        entry(root, "cobblemon:x_accuracy", 20, 1, 2, 35, 1);
        entry(root, "cobblemon:x_attack", 20, 1, 2, 35, 1);
        entry(root, "cobblemon:x_defence", 20, 1, 2, 35, 1);
        entry(root, "cobblemon:x_special_attack", 20, 1, 2, 35, 1);
        entry(root, "cobblemon:x_special_defence", 20, 1, 2, 35, 1);
        entry(root, "cobblemon:x_speed", 20, 1, 2, 35, 1);
        entry(root, "cobblemon:hyper_potion", 30, 1, 2, 58, 1);
        entry(root, "cobblemon:fine_remedy", 30, 1, 2, 28, 1);
        entry(root, "cobblemon:full_heal", 40, 1, 2, 35, 1);
        entry(root, "cobblemon:max_ether", 50, 1, 1, 18, 1);
        entry(root, "cobblemon:max_potion", 60, 1, 1, 20, 1);
        entry(root, "cobblemon:full_restore", 70, 1, 1, 12, 1);
        entry(root, "cobblemon:max_elixir", 80, 1, 1, 8, 1);
        entry(root, "cobblemon:max_revive", 90, 1, 1, 5, 1);
        entry(root, "cobblemon:elixir", 50, 1, 1, 14, 1);
        entry(root, "cobblemon:superb_remedy", 60, 1, 1, 14, 1);
        entry(root, "cobblemon:heal_powder", 10, 1, 2, 25, 1);
    }

    private static void addHeldItems(ConfigRoot root) {
        String[] t1 = {
                "charcoal_stick", "miracle_seed", "mystic_water", "magnet", "hard_stone", "soft_sand",
                "never_melt_ice", "black_belt", "sharp_beak", "poison_barb", "twisted_spoon", "silver_powder",
                "dragon_fang", "black_glasses", "spell_tag", "metal_coat", "silk_scarf", "fairy_feather"
        };
        for (String id : t1) entry(root, "cobblemon:" + id, 20, 1, 1, 24, 1);

        String[] t2 = {
                "absorb_bulb", "cell_battery", "mental_herb", "mirror_herb", "power_herb", "white_herb",
                "quick_claw", "scope_lens", "wide_lens", "zoom_lens", "wise_glasses", "muscle_band",
                "expert_belt", "shell_bell", "big_root", "binding_band", "cleanse_tag", "smoke_ball"
        };
        for (String id : t2) entry(root, "cobblemon:" + id, 40, 1, 1, 15, 1);

        String[] t3 = {
                "leftovers", "focus_band", "kings_rock", "loaded_dice", "metronome", "protective_pads",
                "punching_glove", "rocky_helmet", "shed_shell", "smooth_rock", "damp_rock", "heat_rock", "icy_rock",
                "light_clay", "terrain_extender", "utility_umbrella", "room_service", "throat_spray", "eject_button", "eject_pack"
        };
        for (String id : t3) entry(root, "cobblemon:" + id, 60, 1, 1, 10, 1);

        String[] t4 = {
                "life_orb", "focus_sash", "choice_band", "choice_scarf", "choice_specs", "assault_vest",
                "heavy_duty_boots", "safety_goggles", "weakness_policy", "red_card", "air_balloon", "float_stone",
                "iron_ball", "ring_target", "sticky_barb", "toxic_orb", "flame_orb", "blunder_policy", "covert_cloak"
        };
        for (String id : t4) entry(root, "cobblemon:" + id, 80, 1, 1, 5, 0);

        String[] t5 = {
                "ability_shield", "bright_powder", "destiny_knot", "eviolite", "exp_share", "lucky_egg",
                "light_ball", "metal_powder", "quick_powder", "scope_lens", "razor_claw", "razor_fang", "deep_sea_scale", "deep_sea_tooth",
                "black_sludge", "everstone", "medicinal_leek"
        };
        for (String id : t5) entry(root, "cobblemon:" + id, 90, 1, 1, 3, 0);

        String[] powerItems = {"power_anklet", "power_band", "power_belt", "power_bracer", "power_lens", "power_weight"};
        for (String id : powerItems) entry(root, "cobblemon:" + id, 70, 1, 1, 8, 1);

        entry(root, "minecraft:bone", 10, 1, 3, 16, 1);
        entry(root, "minecraft:snowball", 10, 1, 4, 16, 1);

        String[] terrainSeeds = {"electric_seed", "grassy_seed", "misty_seed", "psychic_seed"};
        for (String id : terrainSeeds) entry(root, "cobblemon:" + id, 50, 1, 1, 10, 1);

        String[] gems = {"bug", "dark", "dragon", "electric", "fairy", "fighting", "fire", "flying", "ghost", "grass", "ground", "ice", "normal", "poison", "psychic", "rock", "steel", "water"};
        for (String type : gems) entry(root, "cobblemon:" + type + "_gem", 30, 1, 2, 18, 1);
    }

    private static void entry(ConfigRoot root, String itemId, int minLevel, int min, int max, int weight, int weightPerLevel) {
        LootEntry entry = new LootEntry();
        entry.itemId = itemId;
        entry.minBattlingLevel = minLevel;
        entry.minAmount = min;
        entry.maxAmount = max;
        entry.weight = weight;
        entry.weightPerLevelAboveUnlock = weightPerLevel;
        entry.enabled = true;
        root.rewards.add(entry);
    }
}
