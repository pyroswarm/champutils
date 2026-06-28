package com.champutils.genesis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MegaShopConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "mega_shop.json");

    public static ShopRoot CONFIG = new ShopRoot();

    private MegaShopConfig() {}

    public static final class ShopRoot {
        public String title = "Mega & Special Item Shop";
        public Boolean autoAddMissingGenesisItems = true;
        public Boolean autoAddMissingNonCraftableCobblemonItems = true;
        public List<ShopEntry> entries = new ArrayList<>();
    }

    public static final class ShopEntry {
        public String id = "genesisforms:abomasite";
        public String displayName = "Abomasite";
        public String category = "Mega Stones";
        public boolean available = true;
        /** Price is in whole Credits, not cents. Decimals are supported. */
        public double priceCredits = 5000.0D;
        public int amount = 1;
        public String icon = "";
        public List<String> lore = new ArrayList<>();
    }

    public static void load() {
        try {
            if (!DIR.exists()) DIR.mkdirs();

            if (!FILE.exists()) {
                CONFIG = createDefault();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                ShopRoot loaded = GSON.fromJson(reader, ShopRoot.class);
                CONFIG = loaded == null ? createDefault() : loaded;
            }

            sanitize();
            save();
        } catch (Exception exception) {
            exception.printStackTrace();
            CONFIG = createDefault();
        }
    }

    public static void save() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(CONFIG, writer);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static ShopRoot createDefault() {
        ShopRoot root = new ShopRoot();
        root.title = "Mega & Special Item Shop";
        root.autoAddMissingGenesisItems = true;
        root.autoAddMissingNonCraftableCobblemonItems = true;
        CONFIG = root;
        addMissingConfiguredItems();
        root.entries.sort(entryComparator());
        return root;
    }

    private static void sanitize() {
        if (CONFIG == null) CONFIG = new ShopRoot();
        if (CONFIG.title == null || CONFIG.title.isBlank()) CONFIG.title = "Mega & Special Item Shop";
        if (CONFIG.entries == null) CONFIG.entries = new ArrayList<>();
        if (CONFIG.autoAddMissingGenesisItems == null) CONFIG.autoAddMissingGenesisItems = true;
        if (CONFIG.autoAddMissingNonCraftableCobblemonItems == null) CONFIG.autoAddMissingNonCraftableCobblemonItems = true;

        CONFIG.entries.removeIf(entry -> entry == null || entry.id == null || entry.id.isBlank());

        for (ShopEntry entry : CONFIG.entries) {
            entry.id = normalizeId(entry.id);
            if (entry.displayName == null || entry.displayName.isBlank()) entry.displayName = niceName(path(entry.id));
            if (entry.category == null || entry.category.isBlank()) entry.category = defaultCategory(entry.id, entry.displayName);
            if (entry.priceCredits < 0.0D || Double.isNaN(entry.priceCredits) || Double.isInfinite(entry.priceCredits)) entry.priceCredits = defaultPrice(entry.id, entry.category);
            if (entry.amount <= 0) entry.amount = 1;
            if (entry.icon == null || entry.icon.isBlank()) entry.icon = entry.id;
            if (entry.lore == null) entry.lore = new ArrayList<>();
        }

        addMissingConfiguredItems();

        CONFIG.entries.sort(entryComparator());
    }

    private static void addMissingConfiguredItems() {
        if (Boolean.TRUE.equals(CONFIG.autoAddMissingGenesisItems)) addMissingGenesisItems();
        if (Boolean.TRUE.equals(CONFIG.autoAddMissingNonCraftableCobblemonItems)) addMissingNonCraftableCobblemonItems();
    }

    private static void addMissingGenesisItems() {
        Set<String> existing = new LinkedHashSet<>();
        for (ShopEntry entry : CONFIG.entries) {
            if (entry != null && entry.id != null) existing.add(normalizeId(entry.id));
        }

        List<ResourceLocation> genesisItems = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            if (!"genesisforms".equals(id.getNamespace())) continue;
            if (item == Items.AIR) continue;
            if (isMegaStoneItem(id.toString(), null, null)) continue;
            genesisItems.add(id);
        }
        genesisItems.sort(Comparator.comparing(ResourceLocation::toString));

        for (ResourceLocation id : genesisItems) {
            String fullId = id.toString();
            if (existing.contains(fullId)) continue;
            ShopEntry entry = new ShopEntry();
            entry.id = fullId;
            entry.displayName = niceName(id.getPath());
            entry.category = defaultCategory(entry.id, entry.displayName);
            entry.available = true;
            entry.priceCredits = defaultPrice(entry.id, entry.category);
            entry.amount = 1;
            entry.icon = entry.id;
            entry.lore.add("§7Genesis Forms item.");
            entry.lore.add("§8Set available=false to hide this from the NPC shop.");
            CONFIG.entries.add(entry);
            existing.add(fullId);
        }
    }


    private static void addMissingNonCraftableCobblemonItems() {
        Set<String> existing = new LinkedHashSet<>();
        for (ShopEntry entry : CONFIG.entries) {
            if (entry != null && entry.id != null) existing.add(normalizeId(entry.id));
        }

        List<ResourceLocation> cobblemonItems = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            if (!"cobblemon".equals(id.getNamespace())) continue;
            if (item == Items.AIR) continue;
            String fullId = id.toString();
            if (CobblemonCraftableItems.IDS.contains(fullId)) continue;
            if (isMegaStoneItem(fullId, null, null)) continue;
            if (isInternalOrUnsafeCobblemonShopItem(id.getPath())) continue;
            cobblemonItems.add(id);
        }
        cobblemonItems.sort(Comparator.comparing(ResourceLocation::toString));

        for (ResourceLocation id : cobblemonItems) {
            String fullId = id.toString();
            if (existing.contains(fullId)) continue;
            ShopEntry entry = new ShopEntry();
            entry.id = fullId;
            entry.displayName = niceName(id.getPath());
            entry.category = defaultCategory(entry.id, entry.displayName);
            entry.available = true;
            entry.priceCredits = defaultPrice(entry.id, entry.category);
            entry.amount = 1;
            entry.icon = entry.id;
            entry.lore.add("§7Non-craftable Cobblemon item.");
            entry.lore.add("§8Set available=false to hide this from the NPC shop.");
            CONFIG.entries.add(entry);
            existing.add(fullId);
        }
    }

    private static boolean isInternalOrUnsafeCobblemonShopItem(String path) {
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return p.isBlank()
                || p.contains("debug")
                || p.contains("creative")
                || p.endsWith("spawn_egg")
                || p.equals("npc_editor")
                || p.equals("pokemon_model")
                || p.equals("pokemon_model_block")
                || p.equals("pasture_debugger");
    }

    private static boolean isMegaStoneItem(String id, String displayName, String category) {
        String path = path(id).toLowerCase(Locale.ROOT);
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        String cat = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (cat.contains("mega stone")) return true;
        if (path.contains("mega_bracelet") || path.contains("dynamax")) return false;
        return path.endsWith("ite") || name.endsWith("ite");
    }

    public static List<String> categories() {
        Set<String> set = new LinkedHashSet<>();
        for (ShopEntry entry : CONFIG.entries) {
            if (entry == null || !entry.available) continue;
            if (entry.category == null || entry.category.isBlank()) continue;
            set.add(entry.category);
        }
        return new ArrayList<>(set);
    }

    public static List<ShopEntry> entriesForCategory(String category) {
        List<ShopEntry> list = new ArrayList<>();
        String target = category == null ? "" : category.trim();
        for (ShopEntry entry : CONFIG.entries) {
            if (entry == null || !entry.available) continue;
            if (entry.category == null) continue;
            if (entry.category.trim().equalsIgnoreCase(target)) list.add(entry);
        }
        list.sort(Comparator.comparing(e -> e.displayName == null ? e.id : e.displayName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public static String defaultCategory(String id, String displayName) {
        String path = path(id).toLowerCase(Locale.ROOT);
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);

        if (path.contains("mint")) return "Nature Mints";
        if (path.contains("vitamin") || path.equals("hp_up") || path.equals("protein") || path.equals("iron") || path.equals("calcium") || path.equals("zinc") || path.equals("carbos") || path.equals("pp_up") || path.equals("pp_max")) return "Vitamins & EV Items";
        if (path.endsWith("_feather")) return "Feathers";
        if (path.contains("ability_patch") || path.contains("ability_capsule") || path.contains("ability")) return "Ability Items";
        if (path.contains("bottle_cap")) return "Hyper Training";
        if (path.contains("fossil")) return "Fossils";
        if (path.endsWith("_stone") || path.contains("evolution") || path.contains("link_cable") || path.contains("protector") || path.contains("electirizer") || path.contains("magmarizer") || path.contains("upgrade") || path.contains("dubious_disc") || path.contains("razor") || path.contains("sweet") || path.contains("apple")) return "Evolution Items";
        if (path.contains("candy") || path.equals("rare_candy")) return "Candies";
        if (path.contains("ball")) return "Poké Balls";
        if (path.contains("tm") || path.contains("tr")) return "TMs & Moves";
        if (path.endsWith("_tera_shard")) return "Tera Shards";
        if (path.endsWith("_memory")) return "Memories";
        if (path.endsWith("_plate")) return "Plates";
        if (path.endsWith("ium-z") || path.endsWith("-z") || name.endsWith(" z")) return "Z-Crystals";
        if (path.contains("orb") || path.contains("crystal")) return "Orbs & Crystals";
        if (path.contains("drive")) return "Drives";
        if (path.contains("mask")) return "Masks";
        if (path.endsWith("ite") || name.endsWith("ite")) return "Mega Stones";
        if (path.contains("dynamax") || path.contains("mega_bracelet")) return "Key Items";
        if (isLikelyHeldItem(path)) return "Held Items";
        return id != null && id.startsWith("cobblemon:") ? "Other Cobblemon Items" : "Other Items";
    }


    private static boolean isLikelyHeldItem(String path) {
        return path.contains("choice_")
                || path.contains("orb")
                || path.contains("band")
                || path.contains("scarf")
                || path.contains("specs")
                || path.contains("vest")
                || path.contains("helmet")
                || path.contains("lens")
                || path.contains("herb")
                || path.contains("boots")
                || path.contains("eviolite")
                || path.contains("leftovers")
                || path.contains("focus_sash")
                || path.contains("rocky_helmet")
                || path.contains("weakness_policy")
                || path.contains("loaded_dice")
                || path.contains("terrain_extender");
    }

    private static double defaultPrice(String id, String category) {
        String path = path(id).toLowerCase(Locale.ROOT);
        if (path.equals("ability_patch")) return 50000.0D;
        if (path.equals("ability_capsule")) return 7500.0D;
        if (path.equals("gold_bottle_cap")) return 75000.0D;
        if (path.equals("bottle_cap")) return 15000.0D;
        if (path.endsWith("_mint")) return 2500.0D;
        if (path.equals("hp_up") || path.equals("protein") || path.equals("iron") || path.equals("calcium") || path.equals("zinc") || path.equals("carbos")) return 500.0D;
        if (path.equals("pp_up")) return 2500.0D;
        if (path.equals("pp_max")) return 10000.0D;
        String normalized = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (normalized.contains("mega")) return 10000.0D;
        if (normalized.contains("hyper")) return 15000.0D;
        if (normalized.contains("ability")) return 10000.0D;
        if (normalized.contains("nature")) return 2500.0D;
        if (normalized.contains("vitamin") || normalized.contains("ev")) return 500.0D;
        if (normalized.contains("feather")) return 50.0D;
        if (normalized.contains("held")) return 7500.0D;
        if (normalized.contains("evolution")) return 2500.0D;
        if (normalized.contains("fossil")) return 10000.0D;
        if (normalized.contains("candie")) return 1000.0D;
        if (normalized.contains("poké") || normalized.contains("poke")) return 1000.0D;
        if (normalized.contains("z-crystal")) return 7500.0D;
        if (normalized.contains("tera")) return 500.0D;
        if (normalized.contains("memory")) return 5000.0D;
        if (normalized.contains("plate")) return 7500.0D;
        if (normalized.contains("orb") || normalized.contains("crystal")) return 25000.0D;
        if (normalized.contains("key")) return 50000.0D;
        return 5000.0D;
    }

    private static Comparator<ShopEntry> entryComparator() {
        return Comparator
                .comparing((ShopEntry entry) -> categoryOrder(entry.category))
                .thenComparing(entry -> entry.category == null ? "" : entry.category, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.displayName == null ? entry.id : entry.displayName, String.CASE_INSENSITIVE_ORDER);
    }

    private static int categoryOrder(String category) {
        String c = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (c.contains("mega")) return 10;
        if (c.contains("hyper")) return 12;
        if (c.contains("ability")) return 14;
        if (c.contains("nature")) return 16;
        if (c.contains("vitamin") || c.contains("ev")) return 18;
        if (c.contains("feather")) return 19;
        if (c.contains("held")) return 21;
        if (c.contains("evolution")) return 22;
        if (c.contains("fossil")) return 23;
        if (c.contains("candie")) return 24;
        if (c.contains("poké") || c.contains("poke")) return 25;
        if (c.contains("z-crystal")) return 30;
        if (c.contains("tera")) return 40;
        if (c.contains("memory")) return 40;
        if (c.contains("plate")) return 50;
        if (c.contains("orb") || c.contains("crystal")) return 60;
        if (c.contains("drive")) return 70;
        if (c.contains("mask")) return 80;
        if (c.contains("key")) return 90;
        return 100;
    }

    private static String normalizeId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!value.contains(":")) value = "genesisforms:" + value;
        return value;
    }

    private static String path(String id) {
        if (id == null) return "";
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static String niceName(String path) {
        if (path == null || path.isBlank()) return "Mega Shop Item";
        String[] parts = path.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            if (part.equalsIgnoreCase("z")) {
                builder.append('Z');
            } else {
                builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return builder.length() == 0 ? path : builder.toString();
    }
}
