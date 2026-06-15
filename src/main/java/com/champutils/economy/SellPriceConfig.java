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
