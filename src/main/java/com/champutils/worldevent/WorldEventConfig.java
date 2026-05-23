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
    public static int CHECK_INTERVAL_MINUTES = 25;
    public static double EVENT_CHANCE = 0.35D;
    public static int MAX_ACTIVE_EVENTS = 5;
    public static boolean OVERWORLD_ONLY = true;
    public static boolean REQUIRE_FLAN_UNCLAIMED = true;
    public static boolean ANNOUNCE_TELEPORT_BUTTON = true;
    public static double REPEAT_EVENT_WEIGHT_MULTIPLIER = 0.15D;
    public static Map<String, EventDefinition> EVENTS = new LinkedHashMap<>();

    private WorldEventConfig() {}

    public static class Root {
        public boolean enabled = true;
        public int checkIntervalMinutes = 25;
        public double eventChance = 0.35D;
        public int maxActiveEvents = 5;
        public boolean overworldOnly = true;
        public boolean requireFlanUnclaimed = true;
        public boolean announceTeleportButton = true;
        public double repeatEventWeightMultiplier = 0.15D;
        public Map<String, EventDefinition> events = new LinkedHashMap<>();
    }

    public static class EventDefinition {
        public boolean enabled = true;
        public String displayName = "World Event";
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
        public int minFragments = 5;
        public int maxFragments = 10;
        public String crateCreditId = "event";
        public int crateCredits = 1;
        public Map<String, Integer> fragmentWeights = new LinkedHashMap<>();
    }

    public static class TeamDefinition {
        public String name = "Extreme Pool";
        public int weight = 1;
        public int levelCap = 100;
        public int partySize = 6;
        public boolean itemsAllowed = true;
        public List<PokemonSet> party = new ArrayList<>();
    }

    public static class PokemonSet {
        public String species = "mewtwo";
        public int level = 100;
        public String nature = "hardy";
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
                ENABLED = root.enabled;
                CHECK_INTERVAL_MINUTES = Math.max(1, root.checkIntervalMinutes);
                EVENT_CHANCE = Math.max(0D, Math.min(1D, root.eventChance));
                MAX_ACTIVE_EVENTS = Math.max(5, root.maxActiveEvents);
                OVERWORLD_ONLY = root.overworldOnly;
                REQUIRE_FLAN_UNCLAIMED = root.requireFlanUnclaimed;
                ANNOUNCE_TELEPORT_BUTTON = root.announceTeleportButton;
                REPEAT_EVENT_WEIGHT_MULTIPLIER = Math.max(0D, Math.min(1D, root.repeatEventWeightMultiplier));
                Root defaults = defaultRoot();
                if (root.events == null || root.events.isEmpty()) {
                    EVENTS = defaults.events;
                } else {
                    for (Map.Entry<String, EventDefinition> entry : defaults.events.entrySet()) {
                        root.events.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                    EVENTS = root.events;
                }
                hardenEventPools();
                normalizeRewardCrates();
            }
            System.out.println("[ChampUtils] Loaded " + EVENTS.size() + " world event definitions.");
        } catch (Exception e) {
            e.printStackTrace();
            EVENTS = defaultRoot().events;
            hardenEventPools();
            normalizeRewardCrates();
        }
    }

    private static void hardenEventPools() {
        forcePool("molten_siege", "Molten Siege 32-Pokémon Mythic Pool", poolFire());
        forcePool("abyssal_storm", "Abyssal Storm 32-Pokémon Mythic Pool", poolWater());
        forcePool("verdant_collapse", "Verdant Collapse 32-Pokémon Mythic Pool", poolGrass());
        forcePool("void_invasion", "Void Invasion 32-Pokémon Mythic Pool", poolVoid());
        forcePool("iron_uprising", "Iron Uprising 32-Pokémon Mythic Pool", poolSteel());
    }

    private static void forcePool(String eventId, String teamName, List<PokemonSet> pool) {
        EventDefinition event = EVENTS.get(eventId);
        if (event == null) return;
        if (event.teams == null) event.teams = new ArrayList<>();
        TeamDefinition team = event.teams.isEmpty() ? new TeamDefinition() : event.teams.get(0);
        team.name = teamName;
        team.weight = 1;
        team.levelCap = 100;
        team.partySize = 6;
        team.itemsAllowed = true;
        team.party = new ArrayList<>(pool);
        event.teams.clear();
        event.teams.add(team);
    }

    private static void normalizeRewardCrates() {
        for (EventDefinition event : EVENTS.values()) {
            if (event == null) continue;
            if (event.rewards == null) event.rewards = defaultRewards();
            if (event.rewards.crateCreditId == null || event.rewards.crateCreditId.isBlank() || event.rewards.crateCreditId.equalsIgnoreCase("world_boss")) {
                event.rewards.crateCreditId = "event";
            }
            if (event.rewards.crateCredits <= 0) event.rewards.crateCredits = 1;
        }
    }

    private static void createDefault(File file) { try (FileWriter writer = new FileWriter(file)) { GSON.toJson(defaultRoot(), writer); } catch (Exception e) { e.printStackTrace(); } }

    private static Root defaultRoot() {
        Root root = new Root();
        root.events.put("molten_siege", event("Molten Siege", "Inferno Warlord", 25, poolFire()));
        root.events.put("abyssal_storm", event("Abyssal Storm", "Abyssal Tyrant", 25, poolWater()));
        root.events.put("verdant_collapse", event("Verdant Collapse", "Elder Bloom Tyrant", 20, poolGrass()));
        root.events.put("void_invasion", event("Void Invasion", "Void Rift Monarch", 20, poolVoid()));
        root.events.put("iron_uprising", event("Iron Uprising", "Chrome Apex", 10, poolSteel()));
        return root;
    }

    private static EventDefinition event(String display, String boss, int weight, List<PokemonSet> pool) {
        EventDefinition e = new EventDefinition();
        e.displayName = display; e.bossName = boss; e.weight = weight; e.rewards = defaultRewards();
        TeamDefinition t = new TeamDefinition(); t.name = display + " 32-Pokémon Extreme Pool"; t.party.addAll(pool); e.teams.add(t);
        return e;
    }

    private static RewardTable defaultRewards() {
        RewardTable r = new RewardTable();
        r.minFragments = 5; r.maxFragments = 10; r.crateCreditId = "event"; r.crateCredits = 1;
        r.fragmentWeights.put("EPIC", 65); r.fragmentWeights.put("LEGENDARY", 30); r.fragmentWeights.put("MYTHIC", 5);
        return r;
    }

    private static List<PokemonSet> poolFire() { return pool(
            mon("groudon","adamant","drought","red_orb","precipice_blades","heat_crash","stone_edge","swords_dance"),
            mon("koraidon","jolly","orichalcum_pulse","choice_scarf","collision_course","flare_blitz","dragon_claw","u_turn"),
            mon("ho_oh","careful","regenerator","heavy_duty_boots","sacred_fire","brave_bird","earthquake","recover"),
            mon("reshiram","timid","turboblaze","choice_specs","blue_flare","draco_meteor","earth_power","roost"),
            mon("chi_yu","timid","beads_of_ruin","choice_specs","overheat","dark_pulse","psychic","flamethrower"),
            mon("heatran","modest","flash_fire","air_balloon","magma_storm","earth_power","flash_cannon","taunt"),
            mon("volcarona","timid","flame_body","heavy_duty_boots","quiver_dance","fiery_dance","bug_buzz","giga_drain"),
            mon("charizard","timid","solar_power","life_orb","fire_blast","air_slash","solar_beam","roost"),
            mon("walking_wake","timid","protosynthesis","booster_energy","hydro_steam","draco_meteor","flamethrower","dragon_pulse"),
            mon("roaring_moon","jolly","protosynthesis","booster_energy","dragon_dance","crunch","acrobatics","earthquake"),
            mon("flutter_mane","timid","protosynthesis","booster_energy","moonblast","shadow_ball","mystical_fire","calm_mind"),
            mon("great_tusk","jolly","protosynthesis","booster_energy","headlong_rush","close_combat","rapid_spin","knock_off"),
            mon("landorus_therian","jolly","intimidate","choice_scarf","earthquake","stone_edge","u_turn","knock_off"),
            mon("zacian","jolly","intrepid_sword","rusted_sword","behemoth_blade","play_rough","close_combat","swords_dance"),
            mon("rayquaza","jolly","air_lock","life_orb","dragon_ascent","earthquake","extreme_speed","dragon_dance"),
            mon("mewtwo","timid","pressure","life_orb","psystrike","ice_beam","aura_sphere","calm_mind"),
            mon("necrozma_dusk_mane","adamant","prism_armor","weakness_policy","sunsteel_strike","earthquake","dragon_dance","morning_sun"),
            mon("solgaleo","jolly","full_metal_body","weakness_policy","sunsteel_strike","earthquake","flare_blitz","morning_sun"),
            mon("blaziken","jolly","speed_boost","life_orb","swords_dance","flare_blitz","close_combat","protect"),
            mon("cinderace","jolly","libero","heavy_duty_boots","pyro_ball","high_jump_kick","u_turn","sucker_punch"),
            mon("iron_moth","timid","quark_drive","booster_energy","fiery_dance","sludge_wave","energy_ball","agility"),
            mon("entei","adamant","inner_focus","choice_band","sacred_fire","extreme_speed","stone_edge","stomping_tantrum"),
            mon("moltres","timid","flame_body","heavy_duty_boots","hurricane","flamethrower","roost","will_o_wisp"),
            mon("garchomp","jolly","rough_skin","rocky_helmet","earthquake","dragon_claw","stone_edge","swords_dance"),
            mon("dragonite","adamant","multiscale","weakness_policy","dragon_dance","dual_wingbeat","earthquake","extreme_speed"),
            mon("kingambit","adamant","supreme_overlord","black_glasses","kowtow_cleave","sucker_punch","iron_head","swords_dance"),
            mon("gholdengo","timid","good_as_gold","choice_scarf","make_it_rain","shadow_ball","focus_blast","trick"),
            mon("iron_valiant","timid","quark_drive","booster_energy","moonblast","aura_sphere","thunderbolt","calm_mind"),
            mon("kartana","jolly","beast_boost","choice_scarf","leaf_blade","smart_strike","sacred_sword","knock_off"),
            mon("urshifu","jolly","unseen_fist","choice_band","wicked_blow","close_combat","sucker_punch","u_turn"),
            mon("terapagos","modest","tera_shift","leftovers","tera_starstorm","earth_power","calm_mind","protect"),
            mon("eternatus","timid","pressure","black_sludge","dynamax_cannon","sludge_bomb","flamethrower","recover")); }

    private static List<PokemonSet> poolWater(){ return pool(
            mon("kyogre","modest","drizzle","choice_scarf","water_spout","origin_pulse","ice_beam","thunder"),
            mon("palkia","timid","pressure","lustrous_orb","spacial_rend","hydro_pump","thunder","fire_blast"),
            mon("lugia","bold","multiscale","heavy_duty_boots","aeroblast","ice_beam","calm_mind","recover"),
            mon("suicune","bold","pressure","leftovers","scald","calm_mind","rest","sleep_talk"),
            mon("urshifu_rapid_strike","jolly","unseen_fist","choice_band","surging_strikes","close_combat","aqua_jet","u_turn"),
            mon("walking_wake","timid","protosynthesis","booster_energy","hydro_steam","draco_meteor","flamethrower","dragon_pulse"),
            mon("greninja","timid","torrent","life_orb","hydro_pump","dark_pulse","ice_beam","spikes"),
            mon("palafin","adamant","zero_to_hero","choice_band","jet_punch","wave_crash","close_combat","ice_punch"),
            mon("barraskewda","adamant","swift_swim","choice_band","liquidation","close_combat","psychic_fangs","aqua_jet"),
            mon("pelipper","bold","drizzle","damp_rock","hurricane","hydro_pump","u_turn","roost"),
            mon("ferrothorn","relaxed","iron_barbs","leftovers","power_whip","gyro_ball","leech_seed","protect"),
            mon("zapdos","timid","static","heavy_duty_boots","thunder","hurricane","roost","heat_wave"),
            mon("tornadus_therian","timid","regenerator","heavy_duty_boots","hurricane","weather_ball","knock_off","u_turn"),
            mon("landorus_therian","jolly","intimidate","choice_scarf","earthquake","stone_edge","u_turn","knock_off"),
            mon("miraidon","timid","hadron_engine","choice_specs","electro_drift","draco_meteor","overheat","volt_switch"),
            mon("iron_bundle","timid","quark_drive","booster_energy","hydro_pump","freeze_dry","ice_beam","encore"),
            mon("tapufini","calm","misty_surge","leftovers","moonblast","surf","calm_mind","taunt"),
            mon("manaphy","timid","hydration","leftovers","tail_glow","surf","ice_beam","energy_ball"),
            mon("azumarill","adamant","huge_power","sitrus_berry","belly_drum","aqua_jet","play_rough","knock_off"),
            mon("toxapex","bold","regenerator","black_sludge","scald","toxic","recover","haze"),
            mon("rotom_wash","bold","levitate","leftovers","hydro_pump","volt_switch","will_o_wisp","protect"),
            mon("swampert","adamant","swift_swim","life_orb","waterfall","earthquake","ice_punch","stealth_rock"),
            mon("kingdra","modest","swift_swim","choice_specs","hydro_pump","draco_meteor","hurricane","flash_cannon"),
            mon("keldeo","timid","justified","choice_specs","hydro_pump","secret_sword","icy_wind","air_slash"),
            mon("dragonite","adamant","multiscale","weakness_policy","dragon_dance","dual_wingbeat","earthquake","extreme_speed"),
            mon("rayquaza","jolly","air_lock","life_orb","dragon_ascent","earthquake","extreme_speed","dragon_dance"),
            mon("mewtwo","timid","pressure","life_orb","psystrike","ice_beam","aura_sphere","calm_mind"),
            mon("zacian","jolly","intrepid_sword","rusted_sword","behemoth_blade","play_rough","close_combat","swords_dance"),
            mon("calyrex_shadow","timid","as_one","choice_specs","astral_barrage","psyshock","draining_kiss","trick"),
            mon("deoxys_attack","naive","pressure","focus_sash","psycho_boost","superpower","ice_beam","extreme_speed"),
            mon("eternatus","timid","pressure","black_sludge","dynamax_cannon","sludge_bomb","flamethrower","recover"),
            mon("terapagos","modest","tera_shift","leftovers","tera_starstorm","earth_power","calm_mind","protect")); }

    private static List<PokemonSet> poolGrass(){ return pool(
            mon("arceus_grass","timid","multitype","meadow_plate","judgment","earth_power","calm_mind","recover"),
            mon("zarude","jolly","leaf_guard","choice_band","power_whip","darkest_lariat","close_combat","u_turn"),
            mon("rillaboom","adamant","grassy_surge","choice_band","grassy_glide","wood_hammer","knock_off","u_turn"),
            mon("ogerpon_wellspring","jolly","water_absorb","wellspring_mask","ivy_cudgel","power_whip","play_rough","swords_dance"),
            mon("ogerpon_hearthflame","jolly","mold_breaker","hearthflame_mask","ivy_cudgel","power_whip","play_rough","swords_dance"),
            mon("kartana","jolly","beast_boost","choice_scarf","leaf_blade","smart_strike","sacred_sword","knock_off"),
            mon("shaymin_sky","timid","serene_grace","life_orb","seed_flare","air_slash","earth_power","healing_wish"),
            mon("venusaur","timid","chlorophyll","life_orb","growth","giga_drain","sludge_bomb","earth_power"),
            mon("amoonguss","bold","regenerator","black_sludge","spore","giga_drain","sludge_bomb","foul_play"),
            mon("ferrothorn","relaxed","iron_barbs","leftovers","power_whip","gyro_ball","leech_seed","protect"),
            mon("tangrowth","bold","regenerator","rocky_helmet","giga_drain","knock_off","focus_blast","sleep_powder"),
            mon("landorus_therian","jolly","intimidate","choice_scarf","earthquake","stone_edge","u_turn","knock_off"),
            mon("groudon","adamant","drought","leftovers","precipice_blades","stone_edge","fire_punch","swords_dance"),
            mon("koraidon","jolly","orichalcum_pulse","choice_scarf","collision_course","flare_blitz","dragon_claw","u_turn"),
            mon("flutter_mane","timid","protosynthesis","booster_energy","moonblast","shadow_ball","mystical_fire","calm_mind"),
            mon("great_tusk","jolly","protosynthesis","booster_energy","headlong_rush","close_combat","rapid_spin","knock_off"),
            mon("roaring_moon","jolly","protosynthesis","booster_energy","dragon_dance","crunch","acrobatics","earthquake"),
            mon("zacian","jolly","intrepid_sword","rusted_sword","behemoth_blade","play_rough","close_combat","swords_dance"),
            mon("xerneas","modest","fairy_aura","power_herb","geomancy","moonblast","thunderbolt","focus_blast"),
            mon("zygarde","careful","power_construct","leftovers","thousand_arrows","coil","glare","rest"),
            mon("lugia","bold","multiscale","heavy_duty_boots","aeroblast","ice_beam","calm_mind","recover"),
            mon("ho_oh","careful","regenerator","heavy_duty_boots","sacred_fire","brave_bird","earthquake","recover"),
            mon("rayquaza","jolly","air_lock","life_orb","dragon_ascent","earthquake","extreme_speed","dragon_dance"),
            mon("dragonite","adamant","multiscale","weakness_policy","dragon_dance","dual_wingbeat","earthquake","extreme_speed"),
            mon("kingambit","adamant","supreme_overlord","black_glasses","kowtow_cleave","sucker_punch","iron_head","swords_dance"),
            mon("gholdengo","timid","good_as_gold","choice_scarf","make_it_rain","shadow_ball","focus_blast","trick"),
            mon("iron_valiant","timid","quark_drive","booster_energy","moonblast","aura_sphere","thunderbolt","calm_mind"),
            mon("mewtwo","timid","pressure","life_orb","psystrike","ice_beam","aura_sphere","calm_mind"),
            mon("calyrex_shadow","timid","as_one","choice_specs","astral_barrage","psyshock","draining_kiss","trick"),
            mon("deoxys_attack","naive","pressure","focus_sash","psycho_boost","superpower","ice_beam","extreme_speed"),
            mon("eternatus","timid","pressure","black_sludge","dynamax_cannon","sludge_bomb","flamethrower","recover"),
            mon("terapagos","modest","tera_shift","leftovers","tera_starstorm","earth_power","calm_mind","protect")); }

    private static List<PokemonSet> poolVoid(){ return pool(
            mon("calyrex_shadow","timid","as_one","choice_specs","astral_barrage","psyshock","draining_kiss","trick"),
            mon("necrozma_dawn_wings","timid","prism_armor","power_herb","moongeist_beam","photon_geyser","earth_power","meteor_beam"),
            mon("lunala","timid","shadow_shield","heavy_duty_boots","moongeist_beam","psyshock","calm_mind","roost"),
            mon("giratina_origin","modest","levitate","griseous_orb","shadow_ball","draco_meteor","earth_power","will_o_wisp"),
            mon("darkrai","timid","bad_dreams","life_orb","dark_pulse","sludge_bomb","focus_blast","nasty_plot"),
            mon("hoopa_unbound","naive","magician","choice_scarf","hyperspace_fury","psychic","focus_blast","gunk_shot"),
            mon("mewtwo","timid","pressure","life_orb","psystrike","ice_beam","aura_sphere","calm_mind"),
            mon("deoxys_attack","naive","pressure","focus_sash","psycho_boost","superpower","ice_beam","extreme_speed"),
            mon("flutter_mane","timid","protosynthesis","booster_energy","moonblast","shadow_ball","mystical_fire","calm_mind"),
            mon("blacephalon","timid","beast_boost","choice_scarf","shadow_ball","fire_blast","psyshock","trick"),
            mon("nihilego","timid","beast_boost","power_herb","meteor_beam","sludge_wave","thunderbolt","grass_knot"),
            mon("naganadel","timid","beast_boost","life_orb","nasty_plot","draco_meteor","sludge_wave","fire_blast"),
            mon("guzzlord","modest","beast_boost","assault_vest","draco_meteor","dark_pulse","sludge_bomb","flamethrower"),
            mon("xurkitree","timid","beast_boost","choice_scarf","thunderbolt","energy_ball","dazzling_gleam","volt_switch"),
            mon("kartana","jolly","beast_boost","choice_scarf","leaf_blade","smart_strike","sacred_sword","knock_off"),
            mon("celesteela","careful","beast_boost","leftovers","heavy_slam","leech_seed","protect","flamethrower"),
            mon("pheromosa","naive","beast_boost","life_orb","close_combat","u_turn","ice_beam","poison_jab"),
            mon("buzzwole","impish","beast_boost","rocky_helmet","close_combat","ice_punch","roost","bulk_up"),
            mon("stakataka","brave","beast_boost","life_orb","trick_room","gyro_ball","stone_edge","earthquake"),
            mon("eternatus","timid","pressure","black_sludge","dynamax_cannon","sludge_bomb","flamethrower","recover"),
            mon("miraidon","timid","hadron_engine","choice_specs","electro_drift","draco_meteor","overheat","volt_switch"),
            mon("terapagos","modest","tera_shift","leftovers","tera_starstorm","earth_power","calm_mind","protect"),
            mon("gengar","timid","cursed_body","life_orb","shadow_ball","sludge_wave","focus_blast","nasty_plot"),
            mon("dragapult","timid","infiltrator","choice_specs","shadow_ball","draco_meteor","flamethrower","u_turn"),
            mon("gholdengo","timid","good_as_gold","choice_scarf","make_it_rain","shadow_ball","focus_blast","trick"),
            mon("kingambit","adamant","supreme_overlord","black_glasses","kowtow_cleave","sucker_punch","iron_head","swords_dance"),
            mon("yveltal","timid","dark_aura","heavy_duty_boots","dark_pulse","oblivion_wing","heat_wave","roost"),
            mon("marshadow","jolly","technician","life_orb","spectral_thief","close_combat","shadow_sneak","bulk_up"),
            mon("zacian","jolly","intrepid_sword","rusted_sword","behemoth_blade","play_rough","close_combat","swords_dance"),
            mon("rayquaza","jolly","air_lock","life_orb","dragon_ascent","earthquake","extreme_speed","dragon_dance"),
            mon("lugia","bold","multiscale","heavy_duty_boots","aeroblast","ice_beam","calm_mind","recover"),
            mon("zygarde","careful","power_construct","leftovers","thousand_arrows","coil","glare","rest")); }

    private static List<PokemonSet> poolSteel(){ return pool(
            mon("zacian","jolly","intrepid_sword","rusted_sword","behemoth_blade","play_rough","close_combat","swords_dance"),
            mon("necrozma_dusk_mane","adamant","prism_armor","weakness_policy","sunsteel_strike","earthquake","dragon_dance","morning_sun"),
            mon("solgaleo","jolly","full_metal_body","weakness_policy","sunsteel_strike","earthquake","flare_blitz","morning_sun"),
            mon("dialga","modest","pressure","adamant_orb","draco_meteor","flash_cannon","thunderbolt","fire_blast"),
            mon("magearna","modest","soul_heart","leftovers","shift_gear","fleur_cannon","flash_cannon","thunderbolt"),
            mon("melmetal","adamant","iron_fist","assault_vest","double_iron_bash","earthquake","ice_punch","thunder_punch"),
            mon("genesect","hasty","download","choice_scarf","u_turn","iron_head","ice_beam","flamethrower"),
            mon("metagross","jolly","clear_body","weakness_policy","meteor_mash","zen_headbutt","earthquake","agility"),
            mon("heatran","modest","flash_fire","air_balloon","magma_storm","earth_power","flash_cannon","taunt"),
            mon("gholdengo","timid","good_as_gold","choice_scarf","make_it_rain","shadow_ball","focus_blast","trick"),
            mon("kingambit","adamant","supreme_overlord","black_glasses","kowtow_cleave","sucker_punch","iron_head","swords_dance"),
            mon("kartana","jolly","beast_boost","choice_scarf","leaf_blade","smart_strike","sacred_sword","knock_off"),
            mon("celesteela","careful","beast_boost","leftovers","heavy_slam","leech_seed","protect","flamethrower"),
            mon("stakataka","brave","beast_boost","life_orb","trick_room","gyro_ball","stone_edge","earthquake"),
            mon("iron_treads","jolly","quark_drive","booster_energy","earthquake","iron_head","rapid_spin","knock_off"),
            mon("iron_moth","timid","quark_drive","booster_energy","fiery_dance","sludge_wave","energy_ball","agility"),
            mon("iron_bundle","timid","quark_drive","booster_energy","hydro_pump","freeze_dry","ice_beam","encore"),
            mon("iron_valiant","timid","quark_drive","booster_energy","moonblast","aura_sphere","thunderbolt","calm_mind"),
            mon("miraidon","timid","hadron_engine","choice_specs","electro_drift","draco_meteor","overheat","volt_switch"),
            mon("regieleki","timid","transistor","choice_specs","thunderbolt","volt_switch","rapid_spin","ancient_power"),
            mon("regidrago","modest","dragons_maw","choice_specs","dragon_energy","draco_meteor","dragon_pulse","earth_power"),
            mon("ferrothorn","relaxed","iron_barbs","leftovers","power_whip","gyro_ball","leech_seed","protect"),
            mon("corviknight","impish","mirror_armor","leftovers","brave_bird","body_press","roost","defog"),
            mon("excadrill","jolly","mold_breaker","air_balloon","earthquake","iron_head","rapid_spin","swords_dance"),
            mon("magnezone","modest","sturdy","choice_specs","thunderbolt","flash_cannon","volt_switch","body_press"),
            mon("scizor","adamant","technician","choice_band","bullet_punch","u_turn","close_combat","knock_off"),
            mon("garchomp","jolly","rough_skin","rocky_helmet","earthquake","dragon_claw","stone_edge","swords_dance"),
            mon("landorus_therian","jolly","intimidate","choice_scarf","earthquake","stone_edge","u_turn","knock_off"),
            mon("kyogre","modest","drizzle","choice_scarf","water_spout","origin_pulse","ice_beam","thunder"),
            mon("groudon","adamant","drought","leftovers","precipice_blades","stone_edge","fire_punch","swords_dance"),
            mon("calyrex_shadow","timid","as_one","choice_specs","astral_barrage","psyshock","draining_kiss","trick"),
            mon("terapagos","modest","tera_shift","leftovers","tera_starstorm","earth_power","calm_mind","protect")); }

    private static List<PokemonSet> pool(PokemonSet... mons){ List<PokemonSet> out=new ArrayList<>(); for(PokemonSet p:mons) out.add(p); return out; }
    private static PokemonSet mon(String species,String nature,String ability,String item,String... moves){ PokemonSet p=new PokemonSet(); p.species=species; p.level=100; p.nature=nature; p.ability=ability; p.heldItem=item; p.ivs=new StatSet(31,31,31,31,31,31); p.evs=new StatSet(252,252,252,252,252,252); for(String m:moves) p.moves.add(m); return p; }
}
