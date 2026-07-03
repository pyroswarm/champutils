package com.champutils.wiki;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class PokemonWikiIndex {
    private static final Gson GSON = new Gson();
    private static final Map<String, Info> INFO = new HashMap<>();
    private static final Set<String> SPECIES = new TreeSet<>();

    private PokemonWikiIndex() {}

    public static void reload(MinecraftServer server) {
        INFO.clear();
        SPECIES.clear();
        loadSpeciesNames();
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
                        String speciesKey = normal(pokemon);
                        SPECIES.add(speciesKey);
                        Info info = info(speciesKey);
                        info.sources.add(entry.getKey().toString());
                        if (spawn.has("bucket")) info.rarity.add(prettyBucket(string(spawn, "bucket")));
                        if (spawn.has("level")) info.levels.add(cleanLevel(string(spawn, "level")));
                        JsonObject condition = spawn.has("condition") && spawn.get("condition").isJsonObject() ? spawn.getAsJsonObject("condition") : null;
                        if (condition != null) {
                            addArray(info.biomes, condition, "biomes", PokemonWikiIndex::prettyBiome);
                            addArray(info.blocks, condition, "neededNearbyBlocks", PokemonWikiIndex::prettyId);
                            addArray(info.blocks, condition, "neededBaseBlocks", PokemonWikiIndex::prettyId);
                            addArray(info.structures, condition, "structures", PokemonWikiIndex::prettyId);
                            addTime(info.times, condition);
                            addWeather(info.weather, condition);
                            if (condition.has("canSeeSky")) info.extra.add(bool(condition.get("canSeeSky")) ? "Must Be Outside Under Open Sky" : "Does Not Need Open Sky");
                            if (condition.has("isRaining")) info.weather.add(bool(condition.get("isRaining")) ? "Rain" : "Not Raining");
                            if (condition.has("isThundering")) info.weather.add(bool(condition.get("isThundering")) ? "Thunderstorm" : "Not Thundering");
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to build Pokémon wiki spawn index.");
            e.printStackTrace();
        }
    }

    public static Info get(String species) { return INFO.get(normal(species)); }
    public static Set<String> speciesSuggestions() { return Collections.unmodifiableSet(SPECIES); }
    public static Set<String> topicSuggestions() { return Set.of("biome", "time", "ability", "type", "level", "rarity", "block", "structure", "weather", "egg_moves", "drops"); }

    public static String abilities(String speciesName) {
        Species species = findSpecies(speciesName);
        if (species == null) return "I do not know that Pokémon.";

        LinkedHashSet<String> normal = new LinkedHashSet<>();
        LinkedHashSet<String> hidden = new LinkedHashSet<>();
        LinkedHashSet<String> fallback = new LinkedHashSet<>();

        collectNamedAbilities(species, normal, "getStandardAbilities", "getNormalAbilities", "standardAbilities", "normalAbilities");
        collectNamedAbilities(species, hidden, "getHiddenAbilities", "hiddenAbilities");
        collectNamedAbilities(species, fallback, "getAbilities", "getAbilitiesMapping", "getPossibleAbilities", "abilities");

        cleanAbilitySet(normal);
        cleanAbilitySet(hidden);
        cleanAbilitySet(fallback);

        // If the Cobblemon API only exposes one combined pool on this version, still show it clearly.
        if (normal.isEmpty() && hidden.isEmpty()) normal.addAll(fallback);
        normal.removeAll(hidden);

        if (normal.isEmpty() && hidden.isEmpty()) return "No ability data found for this Pokémon yet.";

        String normalText = normal.isEmpty() ? "None found" : String.join("§7, §f", normal);
        String hiddenText = hidden.isEmpty() ? "None found" : String.join("§7, §f", hidden);
        return "Normal: §f" + normalText + " §7| Hidden: §f" + hiddenText;
    }

    private static void collectNamedAbilities(Species species, Set<String> out, String... memberNames) {
        if (species == null || out == null) return;
        for (String name : memberNames) {
            try {
                Method m = species.getClass().getMethod(name);
                if (m.getParameterCount() == 0) {
                    collectAbilityNames(m.invoke(species), out, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
                }
            } catch (Throwable ignored) {}
            try {
                Field f = findField(species.getClass(), name);
                if (f != null) {
                    f.setAccessible(true);
                    collectAbilityNames(f.get(species), out, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void cleanAbilitySet(Set<String> values) {
        values.removeIf(s -> s == null || s.isBlank() || s.equalsIgnoreCase("abilities") || s.equalsIgnoreCase("abilitypool") || s.equalsIgnoreCase("hidden") || s.equalsIgnoreCase("normal") || s.equalsIgnoreCase("standard"));
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (Throwable ignored) {}
        }
        return null;
    }

    public static String types(String speciesName) {
        Species species = findSpecies(speciesName);
        if (species == null) return "I do not know that Pokémon.";
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String methodName : List.of("getTypes", "getPrimaryType", "getSecondaryType")) {
            try {
                Method m = species.getClass().getMethod(methodName);
                collectPrettyNames(m.invoke(species), names, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
            } catch (Throwable ignored) {}
        }
        names.removeIf(s -> s.isBlank() || s.length() > 24 || s.contains("@"));
        if (names.isEmpty()) return "No type data found for this Pokémon.";
        return String.join("§7, §f", names);
    }


    public static String eggMoves(String speciesName) {
        Species species = findSpecies(speciesName);
        if (species == null) return "I do not know that Pokémon.";
        LinkedHashSet<String> moves = new LinkedHashSet<>();
        for (String methodName : List.of("getEggMoves", "getEggMoveNames", "getEggMoveset", "eggMoves")) {
            try {
                Method m = species.getClass().getMethod(methodName);
                if (m.getParameterCount() == 0) collectPrettyNames(m.invoke(species), moves, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
            } catch (Throwable ignored) {}
            try {
                Field f = findField(species.getClass(), methodName);
                if (f != null) { f.setAccessible(true); collectPrettyNames(f.get(species), moves, Collections.newSetFromMap(new IdentityHashMap<>()), 0); }
            } catch (Throwable ignored) {}
        }
        moves.removeIf(s -> s.isBlank() || s.length() > 32 || s.equalsIgnoreCase("Egg Moves"));
        return moves.isEmpty() ? "No egg move data found in the loaded Cobblemon data." : String.join("§7, §f", moves);
    }

    public static String drops(String speciesName) {
        Species species = findSpecies(speciesName);
        if (species == null) return "I do not know that Pokémon.";
        LinkedHashSet<String> drops = new LinkedHashSet<>();
        for (String methodName : List.of("getDrops", "getDropTable", "getLoot", "getBattleDrops", "drops")) {
            try {
                Method m = species.getClass().getMethod(methodName);
                if (m.getParameterCount() == 0) collectPrettyNames(m.invoke(species), drops, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
            } catch (Throwable ignored) {}
            try {
                Field f = findField(species.getClass(), methodName);
                if (f != null) { f.setAccessible(true); collectPrettyNames(f.get(species), drops, Collections.newSetFromMap(new IdentityHashMap<>()), 0); }
            } catch (Throwable ignored) {}
        }
        drops.removeIf(s -> s.isBlank() || s.length() > 40 || s.equalsIgnoreCase("Drops"));
        return drops.isEmpty() ? "No wild battle drop data found in the loaded Cobblemon data." : String.join("§7, §f", drops);
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
        try { Species byName = PokemonSpecies.INSTANCE.getByName(cleaned); if (byName != null) return byName; } catch (Throwable ignored) {}
        try { return PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", cleaned)); } catch (Throwable ignored) { return null; }
    }

    private static void loadSpeciesNames() {
        Object registry = PokemonSpecies.INSTANCE;
        for (String methodName : List.of("getSpecies", "getSpeciesList", "all", "allSpecies")) {
            try {
                Method m = registry.getClass().getMethod(methodName);
                Object result = m.invoke(registry);
                collectSpecies(result);
                if (!SPECIES.isEmpty()) return;
            } catch (Throwable ignored) {}
        }
    }

    private static void collectSpecies(Object result) {
        if (result == null) return;
        if (result instanceof Map<?, ?> map) { for (Object v : map.values()) collectSpecies(v); return; }
        if (result instanceof Iterable<?> iterable) { for (Object v : iterable) collectSpecies(v); return; }
        if (result.getClass().isArray()) { for (int i = 0; i < Array.getLength(result); i++) collectSpecies(Array.get(result, i)); return; }
        if (result instanceof Species species) SPECIES.add(normal(species.getName()));
    }

    private static Info info(String species) { return INFO.computeIfAbsent(normal(species), k -> new Info()); }
    private static String normal(String species) {
        String v = species == null ? "" : species.trim().toLowerCase(Locale.ROOT);
        int c = v.lastIndexOf(':'); if (c >= 0 && c + 1 < v.length()) v = v.substring(c + 1);
        return v.replace('-', '_').replace(' ', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static String string(JsonObject obj, String key) { return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : ""; }
    private static boolean bool(JsonElement e) { try { return e.getAsBoolean(); } catch (Exception ex) { return false; } }

    private interface Formatter { String apply(String value); }
    private static void addArray(Set<String> out, JsonObject obj, String key, Formatter formatter) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return;
        for (JsonElement e : obj.getAsJsonArray(key)) {
            if (e.isJsonNull()) continue;
            String formatted = formatter.apply(e.getAsString());
            if (formatted == null || formatted.isBlank()) continue;
            out.add(formatted);
        }
    }

    private static void addTime(Set<String> out, JsonObject condition) {
        for (String key : List.of("timeRange", "timeRanges", "times", "time")) {
            if (!condition.has(key)) continue;
            JsonElement e = condition.get(key);
            if (e.isJsonArray()) for (JsonElement item : e.getAsJsonArray()) out.add(prettyTime(item.toString()));
            else out.add(prettyTime(e.toString()));
        }
    }

    private static void addWeather(Set<String> out, JsonObject condition) {
        for (String key : List.of("moonPhase", "moonPhases")) {
            if (!condition.has(key)) continue;
            JsonElement e = condition.get(key);
            if (e.isJsonArray()) for (JsonElement item : e.getAsJsonArray()) out.add("Moon Phase " + prettyId(item.toString()));
            else out.add("Moon Phase " + prettyId(e.toString()));
        }
    }

    private static String cleanLevel(String raw) { return raw == null ? "" : raw.replace("-", " to ").replace("..", " to ").trim(); }

    private static String prettyBucket(String raw) {
        String v = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace("_", "-");
        return switch (v) {
            case "common" -> "Common";
            case "uncommon" -> "Uncommon";
            case "rare" -> "Rare";
            case "ultra-rare", "ultrarare" -> "Ultra Rare";
            default -> prettyId(raw);
        };
    }

    private static String prettyBiome(String raw) {
        String v = raw == null ? "" : raw.replace("#", "").trim();
        String normalized = v.toLowerCase(Locale.ROOT);
        if (normalized.equals("overworld")
                || normalized.equals("minecraft:overworld")
                || normalized.equals("cobblemon:is_overworld")
                || normalized.equals("minecraft:is_overworld")
                || normalized.equals("is_overworld")) {
            return "";
        }
        if (v.startsWith("cobblemon:is_")) v = v.substring("cobblemon:is_".length());
        if (v.startsWith("minecraft:is_")) v = v.substring("minecraft:is_".length());
        return prettyId(v);
    }

    /**
     * Cleans every wiki-facing value before it is printed to chat.
     * Handles Cobblemon translation keys, registry IDs, tags, snake_case, kebab-case,
     * enum-style values, and quoted JSON fragments.
     */
    public static String prettyId(String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        if (v.isBlank()) return "";

        v = v.replace("\"", "").replace("'", "").replace("[", "").replace("]", "").trim();
        v = v.replace("#", "");

        // Cobblemon/Minecraft translation keys sometimes leak from reflection, for example:
        // cobblemon.ability.protean, cobblemon.move.hydro_pump, cobblemon.type.fire
        if (v.startsWith("cobblemon.") || v.startsWith("minecraft.")) {
            int dot = v.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < v.length()) v = v.substring(dot + 1);
        }

        int colon = v.indexOf(':');
        if (colon >= 0 && colon + 1 < v.length()) v = v.substring(colon + 1);

        v = v.replaceFirst("^is[_-]", "");
        v = v.replace('_', ' ').replace('-', ' ').replace('.', ' ').trim();
        return titleCase(v);
    }

    private static String prettyTime(String raw) {
        String v = raw == null ? "" : raw.replace("\"", "").replace("[", "").replace("]", "").trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "day" -> "Daytime";
            case "night" -> "Nighttime";
            case "dawn" -> "Dawn";
            case "dusk" -> "Dusk";
            default -> prettyId(v);
        };
    }

    private static String titleCase(String raw) {
        if (raw == null || raw.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (String part : raw.trim().split("\\s+")) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            String lower = part.toLowerCase(Locale.ROOT);
            out.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) out.append(lower.substring(1));
        }
        return out.toString();
    }

    private static void collectAbilityNames(Object obj, Set<String> out, Set<Object> seen, int depth) {
        if (obj == null || depth > 5 || seen.contains(obj)) return;
        seen.add(obj);
        if (obj instanceof Map<?, ?> map) { for (Object v : map.values()) collectAbilityNames(v, out, seen, depth + 1); return; }
        if (obj instanceof Iterable<?> iterable) { for (Object v : iterable) collectAbilityNames(v, out, seen, depth + 1); return; }
        if (obj.getClass().isArray()) { for (int i = 0; i < Array.getLength(obj); i++) collectAbilityNames(Array.get(obj, i), out, seen, depth + 1); return; }
        for (String methodName : List.of("getName", "name", "getDisplayName")) {
            try {
                Method m = obj.getClass().getMethod(methodName);
                if (m.getParameterCount() == 0) addPrettyName(m.invoke(obj), out);
            } catch (Throwable ignored) {}
        }
        for (Field f : obj.getClass().getDeclaredFields()) {
            try {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v = f.get(obj);
                if (f.getName().toLowerCase(Locale.ROOT).contains("ability")) collectAbilityNames(v, out, seen, depth + 1);
                else if (v instanceof String || v instanceof ResourceLocation) addPrettyName(v, out);
                else if (depth < 3) collectAbilityNames(v, out, seen, depth + 1);
            } catch (Throwable ignored) {}
        }
    }

    private static void collectPrettyNames(Object obj, Set<String> out, Set<Object> seen, int depth) {
        if (obj == null || depth > 3 || seen.contains(obj)) return;
        seen.add(obj);
        if (obj instanceof Map<?, ?> map) { for (Object v : map.values()) collectPrettyNames(v, out, seen, depth + 1); return; }
        if (obj instanceof Iterable<?> iterable) { for (Object v : iterable) collectPrettyNames(v, out, seen, depth + 1); return; }
        if (obj.getClass().isArray()) { for (int i = 0; i < Array.getLength(obj); i++) collectPrettyNames(Array.get(obj, i), out, seen, depth + 1); return; }
        for (String methodName : List.of("getName", "name")) {
            try { Method m = obj.getClass().getMethod(methodName); if (m.getParameterCount() == 0) addPrettyName(m.invoke(obj), out); } catch (Throwable ignored) {}
        }
        addPrettyName(obj, out);
    }

    private static void addPrettyName(Object value, Set<String> out) {
        if (value == null) return;
        String raw = value.toString();
        if (raw.contains("@") || raw.startsWith("com.") || raw.startsWith("net.")) return;
        String cleaned = prettyId(raw);
        if (!cleaned.isBlank() && cleaned.length() <= 32) out.add(cleaned);
    }

    public static final class Info {
        public final Set<String> biomes = new TreeSet<>();
        public final Set<String> times = new TreeSet<>();
        public final Set<String> rarity = new TreeSet<>();
        public final Set<String> levels = new TreeSet<>();
        public final Set<String> blocks = new TreeSet<>();
        public final Set<String> structures = new TreeSet<>();
        public final Set<String> weather = new TreeSet<>();
        public final Set<String> extra = new TreeSet<>();
        public final Set<String> sources = new TreeSet<>();
    }
}
