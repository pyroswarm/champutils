package com.champutils.dailylogin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DailyLoginConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "daily_login_rewards.json");

    public static Root DATA = defaults();

    private DailyLoginConfig() {}

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
        if (DATA.settings.requiredOnlineMinutes <= 0) DATA.settings.requiredOnlineMinutes = 30;
        if (DATA.track == null || DATA.track.isEmpty()) DATA.track = defaults().track;
        if (DATA.track.size() > 20) DATA.track = new ArrayList<>(DATA.track.subList(0, 20));
        for (int i = 0; i < DATA.track.size(); i++) {
            RewardDay day = DATA.track.get(i);
            if (day.day <= 0) day.day = i + 1;
            if (day.displayName == null || day.displayName.isBlank()) day.displayName = "Day " + day.day + " Reward";
            if (day.commands == null) day.commands = new ArrayList<>();
            if (day.description == null) day.description = new ArrayList<>();
        }
    }

    public static Root defaults() {
        Root root = new Root();
        root.settings.enabled = true;
        root.settings.requiredOnlineMinutes = 30;
        root.settings.autoOpenMenuOnEarn = false;
        root.settings.announceProgressEveryFiveMinutes = true;
        root.settings.trackLengthDays = 20;

        add(root, 1, "§aStarter Login", list("§7A simple welcome reward."), "give %player% cobblemon:poke_ball 16", "give %player% minecraft:bread 16");
        add(root, 2, "§aHelpful Supplies", list("§7More early-game supplies."), "give %player% cobblemon:great_ball 8", "give %player% cobblemon:potion 8");
        add(root, 3, "§aBerry Bundle", list("§7Useful Cobblemon berries."), "give %player% cobblemon:oran_berry 8", "give %player% cobblemon:sitrus_berry 4");
        add(root, 4, "§aTravel Kit", list("§7Good balls for exploring."), "give %player% cobblemon:quick_ball 6", "give %player% cobblemon:dusk_ball 6");
        add(root, 5, "§bFirst Milestone", list("§7A better day-five reward."), "give %player% cobblemon:ultra_ball 8", "give %player% cobblemon:exp_candy_s 4");
        add(root, 6, "§aHealing Cache", list("§7Battle recovery supplies."), "give %player% cobblemon:super_potion 8", "give %player% cobblemon:revive 3");
        add(root, 7, "§aStone Shards", list("§7A small evolution boost."), "give %player% cobblemon:fire_stone 1", "give %player% cobblemon:water_stone 1");
        add(root, 8, "§bTrainer Pack", list("§7A stronger capture bundle."), "give %player% cobblemon:ultra_ball 10", "give %player% cobblemon:timer_ball 6");
        add(root, 9, "§bEXP Day", list("§7Candy for party growth."), "give %player% cobblemon:exp_candy_m 4");
        add(root, 10, "§dHalfway Reward", list("§7The track starts getting stronger."), "give %player% cobblemon:rare_candy 1", "give %player% cobblemon:luxury_ball 8");
        add(root, 11, "§bMint Leaves", list("§7Useful crafting materials."), "give %player% cobblemon:blue_mint_leaf 3", "give %player% cobblemon:red_mint_leaf 3");
        add(root, 12, "§bHeld Item Sampler", list("§7A light competitive item."), "give %player% cobblemon:muscle_band 1");
        add(root, 13, "§bEvolution Help", list("§7More evolution materials."), "give %player% cobblemon:thunder_stone 1", "give %player% cobblemon:leaf_stone 1");
        add(root, 14, "§dBattle Prep", list("§7Mid-late track battle supplies."), "give %player% cobblemon:hyper_potion 6", "give %player% cobblemon:revive 5");
        add(root, 15, "§dMajor Milestone", list("§7A strong day-fifteen reward."), "give %player% cobblemon:rare_candy 2", "give %player% cobblemon:ability_capsule 1");
        add(root, 16, "§dPremium Balls", list("§7High-quality capture tools."), "give %player% cobblemon:ultra_ball 16", "give %player% cobblemon:luxury_ball 10");
        add(root, 17, "§dCompetitive Prep", list("§7Useful training items."), "give %player% cobblemon:pp_up 1", "give %player% cobblemon:protein 1");
        add(root, 18, "§5Rare Utility", list("§7A stronger late-track item."), "give %player% cobblemon:lucky_egg 1");
        add(root, 19, "§5Final Stretch", list("§7Almost at the monthly finish."), "give %player% cobblemon:rare_candy 3", "give %player% cobblemon:max_revive 3");
        add(root, 20, "§6Monthly Champion", list("§7Best reward on the monthly track."), "give %player% cobblemon:ability_patch 1", "give %player% cobblemon:rare_candy 5", "give %player% cobblemon:master_ball 1");
        return root;
    }

    private static void add(Root root, int day, String name, List<String> description, String... commands) {
        RewardDay reward = new RewardDay();
        reward.day = day;
        reward.displayName = name;
        reward.description = new ArrayList<>(description);
        reward.commands = new ArrayList<>(Arrays.asList(commands));
        root.track.add(reward);
    }

    private static List<String> list(String... values) { return new ArrayList<>(Arrays.asList(values)); }

    public static class Root {
        public Settings settings = new Settings();
        public List<RewardDay> track = new ArrayList<>();
    }
    public static class Settings {
        public boolean enabled = true;
        public int requiredOnlineMinutes = 30;
        public int trackLengthDays = 20;
        public boolean autoOpenMenuOnEarn = false;
        public boolean announceProgressEveryFiveMinutes = true;
    }
    public static class RewardDay {
        public int day = 1;
        public String displayName = "Day Reward";
        public List<String> description = new ArrayList<>();
        public List<String> commands = new ArrayList<>();
    }
}
