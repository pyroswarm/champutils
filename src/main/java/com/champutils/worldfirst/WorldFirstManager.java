package com.champutils.worldfirst;

import com.champutils.cosmetic.TitleManager;
import com.champutils.cosmetic.TitleConfig;
import com.champutils.profession.ProfessionType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldFirstManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/world_firsts.json");
    private static final File TITLE_CATALOG_FILE = new File("config/champutils/world-first.json");
    private static final File TITLE_CATALOG_FILE_PLURAL = new File("config/champutils/world-firsts.json");
    private static State state = new State();
    private static List<WorldFirstDef> DEFS = new ArrayList<>();

    private static final Set<String> LEGENDARIES = set("articuno","zapdos","moltres","mewtwo","raikou","entei","suicune","lugia","hooh","ho_oh","regirock","regice","registeel","latias","latios","kyogre","groudon","rayquaza","uxie","mesprit","azelf","dialga","palkia","heatran","regigigas","giratina","cresselia","cobalion","terrakion","virizion","tornadus","thundurus","reshiram","zekrom","landorus","kyurem","xerneas","yveltal","zygarde","typenull","type_null","silvally","tapukoko","tapu_koko","tapulele","tapu_lele","tapubulu","tapu_bulu","tapufini","tapu_fini","cosmog","cosmoem","solgaleo","lunala","necrozma","zacian","zamazenta","eternatus","kubfu","urshifu","regieleki","regidrago","glastrier","spectrier","calyrex","enamorus","wochien","wo_chien","chienpao","chien_pao","tinglu","ting_lu","chiyu","chi_yu","okidogi","munkidori","fezandipiti","ogerpon","terapagos","koraidon","miraidon");
    private static final Set<String> MYTHICALS = set("mew","celebi","jirachi","deoxys","phione","manaphy","darkrai","shaymin","arceus","victini","keldeo","meloetta","genesect","diancie","hoopa","volcanion","magearna","marshadow","zeraora","meltan","melmetal","zarude","pecharunt");
    private static final Set<String> ULTRA_BEASTS = set("nihilego","buzzwole","pheromosa","xurkitree","celesteela","kartana","guzzlord","poipole","naganadel","stakataka","blacephalon");
    private static final Set<String> PARADOX = set("greattusk","great_tusk","screamtail","scream_tail","brutebonnet","brute_bonnet","fluttermane","flutter_mane","slitherwing","slither_wing","sandyshocks","sandy_shocks","roaringmoon","roaring_moon","walkingwake","walking_wake","gougingfire","gouging_fire","ragingbolt","raging_bolt","irontreads","iron_treads","ironbundle","iron_bundle","ironhands","iron_hands","ironjugulis","iron_jugulis","ironmoth","iron_moth","ironthorns","iron_thorns","ironvaliant","iron_valiant","ironleaves","iron_leaves","ironboulder","iron_boulder","ironcrown","iron_crown");
    private static final Set<String> STARTERS = set("bulbasaur","charmander","squirtle","chikorita","cyndaquil","totodile","treecko","torchic","mudkip","turtwig","chimchar","piplup","snivy","tepig","oshawott","chespin","fennekin","froakie","rowlet","litten","popplio","grookey","scorbunny","sobble","sprigatito","fuecoco","quaxly");
    private static final Set<String> GOOFY = set("magikarp","bidoof","rattata","zigzagoon","wooper","snom","shuckle","psyduck","wobbuffet","ditto","lechonk","trubbish","slowpoke","dunsparce","smeargle","spinda","delibird","farfetchd","farfetch_d","unown");

    private WorldFirstManager() {}

    public static void load() {
        loadDefinitions();
        if (com.champutils.database.DatabaseManager.isEnabled()) {
            state = new State();
            state.claims = WorldFirstDatabaseRepository.loadClaims();
            return;
        }
        state = new State();
    }
    public static void save() {
        // World first claims are SQL-backed when the database is enabled. Definitions live in config/champutils/world_firsts.json.
    }

    public static void refreshClaimsAsync() {
        if (!com.champutils.database.DatabaseManager.isEnabled()) return;
        com.champutils.database.DatabaseManager.supplyAsync(
                "refresh world first claims",
                connection -> WorldFirstDatabaseRepository.loadClaims()
        ).whenComplete((claims, error) -> {
            if (error != null) {
                error.printStackTrace();
                return;
            }
            if (claims == null) return;
            synchronized (WorldFirstManager.class) {
                state.claims.clear();
                state.claims.putAll(claims);
            }
        });
    }

    private static synchronized void loadDefinitions() {
        try {
            FILE.getParentFile().mkdirs();
            if (!FILE.exists()) {
                DEFS = defaults();
                mergeConfiguredWorldFirstTitles();
                saveDefinitions();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                WorldFirstConfig config = GSON.fromJson(reader, WorldFirstConfig.class);
                DEFS = config == null || config.worldFirsts == null || config.worldFirsts.isEmpty() ? defaults() : config.worldFirsts;
            }
            mergeMissingDefaults();
            mergeConfiguredWorldFirstTitles();
            saveDefinitions();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load world first definitions. Using defaults.");
            e.printStackTrace();
            DEFS = defaults();
        }
    }

    private static void mergeConfiguredWorldFirstTitles() {
        File catalog = TITLE_CATALOG_FILE.exists() ? TITLE_CATALOG_FILE : TITLE_CATALOG_FILE_PLURAL;
        if (!catalog.exists()) return;
        try (FileReader reader = new FileReader(catalog)) {
            TitleConfig.Config config = GSON.fromJson(reader, TitleConfig.Config.class);
            if (config == null || config.titles == null) return;
            Map<String, WorldFirstDef> byId = new java.util.LinkedHashMap<>();
            for (WorldFirstDef def : DEFS) if (def != null && def.id != null) byId.put(def.id, def);
            for (TitleConfig.TitleDef title : config.titles) {
                if (title == null || title.unlock == null || !"world_first".equalsIgnoreCase(title.unlock.type)) continue;
                String id = title.unlock.worldFirstId;
                if (id == null || id.isBlank()) continue;
                WorldFirstDef def = new WorldFirstDef();
                def.id = id;
                def.name = title.unlock.worldFirstName == null || title.unlock.worldFirstName.isBlank() ? title.name : title.unlock.worldFirstName;
                def.titleId = title.id;
                def.titleDisplay = title.display;
                def.rewardText = title.unlock.rewardText == null ? "exclusive world-first title" : title.unlock.rewardText;
                def.xpReward = Math.max(0, title.unlock.xpReward);
                def.trigger = title.unlock.trigger;
                byId.put(id, def);
            }
            DEFS = new ArrayList<>(byId.values());
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to merge configured world-first title definitions.");
            e.printStackTrace();
        }
    }

    public static boolean awardByTrigger(ServerPlayer player, String trigger) {
        if (trigger == null || trigger.isBlank()) return false;
        for (WorldFirstDef def : DEFS) {
            if (def != null && trigger.equalsIgnoreCase(def.trigger)) return award(player, def.id);
        }
        return false;
    }

    private static void mergeMissingDefaults() {
        Set<String> ids = new LinkedHashSet<>();
        for (WorldFirstDef def : DEFS) if (def != null && def.id != null) ids.add(def.id.toLowerCase(Locale.ROOT));
        for (WorldFirstDef def : defaults()) {
            if (def == null || def.id == null) continue;
            if (ids.add(def.id.toLowerCase(Locale.ROOT))) DEFS.add(def);
        }
    }

    private static synchronized void saveDefinitions() {
        try {
            FILE.getParentFile().mkdirs();
            WorldFirstConfig config = new WorldFirstConfig();
            config.worldFirsts = DEFS;
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(config, writer); }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save world first definitions.");
            e.printStackTrace();
        }
    }

    public static List<WorldFirstDef> definitions() { return DEFS; }
    public static Claim claim(String id) { return state.claims.get(id); }
    public static String titleDisplay(String titleId) {
        for (WorldFirstDef def : DEFS) if (def != null && def.titleId != null && def.titleId.equals(titleId)) return def.titleDisplay;
        return null;
    }

    public static void handleCatch(ServerPlayer player, Object pokemon) {
        if (player == null || pokemon == null) return;
        String species = normalizeSpecies(speciesName(pokemon));
        boolean shiny = boolValue(pokemon, "getShiny", "isShiny", "shiny");
        int level = intValue(pokemon, "getLevel", "level");
        if (shiny) award(player, "first_shiny_catch");
        if (level >= 100) award(player, "first_level_100_catch");
        if (isLegendary(pokemon, species)) {
            award(player, "first_legendary_catch");
            award(player, "first_legendary_" + species);
        }
        if (isMythical(pokemon, species)) {
            award(player, "first_mythical_catch");
            award(player, "first_mythical_" + species);
        }
        if (isUltraBeast(species)) {
            award(player, "first_ultra_beast_catch");
            award(player, "first_ultra_beast_" + species);
        }
        if (isParadox(species)) {
            award(player, "first_paradox_catch");
            award(player, "first_paradox_" + species);
        }
        if (isStarter(species)) award(player, "first_starter_catch");
        if (GOOFY.contains(species)) award(player, "first_goofy_" + species);
        if (species.equals("magikarp")) award(player, "first_magikarp_catch");
        if (species.equals("ditto")) award(player, "first_ditto_catch");
        if (shiny && species.equals("magikarp")) award(player, "first_shiny_magikarp");
        if (level >= 100 && species.equals("magikarp")) award(player, "first_level_100_magikarp");
    }

    public static void handleProfessionLevel(ServerPlayer player, ProfessionType type, int level) {
        if (player == null || type == null) return;
        if (level >= 25) award(player, "first_" + type.name().toLowerCase(Locale.ROOT) + "_25");
        if (level >= 50) award(player, "first_" + type.name().toLowerCase(Locale.ROOT) + "_50");
        if (level >= 75) award(player, "first_" + type.name().toLowerCase(Locale.ROOT) + "_75");
        if (level >= 100) award(player, "first_" + type.name().toLowerCase(Locale.ROOT) + "_100");
    }

    public static void handleBattleWin(ServerPlayer player, com.champutils.battle.BattleContextManager.BattleType type) {
        if (player == null || type == null) return;
        award(player, "first_any_battle_win");
        if (type == com.champutils.battle.BattleContextManager.BattleType.RANKED) award(player, "first_ranked_win");
        if (type == com.champutils.battle.BattleContextManager.BattleType.CASUAL) award(player, "first_casual_win");
        if (type == com.champutils.battle.BattleContextManager.BattleType.ADVENTURE_TOWER) award(player, "first_battle_tower_win");
        if (type == com.champutils.battle.BattleContextManager.BattleType.ADVENTURE_ROAMING) award(player, "first_adventurer_request_win");
    }

    public static synchronized boolean award(ServerPlayer player, String id) {
        if (player == null || id == null || id.isBlank() || state.claims.containsKey(id)) return false;
        WorldFirstDef def = DEFS.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
        if (def == null) return false;
        String playerName = player.getName().getString();
        boolean inserted = true;
        if (com.champutils.database.DatabaseManager.isEnabled()) {
            inserted = WorldFirstDatabaseRepository.claim(id, player.getUUID(), playerName);
            if (!inserted) {
                state.claims.putAll(WorldFirstDatabaseRepository.loadClaims());
                return false;
            }
        }
        Claim claim = new Claim();
        claim.playerUuid = player.getUUID().toString();
        claim.playerName = playerName;
        claim.claimedAt = Instant.now().toString();
        state.claims.put(id, claim);
        save();
        com.champutils.network.NetworkEventManager.publishCacheInvalidation(
                "WORLD_FIRSTS",
                new java.util.UUID(0L, 0L)
        );
        TitleManager.unlock(player, def.titleId, def.titleDisplay);
        com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(
                player.server,
                Component.literal("[World First] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(claim.playerName).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" achieved ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(def.name).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("!").withStyle(ChatFormatting.GRAY))
        );
        grantOneTimeReward(player, def);
        return true;
    }

    private static void grantOneTimeReward(ServerPlayer player, WorldFirstDef def) {
        int xp = Math.max(0, def.xpReward);
        ProfessionType avenue = rewardProfession(def);
        if (xp > 0) com.champutils.profession.ProfessionManager.addRewardXp(player, avenue, xp);
        player.sendSystemMessage(Component.literal("World First reward claimed: " + def.rewardText + " (" + prettyProfession(avenue) + " XP)").withStyle(ChatFormatting.GREEN));
    }

    private static ProfessionType rewardProfession(WorldFirstDef def) {
        if (def == null) return ProfessionType.BATTLING;
        TitleConfig.TitleDef title = def.titleId == null ? null : TitleConfig.get(def.titleId);
        String text = String.join(" ",
                def.id == null ? "" : def.id,
                def.name == null ? "" : def.name,
                def.trigger == null ? "" : def.trigger,
                title == null || title.category == null ? "" : title.category,
                title == null || title.description == null ? "" : title.description
        ).toLowerCase(Locale.ROOT);

        if (text.contains("mining") || text.contains("mine_") || text.contains("ore")) return ProfessionType.MINING;
        if (text.contains("forestry") || text.contains("woodcut") || text.contains("log") || text.contains("tree")) return ProfessionType.FORESTRY;
        if (text.contains("farming") || text.contains("farm_") || text.contains("crop") || text.contains("berry")) return ProfessionType.FARMING;
        if (text.contains("breeding") || text.contains("breed") || text.contains("hatch") || text.contains("egg")) return ProfessionType.BREEDING;
        // Battles, catches, gyms, towers, profiles, exploration, and uncategorized rewards use Battling XP.
        return ProfessionType.BATTLING;
    }

    private static String prettyProfession(ProfessionType type) {
        String lower = (type == null ? ProfessionType.BATTLING : type).name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String speciesName(Object pokemon) {
        Object species = value(pokemon, "getSpecies", "species");
        Object name = value(species, "getName", "name", "showdownId");
        return name == null ? "unknown" : String.valueOf(name);
    }

    private static boolean isLegendary(Object pokemon, String species) {
        if (LEGENDARIES.contains(species)) return true;
        Object speciesObj = value(pokemon, "getSpecies", "species");
        return boolValue(speciesObj, "isLegendary", "getLegendary", "a");
    }
    private static boolean isMythical(Object pokemon, String species) {
        if (MYTHICALS.contains(species)) return true;
        Object speciesObj = value(pokemon, "getSpecies", "species");
        return boolValue(speciesObj, "isMythical", "getMythical", "mythical");
    }
    private static boolean isUltraBeast(String s) { return ULTRA_BEASTS.contains(s); }
    private static boolean isParadox(String s) { return PARADOX.contains(s); }
    private static boolean isStarter(String s) { return STARTERS.contains(s); }

    private static Object value(Object o, String... names) {
        if (o == null) return null;
        for (String n : names) try { Method m = o.getClass().getMethod(n); m.setAccessible(true); return m.invoke(o); } catch (Throwable ignored) {}
        for (String n : names) try { var f = o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o); } catch (Throwable ignored) {}
        return null;
    }
    private static boolean boolValue(Object o, String... names) { Object v = value(o, names); return v instanceof Boolean b ? b : v != null && Boolean.parseBoolean(String.valueOf(v)); }
    private static int intValue(Object o, String... names) { Object v = value(o, names); if (v instanceof Number n) return n.intValue(); try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; } }

    private static List<WorldFirstDef> defaults() {
        List<WorldFirstDef> list = new ArrayList<>();
        add(list,"first_legendary_catch","First Legendary Catch","&6[Legend Seeker]","exclusive world-first title with catch/event buffs and 750 XP",750);
        add(list,"first_mythical_catch","First Mythical Catch","&d[Myth Maker]","exclusive world-first title with catch/event buffs and 750 XP",750);
        add(list,"first_shiny_catch","First Shiny Catch","&d[Shiny Pioneer]","exclusive title with shiny-rate buff and 750 XP",750);
        add(list,"first_ultra_beast_catch","First Ultra Beast Catch","&5[Beast Breaker]","exclusive world-first title with catch/event buffs and 750 XP",750);
        add(list,"first_paradox_catch","First Paradox Catch","&b[Timebreaker]","exclusive world-first title with catch/event buffs and 750 XP",750);
        add(list,"first_starter_catch","First Starter Catch","&a[Starter Scout]","exclusive title and 250 XP",250);
        add(list,"first_ditto_catch","First Ditto Catch","&d[Copycat]","exclusive title and 250 XP",250);
        add(list,"first_magikarp_catch","First Magikarp Catch","&6[Karp King]","exclusive goofy title and 250 XP",250);
        add(list,"first_shiny_magikarp","First Shiny Magikarp","&6[Golden Karp King]","exclusive goofy shiny title and 1000 XP",1000);
        add(list,"first_level_100_magikarp","First Level 100 Magikarp","&6[Karp Emperor]","exclusive questionable-life-choices title and 1500 XP",1500);
        add(list,"first_level_100_catch","First Level 100 Catch","&c[Apex Hunter]","exclusive title and 1000 XP",1000);
        add(list,"first_any_battle_win","First Battle Win","&a[First Blood]","exclusive title and 250 XP",250);
        add(list,"first_casual_win","First Casual PvP Win","&b[Casual Victor]","exclusive title and 250 XP",250);
        add(list,"first_ranked_win","First Ranked PvP Win","&6[Ranked Pioneer]","exclusive title and 500 XP",500);
        add(list,"first_battle_tower_win","First Battle Tower Win","&d[Tower Spark]","exclusive Battle Tower title and 500 XP",500);
        add(list,"first_breeding_hatch","First Bred Pokémon Hatched","&d[Egg Pioneer]","exclusive breeding title and 500 XP",500);
        for (int floor=10; floor<=100; floor+=10) add(list,"first_battle_tower_"+floor,"First Battle Tower Floor "+floor,"&6[Tower First "+floor+"]","exclusive scaling Battle Tower title and "+(floor*20)+" XP",floor*20);
        add(list,"first_adventurer_request_win","First Adventurer Request Win","&a[Guild Errand Runner]","exclusive Adventurer title and 500 XP",500);
        add(list,"first_profile_playtime_10_hours","First Profile to 10 Hours","&a[Early Regular]","exclusive profile-time world-first title and 500 XP",500);
        add(list,"first_profile_playtime_100_hours","First Profile to 100 Hours","&b[Centurion of Time]","exclusive profile-time world-first title and 1500 XP",1500);
        add(list,"first_profile_playtime_1000_hours","First Profile to 1,000 Hours","&d[Timeless Vanguard]","exclusive profile-time world-first title and 5000 XP",5000);
        add(list,"first_profile_playtime_10000_hours","First Profile to 10,000 Hours","&6[The Eternal]","the ultimate profile-time world-first title with extraordinary bonuses and 25000 XP",25000);
        for (int milestone : new int[]{1,5,10,25,50,100}) add(list,"first_"+milestone+"_secrets","First to Discover "+milestone+" Secret"+(milestone==1?"":"s"),"&5[Secret Pioneer "+milestone+"]","exclusive secret world-first title",Math.max(250,milestone*50));

        for (String species : LEGENDARIES) add(list, "first_legendary_" + species, "First " + prettySpecies(species) + " Catch", "&6[First " + prettySpecies(species) + "]", "species world-first title and 1000 XP", 1000);
        for (String species : MYTHICALS) add(list, "first_mythical_" + species, "First " + prettySpecies(species) + " Catch", "&d[First " + prettySpecies(species) + "]", "species world-first title and 1000 XP", 1000);
        for (String species : ULTRA_BEASTS) add(list, "first_ultra_beast_" + species, "First " + prettySpecies(species) + " Catch", "&5[First " + prettySpecies(species) + "]", "species world-first title and 1000 XP", 1000);
        for (String species : PARADOX) add(list, "first_paradox_" + species, "First " + prettySpecies(species) + " Catch", "&b[First " + prettySpecies(species) + "]", "species world-first title and 1000 XP", 1000);
        for (String species : GOOFY) add(list, "first_goofy_" + species, "First " + prettySpecies(species) + " Catch", "&e[" + goofyTitle(species) + "]", "goofy world-first title and 300 XP", 300);

        for (ProfessionType t : ProfessionType.values()) {
            String k = t.name().toLowerCase(Locale.ROOT);
            add(list,"first_"+k+"_25","First " + pretty(k) + " Level 25","&a["+pretty(k)+" Trailblazer]","exclusive title and 250 XP",250);
            add(list,"first_"+k+"_50","First " + pretty(k) + " Level 50","&b["+pretty(k)+" Pro]","exclusive title and 500 XP",500);
            add(list,"first_"+k+"_75","First " + pretty(k) + " Level 75","&d["+pretty(k)+" Elite]","exclusive title and 750 XP",750);
            add(list,"first_"+k+"_100","First " + pretty(k) + " Level 100","&6["+pretty(k)+" Master]","exclusive title and 1000 XP",1000);
        }
        return list;
    }

    private static Set<String> set(String... values) { return new LinkedHashSet<>(List.of(values)); }
    private static String normalizeSpecies(String raw) { return raw == null ? "unknown" : raw.toLowerCase(Locale.ROOT).replace(" ", "_").replace("-", "_").replace(".", "").trim(); }
    private static String pretty(String s) { return s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1).toLowerCase(Locale.ROOT); }
    private static String prettySpecies(String raw) { String s = normalizeSpecies(raw).replace('_', ' '); StringBuilder out = new StringBuilder(); for (String part : s.split(" ")) { if (part.isBlank()) continue; if (out.length() > 0) out.append(' '); out.append(part.substring(0,1).toUpperCase(Locale.ROOT)).append(part.substring(1)); } return out.toString(); }
    private static String goofyTitle(String species) { return switch (normalizeSpecies(species)) { case "bidoof" -> "Bidoof Believer"; case "magikarp" -> "Karp King"; case "rattata" -> "Rat Royalty"; case "zigzagoon" -> "Zigzagoon Zoomer"; case "wooper" -> "Wooper Trooper"; case "snom" -> "Snom Nom"; case "shuckle" -> "Don't Shuckle"; case "psyduck" -> "Headache Hero"; case "wobbuffet" -> "Counter Culture"; case "ditto" -> "Copycat"; case "lechonk" -> "Lechonk Lord"; case "trubbish" -> "Trash King"; case "slowpoke" -> "Eventually First"; case "dunsparce" -> "Dun Done It"; case "smeargle" -> "Paint Goblin"; case "spinda" -> "Dizzy Royalty"; case "delibird" -> "Delivery Legend"; case "farfetchd", "farfetch_d" -> "Leek Freak"; case "unown" -> "Question Mark"; default -> prettySpecies(species) + " Fan"; }; }
    private static void add(List<WorldFirstDef> list, String id, String name, String title, String reward, int xp) { WorldFirstDef d = new WorldFirstDef(); d.id=id; d.name=name; d.titleId="wf_"+id; d.titleDisplay=title; d.rewardText=reward; d.xpReward=xp; list.add(d); }

    public static final class State { Map<String, Claim> claims = new ConcurrentHashMap<>(); }
    public static final class WorldFirstConfig { public List<WorldFirstDef> worldFirsts = new ArrayList<>(); }
    public static final class Claim { public String playerUuid; public String playerName; public String claimedAt; }
    public static final class WorldFirstDef { public String id; public String name; public String titleId; public String titleDisplay; public String rewardText; public int xpReward; public String trigger; }
}
