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
        public BossSettings guildBoss = BossSettings.guildDefaults();
        public WorldBossSettings worldBoss = WorldBossSettings.defaults();

        void normalize() {
            if (guildBoss == null) guildBoss = BossSettings.guildDefaults();
            if (worldBoss == null) worldBoss = WorldBossSettings.defaults();
            guildBoss.normalize();
            worldBoss.normalize();
        }
    }

    public static class BossSettings {
        public boolean enabled = true;
        public double scaleModifier = 2.5D;
        public int level = 100;
        public int aliveMinutes = 15;
        /** Cooldown between guild boss spawns for the same guild. */
        public int cooldownMinutes = 1440;
        public int countRadiusBlocks = 96;
        public List<BossPokemon> pool = new ArrayList<>();
        public List<RewardTier> rewardTiers = new ArrayList<>();

        static BossSettings guildDefaults() {
            BossSettings s = new BossSettings();
            s.pool = defaultGuildPool();
            s.rewardTiers = defaultRewardTiers();
            return s;
        }

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

    public static final class WorldBossSettings extends BossSettings {
        public int averageMinutesUntilNextBoss = 1440;
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

        static WorldBossSettings defaults() {
            WorldBossSettings s = new WorldBossSettings();
            s.pool = defaultWorldPool();
            s.rewardTiers = defaultRewardTiers();
            s.spawnDimensions.add("multiworld:spawn1");
            s.spawnLocation = new SpawnLocation(0.5D, 80D, 0.5D);
            s.yaw = 180.0F;
            return s;
        }

        @Override
        void normalize() {
            super.normalize();
            if (averageMinutesUntilNextBoss <= 0) averageMinutesUntilNextBoss = 1440;
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
            if (crateId == null || crateId.isBlank()) crateId = "rare";
            if (crateCredits < 1) crateCredits = 1;
        }
    }

    private static BossPokemon boss(String species, String nature, String heldItem, String ability, int hp, int attack, int defence, int specialAttack, int specialDefence, int speed, String... moves) {
        return new BossPokemon(species, nature, heldItem, ability, moves).withEvs(hp, attack, defence, specialAttack, specialDefence, speed);
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
                new RewardTier(1, "rare", 1),
                new RewardTier(3, "epic", 1),
                new RewardTier(5, "legendary", 2),
                new RewardTier(8, "mythic", 3)
        ));
    }
}
