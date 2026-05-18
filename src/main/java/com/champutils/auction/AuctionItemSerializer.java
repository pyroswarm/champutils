package com.champutils.auction;

import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolMetadata;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuctionItemSerializer {

    private static final Pattern QUALITY_PATTERN = Pattern.compile("\\[\\s*([0-9]+(?:\\.[0-9]+)?)%\\s*]");
    private static final Pattern STAT_PATTERN = Pattern.compile("([+-]?[0-9]+(?:\\.[0-9]+)?%?)\\s+(.+?)(?:\\s+\\[([0-9]+(?:\\.[0-9]+)?)%])?$");

    private AuctionItemSerializer() {
    }

    public static JsonObject toPayload(ServerPlayer player, ItemStack stack) throws Exception {
        ItemStack copy = stack.copy();
        Tag raw = copy.save(player.registryAccess());

        if (!(raw instanceof CompoundTag tag)) {
            throw new IllegalStateException("Could not serialize item stack.");
        }

        String itemId = BuiltInRegistries.ITEM.getKey(copy.getItem()).toString();

        JsonObject payload = new JsonObject();
        payload.addProperty("item_id", itemId);
        payload.addProperty("item_slug", itemSlug(itemId));
        payload.addProperty("display_name", clean(copy.getHoverName().getString()));
        payload.addProperty("count", copy.getCount());
        payload.addProperty("serialized_stack", Base64.getEncoder().encodeToString(tag.toString().getBytes(StandardCharsets.UTF_8)));

        JsonArray lore = captureTooltip(player, copy);
        payload.add("lore", lore);

        JsonObject parsedToolData = parseChampToolData(copy, lore);
        for (String key : parsedToolData.keySet()) {
            payload.add(key, parsedToolData.get(key));
        }

        return payload;
    }

    public static ItemStack fromPayload(ServerPlayer player, JsonObject payload) throws Exception {
        if (payload == null || !payload.has("serialized_stack")) {
            return ItemStack.EMPTY;
        }

        String encoded = payload.get("serialized_stack").getAsString();
        String snbt = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        CompoundTag tag = TagParser.parseTag(snbt);
        return ItemStack.parse(player.registryAccess(), tag).orElse(ItemStack.EMPTY);
    }

    private static JsonArray captureTooltip(ServerPlayer player, ItemStack stack) {
        JsonArray lore = new JsonArray();

        try {
            List<Component> lines = stack.getTooltipLines(Item.TooltipContext.EMPTY, player, TooltipFlag.Default.NORMAL);
            for (Component component : lines) {
                String line = clean(component.getString());
                if (!line.isBlank() && !line.equals(clean(stack.getHoverName().getString()))) {
                    lore.add(line);
                }
            }
        } catch (Throwable ignored) {
            // Metadata parsing below is authoritative for ChampTools.
        }

        try {
            ItemLore itemLore = stack.get(DataComponents.LORE);
            if (itemLore != null) {
                for (Component component : itemLore.lines()) {
                    String line = clean(component.getString());
                    if (!line.isBlank()) lore.add(line);
                }
            }
        } catch (Throwable ignored) {
        }

        return dedupe(lore);
    }

    private static JsonObject parseChampToolData(ItemStack stack, JsonArray lore) {
        JsonObject data = new JsonObject();
        JsonArray stats = new JsonArray();

        if (ProfessionToolMetadata.isProfessionTool(stack)) {
            String toolId = ProfessionToolMetadata.getToolId(stack);
            ProfessionToolConfig.ToolData toolData = toolId == null ? null : ProfessionToolConfig.TOOLS.get(toolId);

            if (toolId != null && !toolId.isBlank()) {
                data.addProperty("tool_id", toolId);
            }

            String displayName = clean(stack.getHoverName().getString());
            if (!displayName.isBlank()) {
                data.addProperty("tool_name", displayName.replaceAll("\\s*\\[[0-9]+(?:\\.[0-9]+)?%\\]", "").trim());
            }

            if (toolData != null) {
                if (toolData.displayName != null && !toolData.displayName.isBlank()) {
                    data.addProperty("tool_name", toolData.displayName);
                }
                data.addProperty("rarity", safe(toolData.rarity));
                data.addProperty("profession", safe(toolData.profession));
                data.addProperty("required_level", toolData.requiredLevel);
                data.addProperty("base_item", safe(toolData.baseItem));
                data.addProperty("base_item_slug", itemSlug(toolData.baseItem));
                data.addProperty("custom_model_data", toolData.customModelData);
                if (toolId != null && !toolId.isBlank()) {
                    data.addProperty("image_url", "/auction-icons/tools/" + toolId + ".png");
                }

                if (toolData.activeAbility != null && !toolData.activeAbility.isBlank()) {
                    data.addProperty("active_ability_id", toolData.activeAbility);
                    data.addProperty("active_ability", formatWords(toolData.activeAbility));
                    data.addProperty("active_cooldown_seconds", toolData.activeCooldownSeconds);
                    if (toolData.activeDurationSeconds > 0) {
                        data.addProperty("active_duration_seconds", toolData.activeDurationSeconds);
                    }
                }
            }

            data.addProperty("overall_quality", roundOne(ProfessionToolMetadata.getQuality(stack)));
            data.addProperty("rerolls", ProfessionToolMetadata.getRerolls(stack));

            int currentDurability = ProfessionToolMetadata.getCurrentDurability(stack);
            int maxDurability = ProfessionToolMetadata.getMaxDurability(stack);
            if (maxDurability > 0) {
                data.addProperty("durability", currentDurability);
                data.addProperty("max_durability", maxDurability);
            }

            Map<String, Double> rolledStats = ProfessionToolMetadata.getRolledStats(stack);
            if (rolledStats != null && !rolledStats.isEmpty()) {
                for (Map.Entry<String, Double> entry : rolledStats.entrySet()) {
                    String statId = entry.getKey();
                    Double value = entry.getValue();
                    if (statId == null || statId.isBlank() || value == null) continue;

                    JsonObject stat = new JsonObject();
                    stat.addProperty("key", statId);
                    stat.addProperty("name", formatStatName(statId));
                    stat.addProperty("value", formatStatValue(value));
                    stat.addProperty("raw_value", value);

                    Double rollPercent = calculateRollPercent(toolData, statId, value);
                    if (rollPercent != null) {
                        stat.addProperty("roll_percent", roundOne(rollPercent));
                    }

                    stats.add(stat);
                }
            }

            if (stats.size() > 0) {
                data.add("stats", stats);
            }

            return data;
        }

        String displayName = clean(stack.getHoverName().getString());
        Matcher qualityMatcher = QUALITY_PATTERN.matcher(displayName);
        if (qualityMatcher.find()) {
            try {
                data.addProperty("overall_quality", Double.parseDouble(qualityMatcher.group(1)));
                data.addProperty("tool_name", displayName.replaceAll("\\s*\\[[0-9]+(?:\\.[0-9]+)?%\\]", "").trim());
            } catch (Exception ignored) {
            }
        }

        for (int i = 0; i < lore.size(); i++) {
            String line = clean(lore.get(i).getAsString());
            String lower = line.toLowerCase();

            if (lower.contains("rarity")) {
                String value = afterColon(line);
                if (!value.isBlank()) data.addProperty("rarity", value);
            } else if (lower.contains("profession")) {
                String value = afterColon(line);
                if (!value.isBlank()) data.addProperty("profession", value);
            } else if (lower.contains("durability")) {
                parseDurability(data, line);
            } else if (lower.contains("reroll")) {
                Integer rerolls = firstInt(line);
                if (rerolls != null) data.addProperty("rerolls", rerolls);
            }

            Matcher statMatcher = STAT_PATTERN.matcher(line);
            if (statMatcher.find() && looksLikeToolStat(line)) {
                JsonObject stat = new JsonObject();
                stat.addProperty("value", statMatcher.group(1));
                stat.addProperty("name", clean(statMatcher.group(2)));
                if (statMatcher.group(3) != null) {
                    try {
                        stat.addProperty("roll_percent", Double.parseDouble(statMatcher.group(3)));
                    } catch (Exception ignored) {
                    }
                }
                stats.add(stat);
            }
        }

        if (stats.size() > 0) {
            data.add("stats", stats);
        }

        return data;
    }

    private static Double calculateRollPercent(ProfessionToolConfig.ToolData toolData, String statId, double value) {
        if (toolData == null || toolData.statRanges == null || statId == null) return null;
        ProfessionToolConfig.StatRange range = toolData.statRanges.get(statId);
        if (range == null) return null;

        double min = range.min;
        double max = range.max;
        if (Double.compare(min, max) == 0) return 100.0D;

        double low = Math.min(min, max);
        double high = Math.max(min, max);
        double clamped = Math.max(low, Math.min(high, value));
        return ((clamped - low) / (high - low)) * 100.0D;
    }

    private static boolean looksLikeToolStat(String line) {
        String lower = line.toLowerCase();
        return lower.contains("chance")
            || lower.contains("speed")
            || lower.contains("drop")
            || lower.contains("durability")
            || lower.contains("save")
            || lower.contains("bonus")
            || lower.contains("fortune")
            || lower.contains("smelt")
            || lower.contains("quality")
            || lower.contains("mining")
            || lower.contains("farming")
            || lower.contains("forestry");
    }

    private static void parseDurability(JsonObject data, String line) {
        Matcher matcher = Pattern.compile("([0-9]+)\\s*/\\s*([0-9]+)").matcher(line);
        if (matcher.find()) {
            try {
                data.addProperty("durability", Integer.parseInt(matcher.group(1)));
                data.addProperty("max_durability", Integer.parseInt(matcher.group(2)));
            } catch (Exception ignored) {
            }
        }
    }

    private static Integer firstInt(String line) {
        Matcher matcher = Pattern.compile("([0-9]+)").matcher(line);
        if (!matcher.find()) return null;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String afterColon(String line) {
        int idx = line.indexOf(':');
        return idx >= 0 ? clean(line.substring(idx + 1)) : "";
    }

    private static JsonArray dedupe(JsonArray input) {
        JsonArray output = new JsonArray();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < input.size(); i++) {
            String value = input.get(i).getAsString();
            if (seen.add(value)) output.add(value);
        }
        return output;
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String itemSlug(String itemId) {
        if (itemId == null || itemId.isBlank()) return "";
        String cleaned = itemId.trim();
        int colon = cleaned.indexOf(':');
        if (colon >= 0 && colon + 1 < cleaned.length()) {
            cleaned = cleaned.substring(colon + 1);
        }
        return cleaned.toLowerCase().replaceAll("[^a-z0-9_/-]", "_");
    }

    private static double roundOne(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }

    private static String formatStatName(String statId) {
        if (statId == null || statId.isBlank()) return "Stat";
        return formatWords(statId.replaceAll("([a-z])([A-Z])", "$1 $2"));
    }

    private static String formatStatValue(double value) {
        return ((int) Math.floor(value)) + "%";
    }

    private static String formatWords(String value) {
        if (value == null || value.isBlank()) return "";
        String cleaned = value.replace('_', ' ').replace('-', ' ').trim();
        String[] parts = cleaned.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) builder.append(part.substring(1));
        }
        return builder.toString();
    }
}
