package com.champutils.dungeon;

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

public final class DungeonBindInteractionListener {

    private static final Map<UUID, String> PENDING_BINDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();
    private static final long CLICK_DEBOUNCE_MS = 1000L;

    private DungeonBindInteractionListener() {}

    public static void beginBind(ServerPlayer player, String dungeonId) {
        if (player == null || dungeonId == null || dungeonId.isBlank()) return;
        PENDING_BINDS.put(player.getUUID(), dungeonId);
        player.sendSystemMessage(Component.literal("Right-click the Cobblemon NPC you want to bind to dungeon: " + dungeonId).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Use /dungeon bindcancel to cancel.").withStyle(ChatFormatting.GRAY));
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

            String pendingDungeon = PENDING_BINDS.remove(serverPlayer.getUUID());
            if (pendingDungeon != null) {
                DungeonBindingRegistry.bind(pendingDungeon, npc);
                serverPlayer.sendSystemMessage(Component.literal("Bound " + pendingDungeon + " to this NPC.").withStyle(ChatFormatting.GREEN));
                return InteractionResult.SUCCESS;
            }

            String dungeonId = DungeonBindingRegistry.getDungeonIdForNpc(npc.getUUID());
            if (dungeonId == null) return InteractionResult.PASS;

            UUID key = serverPlayer.getUUID();
            long now = System.currentTimeMillis();
            Long previous = LAST_CLICK.get(key);
            if (previous != null && now - previous < CLICK_DEBOUNCE_MS) {
                return InteractionResult.SUCCESS;
            }
            LAST_CLICK.put(key, now);

            DungeonManager.startDungeon(serverPlayer, dungeonId);
            return InteractionResult.SUCCESS;
        });
    }
}
