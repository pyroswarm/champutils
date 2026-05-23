package com.champutils.exploration;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemBindInteractionListener {

    private static final Map<UUID, PendingBind> PENDING_BINDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PENDING_UNBINDS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_CLICK = new ConcurrentHashMap<>();
    private static final long CLICK_DEBOUNCE_MS = 750L;

    private ItemBindInteractionListener() {}

    public record PendingBind(String bindName, String rewardName, String dialogue) {}

    public static void beginBind(ServerPlayer player, String bindName, String rewardName, String dialogue) {
        if (player == null) return;
        String normalizedBind = ItemBindRegistry.normalize(bindName);
        String normalizedReward = ItemBindRegistry.normalize(rewardName);

        if (normalizedBind.isBlank()) {
            player.sendSystemMessage(Component.literal("Bind name cannot be blank.").withStyle(ChatFormatting.RED));
            return;
        }

        if (!ItemBindRegistry.isDialogueOnly(normalizedReward) && ItemBindRegistry.createRewardStacks(normalizedReward).isEmpty()) {
            player.sendSystemMessage(Component.literal("Unknown reward: " + rewardName).withStyle(ChatFormatting.RED));
            player.sendSystemMessage(Component.literal("Valid basic rewards: " + ItemBindRegistry.validRewardsText()).withStyle(ChatFormatting.GRAY));
            return;
        }

        String finalDialogue = dialogue == null || dialogue.isBlank() ? ItemBindRegistry.defaultDialogue(normalizedReward) : dialogue.trim();
        PENDING_BINDS.put(player.getUUID(), new PendingBind(normalizedBind, normalizedReward, finalDialogue));
        player.sendSystemMessage(Component.literal("Right-click the Cobblemon NPC or Pokemon you want to bind to: " + normalizedBind).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Reward: " + normalizedReward + " | Use /itembind cancel to cancel.").withStyle(ChatFormatting.GRAY));
    }

    public static boolean cancelBind(ServerPlayer player) {
        if (player == null) return false;
        boolean removedBind = PENDING_BINDS.remove(player.getUUID()) != null;
        boolean removedUnbind = PENDING_UNBINDS.remove(player.getUUID()) != null;
        return removedBind || removedUnbind;
    }

    public static void beginUnbind(ServerPlayer player) {
        if (player == null) return;
        PENDING_BINDS.remove(player.getUUID());
        PENDING_UNBINDS.put(player.getUUID(), Boolean.TRUE);
        player.sendSystemMessage(Component.literal("Right-click the bound Cobblemon NPC or Pokemon you want to clear.").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Use /itembind cancel to cancel.").withStyle(ChatFormatting.GRAY));
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            if (PENDING_UNBINDS.remove(serverPlayer.getUUID()) != null) {
                if (!isBindableEntity(entity)) {
                    serverPlayer.sendSystemMessage(Component.literal("That entity cannot have an item binding.").withStyle(ChatFormatting.RED));
                    return InteractionResult.SUCCESS;
                }
                boolean removed = ItemBindRegistry.unbind(entity);
                serverPlayer.sendSystemMessage(Component.literal(removed ? "Cleared this entity's item/dialogue binding." : "This entity was not item-bound.").withStyle(removed ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }

            PendingBind pending = PENDING_BINDS.remove(serverPlayer.getUUID());
            if (pending != null) {
                if (!isBindableEntity(entity)) {
                    serverPlayer.sendSystemMessage(Component.literal("That entity cannot be item-bound. Use a Cobblemon NPC or Pokemon.").withStyle(ChatFormatting.RED));
                    return InteractionResult.SUCCESS;
                }

                ItemBindRegistry.bind(entity, pending.bindName(), pending.rewardName(), pending.dialogue());
                serverPlayer.sendSystemMessage(Component.literal("Bound this entity to /itembind " + pending.bindName() + ".").withStyle(ChatFormatting.GREEN));
                return InteractionResult.SUCCESS;
            }

            if (!isBindableEntity(entity)) return InteractionResult.PASS;

            ItemBindRegistry.Binding binding = ItemBindRegistry.getBinding(entity);
            if (binding == null) return InteractionResult.PASS;

            String debounceKey = serverPlayer.getUUID() + ":" + entity.getUUID();
            long now = System.currentTimeMillis();
            Long previous = LAST_CLICK.get(debounceKey);
            if (previous != null && now - previous < CLICK_DEBOUNCE_MS) return InteractionResult.SUCCESS;
            LAST_CLICK.put(debounceKey, now);

            handleInteraction(serverPlayer, binding);
            return InteractionResult.SUCCESS;
        });
    }

    private static boolean isBindableEntity(Entity entity) {
        return entity instanceof NPCEntity || entity instanceof PokemonEntity;
    }

    private static void handleInteraction(ServerPlayer player, ItemBindRegistry.Binding binding) {
        if (binding.dialogue != null && !binding.dialogue.isBlank()) {
            player.sendSystemMessage(Component.literal(binding.dialogue).withStyle(ChatFormatting.AQUA));
        }

        if (ItemBindRegistry.isDialogueOnly(binding.rewardName)) return;

        if (ItemBindRegistry.hasClaimed(player, binding)) {
            player.sendSystemMessage(Component.literal("You already claimed this one-time reward.").withStyle(ChatFormatting.GRAY));
            return;
        }

        List<ItemStack> rewards = ItemBindRegistry.createRewardStacks(binding.rewardName);
        if (rewards.isEmpty()) {
            player.sendSystemMessage(Component.literal("This NPC has an invalid reward configured: " + binding.rewardName).withStyle(ChatFormatting.RED));
            return;
        }

        for (ItemStack stack : rewards) {
            boolean inserted = player.getInventory().add(stack.copy());
            if (!inserted) player.drop(stack.copy(), false);
        }

        ItemBindRegistry.markClaimed(player, binding);
        player.sendSystemMessage(Component.literal("Reward claimed: " + binding.bindName).withStyle(ChatFormatting.GREEN));
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.35F, 1.2F);
    }
}
