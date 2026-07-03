package com.champutils.protection;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defensive guard for Cobblemon campfire pots. Several clients were being kicked/crashing
 * when opening the campfire pot UI on the live server. Until the upstream interaction is
 * safe in this pack, fail the interaction server-side instead of letting it disconnect players.
 */
public final class CampfirePotSafetyListener {
    private static final Map<UUID, Long> LAST_WARNING = new ConcurrentHashMap<>();
    private static final long WARNING_COOLDOWN_MS = 5000L;

    private CampfirePotSafetyListener() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hitResult.getBlockPos();
            if (!isCampfirePotBlock(level.getBlockState(pos))) return InteractionResult.PASS;
            serverPlayer.closeContainer();
            warn(serverPlayer);
            return InteractionResult.FAIL;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (world.isClientSide() || !(world instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResultHolder.pass(stack);
            }
            if (!isCampfirePotItem(stack)) return InteractionResultHolder.pass(stack);
            serverPlayer.closeContainer();
            warn(serverPlayer);
            return InteractionResultHolder.fail(stack);
        });
    }

    private static boolean isCampfirePotBlock(BlockState state) {
        if (state == null) return false;
        String id = state.getBlock().builtInRegistryHolder().key().location().toString().toLowerCase(Locale.ROOT);
        return id.startsWith("cobblemon:") && (id.contains("campfire_pot") || id.contains("cooking_pot"));
    }

    private static boolean isCampfirePotItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        return id.startsWith("cobblemon:") && (id.contains("campfire_pot") || id.contains("cooking_pot"));
    }

    private static void warn(ServerPlayer player) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        Long last = LAST_WARNING.get(player.getUUID());
        if (last != null && now - last < WARNING_COOLDOWN_MS) return;
        LAST_WARNING.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal("Cobblemon campfire pots are temporarily disabled because they were disconnecting some players.").withStyle(ChatFormatting.RED));
    }
}
