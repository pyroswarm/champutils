package com.champutils.worldborder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChampWorldBorderConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/world_borders.json");

    private static Data data = new Data();

    private ChampWorldBorderConfig() {
    }

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (!FILE.exists()) {
                addDefaults();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                data = loaded == null ? new Data() : loaded;
            }

            normalize();
            save();
        } catch (Exception e) {
            e.printStackTrace();
            data = new Data();
            addDefaults();
            save();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isEnabled() {
        return data.enabled;
    }

    public static boolean shouldEnforceWithTick() {
        return data.enforcePlayerPosition;
    }

    public static int warningBlocks() {
        return Math.max(0, data.warningBlocks);
    }

    public static int warningTimeSeconds() {
        return Math.max(0, data.warningTimeSeconds);
    }

    public static Map<String, BorderEntry> borders() {
        return data.borders;
    }

    public static BorderEntry get(String dimension) {
        return data.borders.get(normalizeDimension(dimension));
    }

    public static void set(String dimension, double radius, double centerX, double centerZ) {
        BorderEntry entry = new BorderEntry();
        entry.radius = Math.max(1.0D, radius);
        entry.centerX = centerX;
        entry.centerZ = centerZ;
        data.borders.put(normalizeDimension(dimension), entry);
        save();
    }

    public static boolean remove(String dimension) {
        boolean removed = data.borders.remove(normalizeDimension(dimension)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public static String normalizeDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return "minecraft:overworld";
        }

        String trimmed = dimension.trim().toLowerCase(java.util.Locale.ROOT);

        if (trimmed.equals("overworld")) return "minecraft:overworld";
        if (trimmed.equals("nether")) return "minecraft:the_nether";
        if (trimmed.equals("end")) return "minecraft:the_end";

        if (!trimmed.contains(":")) {
            return "multiworld:" + trimmed;
        }

        try {
            return ResourceLocation.parse(trimmed).toString();
        } catch (Exception ignored) {
            return trimmed;
        }
    }

    private static void normalize() {
        if (data.borders == null) data.borders = new LinkedHashMap<>();

        Map<String, BorderEntry> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, BorderEntry> entry : data.borders.entrySet()) {
            if (entry.getValue() == null) continue;
            BorderEntry border = entry.getValue();
            border.radius = Math.max(1.0D, border.radius);
            normalized.put(normalizeDimension(entry.getKey()), border);
        }
        data.borders = normalized;
    }

    private static void addDefaults() {
        data.enabled = true;
        data.enforcePlayerPosition = true;
        data.warningBlocks = 16;
        data.warningTimeSeconds = 15;

        BorderEntry overworld = new BorderEntry();
        overworld.radius = 5000.0D;
        data.borders.put("minecraft:overworld", overworld);

        BorderEntry nether = new BorderEntry();
        nether.radius = 625.0D;
        data.borders.put("minecraft:the_nether", nether);

        BorderEntry end = new BorderEntry();
        end.radius = 5000.0D;
        data.borders.put("minecraft:the_end", end);
    }

    public static final class BorderEntry {
        public double radius = 5000.0D;
        public double centerX = 0.0D;
        public double centerZ = 0.0D;
    }

    private static final class Data {
        boolean enabled = true;
        boolean enforcePlayerPosition = true;
        int warningBlocks = 16;
        int warningTimeSeconds = 15;
        Map<String, BorderEntry> borders = new LinkedHashMap<>();
    }
}
