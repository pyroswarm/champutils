package com.champutils.flan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class FlanOnlineClaimBlockConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/flan_claim_blocks.json");

    private static Data data = new Data();

    private FlanOnlineClaimBlockConfig() {
    }

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (!FILE.exists()) {
                data = new Data();
                normalize();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                data = loaded == null ? new Data() : loaded;
            }

            normalize();
            save();
        } catch (Exception e) {
            e.printStackTrace();
            data = new Data();
            normalize();
            save();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean enabled() {
        return data.enabled;
    }

    public static int rewardClaimBlocks() {
        return Math.max(0, data.rewardClaimBlocks);
    }

    public static int intervalMinutes() {
        return Math.max(1, data.intervalMinutes);
    }

    public static String rewardMessage() {
        if (data.rewardMessage == null || data.rewardMessage.isBlank()) {
            data.rewardMessage = "For playing for 1+ hours you have earned {amount} claim blocks!";
        }
        return data.rewardMessage;
    }

    public static void setEnabled(boolean enabled) {
        data.enabled = enabled;
        save();
    }

    public static void setRewardClaimBlocks(int amount) {
        data.rewardClaimBlocks = Math.max(0, amount);
        save();
    }

    public static void setIntervalMinutes(int minutes) {
        data.intervalMinutes = Math.max(1, minutes);
        save();
    }

    public static void setRewardMessage(String message) {
        data.rewardMessage = (message == null || message.isBlank())
                ? "For playing for 1+ hours you have earned {amount} claim blocks!"
                : message;
        save();
    }

    private static void normalize() {
        if (data.rewardClaimBlocks < 0) data.rewardClaimBlocks = 0;
        if (data.intervalMinutes <= 0) data.intervalMinutes = 60;
        if (data.rewardMessage == null || data.rewardMessage.isBlank()) {
            data.rewardMessage = "For playing for 1+ hours you have earned {amount} claim blocks!";
        }
    }

    private static final class Data {
        boolean enabled = true;
        int intervalMinutes = 60;
        int rewardClaimBlocks = 100;
        String rewardMessage = "For playing for 1+ hours you have earned {amount} claim blocks!";
    }
}
