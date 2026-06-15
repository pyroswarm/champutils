package com.champutils.claims;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class LandClaimConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "land_claims.json");
    private static Data data = new Data();

    private LandClaimConfig() {}

    public static synchronized void load() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            if (!FILE.exists()) {
                data = new Data();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                data = loaded == null ? new Data() : loaded;
            }
            sanitize();
        } catch (Exception e) {
            e.printStackTrace();
            data = new Data();
        }
    }

    public static synchronized void save() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            sanitize();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean enabled() { return data.enabled; }
    public static long costPerBlockCents() { return Math.max(0L, data.costPerBlockCents); }
    public static int minArea() { return Math.max(1, data.minArea); }
    public static int maxArea() { return Math.max(1, data.maxArea); }
    public static int maxClaimsPerProfile() { return Math.max(1, data.maxClaimsPerProfile); }

    private static void sanitize() {
        if (data.costPerBlockCents < 0L) data.costPerBlockCents = 100L;
        if (data.minArea < 1) data.minArea = 1;
        if (data.maxArea < data.minArea) data.maxArea = Math.max(data.minArea, 250_000);
        if (data.maxClaimsPerProfile < 1) data.maxClaimsPerProfile = 1;
    }

    private static final class Data {
        boolean enabled = true;
        long costPerBlockCents = 100L;
        int minArea = 25;
        int maxArea = 250_000;
        int maxClaimsPerProfile = 8;
    }
}
