package com.champutils.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class NpcShopConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "npc_shop.json");

    public static ShopRoot CONFIG = new ShopRoot();

    private NpcShopConfig() {
    }

    public static final class ShopRoot {
        public String title = "Essentials Shop";
        public List<ShopEntry> entries = new ArrayList<>();
    }

    public static final class ShopEntry {
        /** item, tool, crate_credit, pokemon_crate, or command */
        public String type = "item";
        public String id = "minecraft:stone";
        public String displayName = "Stone";
        public String icon = "minecraft:stone";
        public long price = 100L;
        public int amount = 1;
        public int slot = -1;

        /** Used by type=tool. pickaxe, axe, hoe, or sword. */
        public String toolType = "pickaxe";
        public String rarity = "COMMON";

        /** Used by type=pokemon_crate. Chances are percentages, so 1.0 = 1%. */
        public double shinyChance = 1.0D;
        public double legendaryChance = 0.1D;
        public double ultraBeastChance = 0.5D;
        public double paradoxChance = 0.5D;
        public int minLevel = 5;
        public int maxLevel = 50;

        /** Used by type=command. Use %player% placeholder. */
        public List<String> commands = new ArrayList<>();

        public List<String> lore = new ArrayList<>();
    }

    public static void load() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

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
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(CONFIG, writer);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static void sanitize() {
        if (CONFIG == null) {
            CONFIG = createDefault();
        }
        if (CONFIG.title == null || CONFIG.title.isBlank()) {
            CONFIG.title = "Essentials Shop";
        }
        if (CONFIG.entries == null) {
            CONFIG.entries = new ArrayList<>();
        }
        CONFIG.entries.removeIf(NpcShopConfig::isRemovedLegacyEntry);

        for (ShopEntry entry : CONFIG.entries) {
            if (entry.type == null || entry.type.isBlank()) entry.type = "item";
            if (entry.id == null) entry.id = "";
            if (entry.displayName == null || entry.displayName.isBlank()) entry.displayName = entry.id;
            if (entry.icon == null || entry.icon.isBlank()) entry.icon = entry.id == null || entry.id.isBlank() ? "minecraft:chest" : entry.id;
            if (entry.amount <= 0) entry.amount = 1;
            if (entry.price < 0L) entry.price = 0L;
            if (entry.toolType == null || entry.toolType.isBlank()) entry.toolType = "pickaxe";
            if (entry.rarity == null || entry.rarity.isBlank()) entry.rarity = "COMMON";
            if ("tool".equalsIgnoreCase(entry.type) && "COMMON".equalsIgnoreCase(entry.rarity)) entry.price = 100L;
            if (entry.shinyChance < 0.0D) entry.shinyChance = 0.0D;
            if (entry.legendaryChance < 0.0D) entry.legendaryChance = 0.0D;
            if (entry.ultraBeastChance < 0.0D) entry.ultraBeastChance = 0.0D;
            if (entry.paradoxChance < 0.0D) entry.paradoxChance = 0.0D;
            if (entry.minLevel <= 0) entry.minLevel = 5;
            if (entry.maxLevel < entry.minLevel) entry.maxLevel = entry.minLevel;
            if (entry.commands == null) entry.commands = new ArrayList<>();
            if (entry.lore == null) entry.lore = new ArrayList<>();
            entry.lore.removeIf(line -> line != null && line.toLowerCase().contains("money sink"));
        }

        upsertDefaultEntry("genesisforms:mega_bracelet", item(12, "§dMega Bracelet", "genesisforms:mega_bracelet", "genesisforms:mega_bracelet", 1, 100000L,
                "§7Unlock Mega Evolution access.", "§8A premium progression purchase."));

        CONFIG.entries.removeIf(entry -> entry != null && "pokemon_crate".equalsIgnoreCase(entry.type == null ? "" : entry.type.trim()));

        upsertDefaultEntry("common_crate_credit", crateCredit(20, "§fCommon Crate Credit", "minecraft:chest", "common", 1, 5000L,
                "§7Adds 1 Common Crate credit.", "§7Open it from §f/opencrates§7."));
        upsertDefaultEntry("uncommon_crate_credit", crateCredit(22, "§aUncommon Crate Credit", "minecraft:barrel", "uncommon", 1, 15000L,
                "§7Adds 1 Uncommon Crate credit.", "§7Open it from §f/opencrates§7."));
        upsertDefaultEntry("rare_crate_credit", crateCredit(24, "§bRare Crate Credit", "minecraft:ender_chest", "rare", 1, 40000L,
                "§7Adds 1 Rare Crate credit.", "§7Open it from §f/opencrates§7."));
    }

    private static boolean isRemovedLegacyEntry(ShopEntry entry) {
        if (entry == null) return true;
        String id = entry.id == null ? "" : entry.id.toLowerCase();
        String name = entry.displayName == null ? "" : entry.displayName.toLowerCase();
        String type = entry.type == null ? "" : entry.type.toLowerCase();
        if (id.equals("cobblemon:great_ball") || id.equals("cobblemon:ultra_ball")) return true;
        if (name.contains("great ball") || name.contains("ultra ball")) return true;
        if (type.equals("pokemon_crate") || id.equals("store_pokemon_crate") || name.contains("store pokémon crate") || name.contains("store pokemon crate")) return true;
        return name.contains("legacy crate") || name.contains("removed crate");
    }

    private static void upsertDefaultEntry(String id, ShopEntry replacement) {
        for (int i = 0; i < CONFIG.entries.size(); i++) {
            ShopEntry existing = CONFIG.entries.get(i);
            if (existing != null && existing.id != null && existing.id.equalsIgnoreCase(id)) {
                CONFIG.entries.set(i, replacement);
                return;
            }
        }
        CONFIG.entries.add(replacement);
    }

    private static void upsertTypedEntry(String type, String id, ShopEntry replacement) {
        replacement.id = id;
        for (int i = 0; i < CONFIG.entries.size(); i++) {
            ShopEntry existing = CONFIG.entries.get(i);
            if (existing != null && type.equalsIgnoreCase(existing.type)) {
                CONFIG.entries.set(i, replacement);
                return;
            }
        }
        CONFIG.entries.add(replacement);
    }

    private static ShopRoot createDefault() {
        ShopRoot root = new ShopRoot();
        root.title = "Essentials Shop";

        root.entries.add(item(10, "§fPoké Ball x16", "cobblemon:poke_ball", "cobblemon:poke_ball", 16, 800L,
                "§7Basic catching supplies.", "§8Most trading should stay player-driven."));
        root.entries.add(item(12, "§dMega Bracelet", "genesisforms:mega_bracelet", "genesisforms:mega_bracelet", 1, 100000L,
                "§7Unlock Mega Evolution access.", "§8A premium progression purchase."));

        root.entries.add(tool(14, "§aCommon Mystery Pickaxe", "minecraft:stone_pickaxe", "pickaxe", 100L));
        root.entries.add(tool(15, "§aCommon Mystery Axe", "minecraft:stone_axe", "axe", 100L));
        root.entries.add(tool(16, "§aCommon Mystery Hoe", "minecraft:stone_hoe", "hoe", 100L));

        root.entries.add(crateCredit(20, "§fCommon Crate Credit", "minecraft:chest", "common", 1, 5000L,
                "§7Adds 1 Common Crate credit.", "§7Open it from §f/opencrates§7."));
        root.entries.add(crateCredit(22, "§aUncommon Crate Credit", "minecraft:barrel", "uncommon", 1, 15000L,
                "§7Adds 1 Uncommon Crate credit.", "§7Open it from §f/opencrates§7."));
        root.entries.add(crateCredit(24, "§bRare Crate Credit", "minecraft:ender_chest", "rare", 1, 40000L,
                "§7Adds 1 Rare Crate credit.", "§7Open it from §f/opencrates§7."));

        return root;
    }

    private static ShopEntry item(int slot, String name, String icon, String id, int amount, long price, String... lore) {
        ShopEntry entry = new ShopEntry();
        entry.type = "item";
        entry.slot = slot;
        entry.displayName = name;
        entry.icon = icon;
        entry.id = id;
        entry.amount = amount;
        entry.price = price;
        entry.lore = new ArrayList<>(List.of(lore));
        return entry;
    }

    private static ShopEntry tool(int slot, String name, String icon, String toolType, long price) {
        ShopEntry entry = new ShopEntry();
        entry.type = "tool";
        entry.slot = slot;
        entry.displayName = name;
        entry.icon = icon;
        entry.toolType = toolType;
        entry.rarity = "COMMON";
        entry.amount = 1;
        entry.price = price;
        entry.lore.add("§7Random unidentified common " + toolType + ".");
        entry.lore.add("§8Starter-friendly progression gear.");
        return entry;
    }


    private static ShopEntry crateCredit(int slot, String name, String icon, String crateId, int amount, long price, String... lore) {
        ShopEntry entry = new ShopEntry();
        entry.type = "crate_credit";
        entry.id = crateId;
        entry.slot = slot;
        entry.displayName = name;
        entry.icon = icon;
        entry.amount = Math.max(1, amount);
        entry.price = price;
        entry.lore = new ArrayList<>(List.of(lore));
        return entry;
    }

    private static ShopEntry pokemonCrate(int slot, String name, String icon, long price) {
        ShopEntry entry = new ShopEntry();
        entry.type = "pokemon_crate";
        entry.id = "store_pokemon_crate";
        entry.slot = slot;
        entry.displayName = name;
        entry.icon = icon;
        entry.amount = 1;
        entry.price = price;
        entry.shinyChance = 1.0D;
        entry.legendaryChance = 0.1D;
        entry.ultraBeastChance = 0.5D;
        entry.paradoxChance = 0.5D;
        entry.minLevel = 5;
        entry.maxLevel = 50;
        entry.lore.add("§7Opens immediately and gives 1 random Pokémon.");
        entry.lore.add("§7Mostly regular Pokémon.");
        entry.lore.add("§e1% shiny chance on regular Pokémon only");
        entry.lore.add("§60.1% legendary chance - never shiny");
        entry.lore.add("§d0.5% Ultra Beast chance - never shiny");
        entry.lore.add("§b0.5% Paradox chance - never shiny");
        return entry;
    }
}
