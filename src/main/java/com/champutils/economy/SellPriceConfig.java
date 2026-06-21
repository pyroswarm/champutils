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
import java.util.Locale;
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
        if (stack == null || stack.isEmpty() || !DATA.enabled) {
            return 0L;
        }

        if (DATA.blockProfessionTools && ProfessionToolMetadata.isProfessionTool(stack)) {
            return 0L;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return 0L;
        }

        String itemId = id.toString().toLowerCase(Locale.ROOT);
        String namespace = id.getNamespace().toLowerCase(Locale.ROOT);

        if (DATA.blockedItems.contains(itemId) || DATA.blockedNamespaces.contains(namespace)) {
            return 0L;
        }

        for (String blockedPart : DATA.blockedItemContains) {
            if (blockedPart != null && !blockedPart.isBlank() && itemId.contains(blockedPart.toLowerCase(Locale.ROOT))) {
                return 0L;
            }
        }

        Double exact = DATA.itemPrices.get(itemId);
        if (exact != null) {
            return priceToStoredUnits(exact);
        }

        Double namespacePrice = DATA.namespaceBasePrices.get(namespace);
        if (namespacePrice != null) {
            return priceToStoredUnits(namespacePrice);
        }

        return priceToStoredUnits(DATA.defaultPrice);
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

    private static long priceToStoredUnits(Double price) {
        if (price == null || Double.isNaN(price) || Double.isInfinite(price) || price <= 0.0D) {
            return 0L;
        }
        return EconomyManager.creditsToCents(price);
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
        if (DATA.namespaceBasePrices == null) DATA.namespaceBasePrices = new LinkedHashMap<>();
        if (DATA.itemPrices == null) DATA.itemPrices = new LinkedHashMap<>();
        if (DATA.blockedItems == null) DATA.blockedItems = new LinkedHashSet<>();
        if (DATA.blockedNamespaces == null) DATA.blockedNamespaces = new LinkedHashSet<>();
        if (DATA.blockedItemContains == null) DATA.blockedItemContains = new LinkedHashSet<>();

        DATA.namespaceBasePrices.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue() < 0.0D);
        DATA.itemPrices.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue() < 0.0D);
        DATA.blockedItems.removeIf(id -> id == null || id.isBlank());
        DATA.blockedNamespaces.removeIf(id -> id == null || id.isBlank());
        DATA.blockedItemContains.removeIf(id -> id == null || id.isBlank());

        Map<String, Double> normalizedItems = new LinkedHashMap<>();
        DATA.itemPrices.forEach((k, v) -> normalizedItems.put(k.toLowerCase(Locale.ROOT), v));
        DATA.itemPrices = normalizedItems;

        Map<String, Double> normalizedNamespaces = new LinkedHashMap<>();
        DATA.namespaceBasePrices.forEach((k, v) -> normalizedNamespaces.put(k.toLowerCase(Locale.ROOT), v));
        DATA.namespaceBasePrices = normalizedNamespaces;

        Set<String> normalizedBlockedItems = new LinkedHashSet<>();
        DATA.blockedItems.forEach(id -> normalizedBlockedItems.add(id.toLowerCase(Locale.ROOT)));
        DATA.blockedItems = normalizedBlockedItems;

        Set<String> normalizedBlockedNamespaces = new LinkedHashSet<>();
        DATA.blockedNamespaces.forEach(id -> normalizedBlockedNamespaces.add(id.toLowerCase(Locale.ROOT)));
        DATA.blockedNamespaces = normalizedBlockedNamespaces;
    }

    private static ConfigRoot defaultConfig() {
        ConfigRoot root = new ConfigRoot();
        root.enabled = true;
        root.blockProfessionTools = true;
        root.allowDecimalPrices = true;
        root.currencyScale = 2;
        root.defaultPrice = 0.0D;
        root.namespaceBasePrices = new LinkedHashMap<>();
        root.itemPrices = new LinkedHashMap<>();
        root.blockedItems = new LinkedHashSet<>();
        root.blockedNamespaces = new LinkedHashSet<>();
        root.blockedItemContains = new LinkedHashSet<>();

        root.namespaceBasePrices.put("minecraft", 0.0D);
        root.namespaceBasePrices.put("cobblemon", 0.0D);
        root.namespaceBasePrices.put("genesisforms", 0.0D);

        addBlocked(root.blockedItems);
        root.blockedNamespaces.add("champitems");
        root.blockedNamespaces.add("champutils");
        root.blockedItemContains.add("champitem");
        root.blockedItemContains.add("unidentified");
        root.blockedItemContains.add("profession_tool");
        root.blockedItemContains.add("crate_credit");
        root.blockedItemContains.add("crate_key");
        root.blockedItemContains.add("tm_item");

        // Block common AFK-farmable drops and Cobblemon plant materials from server selling.
        root.blockedItemContains.add("apricorn");
        root.blockedItemContains.add("berry");
        root.blockedItemContains.add("mint_leaf");
        root.blockedItemContains.add("mint_seeds");
        root.blockedItemContains.add("vivichoke");
        root.blockedItemContains.add("rotten_flesh");
        root.blockedItemContains.add("spider_eye");
        root.blockedItemContains.add("gunpowder");
        root.blockedItemContains.add("slime_ball");
        root.blockedItemContains.add("magma_cream");
        root.blockedItemContains.add("ender_pearl");
        root.blockedItemContains.add("phantom_membrane");

        // Basic building materials. Logs intentionally sell for more than planks.
        root.itemPrices.put("minecraft:stick", 0.01D);
        root.itemPrices.put("minecraft:oak_planks", 0.02D);
        root.itemPrices.put("minecraft:spruce_planks", 0.02D);
        root.itemPrices.put("minecraft:birch_planks", 0.02D);
        root.itemPrices.put("minecraft:jungle_planks", 0.02D);
        root.itemPrices.put("minecraft:acacia_planks", 0.02D);
        root.itemPrices.put("minecraft:dark_oak_planks", 0.02D);
        root.itemPrices.put("minecraft:mangrove_planks", 0.02D);
        root.itemPrices.put("minecraft:cherry_planks", 0.02D);
        root.itemPrices.put("minecraft:bamboo_planks", 0.02D);
        root.itemPrices.put("minecraft:crimson_planks", 0.02D);
        root.itemPrices.put("minecraft:warped_planks", 0.02D);
        root.itemPrices.put("minecraft:oak_log", 0.12D);
        root.itemPrices.put("minecraft:spruce_log", 0.12D);
        root.itemPrices.put("minecraft:birch_log", 0.12D);
        root.itemPrices.put("minecraft:jungle_log", 0.12D);
        root.itemPrices.put("minecraft:acacia_log", 0.12D);
        root.itemPrices.put("minecraft:dark_oak_log", 0.12D);
        root.itemPrices.put("minecraft:mangrove_log", 0.12D);
        root.itemPrices.put("minecraft:cherry_log", 0.12D);
        root.itemPrices.put("minecraft:crimson_stem", 0.12D);
        root.itemPrices.put("minecraft:warped_stem", 0.12D);

        addLowValueProfessionMaterials(root);

        return root;
    }

    private static void putAll(Map<String, Double> prices, double price, String... itemIds) {
        for (String itemId : itemIds) {
            prices.put(itemId, price);
        }
    }

    private static void addLowValueProfessionMaterials(ConfigRoot root) {
        // Stone and common mine bulk should be sellable, but intentionally pays almost nothing.
        putAll(root.itemPrices, 0.01D,
                "minecraft:stone", "minecraft:cobblestone", "minecraft:deepslate", "minecraft:cobbled_deepslate",
                "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff", "minecraft:calcite",
                "minecraft:basalt", "minecraft:blackstone", "minecraft:netherrack", "minecraft:end_stone", "minecraft:dripstone_block",
                "cobblemon:tumblestone", "cobblemon:tumblestone_block", "cobblemon:black_tumblestone", "cobblemon:black_tumblestone_block",
                "cobblemon:sky_tumblestone", "cobblemon:sky_tumblestone_block");

        // Crops are allowed for Farming profession utility, but should never be a strong AFK income source.
        putAll(root.itemPrices, 0.02D,
                "minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot",
                "minecraft:melon_slice", "minecraft:pumpkin", "minecraft:cocoa_beans", "minecraft:nether_wart");
        putAll(root.itemPrices, 0.005D,
                "minecraft:wheat_seeds", "minecraft:beetroot_seeds", "minecraft:melon_seeds", "minecraft:pumpkin_seeds");
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

        blocked.add("minecraft:rotten_flesh");
        blocked.add("minecraft:bone");
        blocked.add("minecraft:string");
        blocked.add("minecraft:spider_eye");
        blocked.add("minecraft:fermented_spider_eye");
        blocked.add("minecraft:gunpowder");
        blocked.add("minecraft:slime_ball");
        blocked.add("minecraft:magma_cream");
        blocked.add("minecraft:ender_pearl");
        blocked.add("minecraft:phantom_membrane");
        blocked.add("minecraft:blaze_rod");
        blocked.add("minecraft:blaze_powder");
        blocked.add("minecraft:ghast_tear");
        blocked.add("minecraft:prismarine_shard");
        blocked.add("minecraft:prismarine_crystals");
        blocked.add("minecraft:shulker_shell");
        blocked.add("minecraft:rabbit_foot");
        blocked.add("minecraft:ink_sac");
        blocked.add("minecraft:glow_ink_sac");
    }

    public static final class ConfigRoot {
        public boolean enabled = true;
        public boolean blockProfessionTools = true;
        public boolean allowDecimalPrices = true;
        public int currencyScale = 2;
        public double defaultPrice = 0.0D;
        public Map<String, Double> namespaceBasePrices = new LinkedHashMap<>();
        public Map<String, Double> itemPrices = new LinkedHashMap<>();
        public Set<String> blockedItems = new LinkedHashSet<>();
        public Set<String> blockedNamespaces = new LinkedHashSet<>();
        public Set<String> blockedItemContains = new LinkedHashSet<>();
    }
}
