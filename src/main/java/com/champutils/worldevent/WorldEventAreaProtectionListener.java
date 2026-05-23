package com.champutils.worldevent;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class WorldEventAreaProtectionListener {

    private WorldEventAreaProtectionListener() {
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide()) {
                return true;
            }
            if (!(world instanceof ServerLevel level)) {
                return true;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            if (serverPlayer.hasPermissions(4)) {
                return true;
            }

            WorldEventManager.ActiveEvent active = WorldEventManager.getActiveEventProtecting(level, pos);
            if (active == null) {
                return true;
            }

            serverPlayer.sendSystemMessage(Component.literal("You cannot break blocks near active world event NPCs.").withStyle(ChatFormatting.RED));
            return false;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level)) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (serverPlayer.hasPermissions(4)) {
                return InteractionResult.PASS;
            }

            ItemStack stack = serverPlayer.getItemInHand(hand);
            if (!(stack.getItem() instanceof BlockItem)) {
                return InteractionResult.PASS;
            }

            BlockPos targetPos = hitResult.getBlockPos();
            BlockPos placedPos = targetPos.relative(hitResult.getDirection());

            WorldEventManager.ActiveEvent active = WorldEventManager.getActiveEventProtecting(level, placedPos);
            if (active == null) {
                active = WorldEventManager.getActiveEventProtecting(level, targetPos);
            }
            if (active == null) {
                return InteractionResult.PASS;
            }

            serverPlayer.sendSystemMessage(Component.literal("You cannot place blocks near active world event NPCs.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        });
    }
}
