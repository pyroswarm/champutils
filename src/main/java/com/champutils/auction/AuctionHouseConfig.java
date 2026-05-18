package com.champutils.auction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AuctionHouseConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("champutils")
            .resolve("auction_house.json");

    private static AuctionHouseConfig INSTANCE;

    public int maxActiveListingsPerPlayer = 10;
    public long maxListingPrice = 9_000_000_000_000_000L;
    public int listingDurationDays = 7;

    private AuctionHouseConfig() {
    }

    public static synchronized AuctionHouseConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static synchronized void reload() {
        INSTANCE = load();
    }

    public int safeMaxActiveListingsPerPlayer() {
        return Math.max(1, Math.min(1_000, maxActiveListingsPerPlayer));
    }

    public long safeMaxListingPrice() {
        return Math.max(1L, maxListingPrice);
    }

    public int safeListingDurationDays() {
        return Math.max(1, Math.min(365, listingDurationDays));
    }

    private static AuctionHouseConfig load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            if (!Files.exists(CONFIG_PATH)) {
                AuctionHouseConfig created = new AuctionHouseConfig();
                created.save();
                return created;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                AuctionHouseConfig loaded = GSON.fromJson(reader, AuctionHouseConfig.class);
                if (loaded == null) {
                    loaded = new AuctionHouseConfig();
                }
                loaded.normalizeAndSave();
                return loaded;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new AuctionHouseConfig();
        }
    }

    private void normalizeAndSave() {
        maxActiveListingsPerPlayer = safeMaxActiveListingsPerPlayer();
        maxListingPrice = safeMaxListingPrice();
        listingDurationDays = safeListingDurationDays();
        save();
    }

    private void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
