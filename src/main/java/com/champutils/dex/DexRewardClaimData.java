package com.champutils.dex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DexRewardClaimData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Set<Integer>>>() {}.getType();
    private static final Map<String, Set<Integer>> CLAIMS = new LinkedHashMap<>();

    private DexRewardClaimData() {
    }

    public static void load() {
        CLAIMS.clear();
        try {
            File file = file();
            if (!file.exists()) {
                save();
                return;
            }

            try (FileReader reader = new FileReader(file)) {
                Map<String, Set<Integer>> loaded = GSON.fromJson(reader, TYPE);
                if (loaded != null) {
                    CLAIMS.putAll(loaded);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            File file = file();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(CLAIMS, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean hasClaimed(UUID uuid, int percent) {
        Set<Integer> claimed = CLAIMS.get(uuid.toString());
        return claimed != null && claimed.contains(percent);
    }

    public static void markClaimed(UUID uuid, int percent) {
        CLAIMS.computeIfAbsent(uuid.toString(), ignored -> new HashSet<>()).add(percent);
        save();
    }

    private static File file() {
        return new File("config/champutils/dex_reward_claims.json");
    }
}
