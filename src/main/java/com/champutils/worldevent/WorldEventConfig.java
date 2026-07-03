package com.champutils.worldevent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WorldEventConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean ENABLED = true;
    public static int CHECK_INTERVAL_MINUTES = 30;
    public static double EVENT_CHANCE = 0.08333333333333333D;
    public static int MAX_ACTIVE_EVENTS = 5;
    public static boolean OVERWORLD_ONLY = true;
    public static boolean REQUIRE_LAND_CLAIM_UNCLAIMED = true;
    public static boolean ANNOUNCE_TELEPORT_BUTTON = true;
    public static int BLOCK_PROTECTION_RADIUS = 24;
    public static double REPEAT_EVENT_WEIGHT_MULTIPLIER = 0.15D;
    public static Map<String, EventDefinition> EVENTS = new LinkedHashMap<>();

    private WorldEventConfig() {}

    public static class Root {
        public boolean enabled = true;
        public int checkIntervalMinutes = 30;
        public double eventChance = 0.08333333333333333D;
        public int maxActiveEvents = 5;
        public boolean overworldOnly = true;
        public boolean requireLandClaimUnclaimed = true;
        public boolean announceTeleportButton = true;
        public int blockProtectionRadius = 24;
        public double repeatEventWeightMultiplier = 0.15D;
        public Map<String, EventDefinition> events = new LinkedHashMap<>();
    }

    public static class EventDefinition {
        public boolean enabled = true;
        public String displayName = "World Event";
        public String tier = "RARE";
        public int weight = 1;
        public String bossName = "World Boss";
        public String spawnName = "";
        public String skin = "";
        public String spawnSkin = "";
        public String playerSkin = "";
        public String skinPlayer = "";
        public String texture = "";
        public String world = "minecraft:overworld";
        public int spawnRadiusMin = 1500;
        public int spawnRadiusMax = 8000;
        public int avoidClaimRadius = 48;
        public int safeSpawnAttempts = 100;
        public int despawnMinutes = 75;
        public int teleportYOffset = 1;
        public int rewardMoney = 50000;
        public RewardTable rewards = new RewardTable();
        public List<TeamDefinition> teams = new ArrayList<>();
    }

    public static class RewardTable {
        public int minFragments = 2;
        public int maxFragments = 5;
        public String crateCreditId = "rare";
        public int crateCredits = 1;
        public Map<String, Integer> fragmentWeights = new LinkedHashMap<>();
    }

    public static class TeamDefinition {
        public String name = "Tiered Pool";
        public int weight = 1;
        public int levelCap = 75;
        public int partySize = 6;
        public boolean itemsAllowed = true;
        public List<PokemonSet> party = new ArrayList<>();
    }

    public static class PokemonSet {
        public String species = "dragonite";
        public int level = 75;
        public String nature = "jolly";
        public String ability = "";
        public String heldItem = "";
        public StatSet ivs = new StatSet(31,31,31,31,31,31);
        public StatSet evs = new StatSet(252,252,252,252,252,252);
        public List<String> moves = new ArrayList<>();
    }

    public static class StatSet {
        public int hp; public int atk; public int def; public int spa; public int spd; public int spe;
        public StatSet() {}
        public StatSet(int hp, int atk, int def, int spa, int spd, int spe) { this.hp=hp; this.atk=atk; this.def=def; this.spa=spa; this.spd=spd; this.spe=spe; }
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "world_events.json");
            if (!file.exists()) createDefault(file);
            try (FileReader reader = new FileReader(file)) {
                Root root = GSON.fromJson(reader, Root.class);
                if (root == null) root = defaultRoot();
                boolean upgradedSpawnCadence = root.checkIntervalMinutes == 25 && Math.abs(root.eventChance - 0.35D) < 0.0000001D;
                if (upgradedSpawnCadence) {
                    root.checkIntervalMinutes = 30;
                    root.eventChance = 0.08333333333333333D;
                }

                ENABLED = root.enabled;
                CHECK_INTERVAL_MINUTES = Math.max(1, root.checkIntervalMinutes);
                EVENT_CHANCE = Math.max(0D, Math.min(1D, root.eventChance));
                MAX_ACTIVE_EVENTS = Math.max(1, root.maxActiveEvents);
                OVERWORLD_ONLY = root.overworldOnly;
                REQUIRE_LAND_CLAIM_UNCLAIMED = root.requireLandClaimUnclaimed;
                ANNOUNCE_TELEPORT_BUTTON = root.announceTeleportButton;
                BLOCK_PROTECTION_RADIUS = Math.max(0, root.blockProtectionRadius);
                REPEAT_EVENT_WEIGHT_MULTIPLIER = Math.max(0D, Math.min(1D, root.repeatEventWeightMultiplier));
                Root defaults = defaultRoot();
                if (root.events == null || root.events.isEmpty()) root.events = defaults.events;
                for (Map.Entry<String, EventDefinition> entry : defaults.events.entrySet()) root.events.putIfAbsent(entry.getKey(), entry.getValue());
                EVENTS = root.events;
                normalizeTieredEvents();
                if (upgradedSpawnCadence) {
                    try (FileWriter writer = new FileWriter(file)) { GSON.toJson(root, writer); }
                }
            }
            System.out.println("[ChampUtils] Loaded " + EVENTS.size() + " world event definitions.");
        } catch (Exception e) {
            e.printStackTrace();
            EVENTS = defaultRoot().events;
            normalizeTieredEvents();
        }
    }

    private static void normalizeTieredEvents() {
        EventDefinition event = EVENTS.getOrDefault("world_boss", event("World Boss", "World Boss", "LEGENDARY", 100, poolWorldBossCompetitive()));
        event.enabled = true;
        event.displayName = "World Boss";
        event.bossName = "World Boss";
        event.tier = "LEGENDARY";
        event.weight = 100;
        event.rewards = rewardsForTier("LEGENDARY");
        TeamDefinition team = event.teams == null || event.teams.isEmpty() ? new TeamDefinition() : event.teams.get(0);
        team.name = "World Boss Competitive Pool";
        team.weight = 1;
        team.levelCap = 100;
        team.partySize = 6;
        team.itemsAllowed = true;
        team.party = new ArrayList<>(poolWorldBossCompetitive());
        event.teams = new ArrayList<>();
        event.teams.add(team);
        EVENTS.clear();
        EVENTS.put("world_boss", event);
    }

    private static void forceTier(String eventId, String tier, String teamName, int displayLevel, List<PokemonSet> pool) {
        EventDefinition event = EVENTS.get(eventId);
        if (event == null) return;
        event.tier = tier;
        event.rewards = rewardsForTier(tier);
        if (event.teams == null) event.teams = new ArrayList<>();
        TeamDefinition team = event.teams.isEmpty() ? new TeamDefinition() : event.teams.get(0);
        team.name = teamName;
        team.weight = 1;
        team.levelCap = displayLevel;
        team.partySize = 6;
        team.itemsAllowed = true;
        team.party = new ArrayList<>(pool);
        event.teams.clear();
        event.teams.add(team);
    }

    private static void createDefault(File file) { try (FileWriter writer = new FileWriter(file)) { GSON.toJson(defaultRoot(), writer); } catch (Exception e) { e.printStackTrace(); } }

    private static Root defaultRoot() {
        Root root = new Root();
        root.events.put("world_boss", event("World Boss", "World Boss", "LEGENDARY", 100, poolWorldBossCompetitive()));
        return root;
    }

    private static EventDefinition event(String display, String boss, String tier, int weight, List<PokemonSet> pool) {
        EventDefinition e = new EventDefinition();
        e.displayName = display; e.bossName = boss; e.tier = tier; e.weight = weight; e.rewards = rewardsForTier(tier);
        TeamDefinition t = new TeamDefinition(); t.name = display + " 32-Pokémon " + tier + " Pool"; t.levelCap = switch (tier) { case "COMMON" -> 55; case "UNCOMMON" -> 65; case "RARE" -> 75; case "EPIC" -> 85; default -> 100; }; t.party.addAll(pool); e.teams.add(t);
        return e;
    }

    private static RewardTable rewardsForTier(String tier) {
        String normalized = tier == null ? "RARE" : tier.trim().toUpperCase(Locale.ROOT);
        RewardTable r = new RewardTable();
        switch (normalized) {
            case "COMMON" -> { r.minFragments = 1; r.maxFragments = 3; r.crateCreditId = "common"; r.fragmentWeights.put("COMMON", 85); r.fragmentWeights.put("UNCOMMON", 15); }
            case "UNCOMMON" -> { r.minFragments = 2; r.maxFragments = 4; r.crateCreditId = "uncommon"; r.fragmentWeights.put("UNCOMMON", 80); r.fragmentWeights.put("RARE", 20); }
            case "RARE" -> { r.minFragments = 3; r.maxFragments = 6; r.crateCreditId = "rare"; r.fragmentWeights.put("RARE", 75); r.fragmentWeights.put("EPIC", 22); r.fragmentWeights.put("LEGENDARY", 3); }
            case "EPIC" -> { r.minFragments = 3; r.maxFragments = 5; r.crateCreditId = "epic"; r.fragmentWeights.put("EPIC", 100); }
            case "LEGENDARY" -> { r.minFragments = 2; r.maxFragments = 4; r.crateCreditId = "legendary"; r.fragmentWeights.put("LEGENDARY", 100); }
            case "MYTHIC" -> { r.minFragments = 3; r.maxFragments = 5; r.crateCreditId = "mythic"; r.fragmentWeights.put("LEGENDARY", 100); }
            default -> { r.minFragments = 3; r.maxFragments = 6; r.crateCreditId = "rare"; r.fragmentWeights.put("RARE", 80); r.fragmentWeights.put("EPIC", 20); }
        }
        r.crateCredits = 1;
        return r;
    }

    private static List<PokemonSet> poolCommonFire() { return pool(
            mon("arcanine","jolly","intimidate","life_orb","flare_blitz","wild_charge","extreme_speed","crunch"), mon("charizard","timid","blaze","heavy_duty_boots","flamethrower","air_slash","dragon_pulse","roost"), mon("talonflame","jolly","flame_body","heavy_duty_boots","brave_bird","flare_blitz","roost","u_turn"), mon("coalossal","careful","flame_body","leftovers","stealth_rock","heat_crash","rock_slide","rapid_spin"),
            mon("centiskorch","adamant","flash_fire","silver_powder","fire_lash","leech_life","power_whip","coil"), mon("houndoom","timid","flash_fire","choice_specs","dark_pulse","flamethrower","sludge_bomb","nasty_plot"), mon("magmortar","modest","flame_body","choice_specs","fire_blast","thunderbolt","psychic","focus_blast"), mon("ninetales","timid","drought","heat_rock","flamethrower","solar_beam","will_o_wisp","nasty_plot"),
            mon("rapidash","jolly","flash_fire","choice_band","flare_blitz","wild_charge","high_horsepower","megahorn"), mon("delphox","timid","blaze","wise_glasses","fire_blast","psychic","grass_knot","calm_mind"), mon("infernape","jolly","blaze","focus_sash","close_combat","flare_blitz","mach_punch","stealth_rock"), mon("blaziken","adamant","speed_boost","life_orb","swords_dance","flare_blitz","close_combat","thunder_punch"),
            mon("typhlosion","timid","blaze","choice_specs","eruption","flamethrower","extrasensory","focus_blast"), mon("skeledirge","bold","unaware","leftovers","torch_song","shadow_ball","will_o_wisp","slack_off"), mon("chandelure","timid","flash_fire","choice_scarf","shadow_ball","fire_blast","energy_ball","trick"), mon("volcarona","timid","flame_body","heavy_duty_boots","quiver_dance","fiery_dance","bug_buzz","giga_drain"),
            mon("torkoal","bold","drought","heat_rock","lava_plume","rapid_spin","stealth_rock","yawn"), mon("rotom_heat","timid","levitate","choice_scarf","overheat","volt_switch","thunderbolt","trick"), mon("salazzle","timid","corrosion","black_sludge","nasty_plot","sludge_wave","flamethrower","toxic"), mon("ceruledge","adamant","flash_fire","heavy_duty_boots","bitter_blade","shadow_sneak","swords_dance","close_combat"),
            mon("armarouge","modest","flash_fire","weakness_policy","armor_cannon","psychic","aura_sphere","calm_mind"), mon("scovillain","timid","chlorophyll","life_orb","fire_blast","energy_ball","growth","stomping_tantrum"), mon("camerupt","modest","solid_rock","leftovers","earth_power","flamethrower","stealth_rock","will_o_wisp"), mon("flareon","adamant","guts","flame_orb","facade","flare_blitz","quick_attack","superpower"),
            mon("pyroar","timid","unnerve","choice_specs","hyper_voice","flamethrower","dark_pulse","will_o_wisp"), mon("darmanitan","jolly","sheer_force","choice_scarf","flare_blitz","earthquake","rock_slide","u_turn"), mon("turtonator","modest","shell_armor","white_herb","shell_smash","fire_blast","dragon_pulse","flash_cannon"), mon("incineroar","careful","intimidate","sitrus_berry","fake_out","flare_blitz","knock_off","parting_shot"),
            mon("heatmor","modest","flash_fire","expert_belt","fire_blast","giga_drain","focus_blast","sucker_punch"), mon("simisear","timid","gluttony","life_orb","nasty_plot","fire_blast","grass_knot","focus_blast"), mon("oricorio_baile","timid","dancer","heavy_duty_boots","revelation_dance","hurricane","roost","quiver_dance"), mon("houndstone","adamant","sand_rush","spell_tag","last_respects","play_rough","will_o_wisp","shadow_sneak")
    ); }

    private static List<PokemonSet> poolUncommonWater() { return pool(
            "gyarados","milotic","lapras","kingdra","swampert","greninja","empoleon","samurott","feraligatr","primarina","slowbro","slowking","starmie","cloyster","toxapex","tentacruel","pelipper","barraskewda","dondozo","veluza","gastrodon","quagsire","rotom_wash","vaporeon","azumarill","crawdaunt","politoed","alomomola","walrein","floatzel","sharpedo","golisopod"); }
    private static List<PokemonSet> poolRareNature() { return pool(
            "venusaur","meowscarada","rillaboom","serperior","amoonguss","breloom","ferrothorn","kartana","tsareena","decidueye","chesnaught","roserade","trevenant","tangrowth","abomasnow","lilligant","sceptile","torterra","goodra","dragonite","kommo_o","hydreigon","garchomp","tyranitar","metagross","salamence","dragapult","haxorus","noivern","volcarona","gliscor","mamoswine"); }
    private static List<PokemonSet> poolEpicVoid() { return pool(
            "flutter_mane","iron_bundle","iron_valiant","roaring_moon","walking_wake","gouging_fire","raging_bolt","iron_crown","iron_boulder","iron_moth","iron_hands","iron_treads","sandy_shocks","scream_tail","brute_bonnet","slither_wing","great_tusk","gholdengo","kingambit","annihilape","dragapult","garchomp","dragonite","ursaluna","basculegion","ceruledge","armarouge","skeledirge","greninja","volcarona","toxapex","glimmora"); }
    private static List<PokemonSet> poolWorldBossCompetitive() {
        List<PokemonSet> list = new ArrayList<>();
        list.addAll(poolLegendarySteel());
        list.addAll(poolEpicVoid());
        list.addAll(poolRareNature());
        return list;
    }

    private static List<PokemonSet> poolLegendarySteel() { return pool(
            "mewtwo","rayquaza","kyogre","groudon","lugia","ho_oh","dialga","palkia","giratina","reshiram","zekrom","kyurem","xerneas","yveltal","zygarde","solgaleo","lunala","necrozma","zacian","zamazenta","eternatus","koraidon","miraidon","calyrex","landorus","thundurus","tornadus","urshifu","kartana","guzzlord","celesteela","magearna"); }

    private static List<PokemonSet> pool(String... species) {
        List<PokemonSet> list = new ArrayList<>();
        for (String s : species) list.add(generic(s));
        return list;
    }
    private static List<PokemonSet> pool(PokemonSet... sets) { return new ArrayList<>(List.of(sets)); }
    private static PokemonSet generic(String species) { return mon(species, "jolly", "", "life_orb", "earthquake", "shadow_ball", "thunderbolt", "close_combat"); }
    private static PokemonSet mon(String species, String nature, String ability, String item, String... moves) {
        PokemonSet p = new PokemonSet();
        p.species = species; p.level = 100; p.nature = nature; p.ability = ability; p.heldItem = item;
        p.ivs = new StatSet(31,31,31,31,31,31); p.evs = new StatSet(252,252,252,252,252,252);
        p.moves = new ArrayList<>(List.of(moves));
        return p;
    }
}
