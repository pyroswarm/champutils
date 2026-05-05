package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public class ProfessionLootConfig {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    public static Map<String, LootTable> TABLES =
            new HashMap<>();

    public static class LootTable {
        public double dropChance;
        public List<LootEntry> items =
                new ArrayList<>();
    }

    public static class LootEntry {
        public String itemId;
        public int weight;
        public int minAmount;
        public int maxAmount;
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
                            "profession_loot.json"
                    );

            if (!file.exists()) {
                createDefault(file);
            }

            try (
                    FileReader reader =
                            new FileReader(file)
            ) {
                ProfessionLootConfig loaded =
                        GSON.fromJson(
                                reader,
                                ProfessionLootConfig.class
                        );

                TABLES = loaded.TABLES;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createDefault(
            File file
    ) {
        try {
            ProfessionLootConfig config =
                    new ProfessionLootConfig();

            config.TABLES.put(
                    "MINING",
                    createTable(
                            0.15,
                            loot("minecraft:iron_ingot",50,1,3),
                            loot("minecraft:coal",40,1,5),
                            loot("cobblemon:fire_stone",10,1,1)
                    )
            );

            config.TABLES.put(
                    "FORESTRY",
                    createTable(
                            0.15,
                            loot("minecraft:oak_log",50,1,4),
                            loot("cobblemon:red_apricorn",35,1,3),
                            loot("cobblemon:miracle_seed",10,1,1)
                    )
            );

            config.TABLES.put(
                    "FARMING",
                    createTable(
                            0.15,
                            loot("minecraft:wheat",50,1,5),
                            loot("minecraft:carrot",35,1,5),
                            loot("cobblemon:oran_berry",10,1,2)
                    )
            );

            try (
                    FileWriter writer =
                            new FileWriter(file)
            ) {
                GSON.toJson(config, writer);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static LootTable createTable(
            double chance,
            LootEntry... entries
    ) {
        LootTable table =
                new LootTable();

        table.dropChance = chance;
        table.items =
                Arrays.asList(entries);

        return table;
    }

    private static LootEntry loot(
            String itemId,
            int weight,
            int min,
            int max
    ) {
        LootEntry entry =
                new LootEntry();

        entry.itemId = itemId;
        entry.weight = weight;
        entry.minAmount = min;
        entry.maxAmount = max;

        return entry;
    }
}