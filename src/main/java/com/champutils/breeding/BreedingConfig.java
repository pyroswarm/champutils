package com.champutils.breeding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BreedingConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = Path.of("config", "champutils", "breeding.json");
    private static volatile Values values = new Values();

    private BreedingConfig() {}

    public static synchronized void load() {
        try {
            Files.createDirectories(PATH.getParent());
            if (Files.exists(PATH)) {
                Values loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), Values.class);
                if (loaded != null) values = loaded;
            }
            normalize(values);
            Files.writeString(PATH, GSON.toJson(values), StandardCharsets.UTF_8);
        } catch (Exception error) {
            System.err.println("[ChampUtils][Breeding] Failed to load breeding.json; using safe defaults.");
            error.printStackTrace();
            values = new Values();
            normalize(values);
        }
    }

    public static Values get() {
        return values;
    }

    public static synchronized boolean setCooldownMinutes(int minutes) {
        if (minutes < 0) return false;
        long seconds = Math.multiplyExact((long) minutes, 60L);
        if (seconds > Integer.MAX_VALUE) return false;
        values.breedingCooldownSeconds = (int) seconds;
        normalize(values);
        return save();
    }

    public static synchronized boolean save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(values), StandardCharsets.UTF_8);
            return true;
        } catch (IOException error) {
            System.err.println("[ChampUtils][Breeding] Failed to save breeding.json.");
            error.printStackTrace();
            return false;
        }
    }

    private static void normalize(Values v) {
        // Version 2 aligns hatching with modern main-series games: 128 steps per Egg Cycle.
        // Existing generated configs are migrated automatically instead of preserving the old 256-step default.
        if (v.mechanicsVersion == null || v.mechanicsVersion < 2) {
            v.stepsPerEggCycle = 128;
            v.mechanicsVersion = 2;
        }
        if (v.mechanicsVersion < 3) {
            v.mechanicsVersion = 3;
        }
        v.breedingCooldownSeconds = Math.max(0, v.breedingCooldownSeconds);
        v.cooldownReductionPerBreedingLevelPercent = Math.max(0.0D, Math.min(0.5D, v.cooldownReductionPerBreedingLevelPercent));
        v.stepsPerEggCycle = Math.max(1, v.stepsPerEggCycle);
        v.stepSampleIntervalTicks = Math.max(5, v.stepSampleIntervalTicks);
        v.persistEverySteps = Math.max(8, v.persistEverySteps);
        v.maximumBlocksPerSample = Math.max(2.0D, v.maximumBlocksPerSample);
        v.maxEggsInParty = Math.max(1, Math.min(5, v.maxEggsInParty));
        v.shinyDenominator = Math.max(1, v.shinyDenominator);
        v.masudaExtraRolls = Math.max(0, v.masudaExtraRolls);
        v.shinyCharmExtraRolls = Math.max(0, v.shinyCharmExtraRolls);
        v.dittoPoolCommonWeight = Math.max(0.0001D, v.dittoPoolCommonWeight);
        v.dittoPoolUncommonWeight = Math.max(0.0001D, v.dittoPoolUncommonWeight);
        v.dittoPoolRareWeight = Math.max(0.0001D, v.dittoPoolRareWeight);
        v.dittoPoolVeryRareWeight = Math.max(0.0001D, v.dittoPoolVeryRareWeight);
        v.dittoPoolCommonLevel100Multiplier = Math.max(0.01D, v.dittoPoolCommonLevel100Multiplier);
        v.dittoPoolUncommonLevel100Multiplier = Math.max(0.01D, v.dittoPoolUncommonLevel100Multiplier);
        v.dittoPoolRareLevel100Multiplier = Math.max(0.01D, v.dittoPoolRareLevel100Multiplier);
        v.dittoPoolVeryRareLevel100Multiplier = Math.max(0.01D, v.dittoPoolVeryRareLevel100Multiplier);
        v.dittoEggBaseHiddenAbilityChancePercent = clampPercent(v.dittoEggBaseHiddenAbilityChancePercent);
        v.breedingLevel100ExtraPerfectIvChancePercent = clampPercent(v.breedingLevel100ExtraPerfectIvChancePercent);
        v.breedingLevel100HiddenAbilityBonusPercent = clampPercent(v.breedingLevel100HiddenAbilityBonusPercent);
        v.breedingLevel100ShinyRelativeBonusPercent = clampPercent(v.breedingLevel100ShinyRelativeBonusPercent);
        v.hatchXpCommon = Math.max(1, v.hatchXpCommon);
        v.hatchXpUncommon = Math.max(v.hatchXpCommon, v.hatchXpUncommon);
        v.hatchXpRare = Math.max(v.hatchXpUncommon, v.hatchXpRare);
        v.hatchXpVeryRare = Math.max(v.hatchXpRare, v.hatchXpVeryRare);
        if (v.hatchChunkWeightsLevel1 == null) v.hatchChunkWeightsLevel1 = defaultLevel1ChunkWeights();
        if (v.hatchChunkWeightsLevel100 == null) v.hatchChunkWeightsLevel100 = defaultLevel100ChunkWeights();
        normalizeChunkWeights(v.hatchChunkWeightsLevel1, defaultLevel1ChunkWeights());
        normalizeChunkWeights(v.hatchChunkWeightsLevel100, defaultLevel100ChunkWeights());
        if (v.flameBodyAbilities == null) v.flameBodyAbilities = new ArrayList<>();
        if (v.shinyCharmPermission == null) v.shinyCharmPermission = "champutils.breeding.shiny_charm";
        if (v.flameBodyAbilities.isEmpty()) {
            v.flameBodyAbilities.addAll(List.of("flamebody", "magmaarmor", "steamengine"));
        }
    }

    private static double clampPercent(double value) {
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    private static Map<String, Double> defaultLevel1ChunkWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("COBBLESTONE", 90.0D);
        weights.put("COPPER", 10.0D);
        weights.put("IRON", 0.0D);
        weights.put("GOLD", 0.0D);
        weights.put("DIAMOND", 0.0D);
        weights.put("NETHERITE", 0.0D);
        return weights;
    }

    private static Map<String, Double> defaultLevel100ChunkWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("COBBLESTONE", 10.0D);
        weights.put("COPPER", 20.0D);
        weights.put("IRON", 30.0D);
        weights.put("GOLD", 25.0D);
        weights.put("DIAMOND", 13.0D);
        weights.put("NETHERITE", 2.0D);
        return weights;
    }

    private static void normalizeChunkWeights(Map<String, Double> values, Map<String, Double> defaults) {
        for (Map.Entry<String, Double> entry : defaults.entrySet()) {
            Double current = values.get(entry.getKey());
            values.put(entry.getKey(), current == null ? entry.getValue() : Math.max(0.0D, current));
        }
    }

    public static final class Values {
        public Integer mechanicsVersion = null;
        public boolean enabled = true;
        public int breedingCooldownSeconds = 1800;
        public double cooldownReductionPerBreedingLevelPercent = 0.5D;
        public int stepsPerEggCycle = 128;
        public int stepSampleIntervalTicks = 10;
        public int persistEverySteps = 256;
        public double maximumBlocksPerSample = 12.0D;
        public int maxEggsInParty = 5;
        public boolean revealOffspringSpecies = false;
        public boolean revealOffspringTypes = true;
        public boolean requireParentsInPartyUntilEggCreated = true;

        public boolean dittoPairEnabled = true;
        public double dittoPoolCommonWeight = 100.0D;
        public double dittoPoolUncommonWeight = 45.0D;
        public double dittoPoolRareWeight = 18.0D;
        public double dittoPoolVeryRareWeight = 6.0D;
        public double dittoPoolCommonLevel100Multiplier = 0.75D;
        public double dittoPoolUncommonLevel100Multiplier = 1.0D;
        public double dittoPoolRareLevel100Multiplier = 1.35D;
        public double dittoPoolVeryRareLevel100Multiplier = 1.75D;
        public double dittoEggBaseHiddenAbilityChancePercent = 5.0D;
        public double breedingLevel100ExtraPerfectIvChancePercent = 2.0D;
        public double breedingLevel100HiddenAbilityBonusPercent = 2.0D;
        public double breedingLevel100ShinyRelativeBonusPercent = 2.0D;
        public int hatchXpCommon = 450;
        public int hatchXpUncommon = 650;
        public int hatchXpRare = 850;
        public int hatchXpVeryRare = 1200;
        public Map<String, Double> hatchChunkWeightsLevel1 = defaultLevel1ChunkWeights();
        public Map<String, Double> hatchChunkWeightsLevel100 = defaultLevel100ChunkWeights();
        public int shinyDenominator = 4096;
        public int masudaExtraRolls = 5;
        public int shinyCharmExtraRolls = 2;
        public String shinyCharmPermission = "champutils.breeding.shiny_charm";
        public List<String> flameBodyAbilities = new ArrayList<>(List.of("flamebody", "magmaarmor", "steamengine"));
    }
}
