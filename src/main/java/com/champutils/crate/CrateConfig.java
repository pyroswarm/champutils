package com.champutils.crate;

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
                    CRATES = root.crates;
                }
                // Event crate was removed. World events now award regular crate credits by event tier.
                CRATES.remove("event");
            }
            System.out.println("[ChampUtils] Loaded " + CRATES.size() + " crate definitions.");
        } catch (Exception e) {
            e.printStackTrace();
            CRATES = defaultRoot().crates;
        }
    }

    public static CrateDefinition getCrate(String id) { if (CRATES.isEmpty()) load(); return CRATES.get(CrateCreditManager.normalize(id)); }
    private static void createDefault(File file) { try (FileWriter writer = new FileWriter(file)) { GSON.toJson(defaultRoot(), writer); } catch (Exception e) { e.printStackTrace(); } }

    private static Root defaultRoot() {
        Root root = new Root();
        add(root,"common","Common Crate","minecraft:chest","COMMON",1,3,5,20,0.05D,
                listP("pidgey:35","rattata:35","caterpie:25","weedle:25","zigzagoon:25","bidoof:20","sentret:20","wurmple:20","patrat:15","poochyena:15"),
                listI("cobblemon:poke_ball:4:10:45","cobblemon:potion:2:5:30","cobblemon:oran_berry:3:8:25"), listT("rookies_pick:2"));
        add(root,"uncommon","Uncommon Crate","minecraft:barrel","UNCOMMON",2,4,15,35,0.12D,
                listP("pidgey:8","rattata:8","eevee:16","growlithe:14","magikarp:16","shinx:14","riolu:10","mareep:14","sandile:10","starly:12"),
                listI("cobblemon:great_ball:3:8:35","cobblemon:super_potion:2:5:25","cobblemon:exp_candy_s:2:5:20","cobblemon:link_cable:1:1:5"), listT("rookies_pick:3","grove_cleaver:2"));
        add(root,"rare","Rare Crate","minecraft:ender_chest","RARE",3,6,25,50,0.25D,
                listP("eevee:8","riolu:8","larvitar:8","bagon:8","beldum:8","gible:8","dratini:8","axew:7","goomy:7","deino:7","rotom:6"),
                listI("cobblemon:ultra_ball:3:8:30","cobblemon:rare_candy:1:3:18","cobblemon:exp_candy_m:2:5:20","cobblemon:ability_capsule:1:1:8","cobblemon:choice_scarf:1:1:4"), listT("miners_fang:3","grove_cleaver:3"));
        add(root,"epic","Epic Crate","minecraft:shulker_box","EPIC",4,8,40,65,0.5D,
                listP("larvitar:3","bagon:3","beldum:3","gible:3","dratini:3","axew:3","goomy:3","deino:3","dreepy:3","charizard:2","metagross:2","dragonite:2"),
                listI("cobblemon:ultra_ball:5:12:24","cobblemon:rare_candy:2:5:18","cobblemon:ability_patch:1:1:8","cobblemon:master_ball:1:1:1","minecraft:diamond:2:6:8"), listT("miners_fang:4","deep_prospector:3","cavern_breaker:2"));
        add(root,"legendary","Legendary Crate","minecraft:nether_star","LEGENDARY",5,10,60,80,0.9D,
                listP("larvitar:2","bagon:2","beldum:2","gible:2","mewtwo:1","rayquaza:1","kyogre:1","groudon:1","xerneas:1","zacian:1","iron_valiant:1","roaring_moon:1","kartana:1"),
                listI("cobblemon:master_ball:1:1:4","cobblemon:dream_ball:2:4:12","cobblemon:beast_ball:2:4:12","cobblemon:rare_candy:4:8:18","minecraft:netherite_ingot:1:2:6"), listT("lodestone_maw:3","treasure_seer:2","obsidian_edge:2"));
        add(root,"mythic","Mythic Crate","minecraft:dragon_egg","MYTHIC",8,14,70,100,1.5D,
                listP("mewtwo:3","rayquaza:3","kyogre:2","groudon:2","lugia:2","ho_oh:2","zacian:2","zamazenta:2","koraidon:2","miraidon:2","iron_valiant:2","roaring_moon:2","kartana:2","guzzlord:1","mew:1"),
                listI("cobblemon:master_ball:1:2:10","cobblemon:dream_ball:3:6:16","cobblemon:beast_ball:3:6:16","cobblemon:rare_candy:6:12:18","minecraft:netherite_block:1:1:3"), listT("starfall:2","void_rift:2","infernal_core:2","vein_reaper:1"));
        add(root,"guild","Guild Crate","minecraft:bell","EPIC",4,8,40,65,0.5D,
                listP("riolu:4","larvitar:4","bagon:4","beldum:4","gible:4","rotom:3","dragonite:2","metagross:2"),
                listI("cobblemon:ultra_ball:5:12:24","cobblemon:rare_candy:2:5:18","cobblemon:ability_patch:1:1:8","cobblemon:master_ball:1:1:1"), listT("deep_prospector:3","cavern_breaker:2"));
        add(root,"world_boss","World Boss Crate","minecraft:beacon","LEGENDARY",5,10,60,80,0.9D,
                listP("mewtwo:1","rayquaza:1","kyogre:1","groudon:1","xerneas:1","zacian:1","iron_valiant:1","roaring_moon:1","kartana:1","guzzlord:1","larvitar:2","bagon:2","beldum:2"),
                listI("cobblemon:master_ball:1:1:5","cobblemon:dream_ball:2:4:12","cobblemon:beast_ball:2:4:12","cobblemon:rare_candy:4:8:18"), listT("lodestone_maw:3","treasure_seer:2","obsidian_edge:2"));
        return root;
    }

    private static void add(Root r,String id,String name,String icon,String shard,int sMin,int sMax,int lMin,int lMax,double shiny,List<WeightedPokemon> p,List<WeightedItem> i,List<WeightedTool> t){
        CrateDefinition c=new CrateDefinition(); c.displayName=name; c.iconItem=icon; c.guaranteedShardRarity=shard; c.guaranteedShardMin=sMin; c.guaranteedShardMax=sMax; c.minPokemonLevel=lMin; c.maxPokemonLevel=lMax; c.shinyChance=shiny; c.pokemon=p; c.items=i; c.tools=t; r.crates.put(id,c);
    }
    private static List<WeightedPokemon> listP(String... vals){ List<WeightedPokemon> out=new ArrayList<>(); for(String v:vals){String[] p=v.split(":"); out.add(new WeightedPokemon(p[0], Integer.parseInt(p[1])));} return out; }
    private static List<WeightedItem> listI(String... vals){ List<WeightedItem> out=new ArrayList<>(); for(String v:vals){String[] p=v.split(":"); out.add(new WeightedItem(p[0]+":"+p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])));} return out; }
    private static List<WeightedTool> listT(String... vals){ List<WeightedTool> out=new ArrayList<>(); for(String v:vals){String[] p=v.split(":"); out.add(new WeightedTool(p[0], Integer.parseInt(p[1])));} return out; }
}
