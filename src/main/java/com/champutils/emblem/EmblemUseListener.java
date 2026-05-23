package com.champutils.emblem;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class EmblemUseListener {

    private static final Map<UUID, LastUse> LAST_USES = new HashMap<>();

    private EmblemUseListener() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            if (EmblemManager.getEmblemId(stack) == null) return InteractionResult.PASS;

            String entityName = entity == null ? "" : entity.getClass().getName().toLowerCase();
            if (!entityName.contains("pokemon")) return InteractionResult.PASS;

            if (world.isClientSide()) return InteractionResult.SUCCESS;

            String emblemId = EmblemManager.getEmblemId(stack);
            long gameTime = world.getGameTime();
            int entityId = entity == null ? -1 : entity.getId();
            LastUse lastUse = LAST_USES.get(serverPlayer.getUUID());
            if (lastUse != null
                    && lastUse.gameTime == gameTime
                    && lastUse.entityId == entityId
                    && lastUse.emblemId.equals(emblemId)) {
                return InteractionResult.FAIL;
            }
            LAST_USES.put(serverPlayer.getUUID(), new LastUse(gameTime, entityId, emblemId));
            pruneOldUses(gameTime);

            EmblemManager.UseResult result = EmblemManager.useOnPokemon(serverPlayer, stack, entity);
            if (!result.handled()) return InteractionResult.PASS;
            if (!result.success()) serverPlayer.sendSystemMessage(Component.literal(result.error() == null ? "That emblem cannot be used there." : result.error()).withStyle(ChatFormatting.RED));
            return result.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        });
    }

    private static void pruneOldUses(long gameTime) {
        Iterator<Map.Entry<UUID, LastUse>> iterator = LAST_USES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, LastUse> entry = iterator.next();
            if (gameTime - entry.getValue().gameTime > 20L) {
                iterator.remove();
            }
        }
    }

    private record LastUse(long gameTime, int entityId, String emblemId) {}
}
