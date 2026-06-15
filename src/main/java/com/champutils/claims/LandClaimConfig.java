package com.champutils.claims;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

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
    public static int maxClaimsPerProfile(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return maxClaimsPerProfile();
        int best = maxClaimsPerProfile();
        if (data.permissionClaimCaps != null) {
            for (Map.Entry<String, Integer> entry : data.permissionClaimCaps.entrySet()) {
                String permission = entry.getKey();
                Integer cap = entry.getValue();
                if (permission == null || permission.isBlank() || cap == null) continue;
                if (com.champutils.permissions.LuckPermsHook.hasPermission(player, permission)) {
                    best = Math.max(best, cap);
                }
            }
        }
        return Math.max(1, best);
    }
    public static int maxTotalClaimBlocksPerProfile() { return Math.max(1, data.maxTotalClaimBlocksPerProfile); }
    public static int borderViewDistanceBlocks() { return Math.max(8, data.borderViewDistanceBlocks); }
    public static int borderParticleStepBlocks() { return Math.max(3, Math.min(8, data.borderParticleStepBlocks)); }

    private static void sanitize() {
        if (data.costPerBlockCents < 0L) data.costPerBlockCents = 100L;
        if (data.minArea < 1) data.minArea = 1;
        if (data.maxArea < data.minArea) data.maxArea = Math.max(data.minArea, 1000);
        if (data.maxClaimsPerProfile < 1) data.maxClaimsPerProfile = 3;
        if (data.permissionClaimCaps == null) data.permissionClaimCaps = new LinkedHashMap<>();
        data.permissionClaimCaps.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() < 1);
        if (data.maxTotalClaimBlocksPerProfile < 1) data.maxTotalClaimBlocksPerProfile = 1000;
        if (data.maxArea > data.maxTotalClaimBlocksPerProfile) data.maxArea = data.maxTotalClaimBlocksPerProfile;
        if (data.borderViewDistanceBlocks < 8) data.borderViewDistanceBlocks = 48;
        if (data.borderParticleStepBlocks < 3) data.borderParticleStepBlocks = 4;
    }

    private static final class Data {
        boolean enabled = true;
        long costPerBlockCents = 100L;
        int minArea = 25;
        int maxArea = 1000;
        int maxClaimsPerProfile = 3;
        int maxTotalClaimBlocksPerProfile = 1000;
        int borderViewDistanceBlocks = 48;
        int borderParticleStepBlocks = 4;
        Map<String, Integer> permissionClaimCaps = new LinkedHashMap<>() {{
            put("champutils.claims.vip", 5);
            put("champutils.claims.vipplus", 8);
            put("champutils.claims.donor", 6);
        }};
    }
}
