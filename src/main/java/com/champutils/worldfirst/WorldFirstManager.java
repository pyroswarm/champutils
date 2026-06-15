package com.champutils.worldfirst;

import com.champutils.cosmetic.TitleManager;
import com.champutils.profession.ProfessionType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldFirstManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/world_firsts.json");
    private static State state = new State();
    private static List<WorldFirstDef> DEFS = new ArrayList<>();

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

    private static synchronized void loadDefinitions() {
        try {
            FILE.getParentFile().mkdirs();
            if (!FILE.exists()) {
                DEFS = defaults();
                saveDefinitions();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                WorldFirstConfig config = GSON.fromJson(reader, WorldFirstConfig.class);
                DEFS = config == null || config.worldFirsts == null || config.worldFirsts.isEmpty() ? defaults() : config.worldFirsts;
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load world first definitions. Using defaults.");
            e.printStackTrace();
            DEFS = defaults();
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
        String species = speciesName(pokemon).toLowerCase(Locale.ROOT);
        boolean shiny = boolValue(pokemon, "getShiny", "isShiny", "shiny");
        int level = intValue(pokemon, "getLevel", "level");
        if (shiny) award(player, "first_shiny_catch");
        if (level >= 100) award(player, "first_level_100_catch");
        if (isLegendary(pokemon, species)) award(player, "first_legendary_catch");
        if (isUltraBeast(species)) award(player, "first_ultra_beast_catch");
        if (isStarter(species)) award(player, "first_starter_catch");
        if (species.equals("magikarp")) award(player, "first_magikarp_catch");
        if (species.equals("ditto")) award(player, "first_ditto_catch");
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
        TitleManager.unlock(player, def.titleId, def.titleDisplay);
        player.server.getPlayerList().broadcastSystemMessage(Component.literal("[World First] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(claim.playerName).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" achieved ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(def.name).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY)), false);
        grantOneTimeReward(player, def);
        return true;
    }

    private static void grantOneTimeReward(ServerPlayer player, WorldFirstDef def) {
        player.giveExperiencePoints(Math.max(0, def.xpReward));
        player.sendSystemMessage(Component.literal("World First reward claimed: " + def.rewardText).withStyle(ChatFormatting.GREEN));
    }

    private static String speciesName(Object pokemon) {
        Object species = value(pokemon, "getSpecies", "species");
        Object name = value(species, "getName", "name", "showdownId");
        return name == null ? "unknown" : String.valueOf(name);
    }
    private static boolean isLegendary(Object pokemon, String species) {
        String s = species.toLowerCase(Locale.ROOT);
        Set<String> known = Set.of("mewtwo","mew","lugia","hooh","celebi","kyogre","groudon","rayquaza","jirachi","deoxys","dialga","palkia","giratina","arceus","victini","reshiram","zekrom","kyurem","xerneas","yveltal","zygarde","diancie","hoopa","volcanion","cosmog","cosmoem","solgaleo","lunala","necrozma","magearna","marshadow","zeraora","meltan","melmetal","zacian","zamazenta","eternatus","zarude","regieleki","regidrago","koraidon","miraidon","terapagos");
        if (known.contains(s)) return true;
        Object speciesObj = value(pokemon, "getSpecies", "species");
        return boolValue(speciesObj, "isLegendary", "getLegendary", "legendary") || boolValue(speciesObj, "isMythical", "getMythical", "mythical");
    }
    private static boolean isUltraBeast(String s) { return Set.of("nihilego","buzzwole","pheromosa","xurkitree","celesteela","kartana","guzzlord","poipole","naganadel","stakataka","blacephalon").contains(s); }
    private static boolean isStarter(String s) { return Set.of("bulbasaur","charmander","squirtle","chikorita","cyndaquil","totodile","treecko","torchic","mudkip","turtwig","chimchar","piplup","snivy","tepig","oshawott","chespin","fennekin","froakie","rowlet","litten","popplio","grookey","scorbunny","sobble","sprigatito","fuecoco","quaxly").contains(s); }

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
        add(list,"first_legendary_catch","First Legendary Catch","&6[Legend Seeker]","+1% permanent bragging-right title and 500 XP",500);
        add(list,"first_shiny_catch","First Shiny Catch","&d[Shiny Pioneer]","exclusive title and 500 XP",500);
        add(list,"first_ultra_beast_catch","First Ultra Beast Catch","&5[Beast Breaker]","exclusive title and 500 XP",500);
        add(list,"first_starter_catch","First Starter Catch","&a[Starter Scout]","exclusive title and 250 XP",250);
        add(list,"first_ditto_catch","First Ditto Catch","&d[Copycat]","exclusive title and 250 XP",250);
        add(list,"first_magikarp_catch","First Magikarp Catch","&6[Karp King]","exclusive title and 100 XP",100);
        add(list,"first_level_100_catch","First Level 100 Catch","&c[Apex Hunter]","exclusive title and 1000 XP",1000);
        add(list,"first_any_battle_win","First Battle Win","&a[First Blood]","exclusive title and 250 XP",250);
        add(list,"first_casual_win","First Casual PvP Win","&b[Casual Victor]","exclusive title and 250 XP",250);
        add(list,"first_ranked_win","First Ranked PvP Win","&6[Ranked Pioneer]","exclusive title and 500 XP",500);
        for (ProfessionType t : ProfessionType.values()) { String k = t.name().toLowerCase(Locale.ROOT); add(list,"first_"+k+"_25","First " + pretty(k) + " Level 25","&a["+pretty(k)+" Trailblazer]","exclusive title and 250 XP",250); add(list,"first_"+k+"_50","First " + pretty(k) + " Level 50","&b["+pretty(k)+" Pro]","exclusive title and 500 XP",500); add(list,"first_"+k+"_75","First " + pretty(k) + " Level 75","&d["+pretty(k)+" Elite]","exclusive title and 750 XP",750); add(list,"first_"+k+"_100","First " + pretty(k) + " Level 100","&6["+pretty(k)+" Master]","exclusive title and 1000 XP",1000); }
        return list;
    }
    private static String pretty(String s) { return s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1).toLowerCase(Locale.ROOT); }
    private static void add(List<WorldFirstDef> list, String id, String name, String title, String reward, int xp) { WorldFirstDef d = new WorldFirstDef(); d.id=id; d.name=name; d.titleId="wf_"+id; d.titleDisplay=title; d.rewardText=reward; d.xpReward=xp; list.add(d); }

    public static final class State { Map<String, Claim> claims = new ConcurrentHashMap<>(); }
    public static final class WorldFirstConfig { public List<WorldFirstDef> worldFirsts = new ArrayList<>(); }
    public static final class Claim { public String playerUuid; public String playerName; public String claimedAt; }
    public static final class WorldFirstDef { public String id; public String name; public String titleId; public String titleDisplay; public String rewardText; public int xpReward; }
}
