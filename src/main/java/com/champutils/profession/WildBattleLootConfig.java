package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class WildBattleLootConfig {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    public static LootTable TABLE =
            new LootTable();

    public static class Wrapper {
        public LootTable table =
                new LootTable();
    }

    public static class LootTable {
        public double dropChance = 1.0;
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
                Wrapper loaded =
                        GSON.fromJson(
                                reader,
                                Wrapper.class
                        );

                if (
                        loaded != null &&
                                loaded.table != null
                ) {
                    TABLE =
                            loaded.table;
                }
            }

            if (
                    TABLE.items == null
            ) {
                TABLE.items =
                        new ArrayList<>();
            }

            System.out.println(
                    "[ChampUtils] Loaded wild battle loot: " +
                            TABLE.items.size() +
                            " entries."
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createDefault(
            File file
    ) {
        try {

            Wrapper config =
                    new Wrapper();

            config.table.dropChance =
                    1.0;

            config.table.items.add(
                    loot(
                            "cobblemon:poke_ball",
                            100,
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

        entry.itemId =
                itemId;

        entry.weight =
                weight;

        entry.minAmount =
                min;

        entry.maxAmount =
                max;

        return entry;
    }
}