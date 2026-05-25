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
            if (countRadiusBlocks <= 0) countRadiusBlocks = 96;
            if (pool == null || pool.isEmpty()) pool = defaultGuildPool();
            if (rewardTiers == null || rewardTiers.isEmpty()) rewardTiers = defaultRewardTiers();
            pool.forEach(BossPokemon::normalize);
            rewardTiers.forEach(RewardTier::normalize);
        }
    }

    public static final class WorldBossSettings extends BossSettings {
        public int averageMinutesUntilNextBoss = 1440;

        /**
         * The boss will spawn at this exact coordinate in every dimension listed in spawnDimensions.
         * This lets spawn1, spawn2, spawn3, etc. all use the same arena location without duplicating X/Y/Z.
         */
        public SpawnLocation spawnLocation = new SpawnLocation(0.5D, 80D, 0.5D);
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
            return s;
        }

        @Override
        void normalize() {
            super.normalize();
            if (averageMinutesUntilNextBoss <= 0) averageMinutesUntilNextBoss = 1440;
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
        void normalize() {
            if (species == null || species.isBlank()) species = "mewtwo";
            if (weight <= 0) weight = 1;
            if (nature == null) nature = "";
            if (ability == null) ability = "";
            if (heldItem == null) heldItem = "";
            if (moves == null) moves = new ArrayList<>();
            if (extraProperties == null) extraProperties = "";
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

    private static List<BossPokemon> defaultGuildPool() {
        return new ArrayList<>(List.of(
                new BossPokemon("mewtwo", "timid", "life_orb", "unnerve", "psystrike", "aura_sphere", "ice_beam", "nasty_plot"),
                new BossPokemon("rayquaza", "jolly", "life_orb", "air_lock", "dragon_ascent", "earthquake", "extreme_speed", "dragon_dance"),
                new BossPokemon("koraidon", "jolly", "clear_amulet", "orichalcum_pulse", "collision_course", "flare_blitz", "dragon_claw", "swords_dance"),
                new BossPokemon("miraidon", "timid", "choice_specs", "hadron_engine", "electro_drift", "draco_meteor", "flash_cannon", "volt_switch"),
                new BossPokemon("flutter_mane", "timid", "booster_energy", "protosynthesis", "moonblast", "shadow_ball", "mystical_fire", "calm_mind"),
                new BossPokemon("iron_valiant", "naive", "booster_energy", "quark_drive", "moonblast", "close_combat", "thunderbolt", "swords_dance"),
                new BossPokemon("necrozma", "adamant", "weakness_policy", "prism_armor", "photon_geyser", "earthquake", "stone_edge", "dragon_dance")
        ));
    }

    private static List<BossPokemon> defaultWorldPool() {
        return new ArrayList<>(List.of(
                new BossPokemon("groudon", "adamant", "leftovers", "drought", "precipice_blades", "fire_punch", "stone_edge", "swords_dance"),
                new BossPokemon("kyogre", "modest", "choice_specs", "drizzle", "water_spout", "origin_pulse", "ice_beam", "thunder"),
                new BossPokemon("dialga", "modest", "assault_vest", "pressure", "roar_of_time", "flash_cannon", "earth_power", "thunderbolt"),
                new BossPokemon("palkia", "timid", "lustrous_orb", "pressure", "spacial_rend", "hydro_pump", "earth_power", "fire_blast"),
                new BossPokemon("giratina", "calm", "leftovers", "pressure", "shadow_ball", "dragon_pulse", "will_o_wisp", "calm_mind"),
                new BossPokemon("eternatus", "timid", "black_sludge", "pressure", "dynamax_cannon", "sludge_bomb", "flamethrower", "recover")
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
