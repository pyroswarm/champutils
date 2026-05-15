package com.champutils.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CobblemonHeldItemUtil {

    private CobblemonHeldItemUtil() {}

    public static ItemStack createHeldItemStack(String configuredItemId) {
        Item item = resolveItem(configuredItemId);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, 1);
    }

    public static Item resolveItem(String configuredItemId) {
        if (configuredItemId == null || configuredItemId.isBlank()) {
            return Items.AIR;
        }

        String cleaned = cleanConfiguredItemId(configuredItemId);
        if (cleaned.isBlank() || cleaned.equals("none") || cleaned.equals("empty")) {
            return Items.AIR;
        }

        Set<String> candidates = new HashSet<>();
        if (cleaned.contains(":")) {
            candidates.add(cleaned);
        } else {
            candidates.add("cobblemon:" + cleaned);
            candidates.add("minecraft:" + cleaned);
        }

        for (String candidate : candidates) {
            try {
                ResourceLocation id = ResourceLocation.parse(candidate);
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != null && item != Items.AIR) {
                    return item;
                }
            } catch (Exception ignored) {}
        }

        return Items.AIR;
    }

    public static boolean matchesConfiguredItem(ItemStack stack, Set<String> configuredItems) {
        if (stack == null || stack.isEmpty() || configuredItems == null || configuredItems.isEmpty()) {
            return false;
        }

        Set<String> actualIds = getComparableItemIds(stack);
        for (String configuredItem : configuredItems) {
            String cleaned = cleanConfiguredItemId(configuredItem);
            if (cleaned.isBlank()) {
                continue;
            }

            if (actualIds.contains(cleaned)) {
                return true;
            }

            if (!cleaned.contains(":")) {
                if (actualIds.contains("cobblemon:" + cleaned) || actualIds.contains("minecraft:" + cleaned)) {
                    return true;
                }
            } else {
                int colon = cleaned.indexOf(':');
                if (colon >= 0 && colon + 1 < cleaned.length() && actualIds.contains(cleaned.substring(colon + 1))) {
                    return true;
                }
            }
        }

        return false;
    }

    public static String getDisplayId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? stack.getItem().toString().toLowerCase(Locale.ROOT) : id.toString().toLowerCase(Locale.ROOT);
    }

    private static Set<String> getComparableItemIds(ItemStack stack) {
        Set<String> ids = new HashSet<>();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            ids.add(id.toString().toLowerCase(Locale.ROOT));
            ids.add(id.getPath().toLowerCase(Locale.ROOT));
        }
        ids.add(stack.getItem().toString().toLowerCase(Locale.ROOT));
        return ids;
    }

    private static String cleanConfiguredItemId(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');

        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }

        return cleaned;
    }
}
