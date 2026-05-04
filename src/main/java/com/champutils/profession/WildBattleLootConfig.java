package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public class WildBattleLootConfig {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    public static LootTable TABLE =
            new LootTable();

    public static class LootTable {
        public double dropChance = 0.10;
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
                            "wild_battle_loot.json"
                    );

            if (!file.exists()) {
                createDefault(file);
            }

            try (
                    FileReader reader =
                            new FileReader(file)
            ) {
                WildBattleLootConfig loaded =
                        GSON.fromJson(
                                reader,
                                WildBattleLootConfig.class
                        );

                TABLE = loaded.TABLE;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createDefault(
            File file
    ) {
        try {

            WildBattleLootConfig config =
                    new WildBattleLootConfig();

            config.TABLE.dropChance = 0.10;

            config.TABLE.items.add(
                    loot(
                            "tm_protect",
                            40,
                            1,
                            1
                    )
            );

            config.TABLE.items.add(
                    loot(
                            "tm_shadow_ball",
                            25,
                            1,
                            1
                    )
            );

            config.TABLE.items.add(
                    loot(
                            "rare_tm_earthquake",
                            10,
                            1,
                            1
                    )
            );

            try (
                    FileWriter writer =
                            new FileWriter(file)
            ) {
                GSON.toJson(
                        config,
                        writer
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
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