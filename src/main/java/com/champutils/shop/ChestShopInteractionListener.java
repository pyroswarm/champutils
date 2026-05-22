package com.champutils.shop;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

public final class ChestShopInteractionListener {

    private ChestShopInteractionListener() {
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide()) {
                return true;
            }
            if (!(world instanceof ServerLevel level)) {
                return true;
            }

            ChestShopRegistry.ChestShop shop = ChestShopRegistry.getAt(level, pos);
            if (shop == null) {
                ChestShopRegistry.cleanupStaleShop(level, pos);
                return true;
            }

            if (player instanceof ServerPlayer serverPlayer) {
                boolean admin = serverPlayer.hasPermissions(4);
                if (shop.isOwner(serverPlayer.getUUID()) || admin) {
                    serverPlayer.sendSystemMessage(Component.literal("Remove this shop with /chestshop remove before breaking the chest.").withStyle(ChatFormatting.RED));
                } else {
                    serverPlayer.sendSystemMessage(Component.literal("This chest is a protected player shop.").withStyle(ChatFormatting.RED));
                }
            }
            return false;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level)) {
                return InteractionResult.PASS;
            }
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            if (ChestShopRegistry.getAt(level, pos) == null) {
                ChestShopRegistry.cleanupStaleShop(level, pos);
                return InteractionResult.PASS;
            }

            return ChestShopService.handleInteract(serverPlayer, level, pos);
        });
    }
}
