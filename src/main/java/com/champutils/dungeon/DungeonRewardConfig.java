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

public final class DungeonRewardConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static RewardRoot CONFIG = new RewardRoot();

    private DungeonRewardConfig() {
    }

    public static class RewardRoot {
        public int normalChestCount = 2;
        public int pokemonChestCount = 1;
        /** Legacy fallback. If you already had chestCount in your config, normalChestCount now replaces it. */
        public int chestCount = 2;
        public boolean announceRareRewards = true;
        public boolean announceLegendaryPokemon = true;
        public boolean announceMythicTools = true;
        public Map<String, RewardTable> rewardsByRarity = new LinkedHashMap<>();
    }

    public static class RewardTable {
        public int guaranteedFragmentsMin = 1;
        public int guaranteedFragmentsMax = 3;
        public double higherTierFragmentChance = 0.0D;
        public int higherTierFragmentsMin = 0;
        public int higherTierFragmentsMax = 0;
        public double unidentifiedToolChance = 0.5D;
        public double ascendedToolChance = 0.0D;
        public List<ItemReward> itemRewards = new ArrayList<>();
        public List<CommandReward> commandRewards = new ArrayList<>();
        /** Rolls only from the special Pokemon dungeon crate, not the two normal crates. */
        public boolean guaranteePokemonReward = true;
        public List<PokemonReward> pokemonRewards = new ArrayList<>();
    }

    public static class ItemReward {
        public String id = "minecraft:diamond";
        public int min = 1;
        public int max = 1;
        public double chance = 100.0D;
        public String display = "Diamond";
    }

    public static class CommandReward {
        public String display = "Console Reward";
        public double chance = 0.0D;
        public List<String> commands = new ArrayList<>();
    }

    public static class PokemonReward {
        public String display = "Pokemon Reward";
        public String pokemon = "pikachu";
        public int level = 20;
        /** For Pokemon crates, this is a WEIGHT, not a percent. Higher number = more common. */
        public double chance = 0.0D;
        public double shinyChance = 0.0D;
        public boolean announce = false;
        /** These run from console if this Pokemon reward rolls. */
        public List<String> commands = new ArrayList<>();
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, "dungeon_rewards.json");
            if (!file.exists()) {
                CONFIG = createDefaultRoot();
                try (FileWriter writer = new FileWriter(file)) {
                    GSON.toJson(CONFIG, writer);
                }
            }

            try (FileReader reader = new FileReader(file)) {
                RewardRoot loaded = GSON.fromJson(reader, RewardRoot.class);
                CONFIG = loaded == null ? createDefaultRoot() : loaded;
            }

            if (CONFIG.rewardsByRarity == null || CONFIG.rewardsByRarity.isEmpty()) {
                CONFIG = createDefaultRoot();
            }

            System.out.println("[ChampUtils] Loaded dungeon reward tables for " + CONFIG.rewardsByRarity.size() + " rarities.");
        } catch (Exception e) {
            e.printStackTrace();
            CONFIG = createDefaultRoot();
        }
    }

    public static RewardTable getTable(DungeonRarity rarity) {
        if (rarity == null) {
            rarity = DungeonRarity.COMMON;
        }

        RewardTable table = CONFIG.rewardsByRarity.get(rarity.name());
        if (table == null) {
            table = createDefaultTable(rarity);
        }
        return table;
    }

    private static RewardRoot createDefaultRoot() {
        RewardRoot root = new RewardRoot();
        for (DungeonRarity rarity : DungeonRarity.values()) {
            root.rewardsByRarity.put(rarity.name(), createDefaultTable(rarity));
        }
        return root;
    }

    private static RewardTable createDefaultTable(DungeonRarity rarity) {
        RewardTable table = new RewardTable();

        switch (rarity) {
            case COMMON -> {
                table.guaranteedFragmentsMin = 2;
                table.guaranteedFragmentsMax = 5;
                table.higherTierFragmentChance = 5.0D;
                table.higherTierFragmentsMin = 1;
                table.higherTierFragmentsMax = 1;
                table.unidentifiedToolChance = 0.75D;
                table.itemRewards.add(item("cobblemon:poke_ball", 4, 8, 100.0D, "Poke Balls"));
                table.itemRewards.add(item("cobblemon:potion", 1, 3, 35.0D, "Potions"));
                table.pokemonRewards.add(pokemon("Pikachu", "pikachu", 20, 60.0D, 0.0D, false));
                table.pokemonRewards.add(pokemon("Eevee", "eevee", 20, 40.0D, 0.0D, false));
            }
            case UNCOMMON -> {
                table.guaranteedFragmentsMin = 3;
                table.guaranteedFragmentsMax = 7;
                table.higherTierFragmentChance = 7.5D;
                table.higherTierFragmentsMin = 1;
                table.higherTierFragmentsMax = 2;
                table.unidentifiedToolChance = 1.0D;
                table.itemRewards.add(item("cobblemon:great_ball", 3, 6, 100.0D, "Great Balls"));
                table.itemRewards.add(item("cobblemon:super_potion", 1, 2, 35.0D, "Super Potions"));
                table.pokemonRewards.add(pokemon("Lucario", "lucario", 40, 55.0D, 0.0D, false));
                table.pokemonRewards.add(pokemon("Gengar", "gengar", 40, 45.0D, 0.0D, false));
            }
            case RARE -> {
                table.guaranteedFragmentsMin = 4;
                table.guaranteedFragmentsMax = 9;
                table.higherTierFragmentChance = 10.0D;
                table.higherTierFragmentsMin = 1;
                table.higherTierFragmentsMax = 2;
                table.unidentifiedToolChance = 1.25D;
                table.itemRewards.add(item("cobblemon:ultra_ball", 2, 5, 100.0D, "Ultra Balls"));
                table.itemRewards.add(item("cobblemon:hyper_potion", 1, 2, 35.0D, "Hyper Potions"));
                table.pokemonRewards.add(pokemon("Dragonite", "dragonite", 60, 50.0D, 0.0D, false));
                table.pokemonRewards.add(pokemon("Tyranitar", "tyranitar", 60, 50.0D, 0.0D, false));
            }
            case EPIC -> {
                table.guaranteedFragmentsMin = 5;
                table.guaranteedFragmentsMax = 11;
                table.higherTierFragmentChance = 12.5D;
                table.higherTierFragmentsMin = 1;
                table.higherTierFragmentsMax = 3;
                table.unidentifiedToolChance = 1.5D;
                table.itemRewards.add(item("cobblemon:ultra_ball", 4, 8, 100.0D, "Ultra Balls"));
                table.itemRewards.add(item("cobblemon:rare_candy", 1, 1, 8.0D, "Rare Candy"));
                table.pokemonRewards.add(pokemon("Metagross", "metagross", 80, 50.0D, 0.0D, false));
                table.pokemonRewards.add(pokemon("Garchomp", "garchomp", 80, 50.0D, 0.0D, false));
            }
            case LEGENDARY -> {
                table.guaranteedFragmentsMin = 6;
                table.guaranteedFragmentsMax = 14;
                table.higherTierFragmentChance = 15.0D;
                table.higherTierFragmentsMin = 1;
                table.higherTierFragmentsMax = 3;
                table.unidentifiedToolChance = 2.0D;
                table.ascendedToolChance = 0.05D;
                table.itemRewards.add(item("cobblemon:dream_ball", 1, 3, 35.0D, "Dream Balls"));
                table.itemRewards.add(item("cobblemon:beast_ball", 1, 2, 20.0D, "Beast Balls"));
                table.itemRewards.add(item("cobblemon:master_ball", 1, 1, 0.15D, "Master Ball"));
                table.pokemonRewards.add(pokemon("Mewtwo", "mewtwo", 90, 1.0D, 0.0D, true));
                table.pokemonRewards.add(pokemon("Rayquaza", "rayquaza", 90, 1.0D, 0.0D, true));
            }
            case MYTHIC -> {
                table.guaranteedFragmentsMin = 8;
                table.guaranteedFragmentsMax = 18;
                table.higherTierFragmentChance = 0.0D;
                table.higherTierFragmentsMin = 0;
                table.higherTierFragmentsMax = 0;
                table.unidentifiedToolChance = 2.5D;
                table.ascendedToolChance = 0.10D;
                table.itemRewards.add(item("cobblemon:dream_ball", 2, 4, 45.0D, "Dream Balls"));
                table.itemRewards.add(item("cobblemon:beast_ball", 1, 3, 30.0D, "Beast Balls"));
                table.itemRewards.add(item("cobblemon:master_ball", 1, 1, 0.35D, "Master Ball"));
                table.pokemonRewards.add(pokemon("Mewtwo", "mewtwo", 100, 2.0D, 0.0D, true));
                table.pokemonRewards.add(pokemon("Rayquaza", "rayquaza", 100, 2.0D, 0.0D, true));
                table.pokemonRewards.add(pokemon("Arceus", "arceus", 100, 0.25D, 0.0D, true));
            }
        }

        return table;
    }

    private static ItemReward item(String id, int min, int max, double chance, String display) {
        ItemReward reward = new ItemReward();
        reward.id = id;
        reward.min = min;
        reward.max = max;
        reward.chance = chance;
        reward.display = display;
        return reward;
    }

    private static PokemonReward pokemon(String display, String pokemon, int level, double chance, double shinyChance, boolean announce) {
        PokemonReward reward = new PokemonReward();
        reward.display = display;
        reward.pokemon = pokemon;
        reward.level = level;
        reward.chance = chance;
        reward.shinyChance = shinyChance;
        reward.announce = announce;
        // Adjust this command to match your Cobblemon command syntax if needed.
        reward.commands.add("givepokemonother %player% %pokemon% level=%level% %shiny_flag%");
        return reward;
    }
}
