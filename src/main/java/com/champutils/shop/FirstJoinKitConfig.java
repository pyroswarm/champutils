package com.champutils.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FirstJoinKitConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "first_join_kit.json");

    public static KitRoot CONFIG = new KitRoot();

    private FirstJoinKitConfig() {
    }

    public static final class KitRoot {
        public boolean enabled = true;
        public List<KitEntry> entries = new ArrayList<>();
        public List<KitEntry> islanderEntries = new ArrayList<>();
    }

    public static final class KitEntry {
        /** item, tool, or command */
        public String type = "item";
        public String id = "minecraft:stone";
        public int amount = 1;
        public String toolType = "pickaxe";
        public String rarity = "COMMON";
        public List<String> commands = new ArrayList<>();
    }

    public static void load() {
        try {
            if (!DIR.exists()) DIR.mkdirs();

            if (!FILE.exists()) {
                CONFIG = createDefault();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                KitRoot loaded = GSON.fromJson(reader, KitRoot.class);
                CONFIG = loaded == null ? createDefault() : loaded;
            }

            sanitize();
            save();
        } catch (Exception exception) {
            exception.printStackTrace();
            CONFIG = createDefault();
        }
    }

    public static void save() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(CONFIG, writer);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static void sanitize() {
        if (CONFIG == null) CONFIG = createDefault();
        if (CONFIG.entries == null) CONFIG.entries = new ArrayList<>();
        if (CONFIG.islanderEntries == null) CONFIG.islanderEntries = new ArrayList<>();
        ensureIslanderBonusEntries(CONFIG.islanderEntries);
        removeApricornSeedEntries(CONFIG.islanderEntries);
        normalizeStarterToolEntries(CONFIG.entries);
        for (KitEntry entry : CONFIG.entries) {
            if (entry.type == null || entry.type.isBlank()) entry.type = "item";
            if (entry.id == null) entry.id = "";
            if (entry.amount <= 0) entry.amount = 1;
            if (entry.toolType == null || entry.toolType.isBlank()) entry.toolType = "pickaxe";
            if (entry.rarity == null || entry.rarity.isBlank()) entry.rarity = "COMMON";
            if (entry.commands == null) entry.commands = new ArrayList<>();
        }
        for (KitEntry entry : CONFIG.islanderEntries) {
            if (entry.type == null || entry.type.isBlank()) entry.type = "item";
            if (entry.id == null) entry.id = "";
            if (entry.amount <= 0) entry.amount = 1;
            if (entry.toolType == null || entry.toolType.isBlank()) entry.toolType = "pickaxe";
            if (entry.rarity == null || entry.rarity.isBlank()) entry.rarity = "COMMON";
            if (entry.commands == null) entry.commands = new ArrayList<>();
        }
    }

    private static void normalizeStarterToolEntries(List<KitEntry> entries) {
        if (entries == null) return;

        for (KitEntry entry : entries) {
            if (entry == null || !"tool".equalsIgnoreCase(entry.type)) continue;
            String type = normalizeToolType(entry.toolType);
            entry.toolType = type.isBlank() ? "pickaxe" : type;
        }

        ensureTool(entries, "pickaxe");
        ensureTool(entries, "axe");
        ensureTool(entries, "hoe");
        ensureTool(entries, "shovel");

        Set<String> seen = new LinkedHashSet<>();
        Iterator<KitEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            KitEntry entry = iterator.next();
            if (entry == null || !"tool".equalsIgnoreCase(entry.type)) continue;
            String key = normalizeToolType(entry.toolType);
            if (!seen.add(key)) {
                iterator.remove();
            }
        }
    }

    private static String normalizeToolType(String toolType) {
        if (toolType == null) return "";
        String normalized = toolType.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.equals("pick") || normalized.equals("pickaxes")) return "pickaxe";
        if (normalized.equals("axes")) return "axe";
        if (normalized.equals("hoes")) return "hoe";
        if (normalized.equals("shovels") || normalized.equals("spade")) return "shovel";
        return normalized;
    }

    private static KitRoot createDefault() {
        KitRoot root = new KitRoot();
        root.enabled = true;
        root.entries.add(tool("pickaxe"));
        root.entries.add(tool("axe"));
        root.entries.add(tool("hoe"));
        root.entries.add(tool("shovel"));
        root.entries.add(item("cobblemon:poke_ball", 16));
        root.entries.add(item("minecraft:cooked_beef", 16));
        root.islanderEntries.addAll(createDefaultIslanderEntries());
        return root;
    }

    private static void ensureTool(List<KitEntry> entries, String toolType) {
        for (KitEntry entry : entries) {
            if (entry != null && "tool".equalsIgnoreCase(entry.type) && toolType.equals(normalizeToolType(entry.toolType))) return;
        }
        entries.add(tool(toolType));
    }

    private static void ensureIslanderBonusEntries(List<KitEntry> entries) {
        ensureItem(entries, "minecraft:oak_sapling", 16);
        ensureItem(entries, "minecraft:lava_bucket", 1);
        ensureItem(entries, "minecraft:water_bucket", 2);
        ensureItem(entries, "minecraft:dirt", 64);
    }

    private static void removeApricornSeedEntries(List<KitEntry> entries) {
        if (entries == null) return;
        entries.removeIf(entry -> entry != null
                && "item".equalsIgnoreCase(entry.type)
                && entry.id != null
                && entry.id.toLowerCase(Locale.ROOT).startsWith("cobblemon:")
                && entry.id.toLowerCase(Locale.ROOT).endsWith("_apricorn_seed"));
    }

    private static void ensureItem(List<KitEntry> entries, String id, int amount) {
        for (KitEntry entry : entries) {
            if (entry != null && "item".equalsIgnoreCase(entry.type) && id.equalsIgnoreCase(entry.id)) {
                entry.amount = Math.max(entry.amount, amount);
                return;
            }
        }
        entries.add(item(id, amount));
    }

    private static List<KitEntry> createDefaultIslanderEntries() {
        List<KitEntry> entries = new ArrayList<>();
        entries.add(item("minecraft:oak_sapling", 16));
        entries.add(item("minecraft:lava_bucket", 1));
        entries.add(item("minecraft:water_bucket", 2));
        entries.add(item("minecraft:dirt", 64));
        return entries;
    }

    private static KitEntry item(String id, int amount) {
        KitEntry entry = new KitEntry();
        entry.type = "item";
        entry.id = id;
        entry.amount = amount;
        return entry;
    }

    private static KitEntry tool(String toolType) {
        KitEntry entry = new KitEntry();
        entry.type = "tool";
        entry.toolType = toolType;
        entry.rarity = "COMMON";
        entry.amount = 1;
        return entry;
    }
}
