package com.champutils.rank;

import com.champutils.economy.EconomyManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class RankedTokenConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/ranked_tokens.json");
    public static Config CONFIG = defaults();
    private RankedTokenConfig() {}

    public static class Config {
        public int tokensPerRankedWin = 2;
        public int dailyTokenCap = 20;
        public int sameOpponentCooldownHours = 1;
        public int immediateForfeitSeconds = 90;
        public long immediateForfeitWinnerCredits = EconomyManager.wholeCreditsToCents(25L);
        public long rankedParticipationCredits = EconomyManager.wholeCreditsToCents(75L);
        public long rankedWinBonusCredits = EconomyManager.wholeCreditsToCents(175L);
        public long rankedFirstWinOfDayCredits = EconomyManager.wholeCreditsToCents(300L);
        public long rankedUpsetWinBonusCredits = EconomyManager.wholeCreditsToCents(100L);
        public long rankedWinStreakBonusCredits = EconomyManager.wholeCreditsToCents(25L);
        public int rankedWinStreakBonusCap = 4;
        public List<PokemonEntry> pokemon = new ArrayList<>();
        public List<ItemEntry> items = new ArrayList<>();
    }
    public static class PokemonEntry { public String species; public int cost = 100; public PokemonEntry() {} public PokemonEntry(String s, int c) { species=s; cost=c; } }
    public static class ItemEntry { public String item; public String displayName; public int cost; public int amount = 1; public ItemEntry() {} public ItemEntry(String i,String d,int c,int a){item=i;displayName=d;cost=c;amount=a;} }

    public static void load() {
        try {
            File parent = FILE.getParentFile(); if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) { CONFIG = defaults(); save(); return; }
            try (FileReader reader = new FileReader(FILE)) { Config loaded = GSON.fromJson(reader, Config.class); CONFIG = merge(loaded == null ? defaults() : loaded); }
            save();
        } catch (Exception e) { e.printStackTrace(); CONFIG = defaults(); }
    }
    public static void save() { try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(CONFIG, writer); } catch (Exception e) { e.printStackTrace(); } }
    private static Config merge(Config c) {
        Config d = defaults();
        if (c.tokensPerRankedWin <= 0) c.tokensPerRankedWin = d.tokensPerRankedWin;
        if (c.dailyTokenCap <= 0) c.dailyTokenCap = d.dailyTokenCap;
        if (c.sameOpponentCooldownHours < 0) c.sameOpponentCooldownHours = d.sameOpponentCooldownHours;
        if (c.immediateForfeitSeconds < 15) c.immediateForfeitSeconds = d.immediateForfeitSeconds;
        if (c.immediateForfeitWinnerCredits <= 0L) c.immediateForfeitWinnerCredits = d.immediateForfeitWinnerCredits;
        if (c.rankedParticipationCredits <= 0L) c.rankedParticipationCredits = d.rankedParticipationCredits;
        if (c.rankedWinBonusCredits <= 0L) c.rankedWinBonusCredits = d.rankedWinBonusCredits;
        if (c.rankedFirstWinOfDayCredits < 0L) c.rankedFirstWinOfDayCredits = d.rankedFirstWinOfDayCredits;
        if (c.rankedUpsetWinBonusCredits < 0L) c.rankedUpsetWinBonusCredits = d.rankedUpsetWinBonusCredits;
        if (c.rankedWinStreakBonusCredits < 0L) c.rankedWinStreakBonusCredits = d.rankedWinStreakBonusCredits;
        if (c.rankedWinStreakBonusCap < 0) c.rankedWinStreakBonusCap = d.rankedWinStreakBonusCap;
        if (c.pokemon == null || c.pokemon.isEmpty()) c.pokemon = d.pokemon;
        if (c.items == null || c.items.isEmpty()) {
            c.items = d.items;
        } else {
            migrateBottleCapItems(c);
            addMissingDefaultItems(c, d);
        }
        return c;
    }

    private static void migrateBottleCapItems(Config c) {
        for (ItemEntry entry : c.items) {
            if (entry == null || entry.item == null) continue;
            String normalized = entry.item.trim().toLowerCase().replace('-', '_');
            if (normalized.equals("cobblemon:gold_bottle_cap") || normalized.equals("cobblemon:golden_bottle_cap") || normalized.equals("bottlecaps:gold_bottle_cap")) {
                entry.item = "bottlecaps:golden_bottle_cap";
                if (entry.displayName == null || entry.displayName.isBlank() || entry.displayName.equals(entry.item)) entry.displayName = "Golden Bottle Cap";
            } else if (normalized.equals("cobblemon:bottle_cap") || normalized.equals("cobblemon:silver_bottle_cap") || normalized.equals("bottlecaps:bottle_cap") || normalized.equals("bottlecaps:silver_bottle_cap")) {
                entry.item = "bottlecaps:silver_bottle_cap_atk";
                if (entry.displayName == null || entry.displayName.isBlank() || entry.displayName.equals(entry.item) || entry.displayName.toLowerCase().contains("silver bottle cap")) entry.displayName = "Attack Bottle Cap";
            }
        }
    }

    private static void addMissingDefaultItems(Config c, Config defaults) {
        for (ItemEntry defaultEntry : defaults.items) {
            if (defaultEntry == null || defaultEntry.item == null) continue;
            String wanted = normalizeItemKey(defaultEntry.item);
            boolean exists = c.items.stream()
                    .filter(e -> e != null && e.item != null)
                    .anyMatch(e -> normalizeItemKey(e.item).equals(wanted));
            if (!exists) c.items.add(defaultEntry);
        }
    }

    private static String normalizeItemKey(String item) {
        if (item == null) return "";
        String value = item.trim().toLowerCase().replace('-', '_');
        if (value.equals("cobblemon:gold_bottle_cap") || value.equals("bottlecaps:gold_bottle_cap")) return "bottlecaps:golden_bottle_cap";
        if (value.equals("cobblemon:bottle_cap") || value.equals("cobblemon:silver_bottle_cap") || value.equals("bottlecaps:bottle_cap") || value.equals("bottlecaps:silver_bottle_cap")) return "bottlecaps:silver_bottle_cap_atk";
        return value;
    }
    private static Config defaults() {
        Config c = new Config();
        String[] mons = {"articuno","zapdos","moltres","mewtwo","mew","raikou","entei","suicune","lugia","ho_oh","celebi","regirock","regice","registeel","latias","latios","kyogre","groudon","rayquaza","jirachi","deoxys","uxie","mesprit","azelf","dialga","palkia","heatran","regigigas","giratina","cresselia","phione","manaphy","darkrai","shaymin","arceus","victini","cobalion","terrakion","virizion","tornadus","thundurus","reshiram","zekrom","landorus","kyurem","keldeo","meloetta","genesect","xerneas","yveltal","zygarde","diancie","hoopa","volcanion","type_null","silvally","tapu_koko","tapu_lele","tapu_bulu","tapu_fini","cosmog","cosmoem","solgaleo","lunala","necrozma","magearna","marshadow","zeraora","meltan","melmetal","zacian","zamazenta","eternatus","kubfu","urshifu","zarude","regieleki","regidrago","glastrier","spectrier","calyrex","enamorus","wo_chien","chien_pao","ting_lu","chi_yu","okidogi","munkidori","fezandipiti","ogerpon","terapagos","koraidon","miraidon","walking_wake","iron_leaves","gouging_fire","raging_bolt","iron_boulder","iron_crown","pecharunt","nihilego","buzzwole","pheromosa","xurkitree","celesteela","kartana","guzzlord","poipole","naganadel","stakataka","blacephalon","great_tusk","scream_tail","brute_bonnet","flutter_mane","slither_wing","sandy_shocks","roaring_moon","iron_treads","iron_bundle","iron_hands","iron_jugulis","iron_moth","iron_thorns","iron_valiant"};
        for (String m : mons) c.pokemon.add(new PokemonEntry(m,100));
        c.items.add(new ItemEntry("cobblemon:ability_patch","Ability Patch",25,1));
        c.items.add(new ItemEntry("bottlecaps:silver_bottle_cap_atk","Attack Bottle Cap",10,1));
        c.items.add(new ItemEntry("bottlecaps:silver_bottle_cap_def","Defence Bottle Cap",10,1));
        c.items.add(new ItemEntry("bottlecaps:silver_bottle_cap_hp","HP Bottle Cap",10,1));
        c.items.add(new ItemEntry("bottlecaps:silver_bottle_cap_sp_atk","Special Attack Bottle Cap",10,1));
        c.items.add(new ItemEntry("bottlecaps:silver_bottle_cap_sp_def","Special Defence Bottle Cap",10,1));
        c.items.add(new ItemEntry("bottlecaps:silver_bottle_cap_speed","Speed Bottle Cap",10,1));
        c.items.add(new ItemEntry("bottlecaps:golden_bottle_cap","Golden Bottle Cap",25,1));
        return c;
    }
}
