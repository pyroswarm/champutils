package com.champutils.emblem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EmblemConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/emblems.json");

    public static Root CONFIG = new Root();

    private EmblemConfig() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) createDefault();
            try (FileReader reader = new FileReader(FILE)) {
                Root loaded = GSON.fromJson(reader, Root.class);
                CONFIG = loaded == null ? new Root() : loaded;
            }
            normalize();
            save();
        } catch (Exception e) {
            e.printStackTrace();
            CONFIG = defaultRoot();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(CONFIG, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createDefault() throws Exception {
        CONFIG = defaultRoot();
        save();
    }

    private static void normalize() {
        if (CONFIG.emblems == null) CONFIG.emblems = new LinkedHashMap<>();
        Root defaults = defaultRoot();
        for (Map.Entry<String, EmblemData> entry : defaults.emblems.entrySet()) {
            CONFIG.emblems.putIfAbsent(entry.getKey(), entry.getValue());
        }
        if (CONFIG.legendarySpecies == null) CONFIG.legendarySpecies = defaults.legendarySpecies;
        if (CONFIG.ultraBeastSpecies == null) CONFIG.ultraBeastSpecies = defaults.ultraBeastSpecies;
        if (CONFIG.paradoxSpecies == null) CONFIG.paradoxSpecies = defaults.paradoxSpecies;
        if (CONFIG.megaCapableSpecies == null) CONFIG.megaCapableSpecies = defaults.megaCapableSpecies;
        if (CONFIG.megaStoneOverrides == null) CONFIG.megaStoneOverrides = new LinkedHashMap<>();
    }

    private static Root defaultRoot() {
        Root root = new Root();
        root.emblems.put("regular_shiny", emblem(
                "regular_shiny", "Regular Shiny Emblem", "REGULAR_SHINY", "minecraft:nether_star", 9101,
                "Turns one regular Pokémon shiny.", "RARE", 250,
                item("cobblemon:shiny_stone", 3), item("minecraft:diamond", 8)));
        root.emblems.put("ultra_paradox_shiny", emblem(
                "ultra_paradox_shiny", "Ultra/Paradox Shiny Emblem", "ULTRA_PARADOX_SHINY", "minecraft:nether_star", 9102,
                "Turns one Ultra Beast or Paradox Pokémon shiny.", "EPIC", 400,
                item("cobblemon:shiny_stone", 8), item("minecraft:netherite_ingot", 2)));
        root.emblems.put("legendary_shiny", emblem(
                "legendary_shiny", "Legendary Shiny Emblem", "LEGENDARY_SHINY", "minecraft:nether_star", 9103,
                "Turns one Legendary Pokémon shiny.", "LEGENDARY", 500,
                item("cobblemon:shiny_stone", 16), item("minecraft:netherite_block", 1)));
        root.emblems.put("megastone", emblem(
                "megastone", "Megastone Emblem", "MEGASTONE", "minecraft:amethyst_shard", 9104,
                "Right-click a Pokémon that has a Mega Evolution to receive its Mega Stone.", "EPIC", 300,
                item("minecraft:diamond_block", 2), item("minecraft:emerald_block", 2)));

        root.ultraBeastSpecies = set("nihilego","buzzwole","pheromosa","xurkitree","celesteela","kartana","guzzlord","poipole","naganadel","stakataka","blacephalon");
        root.paradoxSpecies = set("great_tusk","greattusk","scream_tail","screamtail","brute_bonnet","brutebonnet","flutter_mane","fluttermane","slither_wing","slitherwing","sandy_shocks","sandyshocks","roaring_moon","roaringmoon","walking_wake","walkingwake","gouging_fire","gougingfire","raging_bolt","ragingbolt","iron_treads","irontreads","iron_bundle","ironbundle","iron_hands","ironhands","iron_jugulis","ironjugulis","iron_moth","ironmoth","iron_thorns","ironthorns","iron_valiant","ironvaliant","iron_leaves","ironleaves","iron_boulder","ironboulder","iron_crown","ironcrown");
        root.legendarySpecies = set("articuno","zapdos","moltres","mewtwo","raikou","entei","suicune","lugia","ho_oh","hooh","regirock","regice","registeel","latias","latios","kyogre","groudon","rayquaza","uxie","mesprit","azelf","dialga","palkia","heatran","regigigas","giratina","cresselia","cobalion","terrakion","virizion","tornadus","thundurus","reshiram","zekrom","landorus","kyurem","xerneas","yveltal","zygarde","type_null","typenull","silvally","tapu_koko","tapukoko","tapu_lele","tapulele","tapu_bulu","tapubulu","tapu_fini","tapufini","cosmog","cosmoem","solgaleo","lunala","necrozma","zacian","zamazenta","eternatus","kubfu","urshifu","regieleki","regidrago","glastrier","spectrier","calyrex","enamorus","wo_chien","wochien","chien_pao","chienpao","ting_lu","tinglu","chi_yu","chiyu","okidogi","munkidori","fezandipiti","ogerpon","terapagos","koraidon","miraidon");
        root.megaCapableSpecies = set("venusaur","charizard","blastoise","beedrill","pidgeot","alakazam","slowbro","gengar","kangaskhan","pinsir","gyarados","aerodactyl","mewtwo","ampharos","steelix","scizor","heracross","houndoom","tyranitar","sceptile","blaziken","swampert","gardevoir","sableye","mawile","aggron","medicham","manectric","sharpedo","camerupt","altaria","banette","absol","glalie","salamence","metagross","latias","latios","lopunny","garchomp","lucario","abomasnow","gallade","audino","diancie","rayquaza");
        root.megaStoneItemPattern = "genesisforms:%species%ite";
        root.megaStoneOverrides.put("charizard", "genesisforms:charizardite_x");
        root.megaStoneOverrides.put("mewtwo", "genesisforms:mewtwonite_x");
        return root;
    }

    private static EmblemData emblem(String id, String name, String type, String base, int model, String lore, String fragment, int amount, ItemCost... costs) {
        EmblemData data = new EmblemData();
        data.id = id; data.displayName = name; data.type = type; data.baseItem = base; data.customModelData = model; data.lore = lore;
        data.fragment = fragment; data.fragmentCost = amount;
        for (ItemCost cost : costs) data.itemCosts.add(cost);
        return data;
    }

    private static ItemCost item(String id, int amount) { ItemCost c = new ItemCost(); c.item = id; c.amount = amount; return c; }
    private static Set<String> set(String... vals) { Set<String> s = new LinkedHashSet<>(); for (String v: vals) s.add(v); return s; }

    public static class Root {
        public Map<String, EmblemData> emblems = new LinkedHashMap<>();
        public Set<String> legendarySpecies = new LinkedHashSet<>();
        public Set<String> ultraBeastSpecies = new LinkedHashSet<>();
        public Set<String> paradoxSpecies = new LinkedHashSet<>();
        public Set<String> megaCapableSpecies = new LinkedHashSet<>();
        public String megaStoneItemPattern = "genesisforms:%species%ite";
        public Map<String, String> megaStoneOverrides = new LinkedHashMap<>();
    }

    public static class EmblemData {
        public String id;
        public String displayName;
        public String type;
        public String baseItem;
        public int customModelData;
        public String lore;
        public String fragment;
        public int fragmentCost;
        public List<ItemCost> itemCosts = new ArrayList<>();
    }

    public static class ItemCost {
        public String item;
        public int amount;
    }
}
