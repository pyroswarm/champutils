package com.champutils.tm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TMConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File RARITY_FILE = new File("config/champutils/tm_rarities.json");
    private static final File COST_FILE = new File("config/champutils/tm_costs.json");

    public static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
    public static Map<String, List<String>> configuredRarities = new LinkedHashMap<>();
    public static Map<String, Map<String, Integer>> selectedCosts = new LinkedHashMap<>();
    public static Map<String, Map<String, Integer>> randomCosts = new LinkedHashMap<>();

    private TMConfig() {}

    public static void load() {
        loadRarities();
        loadCosts();
    }

    private static void loadRarities() {
        try {
            File parent = RARITY_FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!RARITY_FILE.exists()) saveRarities(defaultRarities());
            try (FileReader reader = new FileReader(RARITY_FILE)) {
                Map<?, ?> raw = GSON.fromJson(reader, Map.class);
                Map<String, List<String>> parsed = new LinkedHashMap<>();
                for (String rarity : RARITIES) parsed.put(rarity, new ArrayList<>());
                if (raw != null) {
                    for (Map.Entry<?, ?> entry : raw.entrySet()) {
                        String rarity = normalizeRarity(String.valueOf(entry.getKey()));
                        if (!RARITIES.contains(rarity) || !(entry.getValue() instanceof List<?> list)) continue;
                        List<String> moves = parsed.computeIfAbsent(rarity, k -> new ArrayList<>());
                        for (Object value : list) {
                            String move = TMManager.sanitizeMove(String.valueOf(value));
                            if (!move.isBlank() && !moves.contains(move)) moves.add(move);
                        }
                    }
                }
                configuredRarities = parsed;
                saveRarities(configuredRarities);
            }
        } catch (Exception e) {
            e.printStackTrace();
            configuredRarities = defaultRarities();
        }
    }

    private static void loadCosts() {
        try {
            File parent = COST_FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!COST_FILE.exists()) saveCosts(defaultSelectedCosts(), defaultRandomCosts());
            try (FileReader reader = new FileReader(COST_FILE)) {
                Root root = GSON.fromJson(reader, Root.class);
                if (root == null) root = new Root();

                // Backwards compatibility: older configs only had "costs". Treat those as selected TM costs.
                Map<String, Map<String, Integer>> loadedSelected = root.selectedCosts;
                if ((loadedSelected == null || loadedSelected.isEmpty()) && root.costs != null && !root.costs.isEmpty()) {
                    loadedSelected = root.costs;
                }

                selectedCosts = mergeCosts(defaultSelectedCosts(), normalizeCosts(loadedSelected));
                randomCosts = mergeCosts(defaultRandomCosts(), normalizeCosts(root.randomCosts));
                saveCosts(selectedCosts, randomCosts);
            }
        } catch (Exception e) {
            e.printStackTrace();
            selectedCosts = defaultSelectedCosts();
            randomCosts = defaultRandomCosts();
        }
    }

    private static Map<String, Map<String, Integer>> mergeCosts(Map<String, Map<String, Integer>> defaults, Map<String, Map<String, Integer>> overrides) {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        for (String rarity : RARITIES) {
            Map<String, Integer> value = overrides.get(rarity);
            out.put(rarity, value == null || value.isEmpty()
                    ? new LinkedHashMap<>(defaults.getOrDefault(rarity, Map.of()))
                    : new LinkedHashMap<>(value));
        }
        return out;
    }

    private static Map<String, Map<String, Integer>> normalizeCosts(Map<String, Map<String, Integer>> raw) {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (Map.Entry<String, Map<String, Integer>> entry : raw.entrySet()) {
            String rarity = normalizeRarity(entry.getKey());
            if (!RARITIES.contains(rarity)) continue;
            Map<String, Integer> cost = new LinkedHashMap<>();
            if (entry.getValue() != null) {
                for (Map.Entry<String, Integer> c : entry.getValue().entrySet()) {
                    int amount = c.getValue() == null ? 0 : Math.max(0, c.getValue());
                    if (amount > 0) cost.put(normalizeRarity(c.getKey()), amount);
                }
            }
            out.put(rarity, cost);
        }
        return out;
    }


    public static void pruneConfiguredRarities(Set<String> allowedMoves) {
        if (allowedMoves == null || allowedMoves.isEmpty()) return;
        Map<String, List<String>> cleaned = new LinkedHashMap<>();
        for (String rarity : RARITIES) cleaned.put(rarity, new ArrayList<>());
        Set<String> seen = new LinkedHashSet<>();
        for (String rarity : RARITIES) {
            List<String> source = configuredRarities.getOrDefault(rarity, List.of());
            List<String> target = cleaned.get(rarity);
            for (String rawMove : source) {
                String move = TMManager.sanitizeMove(rawMove);
                if (move.isBlank() || !allowedMoves.contains(move) || !seen.add(move)) continue;
                target.add(move);
            }
        }
        configuredRarities = cleaned;
        saveRarities(configuredRarities);
    }

    public static String configuredRarityForMove(String moveId) {
        String clean = TMManager.sanitizeMove(moveId);
        for (Map.Entry<String, List<String>> entry : configuredRarities.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(clean)) return entry.getKey();
        }
        return null;
    }

    public static String normalizeRarity(String value) {
        if (value == null) return "COMMON";
        String clean = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (clean) {
            case "UNCOMMON" -> "UNCOMMON";
            case "RARE" -> "RARE";
            case "EPIC" -> "EPIC";
            case "LEGENDARY" -> "LEGENDARY";
            case "MYTHIC" -> "MYTHIC";
            default -> "COMMON";
        };
    }

    private static Map<String, List<String>> defaultRarities() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("COMMON", new ArrayList<>(List.of("swift", "facade", "mudslap")));
        map.put("UNCOMMON", new ArrayList<>(List.of("rest", "brickbreak")));
        map.put("RARE", new ArrayList<>(List.of("protect", "thunderwave", "roost")));
        map.put("EPIC", new ArrayList<>(List.of("swordsdance", "calmmind", "willowisp")));
        map.put("LEGENDARY", new ArrayList<>(List.of("earthquake", "thunderbolt", "icebeam", "flamethrower")));
        map.put("MYTHIC", new ArrayList<>(List.of("dracometeor", "trickroom", "tailwind")));
        return map;
    }

    private static Map<String, Map<String, Integer>> defaultSelectedCosts() {
        Map<String, Map<String, Integer>> map = new LinkedHashMap<>();
        map.put("COMMON", cost("COMMON", 5));
        map.put("UNCOMMON", cost("UNCOMMON", 5, "COMMON", 3));
        map.put("RARE", cost("RARE", 5, "UNCOMMON", 3));
        map.put("EPIC", cost("EPIC", 5, "RARE", 3));
        map.put("LEGENDARY", cost("LEGENDARY", 5, "EPIC", 3));
        map.put("MYTHIC", cost("MYTHIC", 5, "LEGENDARY", 3));
        return map;
    }


    private static Map<String, Map<String, Integer>> defaultRandomCosts() {
        Map<String, Map<String, Integer>> map = new LinkedHashMap<>();
        map.put("COMMON", cost("COMMON", 2));
        map.put("UNCOMMON", cost("UNCOMMON", 2));
        map.put("RARE", cost("RARE", 2));
        map.put("EPIC", cost("EPIC", 2));
        map.put("LEGENDARY", cost("LEGENDARY", 2));
        map.put("MYTHIC", cost("MYTHIC", 2));
        return map;
    }

    private static Map<String, Integer> cost(Object... values) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) map.put(normalizeRarity(String.valueOf(values[i])), (Integer) values[i + 1]);
        return map;
    }

    private static void saveRarities(Map<String, List<String>> data) { try (FileWriter writer = new FileWriter(RARITY_FILE)) { GSON.toJson(data, writer); } catch (Exception e) { e.printStackTrace(); } }
    private static void saveCosts(Map<String, Map<String, Integer>> selected, Map<String, Map<String, Integer>> random) {
        try (FileWriter writer = new FileWriter(COST_FILE)) {
            Root root = new Root();
            root.selectedCosts = selected;
            root.randomCosts = random;
            GSON.toJson(root, writer);
        } catch (Exception e) { e.printStackTrace(); }
    }
    public static class Root {
        /** Legacy field. If present, it is migrated into selectedCosts on load. */
        public Map<String, Map<String, Integer>> costs = new LinkedHashMap<>();
        public Map<String, Map<String, Integer>> selectedCosts = new LinkedHashMap<>();
        public Map<String, Map<String, Integer>> randomCosts = new LinkedHashMap<>();
    }
}
