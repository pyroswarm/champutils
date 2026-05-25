package com.champutils.exploration;

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
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ExplorationProtectionListener {
    private ExplorationProtectionListener() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return true;
            if (!ExplorationWorldManager.isExplorationLevel(level)) return true;
            if (serverPlayer.hasPermissions(4) && serverPlayer.isCreative()) return true;

            if (isLootContainer(level, pos, state) || ExplorationLootState.isProtected(level, pos)) {
                deny(serverPlayer, "Exploration structures are protected.");
                return false;
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!ExplorationWorldManager.isExplorationLevel(level)) return InteractionResult.PASS;

            BlockPos targetPos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(targetPos);
            if (isLootContainer(level, targetPos, state)) {
                ExplorationLootManager.open(serverPlayer, level, targetPos);
                return InteractionResult.SUCCESS;
            }

            ItemStack stack = serverPlayer.getItemInHand(hand);
            BlockPos placedPos = targetPos.relative(hitResult.getDirection());
            if (stack.getItem() instanceof BlockItem && ExplorationLootState.isProtected(level, placedPos) && !(serverPlayer.hasPermissions(4) && serverPlayer.isCreative())) {
                deny(serverPlayer, "You cannot place blocks inside protected exploration structures.");
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });
    }

    private static boolean isLootContainer(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof net.minecraft.world.Container) {
            return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock || state.getBlock() instanceof ShulkerBoxBlock;
        }
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock || state.getBlock() instanceof ShulkerBoxBlock;
    }

    private static void deny(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
