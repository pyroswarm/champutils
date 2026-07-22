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
    public static final List<String> RARITIES = List.of("F", "E", "D", "C", "B", "A", "S");
    public static Map<String, List<String>> configuredRarities = new LinkedHashMap<>();
    public static Map<String, Map<String, Integer>> selectedCosts = new LinkedHashMap<>();
    public static Map<String, Map<String, Integer>> randomCosts = new LinkedHashMap<>();

    private static final int CURRENT_SCHEMA_VERSION = 2;
    private static final double MIN_TM_PRICE = 1000.0D;
    private static final double MAX_TM_PRICE = 5000.0D;

    public static double defaultPriceCredits = MIN_TM_PRICE;
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
            boolean legacyPricing = root.schemaVersion < CURRENT_SCHEMA_VERSION;
            defaultPriceCredits = clampPrice(root.defaultPriceCredits <= 0.0D ? MIN_TM_PRICE : root.defaultPriceCredits);
            movePricesCredits = normalizePrices(root.movePricesCredits);
            if (legacyPricing) {
                // Old files used the retired 500-1750 range. Replace those values with the
                // server-wide 1000-5000 rebalance while preserving any administrator prices
                // that were already intentionally set inside the new range.
                Map<String, Double> upgraded = defaultMovePrices();
                for (Map.Entry<String, Double> entry : movePricesCredits.entrySet()) {
                    if (entry.getValue() >= MIN_TM_PRICE && entry.getValue() <= MAX_TM_PRICE
                            && entry.getValue() > 1750.0D) {
                        upgraded.put(entry.getKey(), entry.getValue());
                    }
                }
                movePricesCredits = upgraded;
                defaultPriceCredits = MIN_TM_PRICE;
            }
            saveShop(currentRoot());
        } catch (Exception e) {
            e.printStackTrace();
            defaultPriceCredits = MIN_TM_PRICE;
            movePricesCredits = new LinkedHashMap<>();
        }
    }

    private static Map<String, Double> normalizePrices(Map<String, Double> raw) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (Map.Entry<String, Double> entry : raw.entrySet()) {
            String move = TMManager.sanitizeMove(entry.getKey());
            double price = entry.getValue() == null ? 0.0D : entry.getValue();
            if (!move.isBlank() && price > 0.0D) out.put(move, clampPrice(price));
        }
        return out;
    }

    public static double priceCreditsForMove(String rawMove) {
        String move = TMManager.sanitizeMove(rawMove);
        return clampPrice(movePricesCredits.getOrDefault(move, defaultPriceCredits));
    }

    public static void pruneConfiguredRarities(Set<String> allowedMoves) {
        // Rarity no longer controls TM availability.
    }

    public static String configuredRarityForMove(String moveId) {
        return null;
    }

    public static String normalizeRarity(String value) {
        if (value == null) return "F";
        String clean = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (clean) {
            case "E" -> "E";
            case "D" -> "D";
            case "C" -> "C";
            case "B" -> "B";
            case "A" -> "A";
            case "S" -> "S";
            default -> "F";
        };
    }

    private static Root defaultRoot() {
        Root root = new Root();
        root.schemaVersion = CURRENT_SCHEMA_VERSION;
        root.defaultPriceCredits = MIN_TM_PRICE;
        root.movePricesCredits = defaultMovePrices();
        return root;
    }

    private static Map<String, Double> defaultMovePrices() {
        Map<String, Double> prices = new LinkedHashMap<>();

        // Common utility and lower-impact TMs remain accessible.
        prices.put("protect", 2000.0D);
        prices.put("rest", 1500.0D);
        prices.put("sleeptalk", 1500.0D);
        prices.put("substitute", 2500.0D);

        // Strong, broadly useful attacks.
        prices.put("flamethrower", 3000.0D);
        prices.put("icebeam", 3000.0D);
        prices.put("thunderbolt", 3000.0D);
        prices.put("surf", 3000.0D);
        prices.put("shadowball", 3000.0D);
        prices.put("energyball", 3000.0D);
        prices.put("psychic", 3000.0D);
        prices.put("dazzlinggleam", 3000.0D);

        // Premium competitive staples.
        prices.put("stealthrock", 4000.0D);
        prices.put("spikes", 4000.0D);
        prices.put("toxicspikes", 4000.0D);
        prices.put("uturn", 4000.0D);
        prices.put("voltswitch", 4000.0D);
        prices.put("trickroom", 4000.0D);
        prices.put("tailwind", 4000.0D);
        prices.put("swordsdance", 4000.0D);
        prices.put("nastyplot", 4000.0D);
        prices.put("calmmind", 4000.0D);

        // Best-in-class and exceptionally influential TMs reach the 5,000 cap.
        prices.put("earthquake", 5000.0D);
        prices.put("dracometeor", 5000.0D);
        prices.put("closecombat", 5000.0D);
        prices.put("dragonclaw", 4500.0D);
        prices.put("dragondance", 5000.0D);
        prices.put("willowisp", 4500.0D);
        prices.put("toxic", 5000.0D);

        return prices;
    }

    private static double clampPrice(double price) {
        if (!Double.isFinite(price)) return MIN_TM_PRICE;
        return Math.max(MIN_TM_PRICE, Math.min(MAX_TM_PRICE, price));
    }

    private static Root currentRoot() {
        Root root = new Root();
        root.schemaVersion = CURRENT_SCHEMA_VERSION;
        root.defaultPriceCredits = clampPrice(defaultPriceCredits);
        root.movePricesCredits = movePricesCredits;
        return root;
    }

    private static void saveShop(Root root) {
        try (FileWriter writer = new FileWriter(SHOP_FILE)) {
            GSON.toJson(root, writer);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static class Root {
        public int schemaVersion = 0;
        public double defaultPriceCredits = MIN_TM_PRICE;
        public Map<String, Double> movePricesCredits = new LinkedHashMap<>();
    }
}
