package com.champutils.battle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BattleBondEvolutionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/battlebond_evolution.json");

    public static boolean enabled = true;
    public static double chancePercent = 0.5D;
    public static Set<String> protectedAbilities = new LinkedHashSet<>();

    private BattleBondEvolutionConfig() {}

    public static void load() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            if (!FILE.exists()) {
                try (FileWriter writer = new FileWriter(FILE)) {
                    GSON.toJson(defaultRoot(), writer);
                }
            }
            Root root;
            try (FileReader reader = new FileReader(FILE)) {
                root = GSON.fromJson(reader, Root.class);
            }
            if (root == null) root = defaultRoot();
            enabled = root.enabled;
            chancePercent = root.chancePercent;
            protectedAbilities = root.protectedAbilities == null || root.protectedAbilities.isEmpty()
                    ? defaultRoot().protectedAbilities
                    : normalize(root.protectedAbilities);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Root defaultRoot() {
        Root root = new Root();
        root.protectedAbilities.add("protean");
        return root;
    }

    private static Set<String> normalize(Set<String> in) {
        Set<String> out = new LinkedHashSet<>();
        for (String value : in) {
            if (value != null && !value.isBlank()) out.add(value.trim().toLowerCase());
        }
        return out;
    }

    public static final class Root {
        public boolean enabled = true;
        public double chancePercent = 0.5D;
        public Set<String> protectedAbilities = new LinkedHashSet<>();
    }
}
