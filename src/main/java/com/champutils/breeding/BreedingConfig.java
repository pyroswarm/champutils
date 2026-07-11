package com.champutils.breeding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        v.breedingCooldownSeconds = Math.max(0, v.breedingCooldownSeconds);
        v.stepsPerEggCycle = Math.max(1, v.stepsPerEggCycle);
        v.stepSampleIntervalTicks = Math.max(5, v.stepSampleIntervalTicks);
        v.persistEverySteps = Math.max(8, v.persistEverySteps);
        v.maximumBlocksPerSample = Math.max(2.0D, v.maximumBlocksPerSample);
        v.maxEggsInParty = Math.max(1, Math.min(5, v.maxEggsInParty));
        v.shinyDenominator = Math.max(1, v.shinyDenominator);
        v.masudaExtraRolls = Math.max(0, v.masudaExtraRolls);
        v.shinyCharmExtraRolls = Math.max(0, v.shinyCharmExtraRolls);
        if (v.flameBodyAbilities == null) v.flameBodyAbilities = new ArrayList<>();
        if (v.shinyCharmPermission == null) v.shinyCharmPermission = "champutils.breeding.shiny_charm";
        if (v.flameBodyAbilities.isEmpty()) {
            v.flameBodyAbilities.addAll(List.of("flamebody", "magmaarmor", "steamengine"));
        }
    }

    public static final class Values {
        public boolean enabled = true;
        public int breedingCooldownSeconds = 300;
        public int stepsPerEggCycle = 256;
        public int stepSampleIntervalTicks = 10;
        public int persistEverySteps = 256;
        public double maximumBlocksPerSample = 12.0D;
        public int maxEggsInParty = 5;
        public boolean revealOffspringSpecies = false;
        public boolean revealOffspringTypes = true;
        public boolean requireParentsInPartyUntilEggCreated = true;
        public int shinyDenominator = 4096;
        public int masudaExtraRolls = 5;
        public int shinyCharmExtraRolls = 2;
        public String shinyCharmPermission = "champutils.breeding.shiny_charm";
        public List<String> flameBodyAbilities = new ArrayList<>(List.of("flamebody", "magmaarmor", "steamengine"));
    }
}
