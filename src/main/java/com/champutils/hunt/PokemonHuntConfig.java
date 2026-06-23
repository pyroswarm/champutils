package com.champutils.hunt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PokemonHuntConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "pokemon_hunts.json");

    public static Root DATA = new Root();

    private PokemonHuntConfig() {}

    public static synchronized void load() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            if (!FILE.exists()) {
                DATA = defaults();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                Root loaded = GSON.fromJson(reader, Root.class);
                DATA = loaded == null ? defaults() : loaded;
            }
            sanitize();
            save();
        } catch (Exception e) {
            e.printStackTrace();
            DATA = defaults();
        }
    }

    public static synchronized void save() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(DATA, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sanitize() {
        if (DATA == null) DATA = defaults();
        if (DATA.settings == null) DATA.settings = new Settings();
        if (DATA.targetPool == null || DATA.targetPool.isEmpty()) DATA.targetPool = defaults().targetPool;
        if (DATA.settings.huntsPerCycle <= 0) DATA.settings.huntsPerCycle = 9;
        if (DATA.settings.refreshHours <= 0.0) DATA.settings.refreshHours = 1.0;
        if (DATA.settings.crateCreditChancePercent < 0) DATA.settings.crateCreditChancePercent = 0;
        if (DATA.settings.crateCreditChancePercent > 100) DATA.settings.crateCreditChancePercent = 100;
        for (HuntTarget target : DATA.targetPool) sanitizeTarget(target);
    }

    private static void sanitizeTarget(HuntTarget target) {
        if (target == null) return;
        if (target.natures == null || target.natures.isEmpty()) target.natures = new ArrayList<>(Arrays.asList("jolly"));
        if (target.genders == null || target.genders.isEmpty()) target.genders = new ArrayList<>(Arrays.asList("male", "female"));
        if (target.abilities == null || target.abilities.isEmpty()) target.abilities = new ArrayList<>(Arrays.asList("any"));
        if (target.rewards == null) target.rewards = new Rewards();
        if (target.rewards.items == null) target.rewards.items = new ArrayList<>();
        if (target.weight <= 0) target.weight = 1;
        if (target.rewards.rewardRolls <= 0) target.rewards.rewardRolls = 1;
        target.rewards.credits = creditsForDifficulty(target.difficulty);
    }

    private static long creditsForDifficulty(String difficulty) {
        String d = difficulty == null ? "" : difficulty.trim().toUpperCase();
        return switch (d) {
            case "UNCOMMON" -> 250L;
            case "RARE" -> 500L;
            case "EPIC" -> 1000L;
            case "LEGENDARY" -> 2000L;
            case "MYTHIC" -> 5000L;
            default -> 100L;
        };
    }

    public static Root defaults() {
        Root root = new Root();
        root.settings.enabled = true;
        root.settings.refreshHours = 1.0;
        root.settings.huntsPerCycle = 9;
        root.settings.announceNewHunts = true;
        root.settings.announceWinners = true;
        root.settings.allowAlreadyWonHuntsToStayVisible = true;
        root.settings.crateCreditChancePercent = 100;

        add(root, "pikachu", 12, "COMMON", 650, 1,
                list("jolly", "timid", "hasty"), list("male", "female"), list("static"),
                reward("cobblemon:quick_ball", 2, 4, 24), reward("cobblemon:thunder_stone", 1, 1, 5));
        add(root, "eevee", 10, "COMMON", 700, 1,
                list("jolly", "timid", "modest", "calm"), list("male", "female"), list("runaway", "adaptability"),
                reward("cobblemon:great_ball", 3, 5, 24), reward("cobblemon:soothe_bell", 1, 1, 3));
        add(root, "growlithe", 8, "COMMON", 800, 1,
                list("adamant", "jolly", "brave"), list("male", "female"), list("intimidate", "flashfire"),
                reward("cobblemon:fire_stone", 1, 1, 5), reward("cobblemon:great_ball", 2, 4, 22));
        add(root, "vulpix", 8, "COMMON", 800, 1,
                list("timid", "modest", "calm"), list("male", "female"), list("flashfire"),
                reward("cobblemon:fire_stone", 1, 1, 5), reward("cobblemon:dusk_ball", 2, 4, 16));
        add(root, "magikarp", 12, "COMMON", 500, 1,
                list("jolly", "adamant"), list("male", "female"), list("swiftswim"),
                reward("cobblemon:lure_ball", 2, 4, 20), reward("cobblemon:rare_candy", 1, 1, 2));
        add(root, "gastly", 8, "COMMON", 800, 1,
                list("timid", "modest"), list("male", "female"), list("levitate"),
                reward("cobblemon:dusk_ball", 3, 5, 24), reward("cobblemon:spell_tag", 1, 1, 3));
        add(root, "machop", 8, "COMMON", 750, 1,
                list("adamant", "brave"), list("male", "female"), list("guts", "noguard"),
                reward("cobblemon:super_potion", 2, 4, 22), reward("cobblemon:black_belt", 1, 1, 3));
        add(root, "shinx", 8, "COMMON", 800, 1,
                list("jolly", "adamant"), list("male", "female"), list("rivalry", "intimidate"),
                reward("cobblemon:great_ball", 2, 4, 24), reward("cobblemon:magnet", 1, 1, 3));
        add(root, "ralts", 6, "UNCOMMON", 1100, 1,
                list("timid", "modest", "calm"), list("male", "female"), list("synchronize", "trace"),
                reward("cobblemon:heal_ball", 3, 6, 20), reward("cobblemon:dawn_stone", 1, 1, 4));
        add(root, "riolu", 5, "UNCOMMON", 1250, 1,
                list("jolly", "adamant"), list("male", "female"), list("steadfast", "innerfocus"),
                reward("cobblemon:friend_ball", 2, 4, 20), reward("cobblemon:focus_band", 1, 1, 3));
        add(root, "gible", 4, "RARE", 1750, 2,
                list("jolly", "adamant"), list("male", "female"), list("sandveil"),
                reward("cobblemon:ultra_ball", 3, 5, 24), reward("cobblemon:dragon_fang", 1, 1, 4));
        add(root, "bagon", 4, "RARE", 1750, 2,
                list("jolly", "adamant"), list("male", "female"), list("rockhead"),
                reward("cobblemon:ultra_ball", 3, 5, 24), reward("cobblemon:dragon_fang", 1, 1, 4));
        add(root, "beldum", 3, "RARE", 1850, 2,
                list("adamant", "jolly"), list("genderless"), list("clearbody"),
                reward("cobblemon:heavy_ball", 2, 4, 20), reward("cobblemon:metal_coat", 1, 1, 4));
        add(root, "larvesta", 3, "EPIC", 2500, 2,
                list("modest", "timid"), list("male", "female"), list("flamebody"),
                reward("cobblemon:luxury_ball", 2, 4, 20), reward("cobblemon:rare_candy", 1, 2, 6));
        add(root, "feebas", 3, "EPIC", 2500, 2,
                list("calm", "modest", "bold"), list("male", "female"), list("swiftswim", "oblivious"),
                reward("cobblemon:dive_ball", 3, 6, 22), reward("cobblemon:prism_scale", 1, 1, 4));
        add(root, "deino", 2, "EPIC", 3000, 2,
                list("timid", "modest"), list("male", "female"), list("hustle"),
                reward("cobblemon:dusk_ball", 3, 6, 22), reward("cobblemon:dragon_fang", 1, 1, 5));


        String[] common = {"caterpie","weedle","pidgey","rattata","sentret","zigzagoon","bidoof","starly","patrat","fletchling","wooloo","lechonk","skwovet","nidoranmale","nidoranfemale","oddish","bellsprout","geodude","zubat","psyduck","tentacool"};
        for (String sp : common) add(root, sp, 14, "COMMON", 100, 1, list("any"), list("male", "female"), list("any"), reward("cobblemon:poke_ball", 3, 6, 30));
        String[] uncommon = {"ponyta","dratini","togepi","mareep","sneasel","skarmory","trapinch","swablu","shroomish","aron","noibat","rockruff","impidimp","tinkatink","charcadet","applin","dreepy","pawniard"};
        for (String sp : uncommon) add(root, sp, 8, "UNCOMMON", 250, 1, list("any"), list("male", "female"), list("any"), reward("cobblemon:great_ball", 2, 5, 30));
        String[] rare = {"axew","goomy","jangmoo","frigibax","drilbur","larvitar","dratini","dreepy","toxel","ralts","riolu","gible","bagon","beldum"};
        for (String sp : rare) add(root, sp, 5, "RARE", 500, 1, list("adamant", "jolly", "modest", "timid", "bold", "calm"), list("male", "female", "genderless"), list("any"), reward("cobblemon:ultra_ball", 2, 4, 30));
        String[] epic = {"larvesta","feebas","deino","duraludon","drampa","turtonator","rotom","mimikyu","zorua","spiritomb","honedge","sandile"};
        for (String sp : epic) add(root, sp, 5, "EPIC", 1000, 1, list("adamant", "jolly", "modest", "timid", "bold", "calm"), list("male", "female", "genderless"), list("any"), reward("cobblemon:luxury_ball", 2, 4, 30));
        String[] legendary = {"beldum","gible","bagon","larvitar","deino","dreepy","frigibax","jangmoo"};
        for (String sp : legendary) add(root, sp, 3, "LEGENDARY", 2000, 1, list("adamant", "jolly", "modest", "timid"), list("male", "female", "genderless"), list("any"), reward("cobblemon:rare_candy", 1, 1, 10));
        String[] mythic = {"rotom","spiritomb","larvesta","beldum","frigibax","dreepy"};
        for (String sp : mythic) add(root, sp, 2, "MYTHIC", 5000, 1, list("adamant", "jolly", "modest", "timid"), list("male", "female", "genderless"), list("any"), reward("cobblemon:rare_candy", 1, 1, 10));
        return root;
    }

    @SafeVarargs
    private static void add(Root root, String species, int weight, String difficulty, long credits, int rolls,
                            List<String> natures, List<String> genders, List<String> abilities, RewardItem... items) {
        HuntTarget target = new HuntTarget();
        target.species = species;
        target.weight = weight;
        target.difficulty = difficulty;
        target.natures = natures;
        target.genders = genders;
        target.abilities = abilities;
        target.rewards.credits = credits;
        target.rewards.rewardRolls = rolls;
        target.rewards.items.addAll(Arrays.asList(items));
        root.targetPool.add(target);
    }

    private static List<String> list(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static RewardItem reward(String item, int min, int max, int weight) {
        return new RewardItem(item, min, max, weight);
    }

    public static class Root {
        public Settings settings = new Settings();
        public List<HuntTarget> targetPool = new ArrayList<>();
    }

    public static class Settings {
        public boolean enabled = true;
        public double refreshHours = 1.0;
        public int huntsPerCycle = 6;
        public boolean announceNewHunts = true;
        public boolean announceWinners = true;
        public boolean allowAlreadyWonHuntsToStayVisible = true;
        public int crateCreditChancePercent = 33;
    }

    public static class HuntTarget {
        public String species = "pikachu";
        public int weight = 1;
        public String difficulty = "COMMON";
        public List<String> natures = new ArrayList<>();
        public List<String> genders = new ArrayList<>();
        public List<String> abilities = new ArrayList<>();
        public Rewards rewards = new Rewards();
    }

    public static class Rewards {
        public long credits = 750;
        public int rewardRolls = 1;
        public List<RewardItem> items = new ArrayList<>();
    }

    public static class RewardItem {
        public String item = "cobblemon:great_ball";
        public int min = 1;
        public int max = 1;
        public int weight = 1;

        public RewardItem() {}
        public RewardItem(String item, int min, int max, int weight) {
            this.item = item;
            this.min = min;
            this.max = max;
            this.weight = weight;
        }
    }
}
