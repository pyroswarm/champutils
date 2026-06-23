package com.champutils.claims;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LandClaimSelectionItemListener {
    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();

    private LandClaimSelectionItemListener() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!serverPlayer.getMainHandItem().is(Items.GOLDEN_SWORD)) return InteractionResult.PASS;
            long now = System.currentTimeMillis();
            long last = LAST_CLICK.getOrDefault(serverPlayer.getUUID(), 0L);
            if (now - last < 250L) return InteractionResult.FAIL;
            LAST_CLICK.put(serverPlayer.getUUID(), now);
            boolean sneak = serverPlayer.isShiftKeyDown();
            LandClaimCommand.setPosAt(serverPlayer, !sneak, hitResult.getBlockPos());
            serverPlayer.sendSystemMessage(Component.literal(sneak ? "Set claim position 2 with your golden sword." : "Set claim position 1 with your golden sword. Sneak-right-click to set position 2.").withStyle(ChatFormatting.GOLD));
            return InteractionResult.FAIL;
        });
    }
}
