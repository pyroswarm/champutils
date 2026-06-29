package com.champutils.rewardtrack;

import com.champutils.economy.EconomyManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RewardTrackConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/rewardtrack.json");
    private static Config DATA = defaults();

    private RewardTrackConfig() {}

    public static synchronized void load() {
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            if (!FILE.exists()) {
                DATA = defaults();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                Config loaded = GSON.fromJson(reader, Config.class);
                DATA = loaded == null ? defaults() : loaded;
                if (DATA.rewards == null) DATA.rewards = new LinkedHashMap<>();
                if (DATA.xpPerLevel <= 0) DATA.xpPerLevel = 1000;
                if (DATA.maxLevel <= 0) DATA.maxLevel = 50;
                normalizeRewards();
                save();
            }
        } catch (Exception e) {
            e.printStackTrace();
            DATA = defaults();
        }
    }


    private static void normalizeRewards() {
        Config balanced = defaults();
        for (int level = 1; level <= DATA.maxLevel; level++) {
            String key = String.valueOf(level);
            Reward fallback = balanced.rewards.get(key);
            Reward reward = DATA.rewards.get(key);
            if (reward == null) {
                DATA.rewards.put(key, fallback);
                continue;
            }
            // Older configs had the same reward every tier. Replace only those obvious old flat rewards.
            boolean oldFlatReward = reward.credits == EconomyManager.wholeCreditsToCents(50L)
                    || reward.credits == 5000L
                    || (reward.rankedTokens == 0 && reward.items != null && reward.items.size() == 1 && reward.items.get(0) != null
                    && ("cobblemon:great_ball".equalsIgnoreCase(reward.items.get(0).id) || "cobblemon:ultra_ball".equalsIgnoreCase(reward.items.get(0).id))
                    && reward.items.get(0).count == 8);
            if (oldFlatReward) {
                DATA.rewards.put(key, fallback);
                continue;
            }
            if (reward.items == null) reward.items = new ArrayList<>();
            if (reward.credits <= 0L) reward.credits = fallback == null ? 0L : fallback.credits;
            if (reward.rankedTokens < 0) reward.rankedTokens = 0;
        }
    }

    public static synchronized void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(DATA, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int xpPerLevel() { return Math.max(1, DATA.xpPerLevel); }
    public static int maxLevel() { return Math.max(1, DATA.maxLevel); }

    public static Reward reward(int level) {
        return DATA.rewards.get(String.valueOf(level));
    }

    public static List<ItemStack> itemStacks(int level) {
        Reward reward = reward(level);
        List<ItemStack> out = new ArrayList<>();
        if (reward == null || reward.items == null) return out;
        for (ItemReward itemReward : reward.items) {
            if (itemReward == null || itemReward.id == null || itemReward.id.isBlank()) continue;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemReward.id));
            if (item == null || item == Items.AIR) continue;
            out.add(new ItemStack(item, Math.max(1, itemReward.count)));
        }
        return out;
    }

    private static Config defaults() {
        Config config = new Config();
        config.xpPerLevel = 1000;
        config.maxLevel = 50;
        config.rewards = new LinkedHashMap<>();
        for (int level = 1; level <= 50; level++) {
            Reward reward = new Reward();
            long credits = level % 10 == 0 ? 500L + level * 25L : 75L + level * 10L;
            reward.credits = EconomyManager.wholeCreditsToCents(credits);
            reward.rankedTokens = level % 5 == 0 ? 1 : 0;
            if (level % 10 == 0) reward.items.add(new ItemReward(level >= 40 ? "minecraft:netherite_ingot" : "cobblemon:rare_candy", level >= 40 ? 1 : 3));
            if (level % 7 == 0) reward.items.add(new ItemReward("cobblemon:ability_capsule", 1));
            reward.items.add(new ItemReward(level % 5 == 0 ? "cobblemon:ultra_ball" : "cobblemon:great_ball", level % 10 == 0 ? 24 : 10 + Math.min(14, level / 2)));
            config.rewards.put(String.valueOf(level), reward);
        }
        return config;
    }

    public static final class Config {
        public int xpPerLevel = 1000;
        public int maxLevel = 50;
        public Map<String, Reward> rewards = new LinkedHashMap<>();
    }

    public static final class Reward {
        public long credits = 0L;
        public int rankedTokens = 0;
        public List<ItemReward> items = new ArrayList<>();
    }

    public static final class ItemReward {
        public String id;
        public int count;
        public ItemReward() {}
        public ItemReward(String id, int count) { this.id = id; this.count = count; }
    }
}
