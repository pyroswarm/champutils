package com.champutils.worldevent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorldEventConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean ENABLED = true;
    public static int CHECK_INTERVAL_MINUTES = 30;
    public static double EVENT_CHANCE = 0.25D;
    public static int MAX_ACTIVE_EVENTS = 1;
    public static boolean OVERWORLD_ONLY = true;
    public static boolean REQUIRE_FLAN_UNCLAIMED = true;
    public static boolean ANNOUNCE_TELEPORT_BUTTON = true;
    public static Map<String, EventDefinition> EVENTS = new LinkedHashMap<>();

    private WorldEventConfig() {}

    public static class Root {
        public boolean enabled = true;
        public int checkIntervalMinutes = 30;
        public double eventChance = 0.25D;
        public int maxActiveEvents = 1;
        public boolean overworldOnly = true;
        public boolean requireFlanUnclaimed = true;
        public boolean announceTeleportButton = true;
        public Map<String, EventDefinition> events = new LinkedHashMap<>();
    }

    public static class EventDefinition {
        public boolean enabled = true;
        public String displayName = "World Event";
        public String bossName = "World Boss";
        public String world = "minecraft:overworld";
        public int spawnRadiusMin = 1500;
        public int spawnRadiusMax = 8000;
        public int avoidClaimRadius = 48;
        public int safeSpawnAttempts = 80;
        public int despawnMinutes = 60;
        public int teleportYOffset = 1;
        public int rewardMoney = 25000;
        public RewardTable rewards = new RewardTable();
        public List<TeamDefinition> teams = new ArrayList<>();
    }

    public static class RewardTable {
        public int minFragments = 2;
        public int maxFragments = 5;
        public Map<String, Integer> fragmentWeights = new LinkedHashMap<>();
    }

    public static class TeamDefinition {
        public String name = "Default Team";
        public int weight = 1;
        public int levelCap = 75;
        public int partySize = 6;
        public boolean itemsAllowed = false;
        public List<PokemonSet> party = new ArrayList<>();
    }

    public static class PokemonSet {
        public String species = "pikachu";
        public int level = 50;
        public String nature = "hardy";
        public String ability = "";
        public String heldItem = "";
        public StatSet ivs = new StatSet(31,31,31,31,31,31);
        public StatSet evs = new StatSet(0,0,0,0,0,0);
        public List<String> moves = new ArrayList<>();
    }

    public static class StatSet {
        public int hp;
        public int atk;
        public int def;
        public int spa;
        public int spd;
        public int spe;

        public StatSet() {}
        public StatSet(int hp, int atk, int def, int spa, int spd, int spe) {
            this.hp = hp;
            this.atk = atk;
            this.def = def;
            this.spa = spa;
            this.spd = spd;
            this.spe = spe;
        }
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
                ENABLED = root.enabled;
                CHECK_INTERVAL_MINUTES = Math.max(1, root.checkIntervalMinutes);
                EVENT_CHANCE = Math.max(0D, Math.min(1D, root.eventChance));
                MAX_ACTIVE_EVENTS = Math.max(1, root.maxActiveEvents);
                OVERWORLD_ONLY = root.overworldOnly;
                REQUIRE_FLAN_UNCLAIMED = root.requireFlanUnclaimed;
                ANNOUNCE_TELEPORT_BUTTON = root.announceTeleportButton;
                EVENTS = root.events == null ? new LinkedHashMap<>() : root.events;
            }
            if (EVENTS.isEmpty()) EVENTS = defaultRoot().events;
            System.out.println("[ChampUtils] Loaded " + EVENTS.size() + " world event definitions.");
        } catch (Exception e) {
            e.printStackTrace();
            Root root = defaultRoot();
            EVENTS = root.events;
        }
    }

    private static void createDefault(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(defaultRoot(), writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Root defaultRoot() {
        Root root = new Root();
        root.events.put("molten_excavation", moltenEvent());
        root.events.put("ancient_grove", groveEvent());
        return root;
    }

    private static RewardTable defaultRewards() {
        RewardTable rewards = new RewardTable();
        rewards.minFragments = 2;
        rewards.maxFragments = 5;
        rewards.fragmentWeights.put("EPIC", 74);
        rewards.fragmentWeights.put("LEGENDARY", 25);
        rewards.fragmentWeights.put("MYTHIC", 1);
        return rewards;
    }

    private static EventDefinition moltenEvent() {
        EventDefinition e = new EventDefinition();
        e.displayName = "Molten Excavation";
        e.bossName = "Molten Excavator";
        e.spawnRadiusMin = 1500;
        e.spawnRadiusMax = 8000;
        e.avoidClaimRadius = 48;
        e.despawnMinutes = 60;
        e.rewardMoney = 30000;
        e.rewards = defaultRewards();
        e.teams.add(team("Magma Core", 2, 85,
                mon("torkoal",85,"bold","drought","heat_rock","lava_plume","stealth_rock","yawn","rapid_spin"),
                mon("arcanine",85,"jolly","intimidate","life_orb","flare_blitz","extremespeed","wild_charge","morning_sun"),
                mon("rhyperior",85,"adamant","solidrock","weakness_policy","earthquake","stone_edge","megahorn","rock_polish"),
                mon("charizard",85,"timid","blaze","heavy_duty_boots","flamethrower","air_slash","solar_beam","dragon_pulse"),
                mon("volcarona",85,"timid","flamebody","leftovers","fiery_dance","bug_buzz","quiver_dance","giga_drain"),
                mon("heatran",85,"modest","flashfire","leftovers","magma_storm","earth_power","flash_cannon","protect")
        ));
        e.teams.add(team("Obsidian Pressure", 1, 90,
                mon("hippowdon",90,"impish","sandstream","smooth_rock","earthquake","stealth_rock","slack_off","whirlwind"),
                mon("excadrill",90,"jolly","sandrush","air_balloon","earthquake","iron_head","rapid_spin","swords_dance"),
                mon("typhlosion",90,"timid","flashfire","choice_scarf","eruption","flamethrower","focus_blast","extrasensory"),
                mon("magnezone",90,"modest","sturdy","choice_specs","thunderbolt","flash_cannon","volt_switch","body_press"),
                mon("garchomp",90,"jolly","roughskin","rocky_helmet","earthquake","dragon_claw","stone_edge","swords_dance"),
                mon("moltres",90,"timid","flamebody","heavy_duty_boots","hurricane","flamethrower","roost","will_o_wisp")
        ));
        return e;
    }

    private static EventDefinition groveEvent() {
        EventDefinition e = new EventDefinition();
        e.displayName = "Ancient Grove";
        e.bossName = "Grove Warden";
        e.spawnRadiusMin = 1000;
        e.spawnRadiusMax = 7000;
        e.avoidClaimRadius = 48;
        e.despawnMinutes = 60;
        e.rewardMoney = 30000;
        e.rewards = defaultRewards();
        e.teams.add(team("Rootbound Bloom", 2, 85,
                mon("venusaur",85,"bold","overgrow","black_sludge","giga_drain","sludge_bomb","sleep_powder","synthesis"),
                mon("tangrowth",85,"relaxed","regenerator","rocky_helmet","power_whip","knock_off","sleep_powder","leech_seed"),
                mon("roserade",85,"timid","naturalcure","focus_sash","energy_ball","sludge_bomb","toxic_spikes","extrasensory"),
                mon("trevenant",85,"careful","harvest","sitrus_berry","horn_leech","will_o_wisp","leech_seed","protect"),
                mon("leafeon",85,"jolly","chlorophyll","life_orb","leaf_blade","x_scissor","swords_dance","synthesis"),
                mon("rillaboom",85,"adamant","overgrow","miracle_seed","wood_hammer","knock_off","drain_punch","bulk_up")
        ));
        e.teams.add(team("Forest Spirits", 1, 90,
                mon("whimsicott",90,"timid","prankster","focus_sash","moonblast","tailwind","stun_spore","encore"),
                mon("breloom",90,"jolly","technician","loaded_dice","bullet_seed","mach_punch","spore","swords_dance"),
                mon("decidueye",90,"adamant","overgrow","scope_lens","spirit_shackle","leaf_blade","shadow_sneak","swords_dance"),
                mon("ferrothorn",90,"relaxed","ironbarbs","leftovers","power_whip","knock_off","leech_seed","protect"),
                mon("amoonguss",90,"bold","regenerator","black_sludge","giga_drain","sludge_bomb","spore","clear_smog"),
                mon("zarude",90,"jolly","leafguard","life_orb","power_whip","darkest_lariat","close_combat","bulk_up")
        ));
        return e;
    }

    private static TeamDefinition team(String name, int weight, int levelCap, PokemonSet... mons) {
        TeamDefinition t = new TeamDefinition();
        t.name = name;
        t.weight = weight;
        t.levelCap = levelCap;
        t.partySize = 6;
        t.itemsAllowed = false;
        for (PokemonSet mon : mons) t.party.add(mon);
        return t;
    }

    private static PokemonSet mon(String species, int level, String nature, String ability, String heldItem, String... moves) {
        PokemonSet p = new PokemonSet();
        p.species = species;
        p.level = level;
        p.nature = nature;
        p.ability = ability;
        p.heldItem = heldItem;
        p.ivs = new StatSet(31,31,31,31,31,31);
        p.evs = new StatSet(84,84,84,84,84,90);
        p.moves = new ArrayList<>();
        for (String move : moves) p.moves.add(move);
        return p;
    }
}
