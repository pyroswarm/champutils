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
        public int totalPokemon = 1009;
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
            CONFIG.totalPokemon = 1009;
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
        root.totalPokemon = 1009;
        root.tierStepPercent = 5;

        for (int percent = 5; percent <= 100; percent += 5) {
            DexRewardTier tier = new DexRewardTier();
            tier.percent = percent;
            tier.displayName = percent + "% Pokédex Reward";

            if (percent == 5) {
                tier.commands.add("give %player% cobblemon:poke_ball 16");
            } else if (percent == 10) {
                tier.commands.add("give %player% cobblemon:great_ball 8");
            } else if (percent == 25) {
                tier.commands.add("give %player% cobblemon:ultra_ball 8");
                tier.commands.add("give %player% cobblemon:rare_candy 1");
            } else if (percent == 50) {
                tier.commands.add("give %player% cobblemon:rare_candy 3");
                tier.commands.add("give %player% minecraft:diamond 5");
            } else if (percent == 100) {
                tier.commands.add("give %player% cobblemon:master_ball 1");
                tier.commands.add("title %player% title {\"text\":\"Pokédex Complete!\",\"color\":\"gold\",\"bold\":true}");
            } else {
                tier.commands.add("give %player% cobblemon:poke_ball " + Math.max(4, percent));
            }

            root.rewards.put(String.valueOf(percent), tier);
        }

        return root;
    }
}
