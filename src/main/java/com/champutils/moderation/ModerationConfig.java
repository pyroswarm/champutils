package com.champutils.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.util.*;

public final class ModerationConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/moderation.json");

    public static Data DATA = defaults();

    private ModerationConfig() {}

    public static void load() {
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            if (!FILE.exists()) {
                DATA = defaults();
                save();
                return;
            }
            try (FileReader r = new FileReader(FILE)) {
                DATA = GSON.fromJson(r, Data.class);
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
            try (FileWriter w = new FileWriter(FILE)) {
                GSON.toJson(DATA, w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void normalize() {
        Data d = defaults();
        if (DATA.adminAlertPermission == null || DATA.adminAlertPermission.isBlank()) DATA.adminAlertPermission = d.adminAlertPermission;
        if (DATA.moderatorPermission == null || DATA.moderatorPermission.isBlank()) DATA.moderatorPermission = d.moderatorPermission;
        if (DATA.discordWebhookUrl == null) DATA.discordWebhookUrl = "";
        if (DATA.warnMessage == null || DATA.warnMessage.isBlank()) DATA.warnMessage = d.warnMessage;

        // Backwards compatibility with older moderation.json files.
        if (DATA.blockedWords == null) DATA.blockedWords = new ArrayList<>();
        if (DATA.blockedExact == null) DATA.blockedExact = new ArrayList<>(d.blockedExact);
        for (String old : DATA.blockedWords) {
            if (old != null && !old.isBlank() && !old.equalsIgnoreCase("replace_slurs_here") && !DATA.blockedExact.contains(old)) {
                DATA.blockedExact.add(old);
            }
        }

        if (DATA.blockedShortSlurs == null) DATA.blockedShortSlurs = new ArrayList<>(d.blockedShortSlurs);
        for (String shortened : d.blockedShortSlurs) {
            if (shortened != null && !shortened.isBlank() && !DATA.blockedShortSlurs.contains(shortened)) {
                DATA.blockedShortSlurs.add(shortened);
            }
        }
        if (DATA.blockedSevereThreats == null) DATA.blockedSevereThreats = new ArrayList<>(d.blockedSevereThreats);
        if (DATA.blockedSexualHarassment == null) DATA.blockedSexualHarassment = new ArrayList<>(d.blockedSexualHarassment);
        if (DATA.softProfanityAllowed == null) DATA.softProfanityAllowed = new ArrayList<>(d.softProfanityAllowed);
        if (DATA.safeWords == null) DATA.safeWords = new ArrayList<>(d.safeWords);
        if (DATA.normalizedBypassExtraSeverity == null || DATA.normalizedBypassExtraSeverity.isBlank()) DATA.normalizedBypassExtraSeverity = d.normalizedBypassExtraSeverity;

        if (DATA.xrayOreIds == null) DATA.xrayOreIds = d.xrayOreIds;
        if (DATA.xrayContextBlockIds == null || DATA.xrayContextBlockIds.isEmpty()) DATA.xrayContextBlockIds = d.xrayContextBlockIds;
        if (DATA.xrayWindowMinutes <= 0) DATA.xrayWindowMinutes = d.xrayWindowMinutes;
        if (DATA.xrayDiamondThreshold <= 0) DATA.xrayDiamondThreshold = d.xrayDiamondThreshold;
        if (DATA.xrayAncientDebrisThreshold <= 0) DATA.xrayAncientDebrisThreshold = d.xrayAncientDebrisThreshold;
        if (DATA.xrayMinBlocksMinedForAlert <= 0) DATA.xrayMinBlocksMinedForAlert = d.xrayMinBlocksMinedForAlert;
        if (DATA.xrayMinValuableOresForAlert <= 0) DATA.xrayMinValuableOresForAlert = d.xrayMinValuableOresForAlert;
        if (DATA.xrayHiddenValuableOreThreshold <= 0) DATA.xrayHiddenValuableOreThreshold = d.xrayHiddenValuableOreThreshold;
        if (DATA.xrayAlertScoreThreshold <= 0) DATA.xrayAlertScoreThreshold = d.xrayAlertScoreThreshold;
        if (DATA.xrayMinIndependentSignals <= 0) DATA.xrayMinIndependentSignals = d.xrayMinIndependentSignals;
        if (DATA.xrayAlertCooldownMinutes <= 0) DATA.xrayAlertCooldownMinutes = d.xrayAlertCooldownMinutes;
        if (DATA.xrayAutoPunishScoreThreshold <= 0) DATA.xrayAutoPunishScoreThreshold = d.xrayAutoPunishScoreThreshold;
        if (DATA.xrayAutoPunishMinIndependentSignals <= 0) DATA.xrayAutoPunishMinIndependentSignals = d.xrayAutoPunishMinIndependentSignals;
        if (DATA.xrayValuableOreDensityAlertRatio <= 0) DATA.xrayValuableOreDensityAlertRatio = d.xrayValuableOreDensityAlertRatio;
        if (DATA.xrayHiddenOreRatioAlert <= 0) DATA.xrayHiddenOreRatioAlert = d.xrayHiddenOreRatioAlert;
        if (DATA.xrayDirectHiddenOreRunThreshold <= 0) DATA.xrayDirectHiddenOreRunThreshold = d.xrayDirectHiddenOreRunThreshold;
        if (DATA.xrayDirectOreSeconds <= 0) DATA.xrayDirectOreSeconds = d.xrayDirectOreSeconds;
        if (DATA.xrayDirectOreMaxDistance <= 0) DATA.xrayDirectOreMaxDistance = d.xrayDirectOreMaxDistance;
        if (DATA.xrayCloseOreClusterThreshold <= 0) DATA.xrayCloseOreClusterThreshold = d.xrayCloseOreClusterThreshold;
        if (DATA.xrayCloseOreClusterDistance <= 0) DATA.xrayCloseOreClusterDistance = d.xrayCloseOreClusterDistance;
        if (DATA.xrayExposedFacesStillHidden < 0) DATA.xrayExposedFacesStillHidden = d.xrayExposedFacesStillHidden;
        if (DATA.xrayLowContextValuableOreThreshold <= 0) DATA.xrayLowContextValuableOreThreshold = d.xrayLowContextValuableOreThreshold;
        if (DATA.xrayLowContextMaxMinedBlocksPerOre <= 0) DATA.xrayLowContextMaxMinedBlocksPerOre = d.xrayLowContextMaxMinedBlocksPerOre;
    }

    private static Data defaults() {
        Data d = new Data();
        d.chatModEnabled = true;
        d.xrayDetectionEnabled = true;
        d.alertAdmins = true;
        d.adminAlertPermission = "champutils.staff.alerts";
        d.moderatorPermission = "champutils.automod.moderate";
        d.discordWebhookUrl = "";
        d.warnMessage = "That message is blocked on this server. Light cursing is allowed, but slurs, threats, sexual harassment, and filter evasion are not.";
        d.blockedWords = new ArrayList<>();
        d.blockedExact = new ArrayList<>(List.of(
                "nigger", "nigga", "faggot", "fag", "kike", "chink", "spic", "gook", "tranny", "retard", "coon", "wetback"
        ));
        // Shortened hate terms are checked with stricter token rules so normal words like "night" or "Nigeria" do not trip the filter.
        d.blockedShortSlurs = new ArrayList<>(List.of(
                "nig"
        ));
        d.blockedSevereThreats = new ArrayList<>(List.of(
                "kys", "kill yourself", "go kill yourself", "rape you", "i will rape", "i will kill you"
        ));
        d.blockedSexualHarassment = new ArrayList<>(List.of(
                "send nudes", "show tits", "show boobs", "show dick", "suck my dick"
        ));
        d.softProfanityAllowed = new ArrayList<>(List.of("damn", "hell", "shit", "ass", "bitch", "fuck"));
        d.safeWords = new ArrayList<>(List.of("assassin", "class", "classic", "grass", "assessment", "assistant"));
        d.normalizedBypassExtraSeverity = "filter evasion";

        d.xrayWindowMinutes = 20;
        d.xrayDiamondThreshold = 10;
        d.xrayAncientDebrisThreshold = 8;
        d.xrayMinYForDiamondAlert = 16;
        d.xrayIgnoreOps = true;
        d.xrayNotifyPlayerOnStaffAlert = false;
        d.xrayMinBlocksMinedForAlert = 50;
        d.xrayMinValuableOresForAlert = 6;
        d.xrayHiddenValuableOreThreshold = 4;
        d.xrayValuableOreDensityAlertRatio = 0.08;
        d.xrayHiddenOreRatioAlert = 0.55;
        d.xrayDirectHiddenOreRunThreshold = 3;
        d.xrayDirectOreSeconds = 45;
        d.xrayDirectOreMaxDistance = 18;
        d.xrayCloseOreClusterThreshold = 6;
        d.xrayCloseOreClusterDistance = 20;
        d.xrayAlertScoreThreshold = 35;
        d.xrayMinIndependentSignals = 1;
        d.xrayAlertCooldownMinutes = 15;
        d.xrayAutoPunishEnabled = true;
        d.xrayAutoPunishScoreThreshold = 90;
        d.xrayAutoPunishMinIndependentSignals = 4;
        d.xrayExposedFacesStillHidden = 1;
        d.xrayLowContextValuableOreThreshold = 5;
        d.xrayLowContextMaxMinedBlocksPerOre = 6;
        d.xrayContextBlockIds = new ArrayList<>(List.of("minecraft:stone", "minecraft:deepslate", "minecraft:netherrack", "minecraft:tuff", "minecraft:calcite", "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:basalt", "minecraft:blackstone", "minecraft:dirt", "minecraft:gravel", "minecraft:sand", "minecraft:red_sand", "minecraft:clay", "minecraft:dripstone_block"));
        d.xrayOreIds = new ArrayList<>(List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:ancient_debris", "minecraft:iron_ore", "minecraft:deepslate_iron_ore", "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore", "cobblemon:dawn_stone_ore", "cobblemon:deepslate_dawn_stone_ore", "cobblemon:dusk_stone_ore", "cobblemon:deepslate_dusk_stone_ore", "cobblemon:moon_stone_ore", "cobblemon:deepslate_moon_stone_ore", "cobblemon:shiny_stone_ore", "cobblemon:deepslate_shiny_stone_ore", "cobblemon:sun_stone_ore", "cobblemon:deepslate_sun_stone_ore", "cobblemon:fire_stone_ore", "cobblemon:deepslate_fire_stone_ore", "cobblemon:water_stone_ore", "cobblemon:deepslate_water_stone_ore", "cobblemon:thunder_stone_ore", "cobblemon:deepslate_thunder_stone_ore", "cobblemon:ice_stone_ore", "cobblemon:deepslate_ice_stone_ore", "cobblemon:leaf_stone_ore", "cobblemon:deepslate_leaf_stone_ore"));
        return d;
    }

    public static final class Data {
        public boolean chatModEnabled;
        public boolean xrayDetectionEnabled;
        public boolean alertAdmins;
        public String adminAlertPermission;
        public String moderatorPermission;
        public String discordWebhookUrl;
        public String warnMessage;

        /** Legacy field. Still loaded and merged into blockedExact so older configs keep working. */
        public List<String> blockedWords;

        public List<String> blockedExact;
        public List<String> blockedShortSlurs;
        public List<String> blockedSevereThreats;
        public List<String> blockedSexualHarassment;
        public List<String> softProfanityAllowed;
        public List<String> safeWords;
        public String normalizedBypassExtraSeverity;

        public int xrayWindowMinutes;
        public int xrayDiamondThreshold;
        public int xrayAncientDebrisThreshold;
        public int xrayMinYForDiamondAlert;
        public boolean xrayIgnoreOps;
        public boolean xrayNotifyPlayerOnStaffAlert;
        public int xrayMinBlocksMinedForAlert;
        public int xrayMinValuableOresForAlert;
        public int xrayHiddenValuableOreThreshold;
        public double xrayValuableOreDensityAlertRatio;
        public double xrayHiddenOreRatioAlert;
        public int xrayDirectHiddenOreRunThreshold;
        public int xrayDirectOreSeconds;
        public int xrayDirectOreMaxDistance;
        public int xrayCloseOreClusterThreshold;
        public int xrayCloseOreClusterDistance;
        public int xrayExposedFacesStillHidden;
        public int xrayLowContextValuableOreThreshold;
        public int xrayLowContextMaxMinedBlocksPerOre;
        public List<String> xrayContextBlockIds;
        public int xrayAlertScoreThreshold;
        public int xrayMinIndependentSignals;
        public int xrayAlertCooldownMinutes;
        public boolean xrayAutoPunishEnabled;
        public int xrayAutoPunishScoreThreshold;
        public int xrayAutoPunishMinIndependentSignals;
        public List<String> xrayOreIds;
    }
}
