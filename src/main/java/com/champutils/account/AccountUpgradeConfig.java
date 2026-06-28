package com.champutils.account;

import com.champutils.economy.EconomyManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class AccountUpgradeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/account_upgrades.json");

    public static Config CONFIG = defaults();

    private AccountUpgradeConfig() {}

    public static synchronized void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) {
                CONFIG = defaults();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                Config loaded = GSON.fromJson(reader, Config.class);
                CONFIG = loaded == null ? defaults() : loaded;
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load account_upgrades.json. Using defaults.");
            e.printStackTrace();
            CONFIG = defaults();
        }
        sanitize();
    }

    public static synchronized void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(CONFIG, writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save account_upgrades.json.");
            e.printStackTrace();
        }
    }

    public static long priceCents(Upgrade upgrade) {
        if (upgrade == null) return 0L;
        if (upgrade.priceCents > 0L) return upgrade.priceCents;
        return EconomyManager.wholeCreditsToCents(Math.max(0L, upgrade.priceCredits));
    }

    private static void sanitize() {
        if (CONFIG == null) CONFIG = defaults();
        if (CONFIG.vip == null) CONFIG.vip = defaults().vip;
        if (CONFIG.vipPlus == null) CONFIG.vipPlus = defaults().vipPlus;
        sanitizeUpgrade(CONFIG.vip, "VIP", "vip", 1_000_000L, "champutils.rank.vip");
        sanitizeUpgrade(CONFIG.vipPlus, "VIP+", "vipplus", 20_000_000L, "champutils.rank.vipplus");
    }

    private static void sanitizeUpgrade(Upgrade upgrade, String display, String group, long credits, String permission) {
        if (upgrade.displayName == null || upgrade.displayName.isBlank()) upgrade.displayName = display;
        if (upgrade.luckPermsGroup == null || upgrade.luckPermsGroup.isBlank()) upgrade.luckPermsGroup = group;
        if (upgrade.priceCredits <= 0L && upgrade.priceCents <= 0L) upgrade.priceCredits = credits;
        if (upgrade.ownedPermission == null || upgrade.ownedPermission.isBlank()) upgrade.ownedPermission = permission;
    }

    private static Config defaults() {
        Config config = new Config();
        config.vip = new Upgrade();
        config.vip.enabled = true;
        config.vip.displayName = "VIP";
        config.vip.luckPermsGroup = "vip";
        config.vip.priceCredits = 1_000_000L;
        config.vip.priceCents = 0L;
        config.vip.ownedPermission = "champutils.rank.vip";

        config.vipPlus = new Upgrade();
        config.vipPlus.enabled = true;
        config.vipPlus.displayName = "VIP+";
        config.vipPlus.luckPermsGroup = "vipplus";
        config.vipPlus.priceCredits = 20_000_000L;
        config.vipPlus.priceCents = 0L;
        config.vipPlus.ownedPermission = "champutils.rank.vipplus";
        return config;
    }

    public static final class Config {
        public Upgrade vip;
        public Upgrade vipPlus;
    }

    public static final class Upgrade {
        public boolean enabled = true;
        public String displayName = "";
        public String luckPermsGroup = "";
        /** Whole credits. Example: 1000000 = 1,000,000.00 Credits. */
        public long priceCredits = 0L;
        /** Optional exact cent amount. If greater than 0, this overrides priceCredits. */
        public long priceCents = 0L;
        /** Permission used as an ownership check if your LP group grants it. */
        public String ownedPermission = "";
    }
}
