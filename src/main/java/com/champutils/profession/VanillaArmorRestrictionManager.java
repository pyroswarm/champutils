package com.champutils.profession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

public final class VanillaArmorRestrictionManager {
    private static boolean registered = false;

    private VanillaArmorRestrictionManager() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                clampInventory(player);
            }
        });
    }

    public static boolean isRestrictedVanillaArmor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof ArmorItem)) return false;
        if (ProfessionGearManager.isProfessionArmor(stack)) return false;
        if (RunningShoeManager.isRunningShoes(stack)) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    public static void clampToOneDurability(ItemStack stack) {
        if (!isRestrictedVanillaArmor(stack)) return;
        stack.set(DataComponents.MAX_DAMAGE, 1);
        stack.set(DataComponents.DAMAGE, 0);
    }

    public static boolean blockCraftingIfRestricted(ServerPlayer player, ItemStack output) {
        // Vanilla armor must remain craftable because other recipes depend on it.
        // The durability clamp still nerfs regular armor to 1 max durability after crafting.
        if (isRestrictedVanillaArmor(output)) clampToOneDurability(output);
        return false;
    }

    private static void clampInventory(ServerPlayer player) {
        if (player == null) return;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            clampToOneDurability(player.getInventory().getItem(i));
        }
    }
}
