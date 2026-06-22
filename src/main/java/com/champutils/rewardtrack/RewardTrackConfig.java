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
            }
        } catch (Exception e) {
            e.printStackTrace();
            DATA = defaults();
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
            reward.credits = EconomyManager.wholeCreditsToCents(level % 10 == 0 ? 250L : 50L);
            reward.items.add(new ItemReward(level % 5 == 0 ? "cobblemon:ultra_ball" : "cobblemon:great_ball", level % 10 == 0 ? 16 : 8));
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
        public List<ItemReward> items = new ArrayList<>();
    }

    public static final class ItemReward {
        public String id;
        public int count;
        public ItemReward() {}
        public ItemReward(String id, int count) { this.id = id; this.count = count; }
    }
}
