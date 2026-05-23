package com.champutils.wiki;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.*;

public final class PokemonWikiIndex {
    private static final Gson GSON = new Gson();
    private static final Map<String, Info> INFO = new HashMap<>();

    private PokemonWikiIndex() {}

    public static void reload(MinecraftServer server) {
        INFO.clear();
        try {
            Map<ResourceLocation, Resource> resources = server.getResourceManager().listResources("spawn_pool_world", id -> id.getPath().endsWith(".json"));
            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    if (root == null || !root.has("spawns") || !root.get("spawns").isJsonArray()) continue;
                    for (JsonElement element : root.getAsJsonArray("spawns")) {
                        if (!element.isJsonObject()) continue;
                        JsonObject spawn = element.getAsJsonObject();
                        String pokemon = string(spawn, "pokemon");
                        if (pokemon.isBlank()) continue;
                        Info info = info(pokemon);
                        if (spawn.has("bucket")) info.rarity.add(string(spawn, "bucket"));
                        if (spawn.has("level")) info.levels.add(string(spawn, "level"));
                        JsonObject condition = spawn.has("condition") && spawn.get("condition").isJsonObject() ? spawn.getAsJsonObject("condition") : null;
                        if (condition != null) {
                            addArray(info.biomes, condition, "biomes");
                            addArray(info.blocks, condition, "neededNearbyBlocks");
                            addArray(info.structures, condition, "structures");
                            addTime(info.times, condition);
                            if (condition.has("canSeeSky")) info.extra.add("Can see sky: " + condition.get("canSeeSky"));
                            if (condition.has("isRaining")) info.extra.add("Raining: " + condition.get("isRaining"));
                            if (condition.has("isThundering")) info.extra.add("Thundering: " + condition.get("isThundering"));
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to build Pokémon wiki spawn index.");
            e.printStackTrace();
        }
    }

    public static Info get(String species) {
        return INFO.get(normal(species));
    }

    public static String abilities(String speciesName) {
        try {
            Species species = findSpecies(speciesName);
            if (species == null) return "Unknown Pokémon.";
            for (String methodName : List.of("getAbilities", "getAbilitiesMapping", "getPossibleAbilities", "getStandardAbilities")) {
                try {
                    Method m = species.getClass().getMethod(methodName);
                    Object result = m.invoke(species);
                    String formatted = formatObjectList(result);
                    if (!formatted.isBlank()) return formatted;
                } catch (Exception ignored) {}
            }
            return "No ability data found for this Pokémon.";
        } catch (Exception e) {
            return "No ability data found for this Pokémon.";
        }
    }

    public static String types(String speciesName) {
        try {
            Species species = findSpecies(speciesName);
            if (species == null) return "Unknown Pokémon.";
            for (String methodName : List.of("getTypes", "getPrimaryType", "getSecondaryType")) {
                try {
                    Method m = species.getClass().getMethod(methodName);
                    Object result = m.invoke(species);
                    String formatted = formatObjectList(result);
                    if (!formatted.isBlank()) return formatted;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return "No type data found for this Pokémon.";
    }

    public static Species findSpecies(String speciesName) {
        if (speciesName == null || speciesName.isBlank()) return null;
        String cleaned = speciesName.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        try {
            if (cleaned.contains(":")) {
                Species s = PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.parse(cleaned));
                if (s != null) return s;
                cleaned = cleaned.substring(cleaned.indexOf(':') + 1);
            }
        } catch (Throwable ignored) {}
        try {
            Species byName = PokemonSpecies.INSTANCE.getByName(cleaned);
            if (byName != null) return byName;
        } catch (Throwable ignored) {}
        try { return PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", cleaned)); } catch (Throwable ignored) { return null; }
    }

    private static Info info(String species) { return INFO.computeIfAbsent(normal(species), k -> new Info()); }
    private static String normal(String species) {
        String v = species == null ? "" : species.trim().toLowerCase(Locale.ROOT);
        int c = v.lastIndexOf(':'); if (c >= 0 && c + 1 < v.length()) v = v.substring(c + 1);
        return v.replace('-', '_').replace(' ', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static String string(JsonObject obj, String key) { return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : ""; }
    private static void addArray(Set<String> out, JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return;
        for (JsonElement e : obj.getAsJsonArray(key)) if (!e.isJsonNull()) out.add(e.getAsString());
    }
    private static void addTime(Set<String> out, JsonObject condition) {
        for (String key : List.of("timeRange", "timeRanges", "times", "time")) {
            if (!condition.has(key)) continue;
            JsonElement e = condition.get(key);
            if (e.isJsonArray()) for (JsonElement item : e.getAsJsonArray()) out.add(item.toString().replace('"', ' ').trim());
            else out.add(e.toString().replace('"', ' ').trim());
        }
    }

    private static String formatObjectList(Object result) {
        if (result == null) return "";
        String raw = result.toString();
        raw = raw.replace("cobblemon:", "").replace("[", "").replace("]", "").replace("Optional.empty", "");
        raw = raw.replaceAll("[{}]", "").replaceAll("=", ": ");
        raw = raw.replace('_', ' ').replace('-', ' ');
        return raw.trim();
    }

    public static final class Info {
        public final Set<String> biomes = new TreeSet<>();
        public final Set<String> times = new TreeSet<>();
        public final Set<String> rarity = new TreeSet<>();
        public final Set<String> levels = new TreeSet<>();
        public final Set<String> blocks = new TreeSet<>();
        public final Set<String> structures = new TreeSet<>();
        public final Set<String> extra = new TreeSet<>();
    }
}
