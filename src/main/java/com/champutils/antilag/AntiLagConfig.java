package com.champutils.antilag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class AntiLagConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/antilag.json");
    public static Data DATA = defaults();

    private AntiLagConfig() {}

    public static void load() {
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            if (!FILE.exists()) {
                DATA = defaults();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                DATA = GSON.fromJson(reader, Data.class);
            }
            if (DATA == null) DATA = defaults();
            normalize();
            save();
        } catch (Exception e) {
            DATA = defaults();
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(DATA, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void normalize() {
        Data d = defaults();
        if (DATA.adminAlertPermission == null || DATA.adminAlertPermission.isBlank()) DATA.adminAlertPermission = d.adminAlertPermission;
        if (DATA.lagMachineKickMessage == null || DATA.lagMachineKickMessage.isBlank()) DATA.lagMachineKickMessage = d.lagMachineKickMessage;
        if (DATA.disabledDimensions == null) DATA.disabledDimensions = d.disabledDimensions;
        if (DATA.scanIntervalSeconds <= 0) DATA.scanIntervalSeconds = d.scanIntervalSeconds;
        if (DATA.cleanupIntervalMinutes <= 0) DATA.cleanupIntervalMinutes = d.cleanupIntervalMinutes;
        if (DATA.minecartClusterRadiusBlocks <= 0) DATA.minecartClusterRadiusBlocks = d.minecartClusterRadiusBlocks;
        if (DATA.minecartClusterThreshold <= 0) DATA.minecartClusterThreshold = d.minecartClusterThreshold;
        if (DATA.minecartPlayerAttributionRadiusBlocks <= 0) DATA.minecartPlayerAttributionRadiusBlocks = d.minecartPlayerAttributionRadiusBlocks;
        if (DATA.snowballWindowSeconds <= 0) DATA.snowballWindowSeconds = d.snowballWindowSeconds;
        if (DATA.snowballThrowThresholdPerWindow <= 0) DATA.snowballThrowThresholdPerWindow = d.snowballThrowThresholdPerWindow;
        if (DATA.snowballClusterRadiusBlocks <= 0) DATA.snowballClusterRadiusBlocks = d.snowballClusterRadiusBlocks;
        if (DATA.snowballClusterThreshold <= 0) DATA.snowballClusterThreshold = d.snowballClusterThreshold;
        if (DATA.genericEntityClusterRadiusBlocks <= 0) DATA.genericEntityClusterRadiusBlocks = d.genericEntityClusterRadiusBlocks;
        if (DATA.genericEntityClusterThreshold <= 0) DATA.genericEntityClusterThreshold = d.genericEntityClusterThreshold;
        if (DATA.maxKicksPerScan <= 0) DATA.maxKicksPerScan = d.maxKicksPerScan;
        if (DATA.maxRemovalsPerScan <= 0) DATA.maxRemovalsPerScan = d.maxRemovalsPerScan;
    }

    private static Data defaults() {
        Data d = new Data();
        d.enabled = true;
        d.detectLagMachines = true;
        d.autoKickLagMachineSuspects = true;
        d.removeDetectedLagMachineEntities = true;
        d.alertAdmins = true;
        d.adminAlertPermission = "champutils.antilag.alerts";
        d.lagMachineKickMessage = "You were kicked because the server detected a possible lag machine near you.";
        d.scanIntervalSeconds = 5;
        d.maxKicksPerScan = 3;
        d.maxRemovalsPerScan = 500;

        d.minecartDetectionEnabled = true;
        d.minecartClusterRadiusBlocks = 8;
        d.minecartClusterThreshold = 24;
        d.minecartPlayerAttributionRadiusBlocks = 24;

        d.snowballDetectionEnabled = true;
        d.snowballWindowSeconds = 10;
        d.snowballThrowThresholdPerWindow = 45;
        d.snowballClusterRadiusBlocks = 10;
        d.snowballClusterThreshold = 35;

        d.genericEntityClusterDetectionEnabled = true;
        d.genericEntityClusterRadiusBlocks = 8;
        d.genericEntityClusterThreshold = 80;

        d.entityCleanupEnabled = true;
        d.cleanupIntervalMinutes = 15;
        d.cleanupDroppedItems = true;
        d.cleanupWildPokemon = true;
        d.protectPokemonWithPersistenceRequired = true;
        d.protectPokemonWithCustomName = true;
        d.protectPokemonInBattle = true;
        d.protectPokemonWithOwnerOrStorage = true;
        d.disabledDimensions = new ArrayList<>();
        return d;
    }

    public static final class Data {
        public boolean enabled;
        public boolean detectLagMachines;
        public boolean autoKickLagMachineSuspects;
        public boolean removeDetectedLagMachineEntities;
        public boolean alertAdmins;
        public String adminAlertPermission;
        public String lagMachineKickMessage;
        public int scanIntervalSeconds;
        public int maxKicksPerScan;
        public int maxRemovalsPerScan;

        public boolean minecartDetectionEnabled;
        public int minecartClusterRadiusBlocks;
        public int minecartClusterThreshold;
        public int minecartPlayerAttributionRadiusBlocks;

        public boolean snowballDetectionEnabled;
        public int snowballWindowSeconds;
        public int snowballThrowThresholdPerWindow;
        public int snowballClusterRadiusBlocks;
        public int snowballClusterThreshold;

        public boolean genericEntityClusterDetectionEnabled;
        public int genericEntityClusterRadiusBlocks;
        public int genericEntityClusterThreshold;

        public boolean entityCleanupEnabled;
        public int cleanupIntervalMinutes;
        public boolean cleanupDroppedItems;
        public boolean cleanupWildPokemon;
        public boolean protectPokemonWithPersistenceRequired;
        public boolean protectPokemonWithCustomName;
        public boolean protectPokemonInBattle;
        public boolean protectPokemonWithOwnerOrStorage;
        public List<String> disabledDimensions;
    }
}
