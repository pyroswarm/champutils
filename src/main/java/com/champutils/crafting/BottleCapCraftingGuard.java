package com.champutils.crafting;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;

/** Blocks ordinary crafting-table bottlecap outputs while leaving /crafting intact. */
public final class BottleCapCraftingGuard {
    private BottleCapCraftingGuard() {}

    public static boolean blockIfBottleCap(ServerPlayer player, ItemStack output) {
        if (player == null || output == null || output.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(output.getItem());
        if (id == null) return false;
        String itemId = id.toString().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (!itemId.startsWith("bottlecaps:") && !path.contains("bottle_cap") && !isBottleCapPaper(output)) return false;
        player.sendSystemMessage(Component.literal("Bottle Caps can only be crafted through /crafting.").withStyle(ChatFormatting.RED));
        return true;
    }

    private static boolean isBottleCapPaper(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !"minecraft:paper".equals(id.toString())) return false;

        StringBuilder text = new StringBuilder();
        Component itemName = stack.get(DataComponents.ITEM_NAME);
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        ItemLore lore = stack.get(DataComponents.LORE);
        if (itemName != null) text.append(' ').append(itemName.getString());
        if (customName != null) text.append(' ').append(customName.getString());
        if (lore != null) lore.lines().forEach(line -> text.append(' ').append(line.getString()));

        String value = text.toString().toLowerCase(Locale.ROOT);
        return value.contains("bottle cap") || value.contains("bottlecap")
                || value.equals(" atk") || value.equals(" def") || value.equals(" hp")
                || value.contains("sp.atk") || value.contains("sp.def") || value.contains("speed");
    }
}
