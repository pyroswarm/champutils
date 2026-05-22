package com.champutils.economy;

import com.champutils.profession.ProfessionToolMetadata;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SellPriceConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "server_sell_prices.json");

    private static ConfigRoot DATA = defaultConfig();

    private SellPriceConfig() {
    }

    public static synchronized void load() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            if (!FILE.exists()) {
                DATA = defaultConfig();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                ConfigRoot loaded = GSON.fromJson(reader, ConfigRoot.class);
                DATA = loaded == null ? defaultConfig() : loaded;
            }

            sanitize();
            save();
        }
        catch (Exception e) {
            e.printStackTrace();
            DATA = defaultConfig();
        }
    }

    public static synchronized void save() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(DATA, writer);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isEnabled() {
        return DATA.enabled;
    }

    public static long getUnitPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }

        if (!DATA.enabled) {
            return 0L;
        }

        if (DATA.blockProfessionTools && ProfessionToolMetadata.isProfessionTool(stack)) {
            return 0L;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return 0L;
        }

        String itemId = id.toString();
        if (DATA.blockedItems.contains(itemId)) {
            return 0L;
        }

        Long exact = DATA.itemPrices.get(itemId);
        if (exact != null) {
            return Math.max(0L, exact);
        }

        Long namespacePrice = DATA.namespaceBasePrices.get(id.getNamespace());
        if (namespacePrice != null) {
            return Math.max(0L, namespacePrice);
        }

        return 0L;
    }

    public static long getStackValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }
        long unit = getUnitPrice(stack);
        if (unit <= 0L) {
            return 0L;
        }
        return safeMultiply(unit, stack.getCount());
    }

    public static String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "unknown";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "unknown" : id.toString();
    }

    private static long safeMultiply(long price, int count) {
        if (price <= 0L || count <= 0) {
            return 0L;
        }
        if (Long.MAX_VALUE / price < count) {
            return Long.MAX_VALUE;
        }
        return price * count;
    }

    private static void sanitize() {
        if (DATA.namespaceBasePrices == null) {
            DATA.namespaceBasePrices = new LinkedHashMap<>();
        }
        if (DATA.itemPrices == null) {
            DATA.itemPrices = new LinkedHashMap<>();
        }
        if (DATA.blockedItems == null) {
            DATA.blockedItems = new LinkedHashSet<>();
        }
        DATA.namespaceBasePrices.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue() < 0L);
        DATA.itemPrices.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue() < 0L);
        DATA.blockedItems.removeIf(id -> id == null || id.isBlank());
    }

    private static ConfigRoot defaultConfig() {
        ConfigRoot root = new ConfigRoot();
        root.enabled = true;
        root.blockProfessionTools = true;
        root.namespaceBasePrices = new LinkedHashMap<>();
        root.itemPrices = new LinkedHashMap<>();
        root.blockedItems = new LinkedHashSet<>();

        // Covers every Cobblemon item at a low fallback value unless overridden below.
        root.namespaceBasePrices.put("cobblemon", 2L);

        addVanillaProfessionMaterials(root.itemPrices);
        addCobblemonOverrides(root.itemPrices);
        addBlocked(root.blockedItems);

        return root;
    }

    private static void addBlocked(Set<String> blocked) {
        blocked.add("minecraft:air");
        blocked.add("minecraft:barrier");
        blocked.add("minecraft:command_block");
        blocked.add("minecraft:chain_command_block");
        blocked.add("minecraft:repeating_command_block");
        blocked.add("minecraft:structure_block");
        blocked.add("minecraft:structure_void");
        blocked.add("minecraft:debug_stick");
        blocked.add("minecraft:bedrock");
    }

    private static void addVanillaProfessionMaterials(Map<String, Long> prices) {
        // Logs / wood profession baseline. Intentionally low so player shops can beat it.
        String[] logs = {
                "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log", "mangrove_log", "cherry_log",
                "stripped_oak_log", "stripped_spruce_log", "stripped_birch_log", "stripped_jungle_log", "stripped_acacia_log", "stripped_dark_oak_log", "stripped_mangrove_log", "stripped_cherry_log",
                "oak_wood", "spruce_wood", "birch_wood", "jungle_wood", "acacia_wood", "dark_oak_wood", "mangrove_wood", "cherry_wood",
                "stripped_oak_wood", "stripped_spruce_wood", "stripped_birch_wood", "stripped_jungle_wood", "stripped_acacia_wood", "stripped_dark_oak_wood", "stripped_mangrove_wood", "stripped_cherry_wood"
        };
        for (String log : logs) {
            prices.put("minecraft:" + log, 1L);
        }

        // Common crops / farming outputs.
        prices.put("minecraft:wheat", 1L);
        prices.put("minecraft:wheat_seeds", 1L);
        prices.put("minecraft:carrot", 1L);
        prices.put("minecraft:potato", 1L);
        prices.put("minecraft:beetroot", 1L);
        prices.put("minecraft:beetroot_seeds", 1L);
        prices.put("minecraft:pumpkin", 2L);
        prices.put("minecraft:pumpkin_seeds", 1L);
        prices.put("minecraft:melon_slice", 1L);
        prices.put("minecraft:melon", 3L);
        prices.put("minecraft:melon_seeds", 1L);
        prices.put("minecraft:sugar_cane", 1L);
        prices.put("minecraft:cocoa_beans", 1L);
        prices.put("minecraft:cactus", 1L);
        prices.put("minecraft:bamboo", 1L);
        prices.put("minecraft:nether_wart", 2L);
        prices.put("minecraft:sweet_berries", 1L);
        prices.put("minecraft:glow_berries", 2L);
        prices.put("minecraft:apple", 2L);

        // Mining outputs. Still deliberately below player-market value.
        prices.put("minecraft:cobblestone", 1L);
        prices.put("minecraft:stone", 1L);
        prices.put("minecraft:deepslate", 1L);
        prices.put("minecraft:tuff", 1L);
        prices.put("minecraft:calcite", 1L);
        prices.put("minecraft:coal", 2L);
        prices.put("minecraft:charcoal", 2L);
        prices.put("minecraft:raw_copper", 2L);
        prices.put("minecraft:copper_ingot", 2L);
        prices.put("minecraft:raw_iron", 4L);
        prices.put("minecraft:iron_ingot", 4L);
        prices.put("minecraft:raw_gold", 6L);
        prices.put("minecraft:gold_ingot", 6L);
        prices.put("minecraft:redstone", 1L);
        prices.put("minecraft:lapis_lazuli", 2L);
        prices.put("minecraft:quartz", 2L);
        prices.put("minecraft:amethyst_shard", 3L);
        prices.put("minecraft:diamond", 18L);
        prices.put("minecraft:emerald", 12L);
        prices.put("minecraft:ancient_debris", 60L);
        prices.put("minecraft:netherite_scrap", 80L);
        prices.put("minecraft:netherite_ingot", 350L);
    }

    private static void addCobblemonOverrides(Map<String, Long> prices) {
        // Pokeballs. Server buyback should be very low compared to real value.
        String[] tier1Balls = {"poke_ball", "premier_ball", "heal_ball", "azure_ball", "citrine_ball", "roseate_ball", "slate_ball", "verdant_ball"};
        for (String ball : tier1Balls) prices.put("cobblemon:" + ball, 2L);
        String[] tier2Balls = {"great_ball", "dive_ball", "fast_ball", "friend_ball", "heavy_ball", "level_ball", "lure_ball", "moon_ball", "nest_ball", "net_ball", "park_ball", "sport_ball"};
        for (String ball : tier2Balls) prices.put("cobblemon:" + ball, 4L);
        String[] tier3Balls = {"ultra_ball", "dusk_ball", "love_ball", "luxury_ball", "quick_ball", "repeat_ball", "timer_ball"};
        for (String ball : tier3Balls) prices.put("cobblemon:" + ball, 8L);
        prices.put("cobblemon:dream_ball", 20L);
        prices.put("cobblemon:beast_ball", 30L);
        prices.put("cobblemon:master_ball", 250L);

        // Recovery and battle items.
        prices.put("cobblemon:potion", 2L);
        prices.put("cobblemon:super_potion", 5L);
        prices.put("cobblemon:hyper_potion", 10L);
        prices.put("cobblemon:max_potion", 16L);
        prices.put("cobblemon:full_restore", 22L);
        prices.put("cobblemon:revive", 12L);
        prices.put("cobblemon:max_revive", 35L);
        prices.put("cobblemon:full_heal", 5L);
        prices.put("cobblemon:antidote", 2L);
        prices.put("cobblemon:awakening", 2L);
        prices.put("cobblemon:burn_heal", 2L);
        prices.put("cobblemon:ice_heal", 2L);
        prices.put("cobblemon:paralyze_heal", 2L);

        // Evolution stones / fossils / competitive items.
        String[] stones = {"fire_stone", "water_stone", "thunder_stone", "leaf_stone", "ice_stone", "moon_stone", "sun_stone", "dawn_stone", "dusk_stone", "shiny_stone"};
        for (String stone : stones) prices.put("cobblemon:" + stone, 15L);
        String[] fossils = {"armor_fossil", "claw_fossil", "cover_fossil", "dome_fossil", "helix_fossil", "jaw_fossil", "old_amber_fossil", "plume_fossil", "root_fossil", "sail_fossil", "skull_fossil", "fossilized_bird", "fossilized_dino", "fossilized_drake", "fossilized_fish"};
        for (String fossil : fossils) prices.put("cobblemon:" + fossil, 25L);
        prices.put("cobblemon:ability_capsule", 60L);
        prices.put("cobblemon:ability_patch", 120L);
        prices.put("cobblemon:rare_candy", 25L);
        prices.put("cobblemon:exp_candy_xs", 2L);
        prices.put("cobblemon:exp_candy_s", 4L);
        prices.put("cobblemon:exp_candy_m", 8L);
        prices.put("cobblemon:exp_candy_l", 16L);
        prices.put("cobblemon:exp_candy_xl", 32L);
    }

    public static final class ConfigRoot {
        public boolean enabled = true;
        public boolean blockProfessionTools = true;
        public Map<String, Long> namespaceBasePrices = new LinkedHashMap<>();
        public Map<String, Long> itemPrices = new LinkedHashMap<>();
        public Set<String> blockedItems = new LinkedHashSet<>();
    }
}
