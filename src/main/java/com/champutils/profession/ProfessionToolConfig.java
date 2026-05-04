package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfessionToolConfig {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    public static Map<String, ToolData> TOOLS =
            new HashMap<>();

    public static class ToolData {

        /*
         Unlock requirements
         */
        public String profession;
        public int requiredLevel;

        /*
         Visuals
         */
        public String displayName;
        public String rarity;
        public String baseItem;
        public int customModelData;

        /*
         Stat scaling
         */
        public Map<String, Double> stats =
                new HashMap<>();

        /*
         Passive abilities
         */
        public List<String> passives =
                new ArrayList<>();
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
                            "profession_tools.json"
                    );

            if (!file.exists()) {
                createDefault(file);
            }

            try (
                    FileReader reader =
                            new FileReader(file)
            ) {
                Type type =
                        new TypeToken<
                                Map<String, ToolData>
                                >(){}.getType();

                TOOLS =
                        GSON.fromJson(
                                reader,
                                type
                        );
            }

            System.out.println(
                    "[ChampUtils] Loaded " +
                            TOOLS.size() +
                            " profession tools."
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createDefault(
            File file
    ) {
        try {

            Map<String, ToolData> defaults =
                    new HashMap<>();

            /*
             MINING TOOL
             */
            defaults.put(
                    "miners_fang",
                    createTool(
                            "MINING",
                            25,
                            "Miner's Fang",
                            "RARE",
                            "minecraft:diamond_pickaxe",
                            1001,
                            Map.of(
                                    "miningSpeed", 20.0,
                                    "fortuneBonus", 5.0,
                                    "durabilityBonus", 100.0
                            ),
                            List.of(
                                    "bonus_ore_drops"
                            )
                    )
            );

            /*
             LEGENDARY MINING TOOL
             */
            defaults.put(
                    "titanbreaker",
                    createTool(
                            "MINING",
                            100,
                            "Titanbreaker",
                            "LEGENDARY",
                            "minecraft:netherite_pickaxe",
                            1002,
                            Map.of(
                                    "miningSpeed", 50.0,
                                    "fortuneBonus", 20.0,
                                    "durabilityBonus", 500.0
                            ),
                            List.of(
                                    "vein_mining",
                                    "bonus_ore_drops"
                            )
                    )
            );

            /*
             FORESTRY TOOL
             */
            defaults.put(
                    "woodcleaver",
                    createTool(
                            "FORESTRY",
                            25,
                            "Woodcleaver",
                            "RARE",
                            "minecraft:diamond_axe",
                            2001,
                            Map.of(
                                    "chopSpeed", 20.0,
                                    "bonusLogs", 5.0
                            ),
                            List.of(
                                    "faster_tree_chopping"
                            )
                    )
            );

            /*
             LEGENDARY FORESTRY TOOL
             */
            defaults.put(
                    "worldtree_axe",
                    createTool(
                            "FORESTRY",
                            100,
                            "Worldtree Axe",
                            "LEGENDARY",
                            "minecraft:netherite_axe",
                            2002,
                            Map.of(
                                    "chopSpeed", 50.0,
                                    "bonusLogs", 20.0
                            ),
                            List.of(
                                    "timber_break"
                            )
                    )
            );

            /*
             FISHING TOOL
             */
            defaults.put(
                    "poseidons_line",
                    createTool(
                            "FISHING",
                            100,
                            "Poseidon's Line",
                            "LEGENDARY",
                            "minecraft:fishing_rod",
                            3001,
                            Map.of(
                                    "rareLootChance", 25.0
                            ),
                            List.of(
                                    "rare_loot_boost"
                            )
                    )
            );

            /*
             FARMING TOOL
             */
            defaults.put(
                    "gaias_blessing",
                    createTool(
                            "FARMING",
                            100,
                            "Gaia's Blessing",
                            "LEGENDARY",
                            "minecraft:diamond_hoe",
                            4001,
                            Map.of(
                                    "bonusCropYield", 20.0
                            ),
                            List.of(
                                    "auto_replant"
                            )
                    )
            );

            try (
                    FileWriter writer =
                            new FileWriter(file)
            ) {
                GSON.toJson(
                        defaults,
                        writer
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ToolData createTool(
            String profession,
            int requiredLevel,
            String displayName,
            String rarity,
            String baseItem,
            int customModelData,
            Map<String, Double> stats,
            List<String> passives
    ) {
        ToolData tool =
                new ToolData();

        tool.profession =
                profession;

        tool.requiredLevel =
                requiredLevel;

        tool.displayName =
                displayName;

        tool.rarity =
                rarity;

        tool.baseItem =
                baseItem;

        tool.customModelData =
                customModelData;

        tool.stats =
                stats;

        tool.passives =
                passives;

        return tool;
    }
}