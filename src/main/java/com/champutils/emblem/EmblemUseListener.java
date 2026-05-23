package com.champutils.emblem;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public final class EmblemUseListener {

    private EmblemUseListener() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            if (EmblemManager.getEmblemId(stack) == null) return InteractionResult.PASS;

            String entityName = entity == null ? "" : entity.getClass().getName().toLowerCase();
            if (!entityName.contains("pokemon")) return InteractionResult.PASS;

            if (world.isClientSide()) return InteractionResult.SUCCESS;

            EmblemManager.UseResult result = EmblemManager.useOnPokemon(serverPlayer, stack, entity);
            if (!result.handled()) return InteractionResult.PASS;
            if (!result.success()) serverPlayer.sendSystemMessage(Component.literal(result.error() == null ? "That emblem cannot be used there." : result.error()).withStyle(ChatFormatting.RED));
            return result.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        });
    }
}
