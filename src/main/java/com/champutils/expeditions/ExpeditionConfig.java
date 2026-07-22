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
import java.util.Set;

public final class ExpeditionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/expeditions.json");
    private static final int REWARD_SCHEMA_VERSION = 2;
    private static final Set<String> HELD_ITEM_IDS = Set.of(
            "cobblemon:ability_shield", "cobblemon:absorb_bulb", "cobblemon:air_balloon", "cobblemon:assault_vest",
            "cobblemon:big_root", "cobblemon:binding_band", "cobblemon:black_belt", "cobblemon:black_glasses",
            "cobblemon:black_sludge", "cobblemon:blunder_policy", "cobblemon:bright_powder", "cobblemon:cell_battery",
            "cobblemon:charcoal_stick", "cobblemon:choice_band", "cobblemon:choice_scarf", "cobblemon:choice_specs",
            "cobblemon:cleanse_tag", "cobblemon:covert_cloak", "cobblemon:damp_rock", "cobblemon:deep_sea_scale",
            "cobblemon:deep_sea_tooth", "cobblemon:destiny_knot", "cobblemon:dragon_fang", "cobblemon:eject_button",
            "cobblemon:eject_pack", "cobblemon:everstone", "cobblemon:eviolite", "cobblemon:exp_share",
            "cobblemon:expert_belt", "cobblemon:fairy_feather", "cobblemon:flame_orb", "cobblemon:float_stone",
            "cobblemon:focus_band", "cobblemon:focus_sash", "cobblemon:hard_stone", "cobblemon:heat_rock",
            "cobblemon:heavy_duty_boots", "cobblemon:icy_rock", "cobblemon:iron_ball", "cobblemon:kings_rock",
            "cobblemon:leftovers", "cobblemon:life_orb", "cobblemon:light_ball", "cobblemon:light_clay",
            "cobblemon:loaded_dice", "cobblemon:lucky_egg", "cobblemon:magnet", "cobblemon:medicinal_leek",
            "cobblemon:mental_herb", "cobblemon:metal_coat", "cobblemon:metal_powder", "cobblemon:metronome",
            "cobblemon:miracle_seed", "cobblemon:mirror_herb", "cobblemon:muscle_band", "cobblemon:mystic_water",
            "cobblemon:never_melt_ice", "cobblemon:poison_barb", "cobblemon:power_anklet", "cobblemon:power_band",
            "cobblemon:power_belt", "cobblemon:power_bracer", "cobblemon:power_herb", "cobblemon:power_lens",
            "cobblemon:power_weight", "cobblemon:protective_pads", "cobblemon:punching_glove", "cobblemon:quick_claw",
            "cobblemon:quick_powder", "cobblemon:razor_claw", "cobblemon:razor_fang", "cobblemon:red_card",
            "cobblemon:ring_target", "cobblemon:rocky_helmet", "cobblemon:room_service", "cobblemon:safety_goggles",
            "cobblemon:scope_lens", "cobblemon:sharp_beak", "cobblemon:shed_shell", "cobblemon:shell_bell",
            "cobblemon:silk_scarf", "cobblemon:silver_powder", "cobblemon:smoke_ball", "cobblemon:smooth_rock",
            "cobblemon:soft_sand", "cobblemon:spell_tag", "cobblemon:sticky_barb", "cobblemon:terrain_extender",
            "cobblemon:throat_spray", "cobblemon:toxic_orb", "cobblemon:twisted_spoon", "cobblemon:utility_umbrella",
            "cobblemon:weakness_policy", "cobblemon:white_herb", "cobblemon:wide_lens", "cobblemon:wise_glasses",
            "cobblemon:zoom_lens", "cobblemon:electric_seed", "cobblemon:grassy_seed", "cobblemon:misty_seed",
            "cobblemon:psychic_seed", "cobblemon:bug_gem", "cobblemon:dark_gem", "cobblemon:dragon_gem",
            "cobblemon:electric_gem", "cobblemon:fairy_gem", "cobblemon:fighting_gem", "cobblemon:fire_gem",
            "cobblemon:flying_gem", "cobblemon:ghost_gem", "cobblemon:grass_gem", "cobblemon:ground_gem",
            "cobblemon:ice_gem", "cobblemon:normal_gem", "cobblemon:poison_gem", "cobblemon:psychic_gem",
            "cobblemon:rock_gem", "cobblemon:steel_gem", "cobblemon:water_gem", "minecraft:bone", "minecraft:snowball"
    );
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
                sanitizeTier(DATA.low, "low");
                sanitizeTier(DATA.mid, "mid");
                sanitizeTier(DATA.high, "high");
                DATA.rewardSchemaVersion = REWARD_SCHEMA_VERSION;
                save();
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
            List<String> configured = tier(pokemonLevel).heldItemPool;
            if (configured != null && !configured.isEmpty()) {
                String selected = configured.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(configured.size()));
                if (isValidHeldItemId(selected)) add(out, selected, 1);
                if (!out.isEmpty()) return out;
            }
            String[] pool = level >= 90
                    ? new String[]{"cobblemon:lucky_egg", "cobblemon:exp_share", "cobblemon:leftovers", "cobblemon:life_orb", "cobblemon:choice_scarf"}
                    : level >= 75
                    ? new String[]{"cobblemon:leftovers", "cobblemon:life_orb", "cobblemon:choice_band", "cobblemon:choice_specs", "cobblemon:heavy_duty_boots"}
                    : level >= 55
                    ? new String[]{"cobblemon:focus_sash", "cobblemon:assault_vest", "cobblemon:rocky_helmet", "cobblemon:expert_belt", "cobblemon:eviolite"}
                    : level >= 30
                    ? new String[]{"cobblemon:muscle_band", "cobblemon:wise_glasses", "cobblemon:scope_lens", "cobblemon:shell_bell", "cobblemon:quick_claw"}
                    : new String[]{"cobblemon:black_glasses", "cobblemon:charcoal_stick", "cobblemon:magnet", "cobblemon:miracle_seed", "cobblemon:mystic_water", "cobblemon:soft_sand"};
            add(out, pool[java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.length)], 1);
            return out;
        }

        if ("tm".equals(normalized)) {
            // TM expeditions grant random real TMs during claim. The preview menu
            // shows the amount directly instead of placeholder materials.
            return out;
        }

        if ("pokemon".equals(normalized)) {
            // Pokémon expeditions are about finding Pokémon, not old bulk ball/chunk bundles.
            // Keep item clutter low and show the real rare-find odds in the preview menu.
            if (level >= 25) add(out, "cobblemon:exp_candy_xs", scale(level, 1, 8));
            if (level >= 60) add(out, "cobblemon:exp_candy_s", scale(level, 1, 4));
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


    public static long creditReward(int pokemonLevel, String type) {
        Tier tier = tier(pokemonLevel);
        long base = tier == null ? 0L : Math.max(0L, tier.credits);
        String normalized = normalizeType(type);
        double multiplier = switch (normalized) {
            case "general" -> 1.00D;
            case "pokeball" -> 0.70D;
            case "held_item" -> 0.55D;
            case "candy" -> 0.65D;
            case "tm" -> 0.50D;
            case "pokemon" -> 0.40D;
            default -> 1.00D;
        };
        return Math.max(0L, Math.round(base * multiplier));
    }



    public static String randomTmRank(int pokemonLevel) {
        Tier tier = tier(pokemonLevel);
        if (tier.tmRankPool != null && !tier.tmRankPool.isEmpty()) {
            return tier.tmRankPool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(tier.tmRankPool.size())).trim().toUpperCase(Locale.ROOT);
        }
        return pokemonLevel >= 90 ? "S" : pokemonLevel >= 75 ? "A" : pokemonLevel >= 60 ? "B" : pokemonLevel >= 45 ? "C" : pokemonLevel >= 30 ? "D" : pokemonLevel >= 15 ? "E" : "F";
    }

    public static int tmRewardCount(int pokemonLevel) {
        if (pokemonLevel >= 80) return 3;
        if (pokemonLevel >= 50) return 2;
        return 1;
    }

    public static String tmRewardSummary(int pokemonLevel) {
        int count = tmRewardCount(pokemonLevel);
        return count + " random TM" + (count == 1 ? "" : "s");
    }

    public static double legendaryPokemonChancePercent(int battlingLevel, int sentPokemonLevel, String expeditionType) {
        return specialPokemonChancePercent(battlingLevel, sentPokemonLevel, expeditionType, true);
    }

    public static double paradoxUltraBeastChancePercent(int battlingLevel, int sentPokemonLevel, String expeditionType) {
        return specialPokemonChancePercent(battlingLevel, sentPokemonLevel, expeditionType, false);
    }

    private static double specialPokemonChancePercent(int battlingLevel, int sentPokemonLevel, String expeditionType, boolean legendary) {
        int battle = Math.max(1, Math.min(100, battlingLevel));
        int mon = Math.max(1, Math.min(100, sentPokemonLevel));
        String normalized = normalizeType(expeditionType);
        double typeMultiplier = switch (normalized) {
            case "pokemon" -> 1.0D;
            case "general" -> 0.25D;
            case "candy", "held_item", "tm", "pokeball" -> 0.10D;
            default -> 0.10D;
        };
        double sentTierMultiplier = "pokemon".equals(normalized) ? 1.0D : (mon >= 80 ? 1.0D : (mon >= 50 ? 0.85D : 0.65D));
        double cap = legendary ? 2.5D : 5.0D;
        double floor = legendary ? 0.025D : 0.05D;
        double percent = floor + ((cap - floor) * (battle / 100.0D));
        percent *= typeMultiplier * sentTierMultiplier;
        return Math.max(0.0001D, Math.min(cap * typeMultiplier, percent));
    }

    public static String specialPokemonChanceSummary(int battlingLevel, int sentPokemonLevel, String expeditionType) {
        return String.format(Locale.US, "Legend %.4f%% · Paradox/UB %.4f%%",
                legendaryPokemonChancePercent(battlingLevel, sentPokemonLevel, expeditionType),
                paradoxUltraBeastChancePercent(battlingLevel, sentPokemonLevel, expeditionType));
    }

    public static String typeDisplayName(String type) {
        return switch (normalizeType(type)) {
            case "pokeball" -> "Poké Ball";
            case "held_item" -> "Held Item";
            case "candy" -> "Candy";
            case "tm" -> "TM";
            case "pokemon" -> "Pokémon";
            default -> "General";
        };
    }

    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "general";
        String normalized = type.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        while (normalized.endsWith("_expedition")) normalized = normalized.substring(0, normalized.length() - "_expedition".length());
        if (normalized.endsWith("expedition")) normalized = normalized.substring(0, normalized.length() - "expedition".length());
        return switch (normalized) {
            case "ball", "balls", "pokeball", "pokeballs", "poke_ball", "poke_balls" -> "pokeball";
            case "held", "held_item", "helditem", "helditems", "held_items" -> "held_item";
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

    private static void sanitizeTier(Tier tier, String tierName) {
        if (tier == null) return;
        if (tier.items == null) tier.items = new ArrayList<>();
        if (tier.chunks == null) tier.chunks = new ArrayList<>();
        if (tier.heldItemPool == null) tier.heldItemPool = new ArrayList<>();
        if (tier.tmRankPool == null) tier.tmRankPool = new ArrayList<>();

        // Never allow a general reward (potions, candy, etc.) to leak into a
        // Held Item expedition because of an old or manually edited pool.
        tier.heldItemPool.removeIf(id -> !isValidHeldItemId(id));
        if (tier.heldItemPool.isEmpty()) tier.heldItemPool.addAll(defaultHeldItemPool(tierName));

        tier.tmRankPool.replaceAll(rank -> com.champutils.rarity.RarityScale.normalize(rank));
        tier.tmRankPool.removeIf(rank -> rank == null || rank.isBlank());
        if (tier.tmRankPool.isEmpty()) tier.tmRankPool.addAll(defaultTmRanks(tierName));
    }

    private static boolean isValidHeldItemId(String id) {
        if (id == null || !HELD_ITEM_IDS.contains(id.trim().toLowerCase(Locale.ROOT))) return false;
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            return item != null && item != Items.AIR;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static List<String> defaultHeldItemPool(String tierName) {
        return switch (tierName == null ? "low" : tierName.toLowerCase(Locale.ROOT)) {
            case "high" -> new ArrayList<>(List.of(
                    "cobblemon:lucky_egg", "cobblemon:exp_share", "cobblemon:leftovers", "cobblemon:life_orb",
                    "cobblemon:choice_scarf", "cobblemon:choice_band", "cobblemon:choice_specs", "cobblemon:heavy_duty_boots"
            ));
            case "mid" -> new ArrayList<>(List.of(
                    "cobblemon:focus_sash", "cobblemon:assault_vest", "cobblemon:rocky_helmet", "cobblemon:expert_belt",
                    "cobblemon:eviolite", "cobblemon:muscle_band", "cobblemon:wise_glasses", "cobblemon:scope_lens"
            ));
            default -> new ArrayList<>(List.of(
                    "cobblemon:black_glasses", "cobblemon:charcoal_stick", "cobblemon:magnet", "cobblemon:miracle_seed",
                    "cobblemon:mystic_water", "cobblemon:soft_sand", "cobblemon:shell_bell", "cobblemon:quick_claw"
            ));
        };
    }

    private static List<String> defaultTmRanks(String tierName) {
        return switch (tierName == null ? "low" : tierName.toLowerCase(Locale.ROOT)) {
            case "high" -> new ArrayList<>(List.of("B", "A", "S"));
            case "mid" -> new ArrayList<>(List.of("D", "C", "B"));
            default -> new ArrayList<>(List.of("F", "E", "D"));
        };
    }

    private static Config defaults() {
        Config config = new Config();
        config.low = new Tier(2, EconomyManager.wholeCreditsToCents(50L));
        config.low.items.add(new ItemReward("cobblemon:potion", 4));
        config.low.items.add(new ItemReward("cobblemon:exp_candy_xs", 2));
        config.low.heldItemPool.addAll(defaultHeldItemPool("low"));
        config.low.tmRankPool.addAll(defaultTmRanks("low"));

        config.mid = new Tier(4, EconomyManager.wholeCreditsToCents(100L));
        config.mid.items.add(new ItemReward("cobblemon:super_potion", 4));
        config.mid.items.add(new ItemReward("cobblemon:exp_candy_s", 2));
        config.mid.heldItemPool.addAll(defaultHeldItemPool("mid"));
        config.mid.tmRankPool.addAll(defaultTmRanks("mid"));

        config.high = new Tier(8, EconomyManager.wholeCreditsToCents(250L));
        config.high.items.add(new ItemReward("cobblemon:hyper_potion", 4));
        config.high.items.add(new ItemReward("cobblemon:exp_candy_m", 2));
        config.high.heldItemPool.addAll(defaultHeldItemPool("high"));
        config.high.tmRankPool.addAll(defaultTmRanks("high"));
        config.rewardSchemaVersion = REWARD_SCHEMA_VERSION;
        return config;
    }

    public static final class Config {
        public int rewardSchemaVersion = REWARD_SCHEMA_VERSION;
        public Tier low;
        public Tier mid;
        public Tier high;
    }

    public static final class Tier {
        public int hours;
        public long credits;
        public List<ItemReward> items = new ArrayList<>();
        public List<ChunkReward> chunks = new ArrayList<>();
        public List<String> heldItemPool = new ArrayList<>();
        public List<String> tmRankPool = new ArrayList<>();
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
