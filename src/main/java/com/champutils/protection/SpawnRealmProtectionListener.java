package com.champutils.protection;

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
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

public final class SpawnRealmProtectionListener {
    private SpawnRealmProtectionListener() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return true;
            if (!isSpawn1(level)) return true;
            deny(sp, "Spawn is protected.");
            return false;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!isSpawn1(level) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            ItemStack stack = sp.getItemInHand(hand);
            if (stack.getItem() instanceof BlockItem) {
                deny(sp, "You cannot place blocks in spawn.");
                return InteractionResult.FAIL;
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (isAllowedInteraction(state)) return InteractionResult.PASS;

            deny(sp, "Only NPCs, healers, and PCs can be used in spawn.");
            return InteractionResult.FAIL;
        });
    }

    public static boolean isSpawn1(ServerLevel level) {
        String id = level.dimension().location().toString().toLowerCase(Locale.ROOT);
        return id.equals("multiworld:spawn1") || id.equals("minecraft:spawn1") || id.equals("spawn1") || id.endsWith(":spawn1");
    }

    private static boolean isAllowedInteraction(BlockState state) {
        String id = state.getBlock().builtInRegistryHolder().key().location().toString().toLowerCase(Locale.ROOT);
        return id.contains("healing_machine") || id.endsWith(":pc") || id.contains("pokemon_pc");
    }

    private static void deny(ServerPlayer player, String msg) {
        player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
    }
}
