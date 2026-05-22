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
        /** item, tool, crate_credit, or command */
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

        /** Used by type=crate_credit. COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC. */
        public String crateRarity = "COMMON";
        public boolean pokemonCrate = false;

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
        for (ShopEntry entry : CONFIG.entries) {
            if (entry.type == null || entry.type.isBlank()) entry.type = "item";
            if (entry.id == null) entry.id = "";
            if (entry.displayName == null || entry.displayName.isBlank()) entry.displayName = entry.id;
            if (entry.icon == null || entry.icon.isBlank()) entry.icon = entry.id == null || entry.id.isBlank() ? "minecraft:chest" : entry.id;
            if (entry.amount <= 0) entry.amount = 1;
            if (entry.price < 0L) entry.price = 0L;
            if (entry.toolType == null || entry.toolType.isBlank()) entry.toolType = "pickaxe";
            if (entry.rarity == null || entry.rarity.isBlank()) entry.rarity = "COMMON";
            if (entry.crateRarity == null || entry.crateRarity.isBlank()) entry.crateRarity = "COMMON";
            if (entry.commands == null) entry.commands = new ArrayList<>();
            if (entry.lore == null) entry.lore = new ArrayList<>();
        }
    }

    private static ShopRoot createDefault() {
        ShopRoot root = new ShopRoot();
        root.title = "Essentials Shop";

        root.entries.add(item(10, "§fPoké Ball x16", "cobblemon:poke_ball", "cobblemon:poke_ball", 16, 800L,
                "§7Basic catching supplies.", "§8This keeps the server market player-driven."));
        root.entries.add(item(11, "§bGreat Ball x8", "cobblemon:great_ball", "cobblemon:great_ball", 8, 1200L,
                "§7A small upgrade from Poké Balls."));
        root.entries.add(item(12, "§dUltra Ball x4", "cobblemon:ultra_ball", "cobblemon:ultra_ball", 4, 1800L,
                "§7Useful, but not cheap."));

        root.entries.add(tool(14, "§aCommon Mystery Pickaxe", "minecraft:stone_pickaxe", "pickaxe", 5000L));
        root.entries.add(tool(15, "§aCommon Mystery Axe", "minecraft:stone_axe", "axe", 5000L));
        root.entries.add(tool(16, "§aCommon Mystery Hoe", "minecraft:stone_hoe", "hoe", 5000L));

        root.entries.add(crate(22, "§6Common Dungeon Crate Credit", "cobblemon:gilded_chest", "COMMON", false, 25000L));

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
        entry.lore.add("§8Designed as a starter-friendly money sink.");
        return entry;
    }

    private static ShopEntry crate(int slot, String name, String icon, String rarity, boolean pokemon, long price) {
        ShopEntry entry = new ShopEntry();
        entry.type = "crate_credit";
        entry.slot = slot;
        entry.displayName = name;
        entry.icon = icon;
        entry.crateRarity = rarity;
        entry.pokemonCrate = pokemon;
        entry.amount = 1;
        entry.price = price;
        entry.lore.add("§7Adds 1 bound dungeon crate credit.");
        entry.lore.add("§7This cannot be traded or duped.");
        entry.lore.add("§8Strong money sink for beta.");
        return entry;
    }
}
