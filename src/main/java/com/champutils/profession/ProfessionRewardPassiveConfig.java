package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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

public class ProfessionRewardPassiveConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random RANDOM = new Random();

    public static Map<String, List<RewardEntry>> TABLES = new LinkedHashMap<>();

    public static class Root {
        public Map<String, List<RewardEntry>> tables = new LinkedHashMap<>();
    }

    public static class RewardEntry {
        public String item = "minecraft:air";
        public int min = 1;
        public int max = 1;
        public int weight = 1;
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, "profession_reward_passives.json");
            if (!file.exists()) {
                createDefault(file);
            }

            TABLES = readTables(file);
        } catch (Exception e) {
            e.printStackTrace();
            TABLES = new LinkedHashMap<>();
        }
    }

    /**
     * Current preferred API.
     */
    public static ItemStack rollReward(String tableId) {
        return roll(tableId);
    }

    /**
     * Backwards-compatible API used by older mining files.
     */
    public static ItemStack roll(String tableId) {
        if (TABLES == null || TABLES.isEmpty()) {
            load();
        }

        List<RewardEntry> entries = TABLES.get(tableId);
        if (entries == null || entries.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int totalWeight = 0;
        for (RewardEntry entry : entries) {
            if (entry != null) {
                totalWeight += Math.max(0, entry.weight);
            }
        }

        if (totalWeight <= 0) {
            return ItemStack.EMPTY;
        }

        int roll = RANDOM.nextInt(totalWeight);
        int cursor = 0;

        for (RewardEntry entry : entries) {
            if (entry == null) {
                continue;
            }

            cursor += Math.max(0, entry.weight);
            if (roll < cursor) {
                return createStack(entry);
            }
        }

        return ItemStack.EMPTY;
    }

    public static void giveRolled(ServerPlayer player, String tableId) {
        giveRolled(player, tableId, null, null);
    }

    public static void giveRolled(
            ServerPlayer player,
            String tableId,
            String title,
            String subtitlePrefix
    ) {
        if (player == null) {
            return;
        }

        ItemStack reward = rollReward(tableId);
        if (reward.isEmpty() || reward.getItem() == Items.AIR) {
            return;
        }

        ItemStack displayStack = reward.copy();
        String rewardName = displayStack.getHoverName().getString();
        int count = displayStack.getCount();

        ItemStack toGive = reward.copy();
        if (!player.getInventory().add(toGive)) {
            player.drop(toGive, false);
        }

        player.displayClientMessage(
                Component.literal("§6Found §e" + count + "x " + rewardName),
                true
        );

        if (title != null && !title.isBlank()) {
            String prefix = subtitlePrefix == null || subtitlePrefix.isBlank()
                    ? "§fFound "
                    : subtitlePrefix;

            ProfessionSpecialCelebration.celebrateSpecialActive(
                    player,
                    title,
                    prefix + count + "x " + rewardName
            );
        }
    }

    private static Map<String, List<RewardEntry>> readTables(File file) throws Exception {
        Map<String, List<RewardEntry>> loaded = new LinkedHashMap<>();

        try (FileReader reader = new FileReader(file)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                return loaded;
            }

            JsonObject root = rootElement.getAsJsonObject();

            // New format:
            // { "tables": { "table_name": [ ... ] } }
            if (root.has("tables") && root.get("tables").isJsonObject()) {
                readTableObject(root.getAsJsonObject("tables"), loaded);
            }

            // Legacy/simple format:
            // { "treasurePing": [ ... ], "shardFinder": [ ... ] }
            // Also harmlessly supports forestry/farming top-level tables.
            readTableObject(root, loaded);
        }

        return loaded;
    }

    private static void readTableObject(JsonObject object, Map<String, List<RewardEntry>> output) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String tableName = entry.getKey();
            JsonElement value = entry.getValue();

            if ("tables".equals(tableName)) {
                continue;
            }

            if (value == null || !value.isJsonArray()) {
                continue;
            }

            JsonArray array = value.getAsJsonArray();
            List<RewardEntry> rewards = new ArrayList<>();

            for (JsonElement rewardElement : array) {
                if (rewardElement == null || !rewardElement.isJsonObject()) {
                    continue;
                }

                RewardEntry reward = GSON.fromJson(rewardElement, RewardEntry.class);
                if (reward != null) {
                    rewards.add(reward);
                }
            }

            output.put(tableName, rewards);
        }
    }

    private static ItemStack createStack(RewardEntry entry) {
        if (entry == null || entry.item == null || entry.item.isBlank()) {
            return ItemStack.EMPTY;
        }

        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.item));
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }

            int min = Math.max(1, entry.min);
            int max = Math.max(min, entry.max);
            int amount = min + RANDOM.nextInt(max - min + 1);

            return new ItemStack(item, amount);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void createDefault(File file) throws Exception {
        Root root = new Root();

        // Mining
        root.tables.put("treasurePing", list(
                entry("minecraft:raw_iron", 3, 8, 28),
                entry("minecraft:raw_gold", 2, 5, 16),
                entry("minecraft:diamond", 1, 2, 5),
                entry("minecraft:emerald", 1, 2, 5),
                entry("cobblemon:rare_candy", 1, 1, 1)
        ));
        root.tables.put("shardFinder", list(
                entry("cobblemon:fire_stone", 1, 1, 20),
                entry("cobblemon:water_stone", 1, 1, 20),
                entry("cobblemon:thunder_stone", 1, 1, 20),
                entry("cobblemon:leaf_stone", 1, 1, 18),
                entry("cobblemon:moon_stone", 1, 1, 8),
                entry("cobblemon:shiny_stone", 1, 1, 5)
        ));
        root.tables.put("gemFinder", list(
                entry("minecraft:diamond", 1, 1, 75),
                entry("minecraft:emerald", 1, 1, 25)
        ));

        // Forestry
        root.tables.put("forestry_sap_finder", list(
                entry("minecraft:apple", 1, 3, 50),
                entry("cobblemon:miracle_seed", 1, 1, 8),
                entry("cobblemon:big_root", 1, 1, 6)
        ));
        root.tables.put("forestry_seed_finder", list(
                entry("minecraft:oak_sapling", 1, 2, 25),
                entry("minecraft:spruce_sapling", 1, 2, 20),
                entry("minecraft:cherry_sapling", 1, 1, 8),
                entry("minecraft:cocoa_beans", 1, 3, 12)
        ));

        // Farming
        root.tables.put("farming_seed_saver", list(
                entry("minecraft:wheat_seeds", 1, 3, 30),
                entry("minecraft:pumpkin_seeds", 1, 2, 10),
                entry("minecraft:melon_seeds", 1, 2, 10),
                entry("cobblemon:revival_herb", 1, 1, 2)
        ));
        root.tables.put("farming_golden_harvest", list(
                entry("minecraft:golden_carrot", 1, 2, 30),
                entry("minecraft:golden_apple", 1, 1, 4),
                entry("cobblemon:oran_berry", 1, 3, 20),
                entry("cobblemon:sitrus_berry", 1, 2, 8)
        ));



        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(root, writer);
        }
    }

    private static List<RewardEntry> list(RewardEntry... entries) {
        List<RewardEntry> list = new ArrayList<>();
        for (RewardEntry entry : entries) {
            list.add(entry);
        }
        return list;
    }

    private static RewardEntry entry(String item, int min, int max, int weight) {
        RewardEntry entry = new RewardEntry();
        entry.item = item;
        entry.min = min;
        entry.max = max;
        entry.weight = weight;
        return entry;
    }
}
