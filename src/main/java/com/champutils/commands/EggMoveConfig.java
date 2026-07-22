package com.champutils.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Configurable credit pricing for the egg-move tutor. */
public final class EggMoveConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/egg_moves.json");

    private static double defaultPriceCredits = 5_000.0D;
    private static Map<String, Double> movePricesCredits = new LinkedHashMap<>();

    private EggMoveConfig() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) save(defaultRoot());
            Root root;
            try (FileReader reader = new FileReader(FILE)) {
                root = GSON.fromJson(reader, Root.class);
            }
            if (root == null) root = defaultRoot();
            defaultPriceCredits = validPrice(root.defaultPriceCredits) ? root.defaultPriceCredits : 5_000.0D;
            movePricesCredits = normalize(root.movePricesCredits);
            save(currentRoot());
        } catch (Exception e) {
            e.printStackTrace();
            defaultPriceCredits = 5_000.0D;
            movePricesCredits = new LinkedHashMap<>();
        }
    }

    public static double priceCreditsForMove(String rawMove) {
        String move = normalizeMove(rawMove);
        return movePricesCredits.getOrDefault(move, defaultPriceCredits);
    }

    private static Map<String, Double> normalize(Map<String, Double> raw) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (Map.Entry<String, Double> entry : raw.entrySet()) {
            String move = normalizeMove(entry.getKey());
            Double price = entry.getValue();
            if (!move.isBlank() && price != null && validPrice(price)) out.put(move, price);
        }
        return out;
    }

    private static boolean validPrice(double value) {
        return value > 0.0D && !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String normalizeMove(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
    }

    private static Root defaultRoot() {
        Root root = new Root();
        root.defaultPriceCredits = 5_000.0D;
        root.movePricesCredits = new LinkedHashMap<>();
        return root;
    }

    private static Root currentRoot() {
        Root root = new Root();
        root.defaultPriceCredits = defaultPriceCredits;
        root.movePricesCredits = movePricesCredits;
        return root;
    }

    private static void save(Root root) {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(root, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static final class Root {
        public double defaultPriceCredits = 5_000.0D;
        public Map<String, Double> movePricesCredits = new LinkedHashMap<>();
    }
}
