package com.champutils.exploration;

import com.champutils.shop.ChestShopClaimCompat;

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
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ExplorationProtectionListener {
    private ExplorationProtectionListener() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return true;
            if (serverPlayer.hasPermissions(4) && serverPlayer.isCreative()) return true;

            if (isLootContainer(level, pos, state) && ChestShopClaimCompat.canCreateShop(serverPlayer, level, pos) != ChestShopClaimCompat.ClaimCheckResult.ALLOWED) {
                deny(serverPlayer, "Natural loot containers are protected until this land is claimed.");
                return false;
            }
            if (ExplorationWorldManager.isExplorationLevel(level) && ExplorationLootState.isProtected(level, pos)) {
                deny(serverPlayer, "Exploration structures are protected.");
                return false;
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            BlockPos targetPos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(targetPos);
            if (isLootContainer(level, targetPos, state) && ChestShopClaimCompat.canCreateShop(serverPlayer, level, targetPos) != ChestShopClaimCompat.ClaimCheckResult.ALLOWED) {
                ExplorationLootManager.open(serverPlayer, level, targetPos);
                return InteractionResult.SUCCESS;
            }

            ItemStack stack = serverPlayer.getItemInHand(hand);
            BlockPos placedPos = targetPos.relative(hitResult.getDirection());
            if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof HopperBlock && touchesInstancedLoot(level, placedPos)) {
                deny(serverPlayer, "You cannot place hoppers against per-player loot chests.");
                return InteractionResult.FAIL;
            }
            if (ExplorationWorldManager.isExplorationLevel(level) && stack.getItem() instanceof BlockItem && ExplorationLootState.isProtected(level, placedPos) && !(serverPlayer.hasPermissions(4) && serverPlayer.isCreative())) {
                deny(serverPlayer, "You cannot place blocks inside protected exploration structures.");
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });
    }

    private static boolean touchesInstancedLoot(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (ExplorationLootManager.isInstancedLootContainer(level, pos)) return true;
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            if (ExplorationLootManager.isInstancedLootContainer(level, pos.relative(direction))) return true;
        }
        return false;
    }

    private static boolean isLootContainer(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isAllowedLootBlock(level, pos, state)) return false;
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof net.minecraft.world.Container)) return false;
        // Only natural/generated loot containers should become instanced. Player-placed empty containers
        // do not carry a vanilla LootTable tag and remain normal unless discovered by an actual loot table.
        try {
            var nbt = entity.saveWithFullMetadata(level.registryAccess());
            if (nbt != null && nbt.contains("LootTable")) return true;
        } catch (Throwable ignored) {}
        String blockId = level.getBlockState(pos).getBlock().builtInRegistryHolder().key().location().toString().toLowerCase(java.util.Locale.ROOT);
        return blockId.contains("gilded_chest");
    }

    private static boolean isAllowedLootBlock(ServerLevel level, BlockPos pos, BlockState state) {
        String blockId = level.getBlockState(pos).getBlock().builtInRegistryHolder().key().location().toString().toLowerCase(java.util.Locale.ROOT);
        return blockId.equals("minecraft:chest") || blockId.equals("minecraft:barrel") || blockId.contains("gilded_chest");
    }

    private static void deny(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
