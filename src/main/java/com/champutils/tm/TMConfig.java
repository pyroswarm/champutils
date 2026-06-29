package com.champutils.tm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TMConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SHOP_FILE = new File("config/champutils/tm_shop.json");

    // Kept only so older command/config references still compile; the shop no longer uses rarity.
    public static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
    public static Map<String, List<String>> configuredRarities = new LinkedHashMap<>();
    public static Map<String, Map<String, Integer>> selectedCosts = new LinkedHashMap<>();
    public static Map<String, Map<String, Integer>> randomCosts = new LinkedHashMap<>();

    public static double defaultPriceCredits = 500.0D;
    public static Map<String, Double> movePricesCredits = new LinkedHashMap<>();

    private TMConfig() {}

    public static void load() {
        loadShop();
    }

    private static void loadShop() {
        try {
            File parent = SHOP_FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!SHOP_FILE.exists()) saveShop(defaultRoot());
            Root root;
            try (FileReader reader = new FileReader(SHOP_FILE)) {
                root = GSON.fromJson(reader, Root.class);
            }
            if (root == null) root = defaultRoot();
            defaultPriceCredits = root.defaultPriceCredits <= 0.0D ? 500.0D : root.defaultPriceCredits;
            movePricesCredits = normalizePrices(root.movePricesCredits);
            saveShop(currentRoot());
        } catch (Exception e) {
            e.printStackTrace();
            defaultPriceCredits = 500.0D;
            movePricesCredits = new LinkedHashMap<>();
        }
    }

    private static Map<String, Double> normalizePrices(Map<String, Double> raw) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (Map.Entry<String, Double> entry : raw.entrySet()) {
            String move = TMManager.sanitizeMove(entry.getKey());
            double price = entry.getValue() == null ? 0.0D : entry.getValue();
            if (!move.isBlank() && price > 0.0D) out.put(move, price);
        }
        return out;
    }

    public static double priceCreditsForMove(String rawMove) {
        String move = TMManager.sanitizeMove(rawMove);
        return Math.max(0.01D, movePricesCredits.getOrDefault(move, defaultPriceCredits));
    }

    public static void pruneConfiguredRarities(Set<String> allowedMoves) {
        // Rarity no longer controls TM availability.
    }

    public static String configuredRarityForMove(String moveId) {
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

    private static Root defaultRoot() {
        Root root = new Root();
        root.defaultPriceCredits = 500.0D;
        root.movePricesCredits = new LinkedHashMap<>();
        root.movePricesCredits.put("protect", 750.0D);
        root.movePricesCredits.put("earthquake", 1250.0D);
        root.movePricesCredits.put("thunderbolt", 1250.0D);
        root.movePricesCredits.put("icebeam", 1250.0D);
        root.movePricesCredits.put("flamethrower", 1250.0D);
        root.movePricesCredits.put("dracometeor", 1750.0D);
        return root;
    }

    private static Root currentRoot() {
        Root root = new Root();
        root.defaultPriceCredits = defaultPriceCredits;
        root.movePricesCredits = movePricesCredits;
        return root;
    }

    private static void saveShop(Root root) {
        try (FileWriter writer = new FileWriter(SHOP_FILE)) {
            GSON.toJson(root, writer);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static class Root {
        public double defaultPriceCredits = 500.0D;
        public Map<String, Double> movePricesCredits = new LinkedHashMap<>();
    }
}
