package com.champutils.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DungeonKeyDropConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean enabled = true;
    public static boolean announceDrops = true;
    public static Map<String, ProfessionDropTable> professionDrops = new LinkedHashMap<>();

    private DungeonKeyDropConfig() {
    }

    public static class ConfigRoot {
        public boolean enabled = true;
        public boolean announceDrops = true;
        public Map<String, ProfessionDropTable> professionDrops = new LinkedHashMap<>();
    }

    public static class ProfessionDropTable {
        public boolean enabled = true;
        public List<KeyDropEntry> drops = new ArrayList<>();
    }

    public static class KeyDropEntry {
        public String keyId = "common_dungeon_key";
        public double chance = 0.003D;
        public int minAmount = 1;
        public int maxAmount = 1;
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, "dungeon_key_drops.json");
            if (!file.exists()) {
                try (FileWriter writer = new FileWriter(file)) {
                    GSON.toJson(createDefaultRoot(), writer);
                }
            }

            try (FileReader reader = new FileReader(file)) {
                ConfigRoot root = GSON.fromJson(reader, ConfigRoot.class);
                if (root == null) {
                    root = createDefaultRoot();
                }

                enabled = root.enabled;
                announceDrops = root.announceDrops;
                professionDrops.clear();
                if (root.professionDrops != null) {
                    professionDrops.putAll(root.professionDrops);
                }
            }

            if (professionDrops.isEmpty()) {
                professionDrops.putAll(createDefaultRoot().professionDrops);
            }

            System.out.println("[ChampUtils] Loaded dungeon key drops for " + professionDrops.size() + " professions.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ConfigRoot createDefaultRoot() {
        ConfigRoot root = new ConfigRoot();
        root.professionDrops.put("MINING", defaultTable());
        root.professionDrops.put("FORESTRY", defaultTable());
        root.professionDrops.put("FARMING", defaultTable());
        return root;
    }

    private static ProfessionDropTable defaultTable() {
        ProfessionDropTable table = new ProfessionDropTable();
        table.drops.add(entry("common_dungeon_key", 0.0030D));
        table.drops.add(entry("uncommon_dungeon_key", 0.0012D));
        table.drops.add(entry("rare_dungeon_key", 0.00045D));
        table.drops.add(entry("epic_dungeon_key", 0.00015D));
        table.drops.add(entry("legendary_dungeon_key", 0.00004D));
        table.drops.add(entry("mythic_dungeon_key", 0.00001D));
        return table;
    }

    private static KeyDropEntry entry(String keyId, double chance) {
        KeyDropEntry entry = new KeyDropEntry();
        entry.keyId = keyId;
        entry.chance = chance;
        entry.minAmount = 1;
        entry.maxAmount = 1;
        return entry;
    }
}
