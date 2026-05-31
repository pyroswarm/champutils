package com.champutils.crate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CrateKeyCraftingConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/crate_key_crafting.json");

    public static boolean enabled = true;
    public static Map<String, RecipeData> recipes = new LinkedHashMap<>();

    private CrateKeyCraftingConfig() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) createDefault();

            try (FileReader reader = new FileReader(FILE)) {
                Root root = GSON.fromJson(reader, Root.class);
                Root defaults = defaultRoot();
                if (root == null) root = defaults;
                enabled = root.enabled;
                recipes = root.recipes == null ? new LinkedHashMap<>() : root.recipes;
                for (Map.Entry<String, RecipeData> entry : defaults.recipes.entrySet()) {
                    recipes.putIfAbsent(entry.getKey(), entry.getValue());
                }
                save();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Root defaults = defaultRoot();
            enabled = defaults.enabled;
            recipes = defaults.recipes;
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            Root root = new Root();
            root.enabled = enabled;
            root.recipes = recipes;
            GSON.toJson(root, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createDefault() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(defaultRoot(), writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Root defaultRoot() {
        Root root = new Root();
        add(root, "common", "COMMON", 128,
                item("minecraft:coal", 128), item("minecraft:copper_ingot", 64), item("minecraft:iron_ingot", 32),
                item("cobblemon:tumblestone", 64), item("cobblemon:poke_ball", 16));
        add(root, "uncommon", "UNCOMMON", 192,
                item("minecraft:iron_ingot", 128), item("minecraft:gold_ingot", 64), item("minecraft:redstone", 128),
                item("cobblemon:great_ball", 24), item("cobblemon:exp_candy_s", 16));
        add(root, "rare", "RARE", 160,
                item("minecraft:diamond", 24), item("minecraft:emerald", 16), item("minecraft:lapis_lazuli", 128), item("minecraft:gold_ingot", 128),
                item("cobblemon:ultra_ball", 16), item("cobblemon:rare_candy", 4), item("cobblemon:thunder_stone", 4), item("cobblemon:fire_stone", 4));
        add(root, "epic", "EPIC", 128,
                item("minecraft:diamond", 64), item("minecraft:emerald", 64), item("minecraft:ancient_debris", 4), item("minecraft:netherite_scrap", 4),
                item("cobblemon:rare_candy", 16), item("cobblemon:ability_capsule", 2), item("cobblemon:exp_candy_l", 32));
        add(root, "legendary", "LEGENDARY", 96,
                item("minecraft:netherite_ingot", 2), item("minecraft:ancient_debris", 16), item("minecraft:diamond_block", 16), item("minecraft:emerald_block", 8),
                item("cobblemon:ability_patch", 2), item("cobblemon:rare_candy", 32), item("cobblemon:exp_candy_xl", 16));
        add(root, "mythic", "MYTHIC", 64,
                item("minecraft:netherite_ingot", 8), item("minecraft:netherite_block", 1), item("minecraft:ancient_debris", 32), item("minecraft:diamond_block", 32), item("minecraft:emerald_block", 16),
                item("cobblemon:ability_patch", 4), item("cobblemon:master_ball", 2), item("cobblemon:rare_candy", 64), item("cobblemon:exp_candy_xl", 32));
        return root;
    }

    private static void add(Root root, String crateId, String fragment, int fragmentCost, ItemCost... costs) {
        RecipeData data = new RecipeData();
        data.crateId = crateId;
        data.outputKeys = 1;
        data.fragment = fragment;
        data.fragmentCost = fragmentCost;
        data.items = new ArrayList<>(List.of(costs));
        root.recipes.put(crateId, data);
    }

    private static ItemCost item(String id, int amount) {
        ItemCost cost = new ItemCost();
        cost.item = id;
        cost.amount = amount;
        return cost;
    }

    public static class Root {
        public boolean enabled = true;
        public Map<String, RecipeData> recipes = new LinkedHashMap<>();
    }

    public static class RecipeData {
        public String crateId = "common";
        public int outputKeys = 1;
        public String fragment = "COMMON";
        public int fragmentCost = 128;
        public List<ItemCost> items = new ArrayList<>();
    }

    public static class ItemCost {
        public String item = "minecraft:diamond";
        public int amount = 1;
    }
}
