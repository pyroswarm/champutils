package com.champutils.crate;

import com.champutils.profession.ProfessionToolConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CrateConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Map<String, CrateDefinition> CRATES = new LinkedHashMap<>();

    private CrateConfig() {}


    private static final String[] LEGENDARY_SPECIES = {
            "articuno", "zapdos", "moltres", "mewtwo",
            "raikou", "entei", "suicune", "lugia", "ho_oh",
            "regirock", "regice", "registeel", "latias", "latios", "kyogre", "groudon", "rayquaza",
            "uxie", "mesprit", "azelf", "dialga", "palkia", "heatran", "regigigas", "giratina", "cresselia",
            "cobalion", "terrakion", "virizion", "tornadus", "thundurus", "reshiram", "zekrom", "landorus", "kyurem",
            "xerneas", "yveltal", "zygarde", "type_null", "silvally",
            "tapu_koko", "tapu_lele", "tapu_bulu", "tapu_fini",
            "cosmog", "cosmoem", "solgaleo", "lunala", "necrozma",
            "zacian", "zamazenta", "eternatus", "kubfu", "urshifu", "regieleki", "regidrago", "glastrier", "spectrier", "calyrex",
            "enamorus", "wo_chien", "chien_pao", "ting_lu", "chi_yu",
            "okidogi", "munkidori", "fezandipiti", "ogerpon", "terapagos", "koraidon", "miraidon"
    };

    private static final String[] MYTHICAL_SPECIES = {
            "mew", "celebi", "jirachi", "deoxys", "phione", "manaphy", "darkrai", "shaymin", "arceus",
            "victini", "keldeo", "meloetta", "genesect", "diancie", "hoopa", "volcanion", "magearna",
            "marshadow", "zeraora", "meltan", "melmetal", "zarude", "pecharunt"
    };

    private static final String[] ULTRA_BEAST_SPECIES = {
            "nihilego", "buzzwole", "pheromosa", "xurkitree", "celesteela", "kartana", "guzzlord",
            "poipole", "naganadel", "stakataka", "blacephalon"
    };

    private static final String[] PARADOX_SPECIES = {
            "great_tusk", "scream_tail", "brute_bonnet", "flutter_mane", "slither_wing", "sandy_shocks", "roaring_moon",
            "walking_wake", "gouging_fire", "raging_bolt",
            "iron_treads", "iron_bundle", "iron_hands", "iron_jugulis", "iron_moth", "iron_thorns", "iron_valiant",
            "iron_leaves", "iron_boulder", "iron_crown"
    };

    private static final String[] STRONG_FILLER_SPECIES = {
            "larvitar", "bagon", "beldum", "gible", "dratini", "axew", "goomy", "deino", "dreepy",
            "charizard", "dragonite", "metagross", "tyranitar", "garchomp", "hydreigon", "dragapult",
            "lucario", "absol", "zoroark", "arcanine", "gyarados", "milotic", "rotom", "eevee", "riolu"
    };

    private static final String[] MID_FILLER_SPECIES = {
            "pidgeot", "raichu", "nidoking", "nidoqueen", "lapras", "snorlax", "heracross", "scizor",
            "houndoom", "flygon", "aggron", "altaria", "luxray", "staraptor", "toxicroak", "krookodile",
            "talonflame", "lycanroc", "corviknight", "toxtricity", "annihilape", "clodsire"
    };

    public static class Root { public Map<String, CrateDefinition> crates = new LinkedHashMap<>(); }
    public static class CrateDefinition {
        public boolean enabled = true;
        public String displayName = "Common Crate";
        public String iconItem = "minecraft:chest";
        public String guaranteedShardRarity = "COMMON";
        public int guaranteedShardMin = 1;
        public int guaranteedShardMax = 3;
        public int minPokemonLevel = 5;
        public int maxPokemonLevel = 20;
        public double shinyChance = 0.05D;
        public List<WeightedPokemon> pokemon = new ArrayList<>();
        public List<WeightedItem> items = new ArrayList<>();
        public List<WeightedTool> tools = new ArrayList<>();
    }
    public static class WeightedPokemon { public String species; public int weight; public String pool = "REGULAR"; public WeightedPokemon() {} public WeightedPokemon(String s, int w) { species=s; weight=w; } }
    public static class WeightedItem { public String itemId; public int amountMin=1; public int amountMax=1; public int weight; public WeightedItem() {} public WeightedItem(String id, int min, int max, int w) { itemId=id; amountMin=min; amountMax=max; weight=w; } }
    public static class WeightedTool { public String toolId; public int weight; public WeightedTool() {} public WeightedTool(String id, int w) { toolId=id; weight=w; } }

    public static void load() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "crates.json");
            if (!file.exists()) createDefault(file);
            try (FileReader reader = new FileReader(file)) {
                Root root = GSON.fromJson(reader, Root.class);
                Root defaults = defaultRoot();
                if (root == null || root.crates == null || root.crates.isEmpty()) {
                    CRATES = defaults.crates;
                } else {
                    for (Map.Entry<String, CrateDefinition> entry : defaults.crates.entrySet()) {
                        root.crates.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                    upgradeNewCrates(root.crates, defaults);
                    applyGildedChestIcons(root.crates);
                    applySeasonCrateBalance(root.crates);
                    applyLegendaryMythicHighValueOnly(root.crates);
                    applyProfessionToolLootPools(root.crates);
                    CRATES = root.crates;
                }
                applyGildedChestIcons(CRATES);
                applySeasonCrateBalance(CRATES);
                applyLegendaryMythicHighValueOnly(CRATES);
                applyProfessionToolLootPools(CRATES);
                // Event crate was removed. World events now award regular crate credits by event tier.
                CRATES.remove("event");
                Root saved = new Root();
                saved.crates = CRATES;
                try (FileWriter writer = new FileWriter(file)) {
                    GSON.toJson(saved, writer);
                }
            }
            System.out.println("[ChampUtils] Loaded " + CRATES.size() + " crate definitions.");
        } catch (Exception e) {
            e.printStackTrace();
            CRATES = defaultRoot().crates;
        }
    }

    private static void applyGildedChestIcons(Map<String, CrateDefinition> crates) {
        if (crates == null) return;
        setIcon(crates, "common", "cobblemon:gilded_chest");
        setIcon(crates, "uncommon", "cobblemon:yellow_gilded_chest");
        setIcon(crates, "rare", "cobblemon:green_gilded_chest");
        setIcon(crates, "epic", "cobblemon:blue_gilded_chest");
        setIcon(crates, "legendary", "cobblemon:pink_gilded_chest");
        setIcon(crates, "mythic", "cobblemon:black_gilded_chest");
        setIcon(crates, "guild", "cobblemon:white_gilded_chest");
        setIcon(crates, "world_boss", "cobblemon:white_gilded_chest");
    }

    private static void setIcon(Map<String, CrateDefinition> crates, String id, String iconItem) {
        CrateDefinition crate = crates.get(id);
        if (crate != null) crate.iconItem = iconItem;
    }

    private static void upgradeNewCrates(Map<String, CrateDefinition> loaded, Root defaults) {
        if (loaded == null || defaults == null || defaults.crates == null) return;
        String[] ids = {"epic", "legendary", "mythic", "guild", "world_boss"};
        for (String id : ids) {
            CrateDefinition updated = defaults.crates.get(id);
            if (updated != null) loaded.put(id, updated);
        }
    }

    public static CrateDefinition getCrate(String id) { if (CRATES.isEmpty()) load(); return CRATES.get(CrateCreditManager.normalize(id)); }
    private static void createDefault(File file) { try (FileWriter writer = new FileWriter(file)) { GSON.toJson(defaultRoot(), writer); } catch (Exception e) { e.printStackTrace(); } }

    private static Root defaultRoot() {
        Root root = new Root();
        add(root,"common","Common Crate","cobblemon:gilded_chest","COMMON",1,3,5,20,0.05D,
                listP("pidgey:35","rattata:35","caterpie:25","weedle:25","zigzagoon:25","bidoof:20","sentret:20","wurmple:20","patrat:15","poochyena:15"),
                listI("cobblemon:poke_ball:4:10:45","cobblemon:potion:2:5:30","cobblemon:oran_berry:3:8:25"),
                listT("rookies_pick:2","woodcleaver:1","gaias_blessing:1"));

        add(root,"uncommon","Uncommon Crate","cobblemon:yellow_gilded_chest","UNCOMMON",2,4,15,35,0.12D,
                listP("pidgey:8","rattata:8","eevee:16","growlithe:14","magikarp:16","shinx:14","riolu:10","mareep:14","sandile:10","starly:12"),
                listI("cobblemon:great_ball:3:8:35","cobblemon:super_potion:2:5:25","cobblemon:exp_candy_s:2:5:20","cobblemon:link_cable:1:1:5"),
                listT("rookies_pick:3","woodcleaver:2","gaias_blessing:2"));

        add(root,"rare","Rare Crate","cobblemon:green_gilded_chest","RARE",3,6,25,50,0.25D,
                listP("eevee:8","riolu:8","larvitar:8","bagon:8","beldum:8","gible:8","dratini:8","axew:7","goomy:7","deino:7","rotom:6"),
                listI("cobblemon:ultra_ball:3:8:30","cobblemon:rare_candy:1:3:18","cobblemon:exp_candy_m:2:5:20","cobblemon:ability_capsule:1:1:8","cobblemon:choice_scarf:1:1:4"),
                listT("miners_fang:3","woodcleaver:3","gaias_blessing:2"));

        add(root,"epic","Epic Crate","cobblemon:blue_gilded_chest","EPIC",4,8,40,65,0.5D,
                mergeP(
                        weighted(STRONG_FILLER_SPECIES, 4, "REGULAR"),
                        weighted(MID_FILLER_SPECIES, 2, "REGULAR"),
                        weighted(new String[]{"roaring_moon","iron_valiant","iron_hands","flutter_mane","great_tusk","kartana","buzzwole","guzzlord"}, 2, null),
                        weighted(new String[]{"articuno","zapdos","moltres","raikou","entei","suicune","regirock","regice","registeel","latias","latios"}, 1, null)
                ),
                listI(
                        "cobblemon:ultra_ball:5:12:24","cobblemon:luxury_ball:3:8:14","cobblemon:rare_candy:2:5:18","cobblemon:exp_candy_l:2:5:18","cobblemon:exp_candy_xl:1:3:9",
                        "cobblemon:ability_capsule:1:1:10","cobblemon:ability_patch:1:1:8","cobblemon:leftovers:1:1:6","cobblemon:life_orb:1:1:5","cobblemon:choice_band:1:1:4","cobblemon:choice_specs:1:1:4","cobblemon:choice_scarf:1:1:4","cobblemon:focus_sash:1:1:4","cobblemon:master_ball:1:1:1","minecraft:diamond:2:6:8"),
                listT("miners_fang:4","deep_prospector:3","cavern_breaker:2","woodcleaver:4","worldtree_axe:2","gaias_blessing:3"));

        add(root,"legendary","Legendary Crate","cobblemon:pink_gilded_chest","LEGENDARY",6,12,65,90,2.5D,
                mergeP(
                        weighted(LEGENDARY_SPECIES, 1, "LEGENDARY"),
                        weighted(MYTHICAL_SPECIES, 1, "MYTHICAL"),
                        weighted(ULTRA_BEAST_SPECIES, 2, "ULTRA_BEAST")
                ),
                listI(
                        "cobblemon:master_ball:1:1:4","cobblemon:dream_ball:2:4:12","cobblemon:beast_ball:2:4:12","cobblemon:rare_candy:4:8:18","cobblemon:exp_candy_xl:2:6:16",
                        "cobblemon:ability_patch:1:2:10","cobblemon:leftovers:1:1:8","cobblemon:life_orb:1:1:8","cobblemon:choice_band:1:1:6","cobblemon:choice_specs:1:1:6","cobblemon:choice_scarf:1:1:6","cobblemon:focus_sash:1:1:6","cobblemon:eviolite:1:1:5","cobblemon:heavy_duty_boots:1:1:5","minecraft:netherite_ingot:1:2:6"),
                listT("lodestone_maw:3","treasure_seer:2","obsidian_edge:2","titanbreaker:2","worldtree_axe:3","gaias_blessing:3"));

        add(root,"mythic","Mythic Crate","cobblemon:black_gilded_chest","MYTHIC",10,18,75,100,25.0D,
                mergeP(
                        weighted(LEGENDARY_SPECIES, 2, "LEGENDARY"),
                        weighted(MYTHICAL_SPECIES, 2, "MYTHICAL"),
                        weighted(ULTRA_BEAST_SPECIES, 3, "ULTRA_BEAST")
                ),
                listI(
                        "cobblemon:master_ball:1:2:10","cobblemon:dream_ball:3:6:16","cobblemon:beast_ball:3:6:16","cobblemon:rare_candy:6:12:18","cobblemon:exp_candy_xl:4:10:16",
                        "cobblemon:ability_patch:1:3:12","cobblemon:leftovers:1:1:7","cobblemon:life_orb:1:1:7","cobblemon:choice_band:1:1:6","cobblemon:choice_specs:1:1:6","cobblemon:choice_scarf:1:1:6","cobblemon:focus_sash:1:1:6","cobblemon:eviolite:1:1:4","cobblemon:heavy_duty_boots:1:1:4","minecraft:netherite_block:1:1:3"),
                listT("starfall:2","void_rift:2","infernal_core:2","vein_reaper:1","titanbreaker:2","worldtree_axe:2","gaias_blessing:2"));

        add(root,"guild","Guild Crate","cobblemon:white_gilded_chest","EPIC",4,8,40,65,0.5D,
                mergeP(
                        weighted(STRONG_FILLER_SPECIES, 4, "REGULAR"),
                        weighted(MID_FILLER_SPECIES, 2, "REGULAR"),
                        weighted(new String[]{"roaring_moon","iron_valiant","iron_hands","flutter_mane","great_tusk","kartana","buzzwole","guzzlord"}, 2, null),
                        weighted(new String[]{"articuno","zapdos","moltres","raikou","entei","suicune"}, 1, null)
                ),
                listI("cobblemon:ultra_ball:5:12:24","cobblemon:luxury_ball:3:8:14","cobblemon:rare_candy:2:5:18","cobblemon:exp_candy_l:2:5:18","cobblemon:ability_capsule:1:1:10","cobblemon:ability_patch:1:1:8","cobblemon:master_ball:1:1:1"),
                listT("deep_prospector:3","cavern_breaker:2","woodcleaver:3","worldtree_axe:2","gaias_blessing:3"));

        add(root,"world_boss","World Boss Crate","cobblemon:white_gilded_chest","LEGENDARY",6,12,65,90,2.5D,
                mergeP(
                        weighted(LEGENDARY_SPECIES, 1, "LEGENDARY"),
                        weighted(MYTHICAL_SPECIES, 1, "MYTHICAL"),
                        weighted(ULTRA_BEAST_SPECIES, 2, "ULTRA_BEAST")
                ),
                listI("cobblemon:master_ball:1:1:5","cobblemon:dream_ball:2:4:12","cobblemon:beast_ball:2:4:12","cobblemon:rare_candy:4:8:18","cobblemon:exp_candy_xl:2:6:16","cobblemon:ability_patch:1:2:10","cobblemon:leftovers:1:1:8","cobblemon:life_orb:1:1:8","cobblemon:choice_band:1:1:6","cobblemon:choice_specs:1:1:6","cobblemon:choice_scarf:1:1:6"),
                listT("lodestone_maw:3","treasure_seer:2","obsidian_edge:2","titanbreaker:2","worldtree_axe:3","gaias_blessing:3"));
        applySeasonCrateBalance(root.crates);
        applyProfessionToolLootPools(root.crates);
        return root;
    }


    private static void applyProfessionToolLootPools(Map<String, CrateDefinition> crates) {
        if (crates == null || crates.isEmpty()) return;
        if (ProfessionToolConfig.TOOLS == null || ProfessionToolConfig.TOOLS.isEmpty()) {
            try { ProfessionToolConfig.load(); } catch (Throwable ignored) {}
        }
        if (ProfessionToolConfig.TOOLS == null || ProfessionToolConfig.TOOLS.isEmpty()) return;

        for (Map.Entry<String, CrateDefinition> crateEntry : crates.entrySet()) {
            CrateDefinition crate = crateEntry.getValue();
            if (crate == null) continue;
            String crateRarity = normalizeRarity(crate.guaranteedShardRarity);
            for (Map.Entry<String, ProfessionToolConfig.ToolData> toolEntry : ProfessionToolConfig.TOOLS.entrySet()) {
                String toolId = toolEntry.getKey();
                ProfessionToolConfig.ToolData toolData = toolEntry.getValue();
                if (toolId == null || toolId.isBlank() || toolData == null) continue;
                if (!crateRarity.equals(normalizeRarity(toolData.rarity))) continue;
                addToolOnce(crate, toolId, defaultToolWeight(crateRarity, toolData));
            }
        }
    }

    private static int defaultToolWeight(String rarity, ProfessionToolConfig.ToolData toolData) {
        String base = toolData == null || toolData.baseItem == null ? "" : toolData.baseItem.toLowerCase();
        boolean shovel = base.contains("shovel");
        return switch (normalizeRarity(rarity)) {
            case "COMMON" -> shovel ? 2 : 1;
            case "UNCOMMON" -> shovel ? 2 : 2;
            case "RARE" -> shovel ? 3 : 3;
            case "EPIC" -> shovel ? 3 : 3;
            case "LEGENDARY" -> shovel ? 2 : 2;
            case "MYTHIC" -> shovel ? 2 : 2;
            default -> 1;
        };
    }

    private static void applySeasonCrateBalance(Map<String, CrateDefinition> crates) {
        if (crates == null) return;
        addTierTm(crates.get("common"), "COMMON", 10);
        addTierTm(crates.get("uncommon"), "UNCOMMON", 10);
        addTierTm(crates.get("rare"), "RARE", 10);
        addTierTm(crates.get("epic"), "EPIC", 10);
        addTierTm(crates.get("legendary"), "LEGENDARY", 14);
        CrateDefinition legendary = crates.get("legendary");
        if (legendary != null) {
            addItemOnce(legendary, "cobblemon:ability_patch", 1, 2, 16);
            addItemOnce(legendary, "cobblemon:master_ball", 1, 1, 6);
        }
        CrateDefinition mythic = crates.get("mythic");
        if (mythic != null) {
            mythic.shinyChance = 25.0D;
            mythic.items = new ArrayList<>();
            mythic.items.add(new WeightedItem("cobblemon:ability_patch", 1, 3, 35));
            mythic.items.add(new WeightedItem("cobblemon:master_ball", 1, 2, 25));
            mythic.items.add(new WeightedItem("champutils:random_tm_mythic", 1, 1, 40));

            // Rebuild the Mythic tool pool from the loaded profession tool config.
            // applyProfessionToolLootPools runs after this and adds every valid Mythic
            // pickaxe/axe/hoe/shovel, avoiding stale hard-coded IDs that get skipped.
            mythic.tools = new ArrayList<>();
        }
    }

    private static void applyLegendaryMythicHighValueOnly(Map<String, CrateDefinition> crates) {
        if (crates == null) return;
        keepOnlyPremiumPokemon(crates.get("legendary"));
        keepOnlyPremiumPokemon(crates.get("mythic"));
    }

    private static void keepOnlyPremiumPokemon(CrateDefinition crate) {
        if (crate == null || crate.pokemon == null) return;
        crate.pokemon.removeIf(p -> {
            if (p == null) return true;
            String pool = p.pool == null ? classifyPool(p.species) : p.pool.trim().toUpperCase(java.util.Locale.ROOT);
            return !(pool.contains("LEGEND") || pool.contains("MYTH") || pool.contains("ULTRA"));
        });
        if (crate.pokemon.isEmpty()) {
            crate.pokemon.addAll(weighted(LEGENDARY_SPECIES, 1, "LEGENDARY"));
            crate.pokemon.addAll(weighted(MYTHICAL_SPECIES, 1, "MYTHICAL"));
            crate.pokemon.addAll(weighted(ULTRA_BEAST_SPECIES, 2, "ULTRA_BEAST"));
        }
    }

    private static void addToolOnce(CrateDefinition crate, String toolId, int weight) {
        if (crate == null || toolId == null || toolId.isBlank() || weight <= 0) return;
        if (crate.tools == null) crate.tools = new ArrayList<>();
        for (WeightedTool tool : crate.tools) {
            if (tool != null && toolId.equalsIgnoreCase(tool.toolId)) {
                tool.weight = Math.max(tool.weight, weight);
                return;
            }
        }
        crate.tools.add(new WeightedTool(toolId, weight));
    }

    private static void addTierTm(CrateDefinition crate, String rarity, int weight) {
        if (crate == null) return;
        addItemOnce(crate, "champutils:random_tm_" + rarity.toLowerCase(), 1, 1, weight);
    }

    private static void addItemOnce(CrateDefinition crate, String itemId, int min, int max, int weight) {
        if (crate.items == null) crate.items = new ArrayList<>();
        for (WeightedItem item : crate.items) {
            if (item != null && itemId.equalsIgnoreCase(item.itemId)) {
                item.amountMin = min; item.amountMax = max; item.weight = Math.max(item.weight, weight);
                return;
            }
        }
        crate.items.add(new WeightedItem(itemId, min, max, weight));
    }

    private static void add(Root r,String id,String name,String icon,String shard,int sMin,int sMax,int lMin,int lMax,double shiny,List<WeightedPokemon> p,List<WeightedItem> i,List<WeightedTool> t){
        CrateDefinition c=new CrateDefinition(); c.displayName=name; c.iconItem=icon; c.guaranteedShardRarity=shard; c.guaranteedShardMin=sMin; c.guaranteedShardMax=sMax; c.minPokemonLevel=lMin; c.maxPokemonLevel=lMax; c.shinyChance=shiny; c.pokemon=p; c.items=i; c.tools=t; r.crates.put(id,c);
    }
    private static List<WeightedPokemon> listP(String... vals){
        List<WeightedPokemon> out=new ArrayList<>();
        for(String v:vals){
            String[] p=v.split(":");
            WeightedPokemon wp = new WeightedPokemon(p[0], Integer.parseInt(p[1]));
            wp.pool = p.length >= 3 ? p[2] : classifyPool(p[0]);
            out.add(wp);
        }
        return out;
    }

    @SafeVarargs
    private static List<WeightedPokemon> mergeP(List<WeightedPokemon>... lists){
        List<WeightedPokemon> out = new ArrayList<>();
        for (List<WeightedPokemon> list : lists) if (list != null) out.addAll(list);
        return out;
    }

    private static List<WeightedPokemon> weighted(String[] species, int weight, String pool){
        List<WeightedPokemon> out = new ArrayList<>();
        if (species == null) return out;
        for (String s : species) {
            WeightedPokemon wp = new WeightedPokemon(s, weight);
            wp.pool = pool == null ? classifyPool(s) : pool;
            out.add(wp);
        }
        return out;
    }

    private static String classifyPool(String species){
        String s = normalizeSpecies(species);
        for (String value : ULTRA_BEAST_SPECIES) if (normalizeSpecies(value).equals(s)) return "ULTRA_BEAST";
        for (String value : PARADOX_SPECIES) if (normalizeSpecies(value).equals(s)) return "PARADOX";
        for (String value : MYTHICAL_SPECIES) if (normalizeSpecies(value).equals(s)) return "MYTHICAL";
        for (String value : LEGENDARY_SPECIES) if (normalizeSpecies(value).equals(s)) return "LEGENDARY";
        return "REGULAR";
    }

    private static String normalizeSpecies(String raw){
        if (raw == null) return "";
        String s = raw.toLowerCase().trim();
        int colon = s.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < s.length()) s = s.substring(colon + 1);
        return s.replace('-', '_').replace(' ', '_').replace(".", "_");
    }

    private static String normalizeRarity(String rarity) {
        if (rarity == null || rarity.isBlank()) return "COMMON";
        return rarity.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static List<WeightedItem> listI(String... vals){ List<WeightedItem> out=new ArrayList<>(); for(String v:vals){String[] p=v.split(":"); out.add(new WeightedItem(p[0]+":"+p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])));} return out; }
    private static List<WeightedTool> listT(String... vals){ List<WeightedTool> out=new ArrayList<>(); for(String v:vals){String[] p=v.split(":"); out.add(new WeightedTool(p[0], Integer.parseInt(p[1])));} return out; }
}
