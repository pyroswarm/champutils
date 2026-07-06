package com.champutils.commerce;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.UUID;

public final class AccountCommerceConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/account_commerce.json");

    public static Config CONFIG = new Config();

    private AccountCommerceConfig() {}

    public static synchronized void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) {
                CONFIG = new Config();
                sanitize();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                Config loaded = GSON.fromJson(reader, Config.class);
                CONFIG = loaded == null ? new Config() : loaded;
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load account_commerce.json. Using safe defaults.");
            e.printStackTrace();
            CONFIG = new Config();
        }
        sanitize();
        save();
    }

    public static synchronized void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(CONFIG, writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save account_commerce.json.");
            e.printStackTrace();
        }
    }

    private static void sanitize() {
        if (CONFIG == null) CONFIG = new Config();
        if (CONFIG.tebexStoreUrl == null) CONFIG.tebexStoreUrl = "";
        if (CONFIG.voteAddressHashSalt == null || CONFIG.voteAddressHashSalt.isBlank()) {
            CONFIG.voteAddressHashSalt = UUID.randomUUID().toString();
        }
        CONFIG.votePointsPerVote = Math.max(0, Math.min(CONFIG.votePointsPerVote, 1000));
    }

    public static final class Config {
        /** Public storefront URL only. Never put Tebex secret keys here. */
        public String tebexStoreUrl = "";
        /** Website may show account-level fulfillment history, never payment details. */
        public boolean exposePurchaseHistoryOnWebsite = true;
        public boolean voteRewardsEnabled = true;
        public int votePointsPerVote = 1;
        public boolean deduplicateVotesByServicePerUtcDay = true;
        /** Used only to hash voter IP/address before storage. Keep private. */
        public String voteAddressHashSalt = UUID.randomUUID().toString();
    }
}
