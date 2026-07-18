package com.champutils.wiki;

import com.champutils.breeding.BreedingEggData;
import com.cobblemon.mod.common.api.abilities.Abilities;
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
    private static final Map<String, SupplementalInfo> SUPPLEMENTAL = new HashMap<>();
    private static final Map<String, String> ABILITY_DISPLAY_NAMES = new HashMap<>();
    private static final Set<String> SPECIES = new TreeSet<>();

    private PokemonWikiIndex() {}

    public static void reload(MinecraftServer server) {
        INFO.clear();
        SPECIES.clear();
        SUPPLEMENTAL.clear();
        ABILITY_DISPLAY_NAMES.clear();
        loadSupplementalLookups();
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
        // Cobblemon species resources are the authoritative source for battle drops.
        try {
            Map<ResourceLocation, Resource> speciesResources = server.getResourceManager().listResources("species", id -> id.getPath().endsWith(".json"));
            for (Map.Entry<ResourceLocation, Resource> entry : speciesResources.entrySet()) {
                try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    if (root == null) continue;
                    String key = string(root, "name");
                    if (key.isBlank()) {
                        String path = entry.getKey().getPath();
                        key = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
                    }
                    Info info = info(key);
                    JsonElement drops = root.get("drops");
                    if (drops == null) drops = root.get("dropTable");
                    if (drops == null) drops = root.get("loot");
                    collectDropJson(drops, info.drops);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to build Pokémon wiki drop index: " + e.getMessage());
        }
    }

    public static Info get(String species) { return INFO.get(normal(species)); }
    public static Set<String> speciesSuggestions() { return Collections.unmodifiableSet(SPECIES); }
    public static Set<String> topicSuggestions() { return Set.of("biome", "time", "ability", "type", "level", "rarity", "block", "structure", "weather", "egg_moves", "drops"); }

    public static boolean isBreedableSpecies(String value) {
        Species species = findSpecies(value);
        if (species == null) return false;
        try {
            var groups = species.getStandardForm().getEggGroups();
            if (groups == null || groups.isEmpty()) return false;
            return groups.stream().noneMatch(group -> {
                String name = group == null ? "" : group.name().toLowerCase(Locale.ROOT);
                return name.contains("undiscovered") || name.contains("no_eggs");
            });
        } catch (Throwable ignored) { return false; }
    }

    public static boolean knowsSpecies(String speciesName) {
        String key = normal(speciesName);
        return !key.isBlank() && (SPECIES.contains(key) || SUPPLEMENTAL.containsKey(key) || findSpecies(speciesName) != null);
    }

    public static String displayName(String speciesName) {
        String key = normal(speciesName);
        SupplementalInfo supplemental = SUPPLEMENTAL.get(key);
        if (supplemental != null && supplemental.name != null && !supplemental.name.isBlank()) return supplemental.name;
        Species species = findSpecies(speciesName);
        if (species != null) {
            try { return prettyId(species.getName()); } catch (Throwable ignored) {}
        }
        return prettyId(speciesName);
    }

    public static List<String> abilityNames(String speciesName) {
        SupplementalInfo supplemental = SUPPLEMENTAL.get(PokemonWikiIndex.normal(speciesName));
        if (supplemental != null && supplemental.abilities != null && !supplemental.abilities.isEmpty()) {
            // The bundled lookup is curated per species/form, so prefer it over broad reflection.
            // Cobblemon reflection can expose wrapper/internal ability pools and form-wide values that
            // make the contract selector show duplicate or impossible abilities.
            return canonicalAbilityList(supplemental.abilities);
        }

        LinkedHashSet<String> abilities = new LinkedHashSet<>();
        Species species = findSpecies(speciesName);
        if (species != null) {
            collectNamedAbilities(species, abilities, "getStandardAbilities", "getNormalAbilities", "standardAbilities", "normalAbilities");
            collectNamedAbilities(species, abilities, "getHiddenAbilities", "hiddenAbilities");
            collectNamedAbilities(species, abilities, "getAbilities", "getAbilitiesMapping", "getPossibleAbilities", "abilities");
            cleanAbilitySet(abilities);
        }
        return canonicalAbilityList(abilities);
    }

    public static List<String> genderOptions(String speciesName) {
        Species species = findSpecies(speciesName);
        if (species == null) return List.of("male", "female");

        Double maleRatio = readNumericMember(species,
                "getMaleRatio", "maleRatio", "getMalePercentage", "malePercentage",
                "getGenderRatio", "genderRatio");
        if (maleRatio != null) {
            double ratio = maleRatio;
            // Cobblemon species data uses a negative ratio for genderless species.
            if (ratio < 0.0D) return List.of();
            // Support either a 0..1 ratio or a percentage-style 0..100 ratio.
            if (ratio > 1.0D) ratio /= 100.0D;
            if (ratio <= 0.0D) return List.of("female");
            if (ratio >= 1.0D) return List.of("male");
            return List.of("male", "female");
        }

        Object genderRatio = readMember(species, "getGenderRatio", "genderRatio", "getGender", "gender");
        if (genderRatio != null) {
            String value = String.valueOf(genderRatio).toLowerCase(Locale.ROOT);
            if (value.contains("genderless") || value.contains("none") || value.contains("unknown")) return List.of();
            if (value.equals("female") || value.contains("female_only") || value.contains("female-only")) return List.of("female");
            if (value.equals("male") || value.contains("male_only") || value.contains("male-only")) return List.of("male");
        }

        // Unknown metadata should not incorrectly hide valid selections.
        return List.of("male", "female");
    }

    private static Double readNumericMember(Object target, String... names) {
        Object value = readMember(target, names);
        if (value instanceof Number number) return number.doubleValue();
        if (value != null) {
            try { return Double.parseDouble(String.valueOf(value)); } catch (Exception ignored) {}
        }
        return null;
    }

    private static Object readMember(Object target, String... names) {
        if (target == null || names == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                if (method.getParameterCount() == 0) return method.invoke(target);
            } catch (Throwable ignored) {}
            try {
                Field field = findField(target.getClass(), name);
                if (field != null) {
                    field.setAccessible(true);
                    return field.get(target);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static String abilities(String speciesName) {
        Species species = findSpecies(speciesName);
        SupplementalInfo supplemental = SUPPLEMENTAL.get(PokemonWikiIndex.normal(speciesName));
        if (species == null && supplemental == null) return "I do not know that Pokémon.";

        if (supplemental != null && supplemental.abilities != null && !supplemental.abilities.isEmpty()) {
            List<String> curated = canonicalAbilityList(supplemental.abilities);
            return curated.isEmpty() ? "No ability data found for this Pokémon yet." : "Abilities: §f" + String.join("§7, §f", curated);
        }

        LinkedHashSet<String> normal = new LinkedHashSet<>();
        LinkedHashSet<String> hidden = new LinkedHashSet<>();
        LinkedHashSet<String> fallback = new LinkedHashSet<>();

        if (species != null) {
            collectNamedAbilities(species, normal, "getStandardAbilities", "getNormalAbilities", "standardAbilities", "normalAbilities");
            collectNamedAbilities(species, hidden, "getHiddenAbilities", "hiddenAbilities");
            collectNamedAbilities(species, fallback, "getAbilities", "getAbilitiesMapping", "getPossibleAbilities", "abilities");
        }

        List<String> normalList = canonicalAbilityList(normal);
        List<String> hiddenList = canonicalAbilityList(hidden);
        List<String> fallbackList = canonicalAbilityList(fallback);

        // If the Cobblemon API only exposes one combined pool on this version, still show it clearly.
        if (normalList.isEmpty() && hiddenList.isEmpty()) normalList = fallbackList;
        LinkedHashSet<String> hiddenKeys = new LinkedHashSet<>();
        for (String value : hiddenList) hiddenKeys.add(abilityKey(value));
        normalList = normalList.stream().filter(value -> !hiddenKeys.contains(abilityKey(value))).toList();

        if (normalList.isEmpty() && hiddenList.isEmpty()) return "No ability data found for this Pokémon yet.";

        String normalText = normalList.isEmpty() ? "None found" : String.join("§7, §f", normalList);
        String hiddenText = hiddenList.isEmpty() ? "None found" : String.join("§7, §f", hiddenList);
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
        values.removeIf(s -> s == null || s.isBlank() || !isRealAbilityName(s));
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (Throwable ignored) {}
        }
        return null;
    }

    public static String types(String speciesName) {
        Species species = findSpecies(speciesName);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (species != null) {
            for (String methodName : List.of("getTypes", "getPrimaryType", "getSecondaryType")) {
                try {
                    Method m = species.getClass().getMethod(methodName);
                    collectPrettyNames(m.invoke(species), names, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
                } catch (Throwable ignored) {}
            }
        }
        SupplementalInfo supplemental = SUPPLEMENTAL.get(normal(speciesName));
        if (supplemental != null) names.addAll(supplemental.types);
        if (species == null && names.isEmpty()) return "I do not know that Pokémon.";
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
        Info indexed = get(speciesName);
        LinkedHashSet<String> drops = new LinkedHashSet<>();
        if (indexed != null) drops.addAll(indexed.drops);
        if (species == null && drops.isEmpty()) return "I do not know that Pokémon.";
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


    private static void collectDropJson(JsonElement element, Set<String> out) {
        if (element == null || element.isJsonNull() || out == null) return;
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            if (!value.isBlank()) out.add(prettyId(value));
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectDropJson(child, out);
            return;
        }
        JsonObject object = element.getAsJsonObject();
        String item = string(object, "item");
        if (item.isBlank()) item = string(object, "id");
        if (item.isBlank()) item = string(object, "name");
        if (!item.isBlank()) {
            StringBuilder text = new StringBuilder(prettyId(item));
            if (object.has("quantity")) text.append(" x").append(object.get("quantity").getAsString());
            else if (object.has("amount")) text.append(" x").append(object.get("amount").getAsString());
            if (object.has("percentage")) text.append(" (").append(object.get("percentage").getAsString()).append("%)");
            else if (object.has("chance")) text.append(" (").append(object.get("chance").getAsString()).append(" chance)");
            out.add(text.toString());
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (Set.of("item", "id", "name", "quantity", "amount", "percentage", "chance").contains(entry.getKey())) continue;
            collectDropJson(entry.getValue(), out);
        }
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

    private static void loadSupplementalLookups() {
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(PokemonWikiIndex.class.getClassLoader().getResourceAsStream("data/champutils/wiki/pokemon_lookup.json")),
                StandardCharsets.UTF_8
        )) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("pokemon") || !root.get("pokemon").isJsonArray()) return;
            for (JsonElement element : root.getAsJsonArray("pokemon")) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String key = string(object, "key");
                String name = string(object, "name");
                if (key.isBlank() || name.isBlank()) continue;
                SupplementalInfo info = new SupplementalInfo(name);
                addJsonStrings(info.types, object, "types");
                addJsonStrings(info.abilities, object, "abilities");
                for (String ability : info.abilities) {
                    String abilityKey = abilityKey(ability);
                    if (!abilityKey.isBlank()) ABILITY_DISPLAY_NAMES.putIfAbsent(abilityKey, ability);
                }
                SUPPLEMENTAL.put(normal(key), info);
                SPECIES.add(normal(key));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addJsonStrings(Set<String> out, JsonObject object, String key) {
        if (out == null || object == null || !object.has(key) || !object.get(key).isJsonArray()) return;
        for (JsonElement element : object.getAsJsonArray(key)) {
            if (element == null || element.isJsonNull()) continue;
            String value = element.getAsString();
            if (value != null && !value.isBlank()) out.add(prettyId(value));
        }
    }

    private static void collectSpecies(Object result) {
        if (result == null) return;
        if (result instanceof Map<?, ?> map) { for (Object v : map.values()) collectSpecies(v); return; }
        if (result instanceof Iterable<?> iterable) { for (Object v : iterable) collectSpecies(v); return; }
        if (result.getClass().isArray()) { for (int i = 0; i < Array.getLength(result); i++) collectSpecies(Array.get(result, i)); return; }
        if (result instanceof Species species) {
            try {
                if (BreedingEggData.EGG_SPECIES.equals(String.valueOf(species.getResourceIdentifier()))) return;
            } catch (Throwable ignored) {
            }
            SPECIES.add(normal(species.getName()));
        }
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
            case "f" -> "F Rank";
            case "e" -> "E Rank";
            case "d" -> "D Rank";
            case "c" -> "C Rank";
            case "b" -> "B Rank";
            case "a" -> "A Rank";
            case "s" -> "S Rank";
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
        if (obj == null || depth > 6 || seen.contains(obj)) return;
        seen.add(obj);
        if (obj instanceof Map<?, ?> map) {
            for (Object v : map.values()) collectAbilityNames(v, out, seen, depth + 1);
            return;
        }
        if (obj instanceof Iterable<?> iterable) {
            for (Object v : iterable) collectAbilityNames(v, out, seen, depth + 1);
            return;
        }
        if (obj.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(obj); i++) collectAbilityNames(Array.get(obj, i), out, seen, depth + 1);
            return;
        }

        // Cobblemon ability pools contain entries/wrappers with a template. Only the template's actual
        // registered ability id/name should become a menu option. Do not collect every string field from
        // the object graph; that is what caused random internal words to appear in the contract menu.
        for (String methodName : List.of("getTemplate", "template")) {
            try {
                Method m = obj.getClass().getMethod(methodName);
                if (m.getParameterCount() == 0) {
                    Object template = m.invoke(obj);
                    if (template != null && template != obj) {
                        addAbilityName(template, out);
                        collectAbilityNames(template, out, seen, depth + 1);
                    }
                }
            } catch (Throwable ignored) {}
        }

        addAbilityName(obj, out);

        for (Field f : obj.getClass().getDeclaredFields()) {
            try {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String fieldName = f.getName().toLowerCase(Locale.ROOT);
                if (!fieldName.contains("abil") && !fieldName.equals("template")) continue;
                f.setAccessible(true);
                collectAbilityNames(f.get(obj), out, seen, depth + 1);
            } catch (Throwable ignored) {}
        }
    }

    private static void addAbilityName(Object value, Set<String> out) {
        if (value == null || out == null) return;
        for (String methodName : List.of("getName", "name", "getShowdownId", "showdownId", "getId", "id")) {
            try {
                Method m = value.getClass().getMethod(methodName);
                if (m.getParameterCount() == 0) addAbilityCandidate(m.invoke(value), out);
            } catch (Throwable ignored) {}
        }
        if (value instanceof String || value instanceof ResourceLocation) addAbilityCandidate(value, out);
    }

    private static void addAbilityCandidate(Object value, Set<String> out) {
        if (value == null || out == null) return;
        String raw = value.toString();
        if (raw.isBlank() || raw.contains("@") || raw.startsWith("com.") || raw.startsWith("net.")) return;
        String cleaned = canonicalAbilityDisplay(raw);
        if (!cleaned.isBlank()) out.add(cleaned);
    }

    private static List<String> canonicalAbilityList(Collection<String> rawAbilities) {
        if (rawAbilities == null || rawAbilities.isEmpty()) return List.of();
        LinkedHashMap<String, String> byKey = new LinkedHashMap<>();
        for (String raw : rawAbilities) {
            String display = canonicalAbilityDisplay(raw);
            if (display.isBlank()) continue;
            String key = abilityKey(display);
            if (key.isBlank()) continue;
            byKey.putIfAbsent(key, display);
        }
        return byKey.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private static String canonicalAbilityDisplay(String value) {
        if (value == null || value.isBlank()) return "";
        String key = abilityKey(value);
        if (key.isBlank() || !isRealAbilityKey(key)) return "";

        String curated = ABILITY_DISPLAY_NAMES.get(key);
        if (curated != null && !curated.isBlank()) return curated;

        // Keep already readable data readable. If Cobblemon only gives a compact showdown id
        // and no curated display exists, prettyId is still safe and avoids leaking internals.
        return prettyId(value);
    }

    private static String abilityKey(String value) {
        if (value == null) return "";
        String key = value.trim().toLowerCase(Locale.ROOT)
                .replace("cobblemon.ability.", "")
                .replace("ability.", "");
        int colon = key.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < key.length()) key = key.substring(colon + 1);
        return key.replaceAll("[^a-z0-9]", "");
    }

    private static boolean isRealAbilityName(String value) {
        return isRealAbilityKey(abilityKey(value));
    }

    private static boolean isRealAbilityKey(String key) {
        if (key == null || key.isBlank()) return false;
        try { return Abilities.get(key) != null; } catch (Throwable ignored) { return false; }
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
        public final Set<String> drops = new TreeSet<>();
    }

    private static final class SupplementalInfo {
        private final String name;
        private final Set<String> types = new TreeSet<>();
        private final Set<String> abilities = new TreeSet<>();

        private SupplementalInfo(String name) {
            this.name = name;
        }
    }
}
