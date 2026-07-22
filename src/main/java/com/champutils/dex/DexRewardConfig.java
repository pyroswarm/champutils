package com.champutils.dex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DexRewardConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static DexRewardRoot CONFIG = new DexRewardRoot();

    private DexRewardConfig() {
    }

    public static class DexRewardRoot {
        public int totalPokemon = 1025;
        public int tierStepPercent = 5;
        public Map<String, DexRewardTier> rewards = new LinkedHashMap<>();
    }

    public static class DexRewardTier {
        public int percent = 5;
        public String displayName = "5% Pokédex Reward";
        public List<String> commands = new ArrayList<>();
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, "dex_rewards.json");
            if (!file.exists()) {
                CONFIG = createDefaultRoot();
                try (FileWriter writer = new FileWriter(file)) {
                    GSON.toJson(CONFIG, writer);
                }
            }

            try (FileReader reader = new FileReader(file)) {
                DexRewardRoot loaded = GSON.fromJson(reader, DexRewardRoot.class);
                CONFIG = loaded == null ? createDefaultRoot() : loaded;
            }

            normalize();
            System.out.println("[ChampUtils] Loaded " + CONFIG.rewards.size() + " Pokédex reward tiers.");
        } catch (Exception e) {
            e.printStackTrace();
            CONFIG = createDefaultRoot();
        }
    }

    private static void normalize() {
        if (CONFIG == null) {
            CONFIG = createDefaultRoot();
        }
        if (CONFIG.totalPokemon <= 0) {
            CONFIG.totalPokemon = 1025;
        }
        if (CONFIG.tierStepPercent <= 0) {
            CONFIG.tierStepPercent = 5;
        }
        if (CONFIG.rewards == null || CONFIG.rewards.isEmpty()) {
            CONFIG.rewards = createDefaultRoot().rewards;
        }
    }

    public static DexRewardTier getTier(int percent) {
        normalize();
        DexRewardTier tier = CONFIG.rewards.get(String.valueOf(percent));
        if (tier == null) {
            tier = new DexRewardTier();
            tier.percent = percent;
            tier.displayName = percent + "% Pokédex Reward";
        }
        return tier;
    }

    private static DexRewardRoot createDefaultRoot() {
        DexRewardRoot root = new DexRewardRoot();
        root.totalPokemon = 1025;
        root.tierStepPercent = 5;

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 5;
            tier.displayName = "5% Pokédex Reward";
            tier.commands.add("give %player% cobblemon:poke_ball 32");
            tier.commands.add("give %player% cobblemon:great_ball 16");
            tier.commands.add("eco give %player% 2500");
            root.rewards.put("5", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 10;
            tier.displayName = "10% Pokédex Reward";
            tier.commands.add("give %player% cobblemon:great_ball 32");
            tier.commands.add("give %player% cobblemon:quick_ball 16");
            tier.commands.add("give %player% cobblemon:exp_candy_s 8");
            tier.commands.add("eco give %player% 5000");
            root.rewards.put("10", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 15;
            tier.displayName = "15% Pokédex Reward";
            tier.commands.add("give %player% cobblemon:ultra_ball 24");
            tier.commands.add("give %player% cobblemon:dusk_ball 16");
            tier.commands.add("give %player% cobblemon:rare_candy 2");
            tier.commands.add("eco give %player% 7500");
            root.rewards.put("15", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 20;
            tier.displayName = "20% Pokédex Reward";
            tier.commands.add("opencrates givecredit %player% f 2");
            tier.commands.add("give %player% cobblemon:exp_candy_m 8");
            tier.commands.add("give %player% cobblemon:rare_candy 3");
            tier.commands.add("eco give %player% 10000");
            root.rewards.put("20", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 25;
            tier.displayName = "25% Pokédex Reward";
            tier.commands.add("give %player% cobblemon:ultra_ball 32");
            tier.commands.add("give %player% cobblemon:ability_capsule 1");
            tier.commands.add("give %player% cobblemon:rare_candy 5");
            tier.commands.add("eco give %player% 15000");
            root.rewards.put("25", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 30;
            tier.displayName = "30% Pokédex Reward";
            tier.commands.add("give %player% cobblemon:exp_candy_l 8");
            tier.commands.add("give %player% cobblemon:pp_up 3");
            tier.commands.add("give %player% cobblemon:protein 2");
            tier.commands.add("give %player% cobblemon:calcium 2");
            tier.commands.add("eco give %player% 20000");
            root.rewards.put("30", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 35;
            tier.displayName = "35% Pokédex Reward";
            tier.commands.add("give %player% cobblemon:luxury_ball 32");
            tier.commands.add("give %player% cobblemon:friend_ball 16");
            tier.commands.add("give %player% cobblemon:rare_candy 6");
            tier.commands.add("eco give %player% 25000");
            root.rewards.put("35", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 40;
            tier.displayName = "40% Pokédex Reward";
            tier.commands.add("opencrates givecredit %player% e 2");
            tier.commands.add("give %player% cobblemon:ability_capsule 1");
            tier.commands.add("give %player% cobblemon:exp_candy_l 12");
            tier.commands.add("eco give %player% 30000");
            root.rewards.put("40", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 45;
            tier.displayName = "45% Pokédex Reward";
            tier.commands.add("give %player% cobblemon:choice_band 1");
            tier.commands.add("give %player% cobblemon:choice_specs 1");
            tier.commands.add("give %player% cobblemon:choice_scarf 1");
            tier.commands.add("give %player% cobblemon:rare_candy 8");
            tier.commands.add("eco give %player% 35000");
            root.rewards.put("45", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 50;
            tier.displayName = "50% Pokédex Reward";
            tier.commands.add("opencrates givecredit %player% d 1");
            tier.commands.add("give %player% cobblemon:ability_patch 1");
            tier.commands.add("give %player% cobblemon:rare_candy 10");
            tier.commands.add("give %player% cobblemon:exp_candy_xl 8");
            tier.commands.add("eco give %player% 50000");
            root.rewards.put("50", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 55;
            tier.displayName = "55% Pokédex Reward";
            tier.commands.add("give %player% cobblemon:leftovers 1");
            tier.commands.add("give %player% cobblemon:life_orb 1");
            tier.commands.add("give %player% cobblemon:focus_sash 1");
            tier.commands.add("give %player% cobblemon:pp_max 1");
            tier.commands.add("eco give %player% 55000");
            root.rewards.put("55", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 60;
            tier.displayName = "60% Pokédex Reward";
            tier.commands.add("opencrates givecredit %player% d 2");
            tier.commands.add("give %player% cobblemon:ability_patch 1");
            tier.commands.add("give %player% cobblemon:rare_candy 12");
            tier.commands.add("give %player% cobblemon:exp_candy_xl 12");
            tier.commands.add("eco give %player% 60000");
            root.rewards.put("60", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 65;
            tier.displayName = "65% Pokédex Reward";
            tier.commands.add("give %player% cobblemon:power_bracer 1");
            tier.commands.add("give %player% cobblemon:power_belt 1");
            tier.commands.add("give %player% cobblemon:power_lens 1");
            tier.commands.add("give %player% cobblemon:power_band 1");
            tier.commands.add("give %player% cobblemon:power_anklet 1");
            tier.commands.add("give %player% cobblemon:power_weight 1");
            tier.commands.add("eco give %player% 65000");
            root.rewards.put("65", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 70;
            tier.displayName = "70% Pokédex Reward";
            tier.commands.add("opencrates givecredit %player% c 1");
            tier.commands.add("give %player% cobblemon:ability_patch 2");
            tier.commands.add("give %player% cobblemon:rare_candy 15");
            tier.commands.add("give %player% cobblemon:exp_candy_xl 16");
            tier.commands.add("eco give %player% 70000");
            root.rewards.put("70", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 75;
            tier.displayName = "75% Pokédex Reward";
            tier.commands.add("give %player% cobblemon:lucky_egg 1");
            tier.commands.add("give %player% cobblemon:exp_share 1");
            tier.commands.add("give %player% cobblemon:master_ball 1");
            tier.commands.add("give %player% cobblemon:rare_candy 20");
            tier.commands.add("eco give %player% 75000");
            root.rewards.put("75", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 80;
            tier.displayName = "80% Pokédex Reward";
            tier.commands.add("opencrates givecredit %player% c 2");
            tier.commands.add("give %player% cobblemon:ability_patch 2");
            tier.commands.add("give %player% cobblemon:pp_max 2");
            tier.commands.add("give %player% cobblemon:exp_candy_xl 24");
            tier.commands.add("eco give %player% 80000");
            root.rewards.put("80", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 85;
            tier.displayName = "85% Pokédex Reward";
            tier.commands.add("give %player% cobblemon:heavy_duty_boots 1");
            tier.commands.add("give %player% cobblemon:assault_vest 1");
            tier.commands.add("give %player% cobblemon:rocky_helmet 1");
            tier.commands.add("give %player% cobblemon:rare_candy 25");
            tier.commands.add("eco give %player% 85000");
            root.rewards.put("85", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 90;
            tier.displayName = "90% Pokédex Reward";
            tier.commands.add("opencrates givecredit %player% b 1");
            tier.commands.add("give %player% cobblemon:master_ball 1");
            tier.commands.add("give %player% cobblemon:ability_patch 3");
            tier.commands.add("give %player% cobblemon:exp_candy_xl 32");
            tier.commands.add("eco give %player% 100000");
            root.rewards.put("90", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 95;
            tier.displayName = "95% Pokédex Reward";
            tier.commands.add("opencrates givecredit %player% a 1");
            tier.commands.add("give %player% cobblemon:master_ball 1");
            tier.commands.add("give %player% cobblemon:ability_patch 3");
            tier.commands.add("give %player% cobblemon:rare_candy 32");
            tier.commands.add("give %player% cobblemon:exp_candy_xl 32");
            tier.commands.add("eco give %player% 125000");
            root.rewards.put("95", tier);
        }

        {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = 100;
            tier.displayName = "100% Pokédex Reward";
            tier.commands.add("opencrates givecredit %player% s 1");
            tier.commands.add("opencrates givecredit %player% a 2");
            tier.commands.add("give %player% cobblemon:master_ball 2");
            tier.commands.add("give %player% cobblemon:ability_patch 5");
            tier.commands.add("give %player% cobblemon:rare_candy 64");
            tier.commands.add("give %player% cobblemon:exp_candy_xl 64");
            tier.commands.add("eco give %player% 250000");
            tier.commands.add("title %player% title {\"text\":\"Pokédex Complete!\",\"color\":\"gold\",\"bold\":true}");
            root.rewards.put("100", tier);
        }

        return root;
    }
}
