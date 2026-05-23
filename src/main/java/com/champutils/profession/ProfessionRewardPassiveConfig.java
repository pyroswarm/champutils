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

    private static final List<String> SHARD_RARITY_ORDER = List.of(
            "COMMON",
            "UNCOMMON",
            "RARE",
            "EPIC",
            "LEGENDARY"
    );

    public static class Root {
        public Map<String, List<RewardEntry>> tables = new LinkedHashMap<>();
    }

    public static class RewardEntry {
        public String item = "minecraft:air";
        public String type = "item";
        public String keyId = "";
        public String fragmentRarity = "";
        public int min = 1;
        public int max = 1;
        public int weight = 1;
        public boolean enabled = true;

        /**
         * Optional extra entry-level chance. This is checked after the entry wins
         * the weighted roll. Use this for ultra-rare digital keys/fragments without
         * making the whole table unreadable.
         */
        public double chancePercent = 100.0D;
        public double chancePerProfessionLevel = 0.0D;
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

    public static ItemStack rollReward(String tableId) {
        return roll(tableId);
    }

    public static ItemStack roll(String tableId) {
        return rollReward(tableId, null, null, null);
    }

    public static ItemStack rollReward(
            String tableId,
            ServerPlayer player,
            ProfessionType profession,
            ItemStack tool
    ) {
        if (TABLES == null || TABLES.isEmpty()) {
            load();
        }

        List<RewardEntry> entries = TABLES.get(tableId);
        if (entries == null || entries.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int totalWeight = 0;
        for (RewardEntry entry : entries) {
            if (entry != null && entry.enabled) {
                totalWeight += Math.max(0, entry.weight);
            }
        }

        if (totalWeight <= 0) {
            return ItemStack.EMPTY;
        }

        int roll = RANDOM.nextInt(totalWeight);
        int cursor = 0;

        for (RewardEntry entry : entries) {
            if (entry == null || !entry.enabled) {
                continue;
            }

            cursor += Math.max(0, entry.weight);
            if (roll < cursor) {
                if (!passesEntryChance(entry, player, profession)) {
                    return ItemStack.EMPTY;
                }
                return createStack(entry, player, profession, tool);
            }
        }

        return ItemStack.EMPTY;
    }

    public static void giveRolled(ServerPlayer player, String tableId) {
        giveRolled(player, tableId, null, null, inferProfession(tableId), player == null ? ItemStack.EMPTY : player.getMainHandItem());
    }

    public static void giveRolled(
            ServerPlayer player,
            String tableId,
            String title,
            String subtitlePrefix
    ) {
        giveRolled(player, tableId, title, subtitlePrefix, inferProfession(tableId), player == null ? ItemStack.EMPTY : player.getMainHandItem());
    }

    public static void giveRolled(
            ServerPlayer player,
            String tableId,
            String title,
            String subtitlePrefix,
            ProfessionType profession,
            ItemStack tool
    ) {
        if (player == null) {
            return;
        }

        ItemStack reward = rollReward(tableId, player, profession, tool);
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

        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(
                    Component.literal("§6Found §e" + count + "x " + rewardName),
                    true
            );
        }

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

    private static boolean passesEntryChance(
            RewardEntry entry,
            ServerPlayer player,
            ProfessionType profession
    ) {
        double chance = entry.chancePercent;

        if (player != null && profession != null && entry.chancePerProfessionLevel != 0.0D) {
            int level = Math.max(1, ProfessionManager.getLevel(player, profession));
            chance += level * entry.chancePerProfessionLevel;
        }

        chance = Math.max(0.0D, Math.min(100.0D, chance));
        return chance >= 100.0D || RANDOM.nextDouble() * 100.0D < chance;
    }

    private static Map<String, List<RewardEntry>> readTables(File file) throws Exception {
        Map<String, List<RewardEntry>> loaded = new LinkedHashMap<>();

        try (FileReader reader = new FileReader(file)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                return loaded;
            }

            JsonObject root = rootElement.getAsJsonObject();

            if (root.has("tables") && root.get("tables").isJsonObject()) {
                readTableObject(root.getAsJsonObject("tables"), loaded);
            }

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

            JsonArray array = null;

            if (value != null && value.isJsonArray()) {
                array = value.getAsJsonArray();
            } else if (value != null && value.isJsonObject()) {
                JsonObject tableObject = value.getAsJsonObject();
                if (tableObject.has("rewards") && tableObject.get("rewards").isJsonArray()) {
                    array = tableObject.getAsJsonArray("rewards");
                }
            }

            if (array == null) {
                continue;
            }

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

    private static ItemStack createStack(
            RewardEntry entry,
            ServerPlayer player,
            ProfessionType profession,
            ItemStack tool
    ) {
        if (entry == null) {
            return ItemStack.EMPTY;
        }

        String type = entry.type == null || entry.type.isBlank()
                ? "item"
                : entry.type.trim().toLowerCase();

        int min = Math.max(1, entry.min);
        int max = Math.max(min, entry.max);
        int amount = min + RANDOM.nextInt(max - min + 1);

        if ("profession_fragment".equals(type)) {
            String rarity = entry.fragmentRarity == null || entry.fragmentRarity.isBlank()
                    ? "COMMON"
                    : entry.fragmentRarity.trim().toUpperCase();
            if ("MYTHIC".equals(rarity)) {
                rarity = "LEGENDARY";
            }
            return ProfessionFragmentManager.createFragmentStack(rarity, amount);
        }

        if ("profession_fragment_gamble".equals(type) || "fragment_gamble".equals(type)) {
            String rarity = rollAllowedFragmentRarity(tool);
            if (rarity == null || rarity.isBlank()) {
                return ItemStack.EMPTY;
            }
            return ProfessionFragmentManager.createFragmentStack(rarity, amount);
        }

        return createItemStack(entry, amount);
    }

    private static ItemStack createItemStack(RewardEntry entry, int amount) {
        if (entry.item == null || entry.item.isBlank()) {
            return ItemStack.EMPTY;
        }

        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.item));
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }

            return new ItemStack(item, amount);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Gamble pool rule:
     * - Common tool: COMMON or UNCOMMON
     * - Uncommon tool: COMMON / UNCOMMON / RARE
     * - Rare tool: COMMON / UNCOMMON / RARE / EPIC
     * - Epic tool: COMMON / UNCOMMON / RARE / EPIC / LEGENDARY
     * - Legendary tool: COMMON through LEGENDARY
     * - Mythic tool: COMMON through LEGENDARY
     * Mythic fragments are never produced by profession passive drops.
     */
    private static String rollAllowedFragmentRarity(ItemStack tool) {
        String toolRarity = getToolRarity(tool);
        int maxIndex = switch (toolRarity) {
            case "UNCOMMON" -> 2;
            case "RARE" -> 3;
            case "EPIC", "LEGENDARY", "MYTHIC" -> 4;
            case "COMMON" -> 1;
            default -> 1;
        };

        maxIndex = Math.max(0, Math.min(maxIndex, SHARD_RARITY_ORDER.size() - 1));
        return SHARD_RARITY_ORDER.get(RANDOM.nextInt(maxIndex + 1));
    }

    private static String getToolRarity(ItemStack tool) {
        if (tool == null || tool.isEmpty() || !ProfessionToolMetadata.isProfessionTool(tool)) {
            return "COMMON";
        }

        String toolId = ProfessionToolMetadata.getToolId(tool);
        ProfessionToolConfig.ToolData data = ProfessionToolConfig.TOOLS.get(toolId);
        if (data == null || data.rarity == null || data.rarity.isBlank()) {
            return "COMMON";
        }

        return data.rarity.trim().toUpperCase();
    }

    private static ProfessionType inferProfession(String tableId) {
        if (tableId == null) {
            return null;
        }

        String normalized = tableId.toLowerCase();
        if (normalized.startsWith("forestry")) {
            return ProfessionType.FORESTRY;
        }
        if (normalized.startsWith("farming")) {
            return ProfessionType.FARMING;
        }
        if (normalized.contains("treasure") || normalized.contains("shard") || normalized.contains("gem")) {
            return ProfessionType.MINING;
        }
        return null;
    }

    private static void createDefault(File file) throws Exception {
        Root root = new Root();

        root.tables.put("treasurePing", list(
                entry("minecraft:raw_iron", 3, 8, 28),
                entry("minecraft:raw_copper", 6, 14, 26),
                entry("minecraft:raw_gold", 2, 5, 18),
                entry("minecraft:lapis_lazuli", 4, 10, 12),
                entry("minecraft:redstone", 4, 12, 10),
                entry("minecraft:diamond", 1, 2, 4),
                entry("minecraft:emerald", 1, 2, 2),
                entry("cobblemon:hard_stone", 1, 1, 2),
                entry("cobblemon:soft_sand", 1, 1, 2),
                entry("cobblemon:ancient_relic_copper", 1, 1, 1)
        ));

        root.tables.put("shardFinder", list(
                entry("cobblemon:fire_stone", 1, 1, 14),
                entry("cobblemon:water_stone", 1, 1, 14),
                entry("cobblemon:thunder_stone", 1, 1, 14),
                entry("cobblemon:leaf_stone", 1, 1, 14),
                entry("cobblemon:ice_stone", 1, 1, 12),
                entry("cobblemon:moon_stone", 1, 1, 6),
                entry("cobblemon:sun_stone", 1, 1, 6),
                entry("cobblemon:dawn_stone", 1, 1, 5),
                entry("cobblemon:dusk_stone", 1, 1, 5),
                entry("cobblemon:shiny_stone", 1, 1, 4),
                fragmentGambleEntry(1, 1, 1, 0.05D, 0.003D)
        ));

        root.tables.put("gemFinder", list(
                entry("minecraft:diamond", 1, 1, 50),
                entry("minecraft:emerald", 1, 1, 25),
                entry("cobblemon:hard_stone", 1, 1, 8),
                entry("cobblemon:light_clay", 1, 1, 4),
                entry("cobblemon:metal_coat", 1, 1, 3),
                fragmentGambleEntry(1, 1, 1, 0.04D, 0.0025D)
        ));

        root.tables.put("forestry_sap_finder", list(
                entry("minecraft:apple", 1, 3, 36),
                entry("cobblemon:miracle_seed", 1, 1, 8),
                entry("cobblemon:big_root", 1, 1, 6),
                entry("cobblemon:absorb_bulb", 1, 1, 4),
                entry("cobblemon:silver_powder", 1, 1, 4),
                entry("cobblemon:grassy_seed", 1, 1, 2),
                fragmentGambleEntry(1, 1, 1, 0.04D, 0.0025D)
        ));

        root.tables.put("forestry_seed_finder", list(
                entry("minecraft:oak_sapling", 1, 2, 20),
                entry("minecraft:spruce_sapling", 1, 2, 18),
                entry("minecraft:cherry_sapling", 1, 1, 8),
                entry("minecraft:cocoa_beans", 1, 3, 12),
                entry("cobblemon:red_apricorn", 1, 2, 7),
                entry("cobblemon:blue_apricorn", 1, 2, 7),
                entry("cobblemon:green_apricorn", 1, 2, 7),
                entry("cobblemon:yellow_apricorn", 1, 2, 7),
                entry("cobblemon:revival_herb", 1, 1, 2),
                fragmentGambleEntry(1, 1, 1, 0.04D, 0.0025D)
        ));

        root.tables.put("farming_seed_saver", list(
                entry("minecraft:wheat_seeds", 1, 3, 24),
                entry("minecraft:pumpkin_seeds", 1, 2, 10),
                entry("minecraft:melon_seeds", 1, 2, 10),
                entry("cobblemon:oran_berry", 1, 3, 12),
                entry("cobblemon:leppa_berry", 1, 2, 8),
                entry("cobblemon:pecha_berry", 1, 2, 8),
                entry("cobblemon:cheri_berry", 1, 2, 8),
                entry("cobblemon:revival_herb", 1, 1, 2),
                fragmentGambleEntry(1, 1, 1, 0.04D, 0.0025D)
        ));

        root.tables.put("farming_golden_harvest", list(
                entry("minecraft:golden_carrot", 1, 2, 28),
                entry("minecraft:golden_apple", 1, 1, 4),
                entry("cobblemon:oran_berry", 1, 3, 16),
                entry("cobblemon:sitrus_berry", 1, 2, 8),
                entry("cobblemon:lum_berry", 1, 1, 4),
                entry("cobblemon:liechi_berry", 1, 1, 2),
                fragmentGambleEntry(1, 1, 1, 0.05D, 0.003D)
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

    private static RewardEntry fragmentGambleEntry(int min, int max, int weight, double chance, double chancePerLevel) {
        RewardEntry entry = new RewardEntry();
        entry.type = "profession_fragment_gamble";
        entry.min = min;
        entry.max = max;
        entry.weight = weight;
        entry.chancePercent = chance;
        entry.chancePerProfessionLevel = chancePerLevel;
        return entry;
    }
}
