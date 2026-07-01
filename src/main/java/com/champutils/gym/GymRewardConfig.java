package com.champutils.gym;

import com.champutils.badge.BadgeType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public final class GymRewardConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/gym_rewards.json");
    private static Config DATA = defaults();
    private GymRewardConfig() {}

    public static synchronized void load() {
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            if (!FILE.exists()) { DATA = defaults(); save(); return; }
            try (FileReader r = new FileReader(FILE)) {
                Config loaded = GSON.fromJson(r, Config.class);
                DATA = loaded == null ? defaults() : loaded;
                if (DATA.rewards == null) DATA.rewards = new LinkedHashMap<>();
            }
        } catch (Exception e) { e.printStackTrace(); DATA = defaults(); }
    }

    public static synchronized void save() { try (FileWriter w = new FileWriter(FILE)) { GSON.toJson(DATA, w); } catch (Exception e) { e.printStackTrace(); } }
    public static Reward reward(BadgeType badge) { return badge == null || DATA.rewards == null ? null : DATA.rewards.get(badge.name()); }

    public static List<ItemStack> itemStacks(BadgeType badge) {
        Reward r = reward(badge); if (r == null || r.items == null) return Collections.emptyList();
        List<ItemStack> out = new ArrayList<>();
        for (ItemReward reward : r.items) {
            if (reward == null || reward.id == null || reward.id.isBlank()) continue;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.id));
            if (item == null) continue;
            out.add(new ItemStack(item, Math.max(1, reward.count)));
        }
        return out;
    }

    private static Config defaults() {
        Config c = new Config(); c.rewards = new LinkedHashMap<>();
        int i = 0;
        for (BadgeType badge : BadgeType.values()) {
            Reward r = new Reward();
            long wholeCredits = switch (badge) {
                case BOULDER -> 150L;
                case CASCADE -> 250L;
                case THUNDER -> 500L;
                case RAINBOW -> 1_000L;
                case SOUL -> 2_000L;
                case MARSH -> 3_500L;
                case VOLCANO -> 5_000L;
                case EARTH -> 7_500L;
                case LORELEI -> 10_000L;
                case BRUNO -> 12_500L;
                case AGATHA -> 15_000L;
                case LANCE -> 20_000L;
                case CHAMPION -> 25_000L;
            };
            r.credits = com.champutils.economy.EconomyManager.wholeCreditsToCents(wholeCredits);
            r.items.add(new ItemReward("cobblemon:poke_ball", Math.max(16, 24 + i * 4)));
            if (i >= 1) r.items.add(new ItemReward("cobblemon:great_ball", 8 + i * 2));
            if (i >= 4) r.items.add(new ItemReward("cobblemon:ultra_ball", 6 + i));
            if (i >= 7) r.items.add(new ItemReward("cobblemon:rare_candy", Math.max(1, i / 3)));
            c.rewards.put(badge.name(), r); i++;
        }
        return c;
    }
    public static final class Config { public Map<String, Reward> rewards = new LinkedHashMap<>(); }
    public static final class Reward { public long credits = 0L; public List<ItemReward> items = new ArrayList<>(); }
    public static final class ItemReward { public String id; public int count; public ItemReward() {} public ItemReward(String id, int count){this.id=id;this.count=count;} }
}
