package com.champutils.dungeon;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class DungeonInteractionLock {

    private DungeonInteractionLock() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!DungeonManager.isInDungeon(serverPlayer)) return InteractionResult.PASS;

            BlockState state = world.getBlockState(hitResult.getBlockPos());
            if (!isBlockedDungeonBlock(state)) return InteractionResult.PASS;

            serverPlayer.sendSystemMessage(
                    Component.literal("You cannot use healers or PCs inside a dungeon. Your party was locked on entry.")
                            .withStyle(ChatFormatting.RED)
            );
            return InteractionResult.FAIL;
        });
    }

    private static boolean isBlockedDungeonBlock(BlockState state) {
        if (state == null) return false;
        Block block = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return false;

        String value = id.toString().toLowerCase();
        return value.equals("cobblemon:healing_machine")
                || value.equals("cobblemon:pc")
                || value.contains("healer")
                || value.contains("healing_machine");
    }
}
