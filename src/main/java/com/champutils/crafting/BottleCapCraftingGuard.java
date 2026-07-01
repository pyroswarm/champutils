package com.champutils.crafting;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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
        if (!itemId.startsWith("bottlecaps:") && !path.contains("bottle_cap")) return false;
        player.sendSystemMessage(Component.literal("Bottle Caps can only be crafted through /crafting.").withStyle(ChatFormatting.RED));
        return true;
    }
}
