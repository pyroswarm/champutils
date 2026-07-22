package com.champutils.profile;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Locale;

public final class IslanderMineProtectionListener {
    private IslanderMineProtectionListener() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(world instanceof ServerLevel level)) return;
            if (!IslanderMineManager.isMineWorld(level)) return;

            // Islander mines are resource-only worlds. No Pokémon should ever exist here.
            // This catches natural Cobblemon spawns, special spawns, command-created Pokémon,
            // and Pokémon saved in chunks before this protection was added.
            if (entity instanceof PokemonEntity || entity instanceof NPCEntity) {
                entity.discard();
            }
        });

        CobblemonEvents.POKEMON_SENT_PRE.subscribe(event -> {
            try {
                if (event.getLevel() != null && IslanderMineManager.isMineWorld(event.getLevel())) {
                    event.cancel();
                }
            } catch (Throwable ignored) {}
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
            // Players may place vanilla ladders to safely traverse the shared mine,
            // but every other block and all buckets remain blocked.
            if (stack.getItem() instanceof BlockItem) {
                return stack.is(Items.LADDER) ? InteractionResult.PASS : InteractionResult.FAIL;
            }
            if (stack.getItem() instanceof BucketItem) {
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResultHolder.pass(stack);
            }
            if (!IslanderMineManager.isMineWorld(level)) return InteractionResultHolder.pass(stack);
            if (serverPlayer.hasPermissions(4) && serverPlayer.isCreative()) return InteractionResultHolder.pass(stack);
            if (isPokeBallLike(stack)) {
                deny(serverPlayer, "Pokémon cannot be sent out or used inside Islander mines.");
                return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
        });
    }

    private static boolean isPokeBallLike(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        return id.startsWith("cobblemon:") && (id.endsWith("_ball") || id.contains("poke_ball") || id.contains("pokeball"));
    }

    private static void deny(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
