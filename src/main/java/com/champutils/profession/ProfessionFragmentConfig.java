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
     * Keyed by backend Adventurer rank: F, E, D, C, B, A, S.
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

        /** Preferred name after the Adventurer Rank Essence rename. */
        public Map<String, FragmentData> essence =
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
        public String essence = "";
        public int min = 1;
        public int max = 1;
    }

    public static class UpgradeData {
        public String fromFragment = "";
        public String fromEssence = "";
        public int cost = 10;
        public String toFragment = "";
        public String toEssence = "";
        public int output = 1;
    }

    public static class ToolCraftingData {
        public String fragment = "";
        public String essence = "";
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
                            "profession_essence.json"
                    );

            File legacyFile =
                    new File(
                            dir,
                            "profession_fragments.json"
                    );

            if (!file.exists() && legacyFile.exists()) {
                file = legacyFile;
            }

            if (!file.exists()) {
                createDefault(file);
            }

            try (FileReader reader = new FileReader(file)) {
                ConfigRoot root =
                        GSON.fromJson(
                                reader,
                                ConfigRoot.class
                        );

                if (root == null) {
                    FRAGMENTS = new LinkedHashMap<>();
                } else if (root.essence != null && !root.essence.isEmpty()) {
                    FRAGMENTS = root.essence;
                } else {
                    FRAGMENTS = root.fragments == null ? new LinkedHashMap<>() : root.fragments;
                }

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
            applyRankFragmentDisplay();
            TRADES = TOOL_CRAFTING;
            saveLoaded();

            System.out.println(
                    "[ChampUtils] Loaded " +
                            FRAGMENTS.size() +
                            " profession essences and " +
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

        normalizeEssenceAliases();

        for (Map.Entry<String, FragmentData> entry : defaults.fragments.entrySet()) {
            FRAGMENTS.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, SalvageData> entry : defaults.salvage.entrySet()) {
            SALVAGE.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, UpgradeData> entry : defaults.upgrades.entrySet()) {
            UPGRADES.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, ToolCraftingData> entry : defaults.toolCrafting.entrySet()) {
            TOOL_CRAFTING.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (FragmentData fragment : FRAGMENTS.values()) {
            if (fragment == null) continue;
            fragment.baseItem = "minecraft:paper";
            fragment.customModelData = 0;
        }

        // Remove legacy prestige ladder conversions from already-existing configs.
        UPGRADES.entrySet().removeIf(entry -> {
            UpgradeData data = entry.getValue();
            return data != null && isBlockedPrestigeConversion(data.fromFragment, data.toFragment);
        });
        // Force current upgrade values on old configs and permanently remove every downgrade path.
        UPGRADES.entrySet().removeIf(entry -> entry.getKey().toUpperCase(java.util.Locale.ROOT).contains("DOWNGRADE") ||
                (entry.getValue() != null && rarityIndex(entry.getValue().toFragment) < rarityIndex(entry.getValue().fromFragment)));
        for (Map.Entry<String, UpgradeData> entry : defaults.upgrades.entrySet()) {
            UpgradeData loaded = UPGRADES.get(entry.getKey());
            UpgradeData def = entry.getValue();
            if (loaded == null || def == null) continue;
            loaded.fromFragment = def.fromFragment;
            loaded.cost = def.cost;
            loaded.toFragment = def.toFragment;
            loaded.output = def.output;
        }
    }

    private static void normalizeEssenceAliases() {
        if (SALVAGE != null) {
            for (SalvageData data : SALVAGE.values()) {
                if (data == null) continue;
                if ((data.fragment == null || data.fragment.isBlank()) && data.essence != null && !data.essence.isBlank()) {
                    data.fragment = data.essence;
                }
                data.essence = data.fragment;
            }
        }
        if (UPGRADES != null) {
            for (UpgradeData data : UPGRADES.values()) {
                if (data == null) continue;
                if ((data.fromFragment == null || data.fromFragment.isBlank()) && data.fromEssence != null && !data.fromEssence.isBlank()) {
                    data.fromFragment = data.fromEssence;
                }
                if ((data.toFragment == null || data.toFragment.isBlank()) && data.toEssence != null && !data.toEssence.isBlank()) {
                    data.toFragment = data.toEssence;
                }
                data.fromEssence = data.fromFragment;
                data.toEssence = data.toFragment;
            }
        }
        if (TOOL_CRAFTING != null) {
            for (ToolCraftingData data : TOOL_CRAFTING.values()) {
                if (data == null) continue;
                if ((data.fragment == null || data.fragment.isBlank()) && data.essence != null && !data.essence.isBlank()) {
                    data.fragment = data.essence;
                }
                data.essence = data.fragment;
            }
        }
    }

    private static void saveLoaded() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "profession_essence.json");
            ConfigRoot root = new ConfigRoot();
            root.fragments = new LinkedHashMap<>();
            root.essence = FRAGMENTS;
            root.salvage = SALVAGE;
            root.upgrades = UPGRADES;
            root.toolCrafting = TOOL_CRAFTING;
            try (FileWriter writer = new FileWriter(file)) { GSON.toJson(root, writer); }
        } catch (Exception e) {
            e.printStackTrace();
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

        addFragment(root, "F", "f_rank_essence", "F Rank Essence", "minecraft:paper", 0, "WHITE");
        addFragment(root, "E", "e_rank_essence", "E Rank Essence", "minecraft:paper", 0, "GREEN");
        addFragment(root, "D", "d_rank_essence", "D Rank Essence", "minecraft:paper", 0, "BLUE");
        addFragment(root, "C", "c_rank_essence", "C Rank Essence", "minecraft:paper", 0, "LIGHT_PURPLE");
        addFragment(root, "B", "b_rank_essence", "B Rank Essence", "minecraft:paper", 0, "YELLOW");
        addFragment(root, "A", "a_rank_essence", "A Rank Essence", "minecraft:paper", 0, "GOLD");
        addFragment(root, "S", "s_rank_essence", "S Rank Essence", "minecraft:paper", 0, "DARK_PURPLE");

        addSalvage(root, "F", "F", 3, 5);
        addSalvage(root, "E", "E", 3, 5);
        addSalvage(root, "D", "D", 3, 5);
        addSalvage(root, "C", "C", 2, 4);
        addSalvage(root, "B", "B", 1, 3);
        addSalvage(root, "A", "A", 1, 2);
        addSalvage(root, "S", "S", 1, 2);

        addUpgrade(root, "F_TO_E", "F", 16, "E", 1);
        addUpgrade(root, "E_TO_D", "E", 16, "D", 1);
        addUpgrade(root, "D_TO_C", "D", 12, "C", 1);
        addUpgrade(root, "C_TO_B", "C", 32, "B", 1);
        // A and S fragments are intentionally source-only prestige rewards.
        // B exists as the high grind bridge between C and A.

        addToolCrafting(root, "F", "F", 16);
        addToolCrafting(root, "E", "E", 16);
        addToolCrafting(root, "D", "D", 16);
        addToolCrafting(root, "C", "C", 16);
        addToolCrafting(root, "B", "B", 16);
        addToolCrafting(root, "A", "A", 16);
        addToolCrafting(root, "S", "S", 16);

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
        data.lore = "Salvaged profession tool material. Upgrade into higher-tier essence.";

        root.fragments.put(
                rarity,
                data
        );
        root.essence.put(
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
        data.essence = fragment;
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
        data.fromEssence = fromFragment;
        data.cost = cost;
        data.toFragment = toFragment;
        data.toEssence = toFragment;
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
        data.essence = fragment;
        data.cost = cost;

        root.toolCrafting.put(
                rarity,
                data
        );
    }


    private static void applyRankFragmentDisplay() {
        for (Map.Entry<String, FragmentData> entry : FRAGMENTS.entrySet()) {
            if (entry == null || entry.getValue() == null) continue;
            String rarity = normalizeRarity(entry.getKey());
            String rank = rankForRarity(rarity);
            if (rank == null) continue;
            entry.getValue().displayName = rank + " Rank Essence";
            if (entry.getValue().lore == null || entry.getValue().lore.isBlank() || entry.getValue().lore.toLowerCase(java.util.Locale.ROOT).contains("essence")) {
                entry.getValue().lore = "Used for " + rank + " Rank profession crafting, upgrades, and prestige progression.";
            }
        }
    }

    public static String rankForRarity(String rarity) {
        return normalizeRarity(rarity);
    }

    public static boolean isBlockedPrestigeConversion(String fromFragment, String toFragment) {
        String from = normalizeRarity(fromFragment);
        String to = normalizeRarity(toFragment);

        // C should be the normal long-term baseline. B is craftable but expensive; A/S must come
        // from intended prestige sources, not normal fragment ladder conversion.
        if (from.equals("B") && to.equals("A")) return true;
        if (from.equals("A") && to.equals("S")) return true;
        if (from.equals("S") && !to.equals("S")) return true;

        return false;
    }

    public static int rarityIndex(String rarity) {
        return switch (normalizeRarity(rarity)) {
            case "F" -> 0;
            case "E" -> 1;
            case "D" -> 2;
            case "C" -> 3;
            case "B" -> 4;
            case "A" -> 5;
            case "S" -> 6;
            default -> -1;
        };
    }

    public static String normalizeRarity(String rarity) {
        return com.champutils.rarity.RarityScale.normalize(rarity);
    }
}
