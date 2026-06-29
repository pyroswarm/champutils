package com.champutils.profession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;

/**
 * Makes vanilla tools intentionally bad so profession tools are the real progression path.
 * This never touches custom profession tools.
 */
public final class VanillaToolRestrictionManager {
    private static boolean registered = false;

    private VanillaToolRestrictionManager() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player == null || player.getAbilities().instabuild) continue;
                player.getInventory().items.forEach(VanillaToolRestrictionManager::makeOneUse);
                player.getInventory().offhand.forEach(VanillaToolRestrictionManager::makeOneUse);
            }
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.getAbilities().instabuild) return;
            ItemStack held = serverPlayer.getMainHandItem();
            if (!isVanillaTool(held)) return;
            serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
    }

    public static boolean isVanillaTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (ProfessionToolMetadata.isProfessionTool(stack)) return false;
        return stack.getItem() instanceof PickaxeItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof HoeItem
                || stack.getItem() instanceof ShovelItem
                || stack.getItem() instanceof SwordItem;
    }

    private static void makeOneUse(ItemStack stack) {
        if (!isVanillaTool(stack) || !stack.isDamageableItem()) return;
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 1) return;
        int oneUseLeft = maxDamage - 1;
        if (stack.getDamageValue() < oneUseLeft) {
            stack.setDamageValue(oneUseLeft);
        }
    }
}
