package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfessionFragmentConfig {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    public static Map<String, FragmentData> FRAGMENTS =
            new LinkedHashMap<>();

    public static Map<String, SalvageData> SALVAGE =
            new LinkedHashMap<>();

    public static Map<String, UpgradeData> UPGRADES =
            new LinkedHashMap<>();

    /**
     * Config for crafting random unidentified profession tools from stored fragments.
     * Keyed by rarity: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC.
     */
    public static Map<String, ToolCraftingData> TOOL_CRAFTING =
            new LinkedHashMap<>();

    /**
     * Backwards-compatible alias for older code/configs that called this "trades".
     * New configs should use toolCrafting.
     */
    @Deprecated
    public static Map<String, ToolCraftingData> TRADES =
            TOOL_CRAFTING;

    private ProfessionFragmentConfig() {
    }

    public static class ConfigRoot {
        public Map<String, FragmentData> fragments =
                new LinkedHashMap<>();

        public Map<String, SalvageData> salvage =
                new LinkedHashMap<>();

        public Map<String, UpgradeData> upgrades =
                new LinkedHashMap<>();

        public Map<String, ToolCraftingData> toolCrafting =
                new LinkedHashMap<>();

        /**
         * Legacy field. If your current file still has "trades", it will be read
         * and converted into TOOL_CRAFTING automatically.
         */
        public Map<String, ToolCraftingData> trades =
                new LinkedHashMap<>();
    }

    public static class FragmentData {
        public String itemId = "";
        public String displayName = "";
        public String baseItem = "minecraft:paper";
        public int customModelData = 0;
        public String color = "WHITE";
        public String lore = "Used to craft and upgrade profession tools.";
    }

    public static class SalvageData {
        public String fragment = "";
        public int min = 1;
        public int max = 1;
    }

    public static class UpgradeData {
        public String fromFragment = "";
        public int cost = 10;
        public String toFragment = "";
        public int output = 1;
    }

    public static class ToolCraftingData {
        public String fragment = "";
        public int cost = 64;
    }

    public static void load() {
        try {
            File dir =
                    new File("config/champutils");

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file =
                    new File(
                            dir,
                            "profession_fragments.json"
                    );

            if (!file.exists()) {
                createDefault(file);
            }

            try (FileReader reader = new FileReader(file)) {
                ConfigRoot root =
                        GSON.fromJson(
                                reader,
                                ConfigRoot.class
                        );

                FRAGMENTS = root == null || root.fragments == null
                        ? new LinkedHashMap<>()
                        : root.fragments;

                SALVAGE = root == null || root.salvage == null
                        ? new LinkedHashMap<>()
                        : root.salvage;

                UPGRADES = root == null || root.upgrades == null
                        ? new LinkedHashMap<>()
                        : root.upgrades;

                TOOL_CRAFTING = root == null || root.toolCrafting == null
                        ? new LinkedHashMap<>()
                        : root.toolCrafting;

                if (
                        TOOL_CRAFTING.isEmpty() &&
                                root != null &&
                                root.trades != null &&
                                !root.trades.isEmpty()
                ) {
                    TOOL_CRAFTING = root.trades;
                }
            }

            ensureDefaultsIfEmpty();
            TRADES = TOOL_CRAFTING;

            System.out.println(
                    "[ChampUtils] Loaded " +
                            FRAGMENTS.size() +
                            " profession fragments and " +
                            TOOL_CRAFTING.size() +
                            " tool crafting rules."
            );
        } catch (Exception e) {
            e.printStackTrace();
            ensureDefaultsIfEmpty();
            TRADES = TOOL_CRAFTING;
        }
    }

    private static void ensureDefaultsIfEmpty() {
        if (FRAGMENTS == null) {
            FRAGMENTS = new LinkedHashMap<>();
        }
        if (SALVAGE == null) {
            SALVAGE = new LinkedHashMap<>();
        }
        if (UPGRADES == null) {
            UPGRADES = new LinkedHashMap<>();
        }
        if (TOOL_CRAFTING == null) {
            TOOL_CRAFTING = new LinkedHashMap<>();
        }

        ConfigRoot defaults =
                defaultRoot();

        if (FRAGMENTS.isEmpty()) {
            FRAGMENTS = defaults.fragments;
        }

        if (SALVAGE.isEmpty()) {
            SALVAGE = defaults.salvage;
        }

        if (UPGRADES.isEmpty()) {
            UPGRADES = defaults.upgrades;
        }

        if (TOOL_CRAFTING.isEmpty()) {
            TOOL_CRAFTING = defaults.toolCrafting;
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
        ConfigRoot root =
                new ConfigRoot();

        addFragment(root, "COMMON", "common_tool_fragment", "Common Tool Fragment", "minecraft:paper", 9101, "WHITE");
        addFragment(root, "UNCOMMON", "uncommon_tool_fragment", "Uncommon Tool Fragment", "minecraft:paper", 9102, "GREEN");
        addFragment(root, "RARE", "rare_tool_fragment", "Rare Tool Fragment", "minecraft:paper", 9103, "BLUE");
        addFragment(root, "EPIC", "epic_tool_fragment", "Epic Tool Fragment", "minecraft:paper", 9104, "LIGHT_PURPLE");
        addFragment(root, "LEGENDARY", "legendary_tool_fragment", "Legendary Tool Fragment", "minecraft:paper", 9105, "GOLD");
        addFragment(root, "MYTHIC", "mythic_tool_fragment", "Mythic Tool Fragment", "minecraft:paper", 9106, "DARK_PURPLE");

        addSalvage(root, "COMMON", "COMMON", 3, 5);
        addSalvage(root, "UNCOMMON", "UNCOMMON", 3, 5);
        addSalvage(root, "RARE", "RARE", 3, 5);
        addSalvage(root, "EPIC", "EPIC", 2, 4);
        addSalvage(root, "LEGENDARY", "LEGENDARY", 1, 3);
        addSalvage(root, "MYTHIC", "MYTHIC", 1, 2);

        addUpgrade(root, "COMMON_TO_UNCOMMON", "COMMON", 64, "UNCOMMON", 1);
        addUpgrade(root, "UNCOMMON_TO_RARE", "UNCOMMON", 64, "RARE", 1);
        addUpgrade(root, "RARE_TO_EPIC", "RARE", 48, "EPIC", 1);
        addUpgrade(root, "EPIC_TO_LEGENDARY", "EPIC", 32, "LEGENDARY", 1);
        addUpgrade(root, "LEGENDARY_TO_MYTHIC", "LEGENDARY", 24, "MYTHIC", 1);

        addToolCrafting(root, "COMMON", "COMMON", 64);
        addToolCrafting(root, "UNCOMMON", "UNCOMMON", 64);
        addToolCrafting(root, "RARE", "RARE", 48);
        addToolCrafting(root, "EPIC", "EPIC", 32);
        addToolCrafting(root, "LEGENDARY", "LEGENDARY", 24);
        addToolCrafting(root, "MYTHIC", "MYTHIC", 16);

        return root;
    }

    private static void addFragment(
            ConfigRoot root,
            String rarity,
            String itemId,
            String displayName,
            String baseItem,
            int customModelData,
            String color
    ) {
        FragmentData data =
                new FragmentData();
        data.itemId = itemId;
        data.displayName = displayName;
        data.baseItem = baseItem;
        data.customModelData = customModelData;
        data.color = color;
        data.lore = "Salvaged profession tool material. Upgrade into higher-tier fragments.";

        root.fragments.put(
                rarity,
                data
        );
    }

    private static void addSalvage(
            ConfigRoot root,
            String rarity,
            String fragment,
            int min,
            int max
    ) {
        SalvageData data =
                new SalvageData();
        data.fragment = fragment;
        data.min = min;
        data.max = max;

        root.salvage.put(
                rarity,
                data
        );
    }

    private static void addUpgrade(
            ConfigRoot root,
            String id,
            String fromFragment,
            int cost,
            String toFragment,
            int output
    ) {
        UpgradeData data =
                new UpgradeData();
        data.fromFragment = fromFragment;
        data.cost = cost;
        data.toFragment = toFragment;
        data.output = output;

        root.upgrades.put(
                id,
                data
        );
    }

    private static void addToolCrafting(
            ConfigRoot root,
            String rarity,
            String fragment,
            int cost
    ) {
        ToolCraftingData data =
                new ToolCraftingData();
        data.fragment = fragment;
        data.cost = cost;

        root.toolCrafting.put(
                rarity,
                data
        );
    }

    public static String normalizeRarity(String rarity) {
        return rarity == null
                ? "COMMON"
                : rarity.trim().toUpperCase();
    }
}
