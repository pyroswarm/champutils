package com.champutils.guild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class GuildBuffConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/guilds/guild_buffs.json");

    public static Root CONFIG = new Root();

    private GuildBuffConfig() {}

    public static final class Root {
        public Buffs buffs = new Buffs();
    }

    public static final class Buffs {
        public BuffEntry shinyChance = new BuffEntry(true, 10, 0.0001D, 0.0025D);
        public BuffEntry perfectIvChance = new BuffEntry(true, 20, 0.0001D, 0.0025D);

        public ProfessionXpBuffEntry miningProfessionXp = new ProfessionXpBuffEntry(true, 5, 100, 0.01D, 0.10D);
        public ProfessionXpBuffEntry forestryProfessionXp = new ProfessionXpBuffEntry(true, 10, 100, 0.01D, 0.10D);
        public ProfessionXpBuffEntry farmingProfessionXp = new ProfessionXpBuffEntry(true, 15, 100, 0.01D, 0.10D);
        public ProfessionXpBuffEntry battlingProfessionXp = new ProfessionXpBuffEntry(true, 20, 100, 0.01D, 0.10D);
    }

    public static final class BuffEntry {
        public boolean enabled = true;
        public int unlockLevel = 1;
        public double chancePerLevel = 0.0D;
        public double maxBonus = 0.0D;

        public BuffEntry() {}

        public BuffEntry(boolean enabled, int unlockLevel, double chancePerLevel, double maxBonus) {
            this.enabled = enabled;
            this.unlockLevel = unlockLevel;
            this.chancePerLevel = chancePerLevel;
            this.maxBonus = maxBonus;
        }
    }

    public static final class ProfessionXpBuffEntry {
        public boolean enabled = true;
        public int unlockLevel = 1;
        public int maxLevel = 100;
        public double startingBonus = 0.01D;
        public double maxBonus = 0.10D;

        public ProfessionXpBuffEntry() {}

        public ProfessionXpBuffEntry(boolean enabled, int unlockLevel, int maxLevel, double startingBonus, double maxBonus) {
            this.enabled = enabled;
            this.unlockLevel = unlockLevel;
            this.maxLevel = maxLevel;
            this.startingBonus = startingBonus;
            this.maxBonus = maxBonus;
        }
    }

    public static synchronized void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            if (!FILE.exists()) {
                try (FileWriter writer = new FileWriter(FILE)) {
                    GSON.toJson(new Root(), writer);
                }
            }

            Root loaded;
            try (FileReader reader = new FileReader(FILE)) {
                loaded = GSON.fromJson(reader, Root.class);
            }

            if (loaded == null) loaded = new Root();
            if (loaded.buffs == null) loaded.buffs = new Buffs();
            if (loaded.buffs.shinyChance == null) loaded.buffs.shinyChance = new BuffEntry(true, 10, 0.0001D, 0.0025D);
            if (loaded.buffs.perfectIvChance == null) loaded.buffs.perfectIvChance = new BuffEntry(true, 20, 0.0001D, 0.0025D);
            if (loaded.buffs.miningProfessionXp == null) loaded.buffs.miningProfessionXp = new ProfessionXpBuffEntry(true, 5, 100, 0.01D, 0.10D);
            if (loaded.buffs.forestryProfessionXp == null) loaded.buffs.forestryProfessionXp = new ProfessionXpBuffEntry(true, 10, 100, 0.01D, 0.10D);
            if (loaded.buffs.farmingProfessionXp == null) loaded.buffs.farmingProfessionXp = new ProfessionXpBuffEntry(true, 15, 100, 0.01D, 0.10D);
            if (loaded.buffs.battlingProfessionXp == null) loaded.buffs.battlingProfessionXp = new ProfessionXpBuffEntry(true, 20, 100, 0.01D, 0.10D);

            sanitize(loaded.buffs.shinyChance);
            sanitize(loaded.buffs.perfectIvChance);
            sanitizeProfessionXp(loaded.buffs.miningProfessionXp);
            sanitizeProfessionXp(loaded.buffs.forestryProfessionXp);
            sanitizeProfessionXp(loaded.buffs.farmingProfessionXp);
            sanitizeProfessionXp(loaded.buffs.battlingProfessionXp);
            CONFIG = loaded;

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(CONFIG, writer);
            }

            GuildBuffManager.init();
            System.out.println("[ChampUtils] Loaded guild_buffs.json.");
        } catch (Exception exception) {
            exception.printStackTrace();
            CONFIG = new Root();
        }
    }

    private static void sanitize(BuffEntry entry) {
        entry.unlockLevel = Math.max(1, entry.unlockLevel);
        entry.chancePerLevel = Math.max(0.0D, entry.chancePerLevel);
        entry.maxBonus = Math.max(0.0D, entry.maxBonus);
    }

    private static void sanitizeProfessionXp(ProfessionXpBuffEntry entry) {
        entry.unlockLevel = Math.max(1, entry.unlockLevel);
        entry.maxLevel = Math.max(entry.unlockLevel, entry.maxLevel);
        entry.startingBonus = Math.max(0.0D, entry.startingBonus);
        entry.maxBonus = Math.max(entry.startingBonus, entry.maxBonus);
    }
}
