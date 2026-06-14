package com.champutils.profile;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
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
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;

public final class IslanderMineProtectionListener {
    private IslanderMineProtectionListener() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(world instanceof ServerLevel level)) return;
            if (!IslanderMineManager.isMineWorld(level)) return;

            // Islander mines are resource-only worlds. No Pokémon should ever exist here.
            // This catches natural Cobblemon spawns, special spawns, command-created Pokémon,
            // and Pokémon saved in chunks before this protection was added.
            if (entity instanceof PokemonEntity) {
                entity.discard();
            }
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return true;
            if (!IslanderMineManager.isMineWorld(level)) return true;
            if (serverPlayer.hasPermissions(4) && serverPlayer.isCreative()) return true;

            if (IslanderMineManager.isBreakProtected(level, pos)) {
                return false;
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!IslanderMineManager.isMineWorld(level)) return InteractionResult.PASS;
            if (serverPlayer.hasPermissions(4) && serverPlayer.isCreative()) return InteractionResult.PASS;

            ItemStack stack = serverPlayer.getItemInHand(hand);
            if (stack.getItem() instanceof BlockItem || stack.getItem() instanceof BucketItem) {
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });
    }

    private static void deny(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
