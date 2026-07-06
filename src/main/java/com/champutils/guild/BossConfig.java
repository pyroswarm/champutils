package com.champutils.guild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BossConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/bosses.json");

    public static Data DATA = new Data();

    private BossConfig() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) {
                DATA = new Data();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                DATA = loaded == null ? new Data() : loaded;
            }
            DATA.normalize();
            save();
        } catch (Exception e) {
            e.printStackTrace();
            DATA = new Data();
            save();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            DATA.normalize();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(DATA, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static final class Data {
        public DailyResetSettings dailyReset = DailyResetSettings.defaults();
        public GuildBossSettings guildBoss = GuildBossSettings.guildDefaults();
        public WorldBossSettings worldBoss = WorldBossSettings.defaults();

        void normalize() {
            if (dailyReset == null) dailyReset = DailyResetSettings.defaults();
            if (guildBoss == null) guildBoss = GuildBossSettings.guildDefaults();
            if (worldBoss == null) worldBoss = WorldBossSettings.defaults();
            dailyReset.normalize();
            guildBoss.normalize();
            worldBoss.normalize();
        }
    }

    public static final class DailyResetSettings {
        /** Shared reset time used by guild bosses now and future daily systems later. */
        public int hour = 2;
        public int minute = 0;
        /** Use "system" to follow the server JVM's local timezone. */
        public String timeZone = "system";

        static DailyResetSettings defaults() { return new DailyResetSettings(); }
        void normalize() {
            if (hour < 0 || hour > 23) hour = 2;
            if (minute < 0 || minute > 59) minute = 0;
            if (timeZone == null || timeZone.isBlank()) timeZone = "system";
        }
    }

    public static class BossSettings {
        public boolean enabled = true;
        public double scaleModifier = 2.5D;
        public int level = 100;
        public int aliveMinutes = 15;
        /** Legacy field kept so older bosses.json files still load. Guild bosses now use dailyReset. */
        public int cooldownMinutes = 1440;
        public int countRadiusBlocks = 96;
        public List<BossPokemon> pool = new ArrayList<>();
        public List<RewardTier> rewardTiers = new ArrayList<>();

        void normalize() {
            if (scaleModifier <= 0D) scaleModifier = 2.5D;
            if (level <= 0) level = 100;
            if (aliveMinutes <= 0) aliveMinutes = 15;
            if (cooldownMinutes <= 0) cooldownMinutes = 1440;
            if (countRadiusBlocks <= 0) countRadiusBlocks = 96;
            if (pool == null || pool.isEmpty()) pool = defaultGuildPool();
            if (rewardTiers == null || rewardTiers.isEmpty()) rewardTiers = defaultRewardTiers();
            pool.forEach(BossPokemon::normalize);
            rewardTiers.forEach(RewardTier::normalize);
        }
    }


    public static final class GuildBossSettings extends BossSettings {
        /** Guild bosses mirror world bosses by using themed trainer parties. */
        public int partySize = 6;
        public List<WorldBossTheme> themes = new ArrayList<>();

        static GuildBossSettings guildDefaults() {
            GuildBossSettings s = new GuildBossSettings();
            s.pool = defaultGuildPool();
            s.themes = defaultWorldThemes();
            s.partySize = 6;
            s.rewardTiers = defaultRewardTiers();
            return s;
        }

        @Override
        void normalize() {
            super.normalize();
            if (partySize <= 0 || partySize > 6) partySize = 6;
            if (themes == null || themes.isEmpty()) themes = defaultWorldThemes();
            themes.forEach(WorldBossTheme::normalize);
        }
    }

    public static final class WorldBossSettings extends BossSettings {
        public int averageMinutesUntilNextBoss = 720;
        /** Saved so the scoreboard can show the last world boss spawn even after restart. */
        public long lastSpawnAtMillis = 0L;

        /**
         * The boss will spawn at this exact coordinate in every dimension listed in spawnDimensions.
         * This lets spawn1, spawn2, spawn3, etc. all use the same arena location without duplicating X/Y/Z.
         */
        public SpawnLocation spawnLocation = new SpawnLocation(0.5D, 80D, 0.5D);

        /**
         * Vanilla yaw used by the world boss NPC in every configured spawn world.
         * Example: a player facing west will usually be about 90 degrees.
         */
        public float yaw = 180.0F;

        public List<String> spawnDimensions = new ArrayList<>();

        /**
         * Legacy support for older bosses.json files that used one full coordinate per dimension.
         * On load, these are migrated into spawnDimensions + spawnLocation.
         */
        public List<SpawnPoint> spawnPoints = new ArrayList<>();
        public int partySize = 6;
        public List<WorldBossTheme> themes = new ArrayList<>();

        static WorldBossSettings defaults() {
            WorldBossSettings s = new WorldBossSettings();
            s.pool = defaultWorldPool();
            s.themes = defaultWorldThemes();
            s.partySize = 6;
            s.rewardTiers = defaultRewardTiers();
            s.spawnDimensions.add("multiworld:spawn1");
            s.spawnLocation = new SpawnLocation(0.5D, 80D, 0.5D);
            s.yaw = 180.0F;
            s.averageMinutesUntilNextBoss = 720;
            return s;
        }

        @Override
        void normalize() {
            super.normalize();
            if (pool == null || pool.isEmpty() || looksLikeDefaultGuildPool(pool)) pool = defaultWorldPool();
            pool.forEach(BossPokemon::normalize);
            // Keep world bosses at roughly a 12-hour average. Older beta configs sometimes saved
            // tiny test values like 8 minutes; migrate those automatically.
            if (averageMinutesUntilNextBoss <= 0 || averageMinutesUntilNextBoss < 60) averageMinutesUntilNextBoss = 720;
            if (partySize <= 0 || partySize > 6) partySize = 6;
            if (themes == null || themes.isEmpty()) themes = defaultWorldThemes();
            themes.forEach(WorldBossTheme::normalize);
            if (lastSpawnAtMillis < 0L) lastSpawnAtMillis = 0L;
            if (Float.isNaN(yaw) || Float.isInfinite(yaw)) yaw = 180.0F;
            yaw = normalizeYaw(yaw);
            if (spawnLocation == null) spawnLocation = new SpawnLocation(0.5D, 80D, 0.5D);
            spawnLocation.normalize();

            if (spawnDimensions == null) spawnDimensions = new ArrayList<>();

            // Backward compatibility: if an existing config only has spawnPoints, migrate it.
            if (spawnDimensions.isEmpty() && spawnPoints != null && !spawnPoints.isEmpty()) {
                SpawnPoint first = null;
                for (BossConfig.SpawnPoint point : spawnPoints) {
                    if (point == null) continue;
                    point.normalize();
                    if (first == null) first = point;
                    if (point.dimension != null && !point.dimension.isBlank() && !spawnDimensions.contains(point.dimension)) {
                        spawnDimensions.add(point.dimension);
                    }
                }
                if (first != null) spawnLocation = new SpawnLocation(first.x, first.y, first.z);
            }

            if (spawnDimensions.isEmpty()) spawnDimensions.add("multiworld:spawn1");
            spawnDimensions.removeIf(d -> d == null || d.isBlank());
            if (spawnDimensions.isEmpty()) spawnDimensions.add("multiworld:spawn1");

            // Rebuild legacy spawnPoints from the shared location so saved configs are obvious and old readers still work.
            spawnPoints = new ArrayList<>();
            for (String dimension : spawnDimensions) {
                spawnPoints.add(new SpawnPoint(dimension, spawnLocation.x, spawnLocation.y, spawnLocation.z));
            }
        }

        private static float normalizeYaw(float value) {
            float normalized = value % 360.0F;
            if (normalized < -180.0F) normalized += 360.0F;
            if (normalized >= 180.0F) normalized -= 360.0F;
            return normalized;
        }
    }

    public static final class WorldBossTheme {
        public String name;
        public String type;
        public String displayName;
        public List<BossPokemon> pool = new ArrayList<>();

        public WorldBossTheme() {}
        public WorldBossTheme(String name, String type, String displayName, List<BossPokemon> pool) {
            this.name = name;
            this.type = type;
            this.displayName = displayName;
            this.pool = pool;
        }

        void normalize() {
            if (name == null || name.isBlank()) name = themedDefaultName(type);
            if (type == null || type.isBlank()) type = "Mixed";
            if (displayName == null || displayName.isBlank() || displayName.startsWith("World Boss ") || displayName.endsWith(" Theme")) {
                displayName = type + " Boss " + name;
            }
            if (pool == null || pool.isEmpty()) pool = defaultWorldPool();
            pool.forEach(BossPokemon::normalize);
        }
    }

    public static final class SpawnLocation {
        public double x;
        public double y;
        public double z;

        public SpawnLocation() {}
        public SpawnLocation(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        void normalize() {
            if (Double.isNaN(x) || Double.isInfinite(x)) x = 0.5D;
            if (Double.isNaN(y) || Double.isInfinite(y)) y = 80D;
            if (Double.isNaN(z) || Double.isInfinite(z)) z = 0.5D;
        }
    }

    public static final class SpawnPoint {
        public String dimension;
        public double x;
        public double y;
        public double z;

        public SpawnPoint() {}
        public SpawnPoint(String dimension, double x, double y, double z) {
            this.dimension = dimension; this.x = x; this.y = y; this.z = z;
        }
        void normalize() {
            if (dimension == null || dimension.isBlank()) dimension = "multiworld:spawn1";
        }
    }

    public static final class BossPokemon {
        public String species;
        public int weight = 1;
        public String nature = "adamant";
        public String ability = "";
        public String heldItem = "";
        /** Team role used by structured boss parties. Valid values: lead/setup, sweeper, anchor. */
        public String role = "sweeper";
        public EvSpread evs = new EvSpread(252, 252, 252, 252, 252, 252);
        public List<String> moves = new ArrayList<>();
        public String extraProperties = "";

        public BossPokemon() {}
        public BossPokemon(String species, String nature, String heldItem, String ability, String... moves) {
            this.species = species;
            this.nature = nature;
            this.heldItem = heldItem;
            this.ability = ability;
            this.moves = new ArrayList<>(Arrays.asList(moves));
        }

        public BossPokemon withWeight(int weight) {
            this.weight = weight;
            return this;
        }

        public BossPokemon withEvs(int hp, int attack, int defence, int specialAttack, int specialDefence, int speed) {
            this.evs = new EvSpread(hp, attack, defence, specialAttack, specialDefence, speed);
            return this;
        }

        void normalize() {
            if (species == null || species.isBlank()) species = "mewtwo";
            if (weight <= 0) weight = 1;
            if (nature == null) nature = "";
            if (ability == null) ability = "";
            if (heldItem == null) heldItem = "";
            if (role == null || role.isBlank()) role = "sweeper";
            role = normalizeRole(role);
            if (evs == null) evs = new EvSpread(252, 252, 252, 252, 252, 252);
            evs.normalize();
            if (moves == null) moves = new ArrayList<>();
            if (extraProperties == null) extraProperties = "";
        }
    }

    public static final class EvSpread {
        public int hp;
        public int attack;
        public int defence;
        public int specialAttack;
        public int specialDefence;
        public int speed;

        public EvSpread() {}
        public EvSpread(int hp, int attack, int defence, int specialAttack, int specialDefence, int speed) {
            this.hp = hp;
            this.attack = attack;
            this.defence = defence;
            this.specialAttack = specialAttack;
            this.specialDefence = specialDefence;
            this.speed = speed;
        }

        void normalize() {
            hp = clampEv(hp);
            attack = clampEv(attack);
            defence = clampEv(defence);
            specialAttack = clampEv(specialAttack);
            specialDefence = clampEv(specialDefence);
            speed = clampEv(speed);
        }

        private static int clampEv(int value) {
            return Math.max(0, Math.min(252, value));
        }
    }

    public static final class RewardTier {
        public int minDefeats;
        public String crateId;
        public int crateCredits;

        public RewardTier() {}
        public RewardTier(int minDefeats, String crateId, int crateCredits) {
            this.minDefeats = minDefeats; this.crateId = crateId; this.crateCredits = crateCredits;
        }
        void normalize() {
            if (minDefeats < 1) minDefeats = 1;
            if (crateId == null || crateId.isBlank()) crateId = "d";
            if (crateCredits < 1) crateCredits = 1;
        }
    }

    private static BossPokemon boss(String species, String nature, String heldItem, String ability, int hp, int attack, int defence, int specialAttack, int specialDefence, int speed, String... moves) {
        return new BossPokemon(species, nature, heldItem, ability, moves).withEvs(hp, attack, defence, specialAttack, specialDefence, speed);
    }

    private static String normalizeRole(String role) {
        String clean = role == null ? "" : role.trim().toLowerCase().replace('_', '-');
        if (clean.equals("lead") || clean.equals("setup") || clean.equals("lead-setup") || clean.equals("lead/setup")) return "lead/setup";
        if (clean.equals("anchor") || clean.equals("tank") || clean.equals("wall") || clean.equals("stall")) return "anchor";
        return "sweeper";
    }

    private static List<BossPokemon> defaultGuildPool() {
        return new ArrayList<>(List.of(
                boss("mewtwo", "timid", "life_orb", "unnerve", 0, 0, 4, 252, 0, 252, "psystrike", "aura_sphere", "ice_beam", "nasty_plot"),
                boss("rayquaza", "jolly", "life_orb", "air_lock", 0, 252, 0, 0, 4, 252, "dragon_ascent", "earthquake", "extreme_speed", "dragon_dance"),
                boss("koraidon", "jolly", "clear_amulet", "orichalcum_pulse", 0, 252, 0, 0, 4, 252, "collision_course", "flare_blitz", "dragon_claw", "swords_dance"),
                boss("miraidon", "timid", "choice_specs", "hadron_engine", 0, 0, 0, 252, 4, 252, "electro_drift", "draco_meteor", "flash_cannon", "volt_switch"),
                boss("flutter_mane", "timid", "booster_energy", "protosynthesis", 0, 0, 0, 252, 4, 252, "moonblast", "shadow_ball", "mystical_fire", "calm_mind"),
                boss("iron_valiant", "naive", "booster_energy", "quark_drive", 0, 252, 0, 4, 0, 252, "moonblast", "close_combat", "thunderbolt", "swords_dance"),
                boss("necrozma", "adamant", "weakness_policy", "prism_armor", 0, 252, 0, 0, 4, 252, "photon_geyser", "earthquake", "stone_edge", "dragon_dance"),
                boss("zacian", "jolly", "rusted_sword", "intrepid_sword", 0, 252, 0, 0, 4, 252, "behemoth_blade", "play_rough", "close_combat", "swords_dance"),
                boss("zamazenta", "jolly", "rusted_shield", "dauntless_shield", 0, 252, 4, 0, 0, 252, "behemoth_bash", "body_press", "crunch", "iron_defense"),
                boss("xerneas", "modest", "power_herb", "fairy_aura", 104, 0, 0, 252, 0, 152, "moonblast", "thunder", "focus_blast", "geomancy"),
                boss("yveltal", "timid", "heavy_duty_boots", "dark_aura", 0, 0, 0, 252, 4, 252, "dark_pulse", "oblivion_wing", "heat_wave", "nasty_plot"),
                boss("zygarde", "impish", "leftovers", "power_construct", 252, 0, 252, 0, 4, 0, "thousand_arrows", "coil", "glare", "rest"),
                boss("marshadow", "jolly", "life_orb", "technician", 0, 252, 0, 0, 4, 252, "spectral_thief", "close_combat", "shadow_sneak", "bulk_up"),
                boss("hoopa", "timid", "choice_specs", "magician", 0, 0, 0, 252, 4, 252, "hyperspace_hole", "dark_pulse", "focus_blast", "trick"),
                boss("darkrai", "timid", "life_orb", "bad_dreams", 0, 0, 0, 252, 4, 252, "dark_pulse", "sludge_bomb", "ice_beam", "nasty_plot"),
                boss("genesect", "naive", "choice_scarf", "download", 0, 252, 0, 4, 0, 252, "u_turn", "iron_head", "ice_beam", "flamethrower"),
                boss("deoxys", "naive", "focus_sash", "pressure", 0, 252, 0, 4, 0, 252, "psycho_boost", "superpower", "extreme_speed", "spikes"),
                boss("arceus", "adamant", "silk_scarf", "multitype", 0, 252, 0, 0, 4, 252, "extreme_speed", "shadow_claw", "earthquake", "swords_dance"),
                boss("terapagos", "modest", "leftovers", "tera_shell", 252, 0, 0, 252, 4, 0, "tera_starstorm", "earth_power", "calm_mind", "recover"),
                boss("gouging_fire", "jolly", "booster_energy", "protosynthesis", 0, 252, 0, 0, 4, 252, "flare_blitz", "dragon_claw", "earthquake", "dragon_dance"),
                boss("raging_bolt", "modest", "booster_energy", "protosynthesis", 252, 0, 0, 252, 4, 0, "thunderclap", "dragon_pulse", "thunderbolt", "calm_mind"),
                boss("iron_boulder", "jolly", "booster_energy", "quark_drive", 0, 252, 0, 0, 4, 252, "mighty_cleave", "close_combat", "earthquake", "swords_dance"),
                boss("iron_crown", "timid", "booster_energy", "quark_drive", 0, 0, 0, 252, 4, 252, "tachyon_cutter", "psyshock", "focus_blast", "calm_mind"),
                boss("roaring_moon", "jolly", "booster_energy", "protosynthesis", 0, 252, 0, 0, 4, 252, "knock_off", "dragon_claw", "earthquake", "dragon_dance")
        ));
    }


    private static String themedDefaultName(String type) {
        if (type == null) return "Titan";
        return switch (type.trim().toLowerCase()) {
            case "fire" -> "Molterra";
            case "water" -> "Tidalon";
            case "steel" -> "Ferron";
            case "grass" -> "Verdantis";
            case "electric" -> "Voltrax";
            case "ice" -> "Frostrix";
            case "ground" -> "Terradon";
            case "flying" -> "Aerovox";
            case "psychic" -> "Psyren";
            case "dark" -> "Umbrax";
            case "ghost" -> "Spectra";
            case "dragon" -> "Drakonos";
            case "poison" -> "Venomira";
            case "fairy" -> "Aurelia";
            case "rock" -> "Boulderex";
            case "bug" -> "Arachna";
            case "fighting" -> "Valorak";
            case "normal" -> "Obelisk";
            default -> "Titan";
        };
    }


    private static boolean looksLikeDefaultGuildPool(List<BossPokemon> pool) {
        if (pool == null || pool.size() != 4) return false;
        List<String> species = new ArrayList<>();
        for (BossPokemon pokemon : pool) species.add(pokemon == null ? "" : String.valueOf(pokemon.species).toLowerCase());
        return species.containsAll(Arrays.asList("dragonite", "garchomp", "metagross", "tyranitar"));
    }

    private static List<WorldBossTheme> defaultWorldThemes() {
        return new ArrayList<>(List.of(
                new WorldBossTheme("Molterra", "Fire", "Fire Boss Molterra", new ArrayList<>(List.of(
                        boss("gouging_fire", "jolly", "booster_energy", "protosynthesis", 0, 252, 0, 0, 4, 252, "flare_blitz", "dragon_claw", "earthquake", "dragon_dance"),
                        boss("cinderace", "jolly", "life_orb", "libero", 0, 252, 0, 0, 4, 252, "pyro_ball", "high_jump_kick", "sucker_punch", "u_turn"),
                        boss("ho_oh", "careful", "heavy_duty_boots", "regenerator", 248, 0, 8, 0, 252, 0, "sacred_fire", "brave_bird", "earthquake", "recover"),
                        boss("reshiram", "timid", "choice_specs", "turboblaze", 0, 0, 0, 252, 4, 252, "blue_flare", "draco_meteor", "earth_power", "overheat"),
                        boss("chi_yu", "timid", "choice_specs", "beads_of_ruin", 0, 0, 0, 252, 4, 252, "dark_pulse", "flamethrower", "overheat", "psychic"),
                        boss("volcarona", "timid", "heavy_duty_boots", "flame_body", 0, 0, 0, 252, 4, 252, "fiery_dance", "bug_buzz", "giga_drain", "quiver_dance")
                ))),
                new WorldBossTheme("Tidalon", "Water", "Water Boss Tidalon", new ArrayList<>(List.of(
                        boss("kyogre", "modest", "choice_specs", "drizzle", 0, 0, 0, 252, 4, 252, "water_spout", "origin_pulse", "ice_beam", "thunder"),
                        boss("palkia", "timid", "lustrous_orb", "pressure", 0, 0, 0, 252, 4, 252, "spacial_rend", "hydro_pump", "earth_power", "fire_blast"),
                        boss("walking_wake", "timid", "booster_energy", "protosynthesis", 0, 0, 0, 252, 4, 252, "hydro_steam", "draco_meteor", "flamethrower", "dragon_pulse"),
                        boss("urshifu", "jolly", "choice_band", "unseen_fist", 0, 252, 0, 0, 4, 252, "surging_strikes", "close_combat", "aqua_jet", "u_turn"),
                        boss("greninja", "timid", "life_orb", "protean", 0, 0, 0, 252, 4, 252, "hydro_pump", "dark_pulse", "ice_beam", "water_shuriken"),
                        boss("toxapex", "bold", "black_sludge", "regenerator", 252, 0, 252, 0, 4, 0, "scald", "sludge_bomb", "recover", "toxic")
                ))),
                new WorldBossTheme("Ferron", "Steel", "Steel Boss Ferron", new ArrayList<>(List.of(
                        boss("zacian", "jolly", "rusted_sword", "intrepid_sword", 0, 252, 0, 0, 4, 252, "behemoth_blade", "play_rough", "close_combat", "swords_dance"),
                        boss("dialga", "modest", "assault_vest", "pressure", 248, 0, 0, 252, 8, 0, "roar_of_time", "flash_cannon", "earth_power", "thunderbolt"),
                        boss("solgaleo", "adamant", "weakness_policy", "full_metal_body", 252, 252, 4, 0, 0, 0, "sunsteel_strike", "earthquake", "wild_charge", "morning_sun"),
                        boss("magearna", "modest", "leftovers", "soul_heart", 252, 0, 0, 252, 4, 0, "fleur_cannon", "flash_cannon", "thunderbolt", "shift_gear"),
                        boss("melmetal", "adamant", "assault_vest", "iron_fist", 252, 252, 4, 0, 0, 0, "double_iron_bash", "earthquake", "thunder_punch", "ice_punch"),
                        boss("metagross", "jolly", "life_orb", "clear_body", 0, 252, 0, 0, 4, 252, "meteor_mash", "zen_headbutt", "earthquake", "agility")
                ))),
                new WorldBossTheme("Verdantis", "Grass", "Grass Boss Verdantis", new ArrayList<>(List.of(
                        boss("arceus", "timid", "meadow_plate", "multitype", 0, 0, 0, 252, 4, 252, "judgment", "earth_power", "recover", "calm_mind"),
                        boss("shaymin", "timid", "life_orb", "serene_grace", 0, 0, 0, 252, 4, 252, "seed_flare", "air_slash", "earth_power", "healing_wish"),
                        boss("rillaboom", "adamant", "choice_band", "grassy_surge", 0, 252, 0, 0, 4, 252, "grassy_glide", "wood_hammer", "knock_off", "u_turn"),
                        boss("kartana", "jolly", "choice_scarf", "beast_boost", 0, 252, 0, 0, 4, 252, "leaf_blade", "smart_strike", "sacred_sword", "knock_off"),
                        boss("ogerpon", "jolly", "focus_sash", "defiant", 0, 252, 0, 0, 4, 252, "ivy_cudgel", "power_whip", "play_rough", "swords_dance"),
                        boss("venusaur", "timid", "black_sludge", "chlorophyll", 0, 0, 0, 252, 4, 252, "giga_drain", "sludge_bomb", "earth_power", "growth")
                ))),
                new WorldBossTheme("Voltrax", "Electric", "Electric Boss Voltrax", new ArrayList<>(List.of(
                        boss("miraidon", "timid", "choice_specs", "hadron_engine", 0, 0, 0, 252, 4, 252, "electro_drift", "draco_meteor", "flash_cannon", "volt_switch"),
                        boss("zekrom", "jolly", "life_orb", "teravolt", 0, 252, 0, 0, 4, 252, "bolt_strike", "dragon_claw", "crunch", "dragon_dance"),
                        boss("regieleki", "timid", "magnet", "transistor", 0, 0, 0, 252, 4, 252, "thunderbolt", "volt_switch", "rapid_spin", "ancient_power"),
                        boss("zapdos", "timid", "heavy_duty_boots", "static", 0, 0, 0, 252, 4, 252, "thunderbolt", "hurricane", "heat_wave", "roost"),
                        boss("iron_hands", "adamant", "assault_vest", "quark_drive", 252, 252, 4, 0, 0, 0, "wild_charge", "drain_punch", "ice_punch", "fake_out"),
                        boss("raging_bolt", "modest", "booster_energy", "protosynthesis", 252, 0, 0, 252, 4, 0, "thunderclap", "dragon_pulse", "thunderbolt", "calm_mind")
                ))),
                new WorldBossTheme("Frostrix", "Ice", "Ice Boss Frostrix", new ArrayList<>(List.of(
                        boss("kyurem", "timid", "choice_specs", "pressure", 0, 0, 0, 252, 4, 252, "ice_beam", "draco_meteor", "earth_power", "freeze_dry"),
                        boss("baxcalibur", "jolly", "loaded_dice", "thermal_exchange", 0, 252, 0, 0, 4, 252, "icicle_spear", "glaive_rush", "earthquake", "dragon_dance"),
                        boss("chien_pao", "jolly", "life_orb", "sword_of_ruin", 0, 252, 0, 0, 4, 252, "ice_spinner", "crunch", "sucker_punch", "swords_dance"),
                        boss("iron_bundle", "timid", "booster_energy", "quark_drive", 0, 0, 0, 252, 4, 252, "freeze_dry", "hydro_pump", "ice_beam", "flip_turn"),
                        boss("weavile", "jolly", "heavy_duty_boots", "pressure", 0, 252, 0, 0, 4, 252, "triple_axel", "knock_off", "ice_shard", "swords_dance"),
                        boss("mamoswine", "adamant", "life_orb", "thick_fat", 0, 252, 0, 0, 4, 252, "earthquake", "icicle_crash", "ice_shard", "stealth_rock")
                ))),
                new WorldBossTheme("Terradon", "Ground", "Ground Boss Terradon", new ArrayList<>(List.of(
                        boss("groudon", "adamant", "leftovers", "drought", 252, 252, 4, 0, 0, 0, "precipice_blades", "fire_punch", "stone_edge", "swords_dance"),
                        boss("landorus", "jolly", "choice_scarf", "intimidate", 0, 252, 0, 0, 4, 252, "earthquake", "stone_edge", "u_turn", "stealth_rock"),
                        boss("great_tusk", "jolly", "booster_energy", "protosynthesis", 0, 252, 0, 0, 4, 252, "headlong_rush", "close_combat", "rapid_spin", "knock_off"),
                        boss("garchomp", "jolly", "loaded_dice", "rough_skin", 0, 252, 0, 0, 4, 252, "earthquake", "scale_shot", "stone_edge", "swords_dance"),
                        boss("ting_lu", "careful", "leftovers", "vessel_of_ruin", 252, 0, 4, 0, 252, 0, "earthquake", "ruination", "whirlwind", "stealth_rock"),
                        boss("ursaluna", "adamant", "flame_orb", "guts", 252, 252, 0, 0, 4, 0, "facade", "earthquake", "crunch", "swords_dance")
                ))),
                new WorldBossTheme("Aerovox", "Flying", "Flying Boss Aerovox", new ArrayList<>(List.of(
                        boss("rayquaza", "jolly", "life_orb", "air_lock", 0, 252, 0, 0, 4, 252, "dragon_ascent", "earthquake", "extreme_speed", "dragon_dance"),
                        boss("yveltal", "timid", "heavy_duty_boots", "dark_aura", 0, 0, 0, 252, 4, 252, "dark_pulse", "oblivion_wing", "heat_wave", "nasty_plot"),
                        boss("lugia", "bold", "heavy_duty_boots", "multiscale", 252, 0, 252, 0, 4, 0, "aeroblast", "ice_beam", "recover", "calm_mind"),
                        boss("ho_oh", "careful", "heavy_duty_boots", "regenerator", 248, 0, 8, 0, 252, 0, "sacred_fire", "brave_bird", "earthquake", "recover"),
                        boss("tornadus", "timid", "heavy_duty_boots", "regenerator", 0, 0, 0, 252, 4, 252, "hurricane", "heat_wave", "knock_off", "u_turn"),
                        boss("dragonite", "jolly", "heavy_duty_boots", "multiscale", 0, 252, 0, 0, 4, 252, "dual_wingbeat", "earthquake", "extreme_speed", "dragon_dance")
                ))),
                new WorldBossTheme("Psyren", "Psychic", "Psychic Boss Psyren", new ArrayList<>(List.of(
                        boss("mewtwo", "timid", "life_orb", "unnerve", 0, 0, 4, 252, 0, 252, "psystrike", "aura_sphere", "ice_beam", "nasty_plot"),
                        boss("necrozma", "adamant", "weakness_policy", "prism_armor", 0, 252, 0, 0, 4, 252, "photon_geyser", "earthquake", "stone_edge", "dragon_dance"),
                        boss("lunala", "timid", "power_herb", "shadow_shield", 0, 0, 0, 252, 4, 252, "moongeist_beam", "meteor_beam", "psyshock", "calm_mind"),
                        boss("deoxys", "naive", "focus_sash", "pressure", 0, 252, 0, 4, 0, 252, "psycho_boost", "superpower", "extreme_speed", "spikes"),
                        boss("hoopa", "timid", "choice_specs", "magician", 0, 0, 0, 252, 4, 252, "hyperspace_hole", "dark_pulse", "focus_blast", "trick"),
                        boss("latios", "timid", "soul_dew", "levitate", 0, 0, 0, 252, 4, 252, "luster_purge", "draco_meteor", "aura_sphere", "recover")
                ))),
                new WorldBossTheme("Umbrax", "Dark", "Dark Boss Umbrax", new ArrayList<>(List.of(
                        boss("yveltal", "timid", "heavy_duty_boots", "dark_aura", 0, 0, 0, 252, 4, 252, "dark_pulse", "oblivion_wing", "heat_wave", "nasty_plot"),
                        boss("darkrai", "timid", "life_orb", "bad_dreams", 0, 0, 0, 252, 4, 252, "dark_pulse", "sludge_bomb", "ice_beam", "nasty_plot"),
                        boss("roaring_moon", "jolly", "booster_energy", "protosynthesis", 0, 252, 0, 0, 4, 252, "knock_off", "dragon_claw", "earthquake", "dragon_dance"),
                        boss("chien_pao", "jolly", "life_orb", "sword_of_ruin", 0, 252, 0, 0, 4, 252, "ice_spinner", "crunch", "sucker_punch", "swords_dance"),
                        boss("kingambit", "adamant", "black_glasses", "supreme_overlord", 252, 252, 0, 0, 4, 0, "kowtow_cleave", "sucker_punch", "iron_head", "swords_dance"),
                        boss("hydreigon", "timid", "choice_specs", "levitate", 0, 0, 0, 252, 4, 252, "dark_pulse", "draco_meteor", "flash_cannon", "fire_blast")
                ))),
                new WorldBossTheme("Spectra", "Ghost", "Ghost Boss Spectra", new ArrayList<>(List.of(
                        boss("giratina", "calm", "leftovers", "pressure", 252, 0, 0, 4, 252, 0, "shadow_ball", "dragon_pulse", "will_o_wisp", "calm_mind"),
                        boss("lunala", "timid", "power_herb", "shadow_shield", 0, 0, 0, 252, 4, 252, "moongeist_beam", "meteor_beam", "psyshock", "calm_mind"),
                        boss("marshadow", "jolly", "life_orb", "technician", 0, 252, 0, 0, 4, 252, "spectral_thief", "close_combat", "shadow_sneak", "bulk_up"),
                        boss("flutter_mane", "timid", "booster_energy", "protosynthesis", 0, 0, 0, 252, 4, 252, "moonblast", "shadow_ball", "mystical_fire", "calm_mind"),
                        boss("dragapult", "naive", "choice_specs", "infiltrator", 0, 4, 0, 252, 0, 252, "shadow_ball", "draco_meteor", "flamethrower", "u_turn"),
                        boss("gengar", "timid", "life_orb", "cursed_body", 0, 0, 0, 252, 4, 252, "shadow_ball", "sludge_wave", "focus_blast", "nasty_plot")
                ))),
                new WorldBossTheme("Drakonos", "Dragon", "Dragon Boss Drakonos", new ArrayList<>(List.of(
                        boss("rayquaza", "jolly", "life_orb", "air_lock", 0, 252, 0, 0, 4, 252, "dragon_ascent", "earthquake", "extreme_speed", "dragon_dance"),
                        boss("miraidon", "timid", "choice_specs", "hadron_engine", 0, 0, 0, 252, 4, 252, "electro_drift", "draco_meteor", "flash_cannon", "volt_switch"),
                        boss("koraidon", "jolly", "clear_amulet", "orichalcum_pulse", 0, 252, 0, 0, 4, 252, "collision_course", "flare_blitz", "dragon_claw", "swords_dance"),
                        boss("eternatus", "timid", "black_sludge", "pressure", 0, 0, 0, 252, 4, 252, "dynamax_cannon", "sludge_bomb", "flamethrower", "recover"),
                        boss("kyurem", "timid", "choice_specs", "pressure", 0, 0, 0, 252, 4, 252, "ice_beam", "draco_meteor", "earth_power", "freeze_dry"),
                        boss("dragapult", "naive", "choice_specs", "infiltrator", 0, 4, 0, 252, 0, 252, "shadow_ball", "draco_meteor", "flamethrower", "u_turn")
                ))),
                new WorldBossTheme("Venomira", "Poison", "Poison Boss Venomira", new ArrayList<>(List.of(
                        boss("eternatus", "timid", "black_sludge", "pressure", 0, 0, 0, 252, 4, 252, "dynamax_cannon", "sludge_bomb", "flamethrower", "recover"),
                        boss("naganadel", "timid", "life_orb", "beast_boost", 0, 0, 0, 252, 4, 252, "sludge_wave", "draco_meteor", "fire_blast", "nasty_plot"),
                        boss("nihilego", "timid", "power_herb", "beast_boost", 0, 0, 0, 252, 4, 252, "meteor_beam", "sludge_wave", "thunderbolt", "grass_knot"),
                        boss("toxapex", "bold", "black_sludge", "regenerator", 252, 0, 252, 0, 4, 0, "scald", "sludge_bomb", "recover", "toxic"),
                        boss("sneasler", "jolly", "focus_sash", "unburden", 0, 252, 0, 0, 4, 252, "dire_claw", "close_combat", "throat_chop", "swords_dance"),
                        boss("gengar", "timid", "life_orb", "cursed_body", 0, 0, 0, 252, 4, 252, "shadow_ball", "sludge_wave", "focus_blast", "nasty_plot")
                ))),
                new WorldBossTheme("Aurelia", "Fairy", "Fairy Boss Aurelia", new ArrayList<>(List.of(
                        boss("xerneas", "modest", "power_herb", "fairy_aura", 104, 0, 0, 252, 0, 152, "moonblast", "thunder", "focus_blast", "geomancy"),
                        boss("zacian", "jolly", "rusted_sword", "intrepid_sword", 0, 252, 0, 0, 4, 252, "behemoth_blade", "play_rough", "close_combat", "swords_dance"),
                        boss("magearna", "modest", "leftovers", "soul_heart", 252, 0, 0, 252, 4, 0, "fleur_cannon", "flash_cannon", "thunderbolt", "shift_gear"),
                        boss("flutter_mane", "timid", "booster_energy", "protosynthesis", 0, 0, 0, 252, 4, 252, "moonblast", "shadow_ball", "mystical_fire", "calm_mind"),
                        boss("iron_valiant", "naive", "booster_energy", "quark_drive", 0, 252, 0, 4, 0, 252, "moonblast", "close_combat", "thunderbolt", "swords_dance"),
                        boss("diancie", "naive", "life_orb", "clear_body", 0, 4, 0, 252, 0, 252, "moonblast", "diamond_storm", "earth_power", "stealth_rock")
                ))),
                new WorldBossTheme("Boulderex", "Rock", "Rock Boss Boulderex", new ArrayList<>(List.of(
                        boss("terrakion", "jolly", "choice_band", "justified", 0, 252, 0, 0, 4, 252, "stone_edge", "close_combat", "earthquake", "quick_attack"),
                        boss("diancie", "naive", "life_orb", "clear_body", 0, 4, 0, 252, 0, 252, "moonblast", "diamond_storm", "earth_power", "stealth_rock"),
                        boss("nihilego", "timid", "power_herb", "beast_boost", 0, 0, 0, 252, 4, 252, "meteor_beam", "sludge_wave", "thunderbolt", "grass_knot"),
                        boss("iron_boulder", "jolly", "booster_energy", "quark_drive", 0, 252, 0, 0, 4, 252, "mighty_cleave", "close_combat", "earthquake", "swords_dance"),
                        boss("tyranitar", "adamant", "choice_band", "sand_stream", 252, 252, 0, 0, 4, 0, "stone_edge", "crunch", "earthquake", "fire_punch"),
                        boss("garganacl", "careful", "leftovers", "purifying_salt", 252, 0, 4, 0, 252, 0, "salt_cure", "recover", "body_press", "iron_defense")
                ))),
                new WorldBossTheme("Arachna", "Bug", "Bug Boss Arachna", new ArrayList<>(List.of(
                        boss("genesect", "naive", "choice_scarf", "download", 0, 252, 0, 4, 0, 252, "u_turn", "iron_head", "ice_beam", "flamethrower"),
                        boss("buzzwole", "adamant", "rocky_helmet", "beast_boost", 252, 252, 4, 0, 0, 0, "close_combat", "leech_life", "ice_punch", "roost"),
                        boss("pheromosa", "naive", "life_orb", "beast_boost", 0, 252, 0, 4, 0, 252, "close_combat", "bug_buzz", "ice_beam", "u_turn"),
                        boss("volcarona", "timid", "heavy_duty_boots", "flame_body", 0, 0, 0, 252, 4, 252, "fiery_dance", "bug_buzz", "giga_drain", "quiver_dance"),
                        boss("scizor", "adamant", "choice_band", "technician", 248, 252, 0, 0, 8, 0, "bullet_punch", "u_turn", "close_combat", "knock_off"),
                        boss("slither_wing", "adamant", "booster_energy", "protosynthesis", 0, 252, 0, 0, 4, 252, "first_impression", "close_combat", "flare_blitz", "u_turn")
                ))),
                new WorldBossTheme("Valorak", "Fighting", "Fighting Boss Valorak", new ArrayList<>(List.of(
                        boss("koraidon", "jolly", "clear_amulet", "orichalcum_pulse", 0, 252, 0, 0, 4, 252, "collision_course", "flare_blitz", "dragon_claw", "swords_dance"),
                        boss("zamazenta", "jolly", "rusted_shield", "dauntless_shield", 0, 252, 4, 0, 0, 252, "behemoth_bash", "body_press", "crunch", "iron_defense"),
                        boss("marshadow", "jolly", "life_orb", "technician", 0, 252, 0, 0, 4, 252, "spectral_thief", "close_combat", "shadow_sneak", "bulk_up"),
                        boss("iron_valiant", "naive", "booster_energy", "quark_drive", 0, 252, 0, 4, 0, 252, "moonblast", "close_combat", "thunderbolt", "swords_dance"),
                        boss("great_tusk", "jolly", "booster_energy", "protosynthesis", 0, 252, 0, 0, 4, 252, "headlong_rush", "close_combat", "rapid_spin", "knock_off"),
                        boss("urshifu", "jolly", "choice_band", "unseen_fist", 0, 252, 0, 0, 4, 252, "wicked_blow", "close_combat", "sucker_punch", "u_turn")
                ))),
                new WorldBossTheme("Obelisk", "Normal", "Normal Boss Obelisk", new ArrayList<>(List.of(
                        boss("arceus", "adamant", "silk_scarf", "multitype", 0, 252, 0, 0, 4, 252, "extreme_speed", "shadow_claw", "earthquake", "swords_dance"),
                        boss("terapagos", "modest", "leftovers", "tera_shell", 252, 0, 0, 252, 4, 0, "tera_starstorm", "earth_power", "calm_mind", "recover"),
                        boss("ursaluna", "adamant", "flame_orb", "guts", 252, 252, 0, 0, 4, 0, "facade", "earthquake", "crunch", "swords_dance"),
                        boss("blissey", "bold", "heavy_duty_boots", "natural_cure", 252, 0, 252, 0, 4, 0, "seismic_toss", "soft_boiled", "thunder_wave", "stealth_rock"),
                        boss("snorlax", "careful", "leftovers", "thick_fat", 252, 4, 0, 0, 252, 0, "body_slam", "earthquake", "curse", "rest"),
                        boss("dragonite", "jolly", "heavy_duty_boots", "multiscale", 0, 252, 0, 0, 4, 252, "extreme_speed", "earthquake", "fire_punch", "dragon_dance")
                )))
        ));
    }

    private static List<BossPokemon> defaultWorldPool() {
        return new ArrayList<>(List.of(
                boss("groudon", "adamant", "leftovers", "drought", 252, 252, 4, 0, 0, 0, "precipice_blades", "fire_punch", "stone_edge", "swords_dance"),
                boss("kyogre", "modest", "choice_specs", "drizzle", 0, 0, 0, 252, 4, 252, "water_spout", "origin_pulse", "ice_beam", "thunder"),
                boss("dialga", "modest", "assault_vest", "pressure", 248, 0, 0, 252, 8, 0, "roar_of_time", "flash_cannon", "earth_power", "thunderbolt"),
                boss("palkia", "timid", "lustrous_orb", "pressure", 0, 0, 0, 252, 4, 252, "spacial_rend", "hydro_pump", "earth_power", "fire_blast"),
                boss("giratina", "calm", "leftovers", "pressure", 252, 0, 0, 4, 252, 0, "shadow_ball", "dragon_pulse", "will_o_wisp", "calm_mind"),
                boss("eternatus", "timid", "black_sludge", "pressure", 0, 0, 0, 252, 4, 252, "dynamax_cannon", "sludge_bomb", "flamethrower", "recover"),
                boss("miraidon", "timid", "choice_specs", "hadron_engine", 0, 0, 0, 252, 4, 252, "electro_drift", "draco_meteor", "overheat", "volt_switch"),
                boss("koraidon", "jolly", "choice_band", "orichalcum_pulse", 0, 252, 0, 0, 4, 252, "collision_course", "flare_blitz", "dragon_claw", "u_turn"),
                boss("zacian", "jolly", "rusted_sword", "intrepid_sword", 0, 252, 0, 0, 4, 252, "behemoth_blade", "play_rough", "close_combat", "swords_dance"),
                boss("mewtwo", "timid", "life_orb", "unnerve", 0, 0, 4, 252, 0, 252, "psystrike", "aura_sphere", "fire_blast", "nasty_plot"),
                boss("lugia", "bold", "heavy_duty_boots", "multiscale", 252, 0, 252, 0, 4, 0, "aeroblast", "ice_beam", "recover", "calm_mind"),
                boss("ho_oh", "careful", "heavy_duty_boots", "regenerator", 248, 0, 8, 0, 252, 0, "sacred_fire", "brave_bird", "earthquake", "recover"),
                boss("reshiram", "timid", "choice_specs", "turboblaze", 0, 0, 0, 252, 4, 252, "blue_flare", "draco_meteor", "earth_power", "overheat"),
                boss("zekrom", "jolly", "life_orb", "teravolt", 0, 252, 0, 0, 4, 252, "bolt_strike", "dragon_claw", "crunch", "dragon_dance"),
                boss("kyurem", "timid", "choice_specs", "pressure", 0, 0, 0, 252, 4, 252, "ice_beam", "draco_meteor", "earth_power", "freeze_dry"),
                boss("solgaleo", "adamant", "weakness_policy", "full_metal_body", 252, 252, 4, 0, 0, 0, "sunsteel_strike", "earthquake", "wild_charge", "morning_sun"),
                boss("lunala", "timid", "power_herb", "shadow_shield", 0, 0, 0, 252, 4, 252, "moongeist_beam", "meteor_beam", "psyshock", "calm_mind"),
                boss("magearna", "modest", "leftovers", "soul_heart", 252, 0, 0, 252, 4, 0, "fleur_cannon", "flash_cannon", "thunderbolt", "shift_gear"),
                boss("melmetal", "adamant", "assault_vest", "iron_fist", 252, 252, 4, 0, 0, 0, "double_iron_bash", "earthquake", "thunder_punch", "ice_punch"),
                boss("landorus", "jolly", "choice_scarf", "intimidate", 0, 252, 0, 0, 4, 252, "earthquake", "stone_edge", "u_turn", "knock_off"),
                boss("ursaluna", "adamant", "flame_orb", "guts", 252, 252, 0, 0, 4, 0, "facade", "headlong_rush", "fire_punch", "swords_dance"),
                boss("kingambit", "adamant", "black_glasses", "supreme_overlord", 252, 252, 4, 0, 0, 0, "kowtow_cleave", "sucker_punch", "iron_head", "swords_dance"),
                boss("gholdengo", "timid", "choice_scarf", "good_as_gold", 0, 0, 0, 252, 4, 252, "make_it_rain", "shadow_ball", "focus_blast", "trick"),
                boss("glimmora", "timid", "focus_sash", "toxic_debris", 0, 0, 0, 252, 4, 252, "power_gem", "sludge_wave", "earth_power", "stealth_rock")
        ));
    }

    private static List<RewardTier> defaultRewardTiers() {
        return new ArrayList<>(List.of(
                new RewardTier(1, "d", 1),
                new RewardTier(3, "c", 1),
                new RewardTier(5, "a", 2),
                new RewardTier(8, "s", 3)
        ));
    }
}
