package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class ProfessionRewardPassiveConfig {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private static final Random RANDOM =
            new Random();

    private static Root CONFIG =
            createDefaultRoot();

    private ProfessionRewardPassiveConfig() {
    }

    public static void load() {
        try {
            File dir =
                    new File("config/champutils");

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file =
                    new File(
                            dir,
                            "profession_reward_passives.json"
                    );

            if (!file.exists()) {
                CONFIG =
                        createDefaultRoot();

                saveDefault(file);
            }

            try (
                    FileReader reader =
                            new FileReader(file)
            ) {
                Root loaded =
                        GSON.fromJson(
                                reader,
                                Root.class
                        );

                CONFIG =
                        normalize(
                                loaded
                        );
            }

            System.out.println(
                    "[ChampUtils] Loaded profession_reward_passives.json."
            );

        } catch (Exception e) {
            e.printStackTrace();
            CONFIG =
                    createDefaultRoot();
        }
    }

    public static ItemStack rollReward(
            String tableName
    ) {

        RewardTable table =
                getTable(
                        tableName
                );

        if (
                table == null ||
                        table.rewards == null ||
                        table.rewards.isEmpty()
        ) {
            return ItemStack.EMPTY;
        }

        int totalWeight =
                0;

        for (
                RewardEntry reward :
                table.rewards
        ) {
            if (
                    reward != null &&
                            reward.enabled &&
                            reward.weight > 0
            ) {
                totalWeight +=
                        reward.weight;
            }
        }

        if (totalWeight <= 0) {
            return ItemStack.EMPTY;
        }

        int roll =
                RANDOM.nextInt(
                        totalWeight
                );

        int cursor =
                0;

        for (
                RewardEntry reward :
                table.rewards
        ) {
            if (
                    reward == null ||
                            !reward.enabled ||
                            reward.weight <= 0
            ) {
                continue;
            }

            cursor +=
                    reward.weight;

            if (roll < cursor) {
                return createStack(
                        reward
                );
            }
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack createStack(
            RewardEntry reward
    ) {

        if (
                reward == null ||
                        reward.item == null ||
                        reward.item.isBlank()
        ) {
            return ItemStack.EMPTY;
        }

        Item item;

        try {
            item =
                    BuiltInRegistries.ITEM.get(
                            ResourceLocation.parse(
                                    reward.item
                            )
                    );
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }

        if (
                item == null ||
                        item == Items.AIR
        ) {
            return ItemStack.EMPTY;
        }

        int min =
                Math.max(
                        1,
                        reward.min
                );

        int max =
                Math.max(
                        min,
                        reward.max
                );

        int amount =
                min +
                        RANDOM.nextInt(
                                max - min + 1
                        );

        return new ItemStack(
                item,
                amount
        );
    }

    private static RewardTable getTable(
            String tableName
    ) {

        if (CONFIG == null) {
            CONFIG =
                    createDefaultRoot();
        }

        if (
                tableName == null ||
                        tableName.isBlank()
        ) {
            return null;
        }

        return switch (tableName) {
            case "treasurePing" -> CONFIG.treasurePing;
            case "shardFinder" -> CONFIG.shardFinder;
            case "gemFinder" -> CONFIG.gemFinder;
            default -> null;
        };
    }

    private static Root normalize(
            Root loaded
    ) {

        Root defaults =
                createDefaultRoot();

        if (loaded == null) {
            return defaults;
        }

        if (loaded.treasurePing == null) {
            loaded.treasurePing =
                    defaults.treasurePing;
        }

        if (loaded.shardFinder == null) {
            loaded.shardFinder =
                    defaults.shardFinder;
        }

        if (loaded.gemFinder == null) {
            loaded.gemFinder =
                    defaults.gemFinder;
        }

        sanitizeTable(
                loaded.treasurePing
        );
        sanitizeTable(
                loaded.shardFinder
        );
        sanitizeTable(
                loaded.gemFinder
        );

        return loaded;
    }

    private static void sanitizeTable(
            RewardTable table
    ) {

        if (table.rewards == null) {
            table.rewards =
                    new ArrayList<>();
        }

        for (
                RewardEntry reward :
                table.rewards
        ) {
            if (reward == null) {
                continue;
            }

            if (reward.min <= 0) {
                reward.min =
                        1;
            }

            if (reward.max < reward.min) {
                reward.max =
                        reward.min;
            }
        }
    }

    private static void saveDefault(
            File file
    ) throws Exception {

        try (
                FileWriter writer =
                        new FileWriter(file)
        ) {
            GSON.toJson(
                    CONFIG,
                    writer
            );
        }
    }

    private static Root createDefaultRoot() {
        Root root =
                new Root();

        root.treasurePing =
                table(
                        reward("minecraft:raw_iron", 3, 8, 28),
                        reward("minecraft:raw_copper", 6, 14, 26),
                        reward("minecraft:raw_gold", 2, 5, 18),
                        reward("minecraft:lapis_lazuli", 4, 10, 12),
                        reward("minecraft:redstone", 4, 12, 10),
                        reward("minecraft:diamond", 1, 2, 4),
                        reward("minecraft:emerald", 1, 2, 2)
                );

        root.shardFinder =
                table(
                        reward("cobblemon:fire_stone", 1, 1, 20),
                        reward("cobblemon:water_stone", 1, 1, 20),
                        reward("cobblemon:thunder_stone", 1, 1, 20),
                        reward("cobblemon:leaf_stone", 1, 1, 20),
                        reward("cobblemon:ice_stone", 1, 1, 20),
                        reward("cobblemon:moon_stone", 1, 1, 5),
                        reward("cobblemon:sun_stone", 1, 1, 5),
                        reward("cobblemon:dawn_stone", 1, 1, 5),
                        reward("cobblemon:dusk_stone", 1, 1, 5),
                        reward("cobblemon:shiny_stone", 1, 1, 5)
                );

        root.gemFinder =
                table(
                        reward("minecraft:diamond", 1, 1, 75),
                        reward("minecraft:emerald", 1, 1, 25)
                );

        return root;
    }

    private static RewardTable table(
            RewardEntry... rewards
    ) {

        RewardTable table =
                new RewardTable();

        for (
                RewardEntry reward :
                rewards
        ) {
            table.rewards.add(
                    reward
            );
        }

        return table;
    }

    private static RewardEntry reward(
            String item,
            int min,
            int max,
            int weight
    ) {

        RewardEntry reward =
                new RewardEntry();

        reward.item =
                item;
        reward.min =
                min;
        reward.max =
                max;
        reward.weight =
                weight;

        return reward;
    }

    public static class Root {
        public RewardTable treasurePing =
                new RewardTable();
        public RewardTable shardFinder =
                new RewardTable();
        public RewardTable gemFinder =
                new RewardTable();
    }

    public static class RewardTable {
        public List<RewardEntry> rewards =
                new ArrayList<>();
    }

    public static class RewardEntry {
        public String item =
                "minecraft:air";
        public int min =
                1;
        public int max =
                1;
        public int weight =
                1;
        public boolean enabled =
                true;
        public Map<String, String> notes =
                new LinkedHashMap<>();
    }
}
