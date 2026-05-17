package com.champutils.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DungeonTrainerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Map<String, DungeonTrainerData> TRAINERS = new LinkedHashMap<>();

    private DungeonTrainerConfig() {}

    public static class DungeonTrainerData {
        public String dungeonId = "verdant_ruins";
        public List<TrainerWave> waves = new ArrayList<>();
    }

    public static class TrainerWave {
        public String trainerName = "Dungeon Trainer";
        public String spawnSkin = "";
        public int skill = 4;
        public List<PokemonSet> pokemonPool = new ArrayList<>();
    }

    public static class PokemonSet {
        public String species = "bulbasaur";

        /**
         * Optional per-Pokemon level. If this is 0 or lower, the dungeon rarity level is used instead.
         * This lets a dungeon be level 80 overall, but still have special ace Pokemon at 82, etc.
         */
        public int level = 0;

        public String ability = "";
        public String nature = "";
        public String heldItem = "";
        public List<String> moves = new ArrayList<>();
        public IVs ivs = new IVs();
        public EVs evs = new EVs();
        public boolean shiny = false;
    }

    public static class IVs {
        public int hp = 31;
        public int atk = 31;
        public int def = 31;
        public int spa = 31;
        public int spd = 31;
        public int spe = 31;
    }

    public static class EVs {
        public int hp = 0;
        public int atk = 0;
        public int def = 0;
        public int spa = 0;
        public int spd = 0;
        public int spe = 0;
    }

    public static class ConfigRoot {
        public Map<String, DungeonTrainerData> trainers = new LinkedHashMap<>();
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "dungeon_trainers.json");
            if (!file.exists()) {
                try (FileWriter writer = new FileWriter(file)) {
                    GSON.toJson(createDefaultRoot(), writer);
                }
            }

            try (FileReader reader = new FileReader(file)) {
                ConfigRoot root = GSON.fromJson(reader, ConfigRoot.class);
                TRAINERS.clear();
                if (root != null && root.trainers != null) {
                    TRAINERS.putAll(root.trainers);
                }
            }

            if (TRAINERS.isEmpty()) {
                TRAINERS.putAll(createDefaultRoot().trainers);
            }

            System.out.println("[ChampUtils] Loaded dungeon trainer config for " + TRAINERS.size() + " dungeon(s).");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ConfigRoot createDefaultRoot() {
        ConfigRoot root = new ConfigRoot();

        addDungeon(root, "verdant_ruins", "Verdant", 20, new PokemonSet[]{
                set("bulbasaur", 20, "overgrow", "bold", "cobblemon:miracle_seed", "vine_whip", "razor_leaf", "sleep_powder", "growth", ev(252, 0, 252, 0, 4, 0)),
                set("oddish", 20, "chlorophyll", "modest", "cobblemon:miracle_seed", "mega_drain", "acid", "stun_spore", "growth", ev(0, 0, 0, 252, 4, 252)),
                set("bellsprout", 20, "chlorophyll", "jolly", "cobblemon:miracle_seed", "vine_whip", "acid", "sleep_powder", "growth", ev(0, 252, 0, 0, 4, 252)),
                set("shroomish", 20, "effect_spore", "impish", "cobblemon:big_root", "mega_drain", "stun_spore", "headbutt", "leech_seed", ev(252, 0, 252, 0, 4, 0)),
                set("budew", 20, "natural_cure", "timid", "cobblemon:miracle_seed", "mega_drain", "stun_spore", "growth", "water_sport", ev(0, 0, 0, 252, 4, 252)),
                set("turtwig", 20, "overgrow", "adamant", "cobblemon:miracle_seed", "razor_leaf", "bite", "withdraw", "curse", ev(252, 252, 4, 0, 0, 0)),
                set("seedot", 20, "chlorophyll", "adamant", "cobblemon:miracle_seed", "razor_leaf", "growth", "quick_attack", "harden", ev(0, 252, 0, 0, 4, 252)),
                set("lotad", 20, "swift_swim", "modest", "cobblemon:mystic_water", "bubble", "mega_drain", "mist", "absorb", ev(0, 0, 0, 252, 4, 252)),
                set("petilil", 20, "own_tempo", "modest", "cobblemon:miracle_seed", "mega_drain", "sleep_powder", "growth", "stun_spore", ev(0, 0, 0, 252, 4, 252))
        });

        addDungeon(root, "ember_cavern", "Ember", 40, new PokemonSet[]{
                set("charmeleon", 40, "blaze", "timid", "cobblemon:charcoal_stick", "flamethrower", "dragon_breath", "smokescreen", "slash", ev(0, 0, 0, 252, 4, 252)),
                set("vulpix", 40, "flash_fire", "timid", "cobblemon:heat_rock", "flamethrower", "will_o_wisp", "confuse_ray", "extrasensory", ev(0, 0, 0, 252, 4, 252)),
                set("growlithe", 40, "intimidate", "jolly", "cobblemon:charcoal_stick", "flame_wheel", "crunch", "leer", "take_down", ev(0, 252, 0, 0, 4, 252)),
                set("ponyta", 40, "flash_fire", "jolly", "cobblemon:charcoal_stick", "flame_charge", "stomp", "agility", "double_kick", ev(0, 252, 0, 0, 4, 252)),
                set("houndour", 40, "flash_fire", "modest", "cobblemon:black_glasses", "flamethrower", "bite", "smog", "roar", ev(0, 0, 0, 252, 4, 252)),
                set("numel", 40, "simple", "modest", "cobblemon:soft_sand", "earth_power", "flame_burst", "amnesia", "focus_energy", ev(252, 0, 0, 252, 4, 0)),
                set("slugma", 40, "flame_body", "bold", "cobblemon:charcoal_stick", "lava_plume", "ancient_power", "recover", "harden", ev(252, 0, 252, 4, 0, 0)),
                set("litwick", 40, "flame_body", "modest", "cobblemon:spell_tag", "flamethrower", "hex", "will_o_wisp", "confuse_ray", ev(0, 0, 0, 252, 4, 252)),
                set("combusken", 40, "blaze", "jolly", "cobblemon:black_belt", "flame_charge", "double_kick", "quick_attack", "bulk_up", ev(0, 252, 0, 0, 4, 252))
        });

        addDungeon(root, "tidal_sanctum", "Tidal", 60, new PokemonSet[]{
                set("wartortle", 60, "torrent", "bold", "cobblemon:mystic_water", "surf", "aqua_tail", "protect", "bite", ev(252, 0, 252, 0, 4, 0)),
                set("golduck", 60, "swift_swim", "timid", "cobblemon:mystic_water", "surf", "psychic", "confuse_ray", "aqua_jet", ev(0, 0, 0, 252, 4, 252)),
                set("starmie", 60, "natural_cure", "timid", "cobblemon:mystic_water", "surf", "psychic", "recover", "swift", ev(0, 0, 0, 252, 4, 252)),
                set("seadra", 60, "sniper", "timid", "cobblemon:scope_lens", "surf", "dragon_pulse", "smokescreen", "focus_energy", ev(0, 0, 0, 252, 4, 252)),
                set("croconaw", 60, "torrent", "adamant", "cobblemon:mystic_water", "aqua_tail", "crunch", "ice_fang", "slash", ev(0, 252, 0, 0, 4, 252)),
                set("marshtomp", 60, "torrent", "adamant", "cobblemon:soft_sand", "waterfall", "earthquake", "rock_slide", "mud_shot", ev(252, 252, 4, 0, 0, 0)),
                set("pelipper", 60, "keen_eye", "bold", "cobblemon:damp_rock", "surf", "air_slash", "roost", "mist", ev(252, 0, 252, 4, 0, 0)),
                set("floatzel", 60, "swift_swim", "jolly", "cobblemon:mystic_water", "waterfall", "crunch", "aqua_jet", "quick_attack", ev(0, 252, 0, 0, 4, 252)),
                set("jellicent", 60, "water_absorb", "calm", "cobblemon:leftovers", "surf", "hex", "recover", "will_o_wisp", ev(252, 0, 0, 4, 252, 0))
        });

        addDungeon(root, "storm_spire", "Storm", 80, new PokemonSet[]{
                set("raichu", 80, "static", "timid", "cobblemon:magnet", "thunderbolt", "quick_attack", "thunder_wave", "double_team", ev(0, 0, 0, 252, 4, 252)),
                set("magneton", 80, "sturdy", "modest", "cobblemon:magnet", "thunderbolt", "flash_cannon", "thunder_wave", "metal_sound", ev(0, 0, 0, 252, 4, 252)),
                set("electrode", 80, "static", "timid", "cobblemon:magnet", "thunderbolt", "swift", "light_screen", "thunder_wave", ev(0, 0, 0, 252, 4, 252)),
                set("ampharos", 80, "static", "modest", "cobblemon:magnet", "thunderbolt", "dragon_pulse", "confuse_ray", "power_gem", ev(252, 0, 0, 252, 4, 0)),
                set("manectric", 80, "lightning_rod", "timid", "cobblemon:magnet", "thunderbolt", "flamethrower", "quick_attack", "thunder_wave", ev(0, 0, 0, 252, 4, 252)),
                set("luxray", 80, "intimidate", "adamant", "cobblemon:magnet", "wild_charge", "crunch", "ice_fang", "thunder_wave", ev(0, 252, 0, 0, 4, 252)),
                set("zebstrika", 80, "lightning_rod", "jolly", "cobblemon:magnet", "wild_charge", "flame_charge", "quick_attack", "thunder_wave", ev(0, 252, 0, 0, 4, 252)),
                set("heliolisk", 80, "dry_skin", "timid", "cobblemon:magnet", "thunderbolt", "surf", "quick_attack", "charge", ev(0, 0, 0, 252, 4, 252)),
                set("boltund", 80, "strong_jaw", "jolly", "cobblemon:magnet", "thunder_fang", "crunch", "play_rough", "agility", ev(0, 252, 0, 0, 4, 252))
        });

        addDungeon(root, "dragon_altar", "Dragon", 90, new PokemonSet[]{
                set("dragonair", 90, "shed_skin", "jolly", "cobblemon:dragon_fang", "dragon_dance", "dragon_tail", "aqua_tail", "thunder_wave", ev(0, 252, 0, 0, 4, 252)),
                set("salamence", 90, "intimidate", "jolly", "cobblemon:dragon_fang", "dragon_claw", "fly", "crunch", "dragon_dance", ev(0, 252, 0, 0, 4, 252)),
                set("gabite", 90, "sand_veil", "jolly", "cobblemon:soft_sand", "earthquake", "dragon_claw", "slash", "sandstorm", ev(0, 252, 0, 0, 4, 252)),
                set("haxorus", 90, "mold_breaker", "adamant", "cobblemon:dragon_fang", "dragon_claw", "slash", "swords_dance", "crunch", ev(0, 252, 0, 0, 4, 252)),
                set("zweilous", 90, "hustle", "adamant", "cobblemon:black_glasses", "crunch", "dragon_rush", "headbutt", "roar", ev(0, 252, 0, 0, 4, 252)),
                set("sliggoo", 90, "sap_sipper", "calm", "cobblemon:dragon_fang", "dragon_pulse", "muddy_water", "protect", "acid_armor", ev(252, 0, 0, 4, 252, 0)),
                set("hakamo-o", 90, "bulletproof", "jolly", "cobblemon:black_belt", "dragon_claw", "brick_break", "dragon_dance", "protect", ev(0, 252, 0, 0, 4, 252)),
                set("dragapult", 90, "clear_body", "jolly", "cobblemon:dragon_fang", "dragon_darts", "phantom_force", "quick_attack", "agility", ev(0, 252, 0, 0, 4, 252)),
                set("flygon", 90, "levitate", "jolly", "cobblemon:soft_sand", "earthquake", "dragon_claw", "crunch", "sandstorm", ev(0, 252, 0, 0, 4, 252))
        });

        addDungeon(root, "void_nexus", "Void", 100, new PokemonSet[]{
                set("gengar", 100, "cursed_body", "timid", "cobblemon:spell_tag", "shadow_ball", "sludge_bomb", "hypnosis", "confuse_ray", ev(0, 0, 0, 252, 4, 252)),
                set("dusknoir", 100, "pressure", "careful", "cobblemon:spell_tag", "shadow_punch", "will_o_wisp", "protect", "ice_punch", ev(252, 0, 4, 0, 252, 0)),
                set("sableye", 100, "prankster", "careful", "cobblemon:leftovers", "will_o_wisp", "recover", "foul_play", "confuse_ray", ev(252, 0, 4, 0, 252, 0)),
                set("banette", 100, "frisk", "adamant", "cobblemon:spell_tag", "shadow_claw", "sucker_punch", "will_o_wisp", "curse", ev(0, 252, 0, 0, 4, 252)),
                set("spiritomb", 100, "pressure", "calm", "cobblemon:leftovers", "dark_pulse", "shadow_ball", "will_o_wisp", "protect", ev(252, 0, 0, 4, 252, 0)),
                set("chandelure", 100, "flash_fire", "modest", "cobblemon:spell_tag", "shadow_ball", "flamethrower", "will_o_wisp", "confuse_ray", ev(0, 0, 0, 252, 4, 252)),
                set("trevenant", 100, "natural_cure", "adamant", "cobblemon:miracle_seed", "horn_leech", "shadow_claw", "will_o_wisp", "protect", ev(252, 252, 4, 0, 0, 0)),
                set("aegislash", 100, "stance_change", "brave", "cobblemon:spell_tag", "shadow_claw", "iron_head", "swords_dance", "king_shield", ev(252, 252, 4, 0, 0, 0)),
                set("mimikyu", 100, "disguise", "jolly", "cobblemon:spell_tag", "play_rough", "shadow_claw", "swords_dance", "shadow_sneak", ev(0, 252, 0, 0, 4, 252))
        });

        return root;
    }

    private static void addDungeon(ConfigRoot root, String dungeonId, String theme, int defaultLevel, PokemonSet[] sets) {
        DungeonTrainerData data = new DungeonTrainerData();
        data.dungeonId = dungeonId;

        for (int i = 1; i <= 6; i++) {
            TrainerWave wave = new TrainerWave();
            wave.trainerName = theme + " Trainer " + i;
            wave.skill = i <= 2 ? 3 : i <= 4 ? 4 : 5;

            for (PokemonSet source : sets) {
                PokemonSet copy = copy(source);
                if (copy.level <= 0) copy.level = defaultLevel;
                wave.pokemonPool.add(copy);
            }

            data.waves.add(wave);
        }

        root.trainers.put(dungeonId, data);
    }

    private static PokemonSet set(String species, int level, String ability, String nature, String heldItem, String move1, String move2, String move3, String move4, EVs evs) {
        PokemonSet set = new PokemonSet();
        set.species = species;
        set.level = level;
        set.ability = ability;
        set.nature = nature;
        set.heldItem = heldItem;
        set.moves.add(move1);
        set.moves.add(move2);
        set.moves.add(move3);
        set.moves.add(move4);
        set.ivs = new IVs();
        set.evs = evs;
        set.shiny = false;
        return set;
    }

    private static PokemonSet copy(PokemonSet source) {
        PokemonSet copy = new PokemonSet();
        copy.species = source.species;
        copy.level = source.level;
        copy.ability = source.ability;
        copy.nature = source.nature;
        copy.heldItem = source.heldItem;
        copy.moves = new ArrayList<>(source.moves);
        copy.ivs = source.ivs;
        copy.evs = source.evs;
        copy.shiny = source.shiny;
        return copy;
    }

    private static EVs ev(int hp, int atk, int def, int spa, int spd, int spe) {
        EVs evs = new EVs();
        evs.hp = hp;
        evs.atk = atk;
        evs.def = def;
        evs.spa = spa;
        evs.spd = spd;
        evs.spe = spe;
        return evs;
    }
}
