package com.champutils.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DungeonKeyConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Map<String, KeyData> KEYS = new LinkedHashMap<>();

    private DungeonKeyConfig() {
    }

    public static class KeyData {
        public String itemId = "common_dungeon_key";
        public String displayName = "Common Dungeon Key";
        public String rarity = "COMMON";
        public String baseItem = "minecraft:tripwire_hook";
        public int customModelData = 8101;
        public String color = "WHITE";
        public String lore = "Opens a solo Common dungeon.";
    }

    public static class ConfigRoot {
        public Map<String, KeyData> keys = new LinkedHashMap<>();
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, "dungeon_keys.json");
            if (!file.exists()) {
                ConfigRoot root = createDefaultRoot();
                try (FileWriter writer = new FileWriter(file)) {
                    GSON.toJson(root, writer);
                }
            }

            try (FileReader reader = new FileReader(file)) {
                ConfigRoot root = GSON.fromJson(reader, ConfigRoot.class);
                KEYS.clear();
                if (root != null && root.keys != null) {
                    KEYS.putAll(root.keys);
                }
            }

            if (KEYS.isEmpty()) {
                KEYS.putAll(createDefaultRoot().keys);
            }

            System.out.println("[ChampUtils] Loaded " + KEYS.size() + " dungeon keys.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ConfigRoot createDefaultRoot() {
        ConfigRoot root = new ConfigRoot();
        add(root, "common_dungeon_key", "Common Dungeon Key", "COMMON", 8101, "WHITE");
        add(root, "uncommon_dungeon_key", "Uncommon Dungeon Key", "UNCOMMON", 8102, "GREEN");
        add(root, "rare_dungeon_key", "Rare Dungeon Key", "RARE", 8103, "BLUE");
        add(root, "epic_dungeon_key", "Epic Dungeon Key", "EPIC", 8104, "DARK_PURPLE");
        add(root, "legendary_dungeon_key", "Legendary Dungeon Key", "LEGENDARY", 8105, "GOLD");
        add(root, "mythic_dungeon_key", "Mythic Dungeon Key", "MYTHIC", 8106, "LIGHT_PURPLE");
        return root;
    }

    private static void add(ConfigRoot root, String id, String name, String rarity, int cmd, String color) {
        KeyData data = new KeyData();
        data.itemId = id;
        data.displayName = name;
        data.rarity = rarity;
        data.customModelData = cmd;
        data.color = color;
        data.lore = "Opens a solo " + rarity.toLowerCase() + " dungeon.";
        root.keys.put(id, data);
    }
}
