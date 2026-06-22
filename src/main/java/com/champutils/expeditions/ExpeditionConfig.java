package com.champutils.expeditions;

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
import java.util.List;

public final class ExpeditionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/expeditions.json");
    private static Config DATA = defaults();

    private ExpeditionConfig() {}

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
                if (DATA.low == null) DATA.low = defaults().low;
                if (DATA.mid == null) DATA.mid = defaults().mid;
                if (DATA.high == null) DATA.high = defaults().high;
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

    public static Tier tier(int pokemonLevel) {
        if (pokemonLevel >= 80) return DATA.high;
        if (pokemonLevel >= 50) return DATA.mid;
        return DATA.low;
    }

    public static List<ItemStack> itemStacks(int pokemonLevel) {
        Tier tier = tier(pokemonLevel);
        List<ItemStack> out = new ArrayList<>();
        if (tier.items == null) return out;
        for (ItemReward reward : tier.items) {
            if (reward == null || reward.id == null || reward.id.isBlank()) continue;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.id));
            if (item == null || item == Items.AIR) continue;
            out.add(new ItemStack(item, Math.max(1, reward.count)));
        }
        return out;
    }

    private static Config defaults() {
        Config config = new Config();
        config.low = new Tier(2, EconomyManager.wholeCreditsToCents(50L));
        config.low.items.add(new ItemReward("cobblemon:potion", 4));
        config.low.items.add(new ItemReward("cobblemon:poke_ball", 8));

        config.mid = new Tier(4, EconomyManager.wholeCreditsToCents(100L));
        config.mid.items.add(new ItemReward("cobblemon:super_potion", 4));
        config.mid.items.add(new ItemReward("cobblemon:great_ball", 8));

        config.high = new Tier(8, EconomyManager.wholeCreditsToCents(250L));
        config.high.items.add(new ItemReward("cobblemon:hyper_potion", 4));
        config.high.items.add(new ItemReward("cobblemon:quick_ball", 12));
        return config;
    }

    public static final class Config {
        public Tier low;
        public Tier mid;
        public Tier high;
    }

    public static final class Tier {
        public int hours;
        public long credits;
        public List<ItemReward> items = new ArrayList<>();
        public Tier() {}
        public Tier(int hours, long credits) { this.hours = hours; this.credits = credits; }
    }

    public static final class ItemReward {
        public String id;
        public int count;
        public ItemReward() {}
        public ItemReward(String id, int count) { this.id = id; this.count = count; }
    }
}
