package com.champutils.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class IslanderShopConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "islander_shop.json");

    public static Root CONFIG = createDefault();

    private IslanderShopConfig() {}

    public static final class Root {
        public String title = "Islander Resource Shop";
        public List<NpcShopConfig.ShopEntry> entries = new ArrayList<>();
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
                Root loaded = GSON.fromJson(reader, Root.class);
                CONFIG = loaded == null ? createDefault() : loaded;
            }
            sanitize();
            save();
        } catch (Exception e) {
            e.printStackTrace();
            CONFIG = createDefault();
        }
    }

    public static void save() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            sanitize();
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(CONFIG, writer); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void sanitize() {
        if (CONFIG == null) CONFIG = createDefault();
        if (CONFIG.title == null || CONFIG.title.isBlank()) CONFIG.title = "Islander Resource Shop";
        if (CONFIG.entries == null) CONFIG.entries = new ArrayList<>();
        ensureApricornSeeds(CONFIG);
        ensureRequestedResources(CONFIG);
        for (NpcShopConfig.ShopEntry entry : CONFIG.entries) {
            if (entry == null) continue;
            if (entry.type == null || entry.type.isBlank()) entry.type = "item";
            if (entry.id == null) entry.id = "";
            if (entry.displayName == null || entry.displayName.isBlank()) entry.displayName = entry.id;
            if (entry.icon == null || entry.icon.isBlank()) entry.icon = entry.id.isBlank() ? "minecraft:chest" : entry.id;
            if (entry.amount <= 0) entry.amount = 1;
            if (entry.price < 0L) entry.price = 0L;
            if (entry.lore == null) entry.lore = new ArrayList<>();
        }
    }

    private static void ensureApricornSeeds(Root root) {
        if (root == null || root.entries == null) return;
        String[] colors = {"black", "blue", "green", "pink", "red", "white", "yellow"};
        int slot = 37;
        for (String color : colors) {
            String id = "cobblemon:" + color + "_apricorn_seed";
            boolean exists = root.entries.stream().filter(java.util.Objects::nonNull).anyMatch(e -> id.equalsIgnoreCase(e.id));
            if (!exists) root.entries.add(item(slot, "§a" + Character.toUpperCase(color.charAt(0)) + color.substring(1) + " Apricorn Seeds x4", id, id, 4, 250, "§7Plantable apricorn starter for Islander profiles."));
            slot++;
        }
    }


    private static void ensureRequestedResources(Root root) {
        if (root == null || root.entries == null) return;
        ensureItem(root, "minecraft:beetroot_seeds", "§cBeetroot Seeds x16", 16, 100, "§7Starts beetroot farming progression.");
        ensureItem(root, "minecraft:oak_sapling", "§2Oak Saplings x4", 4, 150, "§7Renewable oak wood starter.");
        ensureItem(root, "minecraft:birch_sapling", "§fBirch Saplings x4", 4, 150, "§7Renewable birch wood starter.");
        ensureItem(root, "minecraft:spruce_sapling", "§2Spruce Saplings x4", 4, 150, "§7Renewable spruce wood starter.");
        ensureItem(root, "minecraft:jungle_sapling", "§aJungle Saplings x4", 4, 200, "§7Renewable jungle wood starter.");
        ensureItem(root, "minecraft:acacia_sapling", "§6Acacia Saplings x4", 4, 175, "§7Renewable acacia wood starter.");
        ensureItem(root, "minecraft:cherry_sapling", "§dCherry Saplings x4", 4, 225, "§7Renewable cherry wood starter.");
        ensureItem(root, "cobblemon:saccharine_sapling", "§dSaccharine Saplings x4", 4, 300, "§7Renewable Saccharine wood starter.");
        ensureItem(root, "cobblemon:tumblestone", "§cTumblestones x16", 16, 200, "§7Poké Ball crafting material.");
        ensureItem(root, "cobblemon:black_tumblestone", "§8Black Tumblestones x16", 16, 250, "§7Heavy Poké Ball crafting material.");
        ensureItem(root, "cobblemon:sky_tumblestone", "§bSky Tumblestones x16", 16, 250, "§7Feather Poké Ball crafting material.");
        ensureItem(root, "cobblemon:blue_mint_seeds", "§9Blue Mint Seeds x4", 4, 500, "§7Renewable mint crop starter.");
        ensureItem(root, "cobblemon:cyan_mint_seeds", "§bCyan Mint Seeds x4", 4, 500, "§7Renewable mint crop starter.");
        ensureItem(root, "cobblemon:green_mint_seeds", "§aGreen Mint Seeds x4", 4, 500, "§7Renewable mint crop starter.");
        ensureItem(root, "cobblemon:pink_mint_seeds", "§dPink Mint Seeds x4", 4, 500, "§7Renewable mint crop starter.");
        ensureItem(root, "cobblemon:red_mint_seeds", "§cRed Mint Seeds x4", 4, 500, "§7Renewable mint crop starter.");
        ensureItem(root, "cobblemon:white_mint_seeds", "§fWhite Mint Seeds x4", 4, 500, "§7Renewable mint crop starter.");
    }

    private static void ensureItem(Root root, String id, String name, int amount, long credits, String lore) {
        boolean exists = root.entries.stream().filter(java.util.Objects::nonNull).anyMatch(e -> id.equalsIgnoreCase(e.id));
        if (!exists) root.entries.add(item(-1, name, id, id, amount, credits, lore));
    }

    private static Root createDefault() {
        Root root = new Root();
        int slot = 10;
        root.entries.add(item(slot++, "§bIce x16", "minecraft:ice", "minecraft:ice", 16, 75, "§7Renewable water source material."));
        root.entries.add(item(slot++, "§9Kelp x16", "minecraft:kelp", "minecraft:kelp", 16, 50, "§7Starts water farms and bubble columns."));
        root.entries.add(item(slot++, "§aSugar Cane x16", "minecraft:sugar_cane", "minecraft:sugar_cane", 16, 50, "§7Paper, books, and early farms."));
        root.entries.add(item(slot++, "§2Cactus x16", "minecraft:cactus", "minecraft:cactus", 16, 50, "§7Green dye and cactus farms."));
        root.entries.add(item(slot++, "§aBamboo x16", "minecraft:bamboo", "minecraft:bamboo", 16, 50, "§7Scaffolding and renewable wood utility."));
        root.entries.add(item(slot++, "§2Moss Block x16", "minecraft:moss_block", "minecraft:moss_block", 16, 100, "§7Composting and natural block progression."));
        root = nextRow(root, 19);
        root.entries.add(item(19, "§6Pointed Dripstone x8", "minecraft:pointed_dripstone", "minecraft:pointed_dripstone", 8, 175, "§7Used to make lava renewable."));
        root.entries.add(item(20, "§6Magma Block x8", "minecraft:magma_block", "minecraft:magma_block", 8, 225, "§7Lava and nether progression ingredient."));
        root.entries.add(item(21, "§8Blackstone x32", "minecraft:blackstone", "minecraft:blackstone", 32, 150, "§7Nether-style building and recipes."));
        root.entries.add(item(22, "§4Netherrack x32", "minecraft:netherrack", "minecraft:netherrack", 32, 150, "§7Nether progression material."));
        root.entries.add(item(23, "§8Soul Sand x8", "minecraft:soul_sand", "minecraft:soul_sand", 8, 250, "§7Nether wart and water elevator utility."));
        root.entries.add(item(24, "§7Soul Soil x8", "minecraft:soul_soil", "minecraft:soul_soil", 8, 250, "§7Soul fire and nether utility."));
        root.entries.add(item(25, "§eGlowstone Dust x16", "minecraft:glowstone_dust", "minecraft:glowstone_dust", 16, 225, "§7Potion and lighting progression."));
        root.entries.add(item(28, "§5Amethyst Shard x8", "minecraft:amethyst_shard", "minecraft:amethyst_shard", 8, 300, "§7Spyglass, tint, and utility crafting."));
        root.entries.add(item(29, "§aSlime Ball x8", "minecraft:slime_ball", "minecraft:slime_ball", 8, 400, "§7Sticky pistons, leads, and redstone builds."));
        root.entries.add(item(30, "§eBlaze Powder x8", "minecraft:blaze_powder", "minecraft:blaze_powder", 8, 500, "§7Brewing and lava crafting ingredient."));
        root.entries.add(item(31, "§cNether Wart x8", "minecraft:nether_wart", "minecraft:nether_wart", 8, 600, "§7Starts brewing progression."));
        root.entries.add(item(32, "§7Cobweb x8", "minecraft:cobweb", "minecraft:cobweb", 8, 350, "§7String and decorative utility."));
        root.entries.add(item(33, "§6Name Tag", "minecraft:name_tag", "minecraft:name_tag", 1, 750, "§7Useful vanilla utility item."));
        root.entries.add(item(34, "§dOld Amber Fossil", "cobblemon:old_amber_fossil", "cobblemon:old_amber_fossil", 1, 2500, "§7Rare fossil safety valve for Islanders."));
        ensureApricornSeeds(root);
        ensureRequestedResources(root);
        return root;
    }

    private static Root nextRow(Root root, int ignored) { return root; }

    private static NpcShopConfig.ShopEntry item(int slot, String name, String icon, String id, int amount, long wholeCredits, String... lore) {
        NpcShopConfig.ShopEntry entry = new NpcShopConfig.ShopEntry();
        entry.type = "item";
        entry.slot = slot;
        entry.displayName = name;
        entry.icon = icon;
        entry.id = id;
        entry.amount = amount;
        entry.price = com.champutils.economy.EconomyManager.wholeCreditsToCents(wholeCredits);
        entry.lore = new ArrayList<>(List.of(lore));
        entry.lore.add("§8Islander safety-valve shop item.");
        return entry;
    }
}
