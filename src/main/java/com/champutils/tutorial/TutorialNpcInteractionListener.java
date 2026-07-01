package com.champutils.tutorial;

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

public final class TutorialNpcInteractionListener {
    private static final Map<UUID, String> PENDING_BINDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();
    private static final long CLICK_DEBOUNCE_MS = 750L;

    private TutorialNpcInteractionListener() {}

    public static void beginBind(ServerPlayer player, String npcId) {
        if (player == null) return;
        String normalized = TutorialManager.normalizeNpcId(npcId);
        SpawnGuideNpc guide = TutorialManager.getNpc(normalized);
        if (guide == null) {
            player.sendSystemMessage(Component.literal("§cUnknown tutorial NPC id: " + npcId));
            return;
        }
        PENDING_BINDS.put(player.getUUID(), normalized);
        player.sendSystemMessage(Component.literal("Right-click the Cobblemon NPC you want to bind as: " + guide.displayName + ".").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Use /tutorial bindcancel to cancel.").withStyle(ChatFormatting.GRAY));
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
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            String pendingId = PENDING_BINDS.remove(serverPlayer.getUUID());
            if (pendingId != null) {
                TutorialNpcBindingRegistry.bind(pendingId, npc);
                SpawnGuideNpc guide = TutorialManager.getNpc(pendingId);
                serverPlayer.sendSystemMessage(Component.literal("§aBound this Cobblemon NPC as " + (guide == null ? pendingId : guide.displayName) + "."));
                serverPlayer.sendSystemMessage(Component.literal("§7Players can right-click it normally; Cobblemon dialogue still opens, and ChampUtils records tutorial progress."));
                return InteractionResult.SUCCESS;
            }

            String tutorialId = TutorialNpcBindingRegistry.getTutorialId(npc);
            if (tutorialId == null) return InteractionResult.PASS;

            UUID key = serverPlayer.getUUID();
            long now = System.currentTimeMillis();
            Long previous = LAST_CLICK.get(key);
            if (previous != null && now - previous < CLICK_DEBOUNCE_MS) return InteractionResult.PASS;
            LAST_CLICK.put(key, now);

            TutorialManager.completeNpcStep(serverPlayer, tutorialId);
            return InteractionResult.PASS;
        });
    }
}
