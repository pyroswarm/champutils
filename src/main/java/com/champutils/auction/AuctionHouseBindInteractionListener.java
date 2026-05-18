package com.champutils.auction;

import com.cobblemon.mod.common.entity.npc.NPCEntity;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionHouseBindInteractionListener {

    private static final Map<UUID, Boolean> PENDING_BINDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();
    private static final long CLICK_DEBOUNCE_MS = 1000L;

    private AuctionHouseBindInteractionListener() {}

    public static void beginBind(ServerPlayer player) {
        if (player == null) return;
        PENDING_BINDS.put(player.getUUID(), Boolean.TRUE);
        player.sendSystemMessage(Component.literal("Right-click the Cobblemon NPC you want to bind as the Auction NPC.").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Use /ah bindcancel to cancel.").withStyle(ChatFormatting.GRAY));
    }

    public static boolean cancelBind(ServerPlayer player) {
        if (player == null) return false;
        return PENDING_BINDS.remove(player.getUUID()) != null;
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!(entity instanceof NPCEntity npc)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;

            if (PENDING_BINDS.remove(serverPlayer.getUUID()) != null) {
                AuctionHouseNpcBindingRegistry.bind(npc);
                serverPlayer.sendSystemMessage(Component.literal("Bound this NPC as the Auction NPC.").withStyle(ChatFormatting.GREEN));
                serverPlayer.sendSystemMessage(Component.literal("Players can now right-click it to open /ah.").withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }

            if (!AuctionHouseNpcBindingRegistry.isBoundNpc(npc)) return InteractionResult.PASS;

            UUID key = serverPlayer.getUUID();
            long now = System.currentTimeMillis();
            Long previous = LAST_CLICK.get(key);
            if (previous != null && now - previous < CLICK_DEBOUNCE_MS) return InteractionResult.SUCCESS;
            LAST_CLICK.put(key, now);

            AuctionHouseGui.openMain(serverPlayer);
            return InteractionResult.SUCCESS;
        });
    }
}
