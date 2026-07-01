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
import java.util.Locale;

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
                sanitizeTier(DATA.low);
                sanitizeTier(DATA.mid);
                sanitizeTier(DATA.high);
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
        return itemStacks(pokemonLevel, 1, "general");
    }

    public static List<ItemStack> itemStacks(int pokemonLevel, int battlingLevel, String type) {
        String normalized = normalizeType(type);
        int level = Math.max(1, Math.min(100, battlingLevel));
        List<ItemStack> out = new ArrayList<>();

        if ("pokeball".equals(normalized)) {
            add(out, "cobblemon:poke_ball", scale(level, 8, 16));
            if (level >= 25) add(out, "cobblemon:great_ball", scale(level, 4, 24));
            if (level >= 50) add(out, "cobblemon:ultra_ball", scale(level, 2, 32));
            if (level >= 75) add(out, "cobblemon:quick_ball", scale(level, 8, 64));
            if (level >= 90) add(out, "cobblemon:dream_ball", scale(level, 1, 8));
            return out;
        }

        if ("candy".equals(normalized)) {
            add(out, "cobblemon:exp_candy_xs", scale(level, 4, 32));
            if (level >= 25) add(out, "cobblemon:exp_candy_s", scale(level, 2, 24));
            if (level >= 50) add(out, "cobblemon:exp_candy_m", scale(level, 1, 16));
            if (level >= 75) add(out, "cobblemon:exp_candy_l", scale(level, 1, 8));
            if (level >= 95) add(out, "cobblemon:exp_candy_xl", Math.max(1, level / 25));
            return out;
        }

        if ("held_item".equals(normalized)) {
            add(out, "cobblemon:wise_glasses", 1);
            if (level >= 30) add(out, "cobblemon:muscle_band", 1);
            if (level >= 55) add(out, "cobblemon:focus_sash", 1);
            if (level >= 75) add(out, "cobblemon:leftovers", 1);
            if (level >= 90) add(out, "cobblemon:life_orb", 1);
            return out;
        }

        if ("tm".equals(normalized)) {
            add(out, "minecraft:paper", scale(level, 2, 12));
            add(out, "minecraft:lapis_lazuli", scale(level, 8, 48));
            if (level >= 50) add(out, "minecraft:amethyst_shard", scale(level, 4, 24));
            return out;
        }

        Tier tier = tier(pokemonLevel);
        if (tier.items == null) return out;
        for (ItemReward reward : tier.items) {
            if (reward == null || reward.id == null || reward.id.isBlank()) continue;
            add(out, reward.id, Math.max(1, reward.count));
        }
        return out;
    }

    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "general";
        String normalized = type.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "ball", "balls", "pokeballs", "poke_ball", "poke_balls" -> "pokeball";
            case "held", "helditem", "helditems", "held_items" -> "held_item";
            case "candy", "candies", "xp", "xp_candy" -> "candy";
            case "tm", "tms" -> "tm";
            case "pokemon", "mon" -> "pokemon";
            default -> "general";
        };
    }

    private static int scale(int level, int min, int max) {
        return Math.max(min, Math.min(max, min + (int) Math.round((max - min) * (level / 100.0D))));
    }

    private static void add(List<ItemStack> out, String id, int count) {
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item != null && item != Items.AIR) out.add(new ItemStack(item, Math.max(1, count)));
        } catch (Throwable ignored) {}
    }

    public static List<ChunkReward> chunkRewards(int pokemonLevel) {
        Tier tier = tier(pokemonLevel);
        List<ChunkReward> out = new ArrayList<>();
        if (tier.chunks == null) return out;
        for (ChunkReward reward : tier.chunks) {
            if (reward == null || reward.chunk == null || reward.chunk.isBlank() || reward.amount <= 0) continue;
            out.add(new ChunkReward(reward.chunk, reward.amount));
        }
        return out;
    }

    private static void sanitizeTier(Tier tier) {
        if (tier == null) return;
        if (tier.items == null) tier.items = new ArrayList<>();
        if (tier.chunks == null) tier.chunks = new ArrayList<>();
    }

    private static Config defaults() {
        Config config = new Config();
        config.low = new Tier(2, EconomyManager.wholeCreditsToCents(50L));
        config.low.items.add(new ItemReward("cobblemon:potion", 4));
        config.low.items.add(new ItemReward("cobblemon:poke_ball", 8));
        config.low.chunks.add(new ChunkReward("COBBLESTONE", 8));
        config.low.chunks.add(new ChunkReward("COPPER", 2));

        config.mid = new Tier(4, EconomyManager.wholeCreditsToCents(100L));
        config.mid.items.add(new ItemReward("cobblemon:super_potion", 4));
        config.mid.items.add(new ItemReward("cobblemon:great_ball", 8));
        config.mid.chunks.add(new ChunkReward("COBBLESTONE", 16));
        config.mid.chunks.add(new ChunkReward("COPPER", 5));
        config.mid.chunks.add(new ChunkReward("IRON", 2));

        config.high = new Tier(8, EconomyManager.wholeCreditsToCents(250L));
        config.high.items.add(new ItemReward("cobblemon:hyper_potion", 4));
        config.high.items.add(new ItemReward("cobblemon:quick_ball", 12));
        config.high.chunks.add(new ChunkReward("COBBLESTONE", 32));
        config.high.chunks.add(new ChunkReward("COPPER", 10));
        config.high.chunks.add(new ChunkReward("IRON", 5));
        config.high.chunks.add(new ChunkReward("GOLD", 2));
        config.high.chunks.add(new ChunkReward("DIAMOND", 1));
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
        public List<ChunkReward> chunks = new ArrayList<>();
        public Tier() {}
        public Tier(int hours, long credits) { this.hours = hours; this.credits = credits; }
    }

    public static final class ItemReward {
        public String id;
        public int count;
        public ItemReward() {}
        public ItemReward(String id, int count) { this.id = id; this.count = count; }
    }

    public static final class ChunkReward {
        public String chunk;
        public int amount;
        public ChunkReward() {}
        public ChunkReward(String chunk, int amount) { this.chunk = chunk; this.amount = amount; }
    }
}
