package com.champutils.battle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class ChampBattleAIConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/battle_ai.json");

    public static Data DATA = Data.defaults();

    private ChampBattleAIConfig() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) {
                DATA = Data.defaults();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                DATA = loaded == null ? Data.defaults() : loaded;
            }
            DATA.normalize();
            save();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load battle_ai.json; using defaults.");
            e.printStackTrace();
            DATA = Data.defaults();
            save();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            DATA.normalize();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(DATA, writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save battle_ai.json.");
            e.printStackTrace();
        }
    }

    public static final class Data {
        public boolean enabled = true;
        public boolean debug = false;
        public int defaultAiSkill = 5;
        public BattleBucket wildBattles = BattleBucket.wildDefaults();
        public BattleBucket trainerBattles = BattleBucket.competitiveDefaults();
        public BattleBucket gymBattles = BattleBucket.competitiveDefaults();
        public BattleBucket guildBossBattles = BattleBucket.competitiveDefaults();
        public BattleBucket worldBossBattles = BattleBucket.competitiveDefaults();
        public AntiSpam antiSpam = AntiSpam.defaults();

        static Data defaults() { return new Data(); }

        void normalize() {
            defaultAiSkill = clamp(defaultAiSkill, 0, 5);
            if (wildBattles == null) wildBattles = BattleBucket.wildDefaults();
            if (trainerBattles == null) trainerBattles = BattleBucket.competitiveDefaults();
            if (gymBattles == null) gymBattles = BattleBucket.competitiveDefaults();
            if (guildBossBattles == null) guildBossBattles = BattleBucket.competitiveDefaults();
            if (worldBossBattles == null) worldBossBattles = BattleBucket.competitiveDefaults();
            if (antiSpam == null) antiSpam = AntiSpam.defaults();
            wildBattles.normalize();
            trainerBattles.normalize();
            gymBattles.normalize();
            guildBossBattles.normalize();
            worldBossBattles.normalize();
            antiSpam.normalize();
        }
    }

    public static final class BattleBucket {
        public boolean enabled = true;
        public int skill = 5;
        public boolean competitiveLayer = true;
        public boolean antiSpamLayer = true;

        static BattleBucket competitiveDefaults() {
            BattleBucket b = new BattleBucket();
            b.skill = 5;
            b.competitiveLayer = true;
            b.antiSpamLayer = true;
            return b;
        }

        static BattleBucket wildDefaults() {
            BattleBucket b = new BattleBucket();
            b.skill = 3;
            b.competitiveLayer = false;
            b.antiSpamLayer = true;
            return b;
        }

        void normalize() { skill = clamp(skill, 0, 5); }
    }

    public static final class AntiSpam {
        public int protectRepeatPenaltyTurns = 5;
        public int sameMoveSoftLimit = 3;
        public boolean preventProtectSpam = true;
        public boolean preventLowValueProtect = true;
        public boolean preventSameMoveLoops = true;

        static AntiSpam defaults() { return new AntiSpam(); }

        void normalize() {
            if (protectRepeatPenaltyTurns < 1) protectRepeatPenaltyTurns = 5;
            if (sameMoveSoftLimit < 1) sameMoveSoftLimit = 3;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
