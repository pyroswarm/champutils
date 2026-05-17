package com.champutils.trainer;

import com.champutils.badge.BadgeType;
import com.champutils.gym.GymNpcPartyBuilder;
import com.champutils.gym.GymRegistry;
import com.champutils.worldevent.WorldEventManager;

import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.entity.npc.NPCEntity;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChampTrainerInteractionListener {

    private ChampTrainerInteractionListener() {}

    private static final Map<UUID, Long> LAST_TRAINER_CLICK = new ConcurrentHashMap<>();
    private static final long CLICK_DEBOUNCE_MS = 1000L;

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!(entity instanceof NPCEntity npc)) return InteractionResult.PASS;

            try {
                WorldEventManager.ActiveEvent active = WorldEventManager.getByNpc(npc.getUUID());
                BadgeType badge = GymRegistry.getBadgeForNpc(npc.getUUID());

                if (active == null && badge == null) {
                    return InteractionResult.PASS;
                }

                // Native trainer NPCs are handled here. Consume off-hand/duplicate callbacks
                // so Cobblemon/Fabric does not start or validate the same battle twice.
                if (hand != InteractionHand.MAIN_HAND) {
                    return InteractionResult.SUCCESS;
                }

                UUID key = serverPlayer.getUUID();
                long now = System.currentTimeMillis();
                Long previous = LAST_TRAINER_CLICK.get(key);
                if (previous != null && now - previous < CLICK_DEBOUNCE_MS) {
                    return InteractionResult.SUCCESS;
                }
                LAST_TRAINER_CLICK.put(key, now);

                if (active != null) {
                    BattleBuilder.INSTANCE.pvn(serverPlayer, npc);
                    return InteractionResult.SUCCESS;
                }

                GymNpcPartyBuilder.applyGymTeam(npc, badge);
                BattleBuilder.INSTANCE.pvn(serverPlayer, npc);
                return InteractionResult.SUCCESS;
            } catch (Exception e) {
                e.printStackTrace();
                serverPlayer.sendSystemMessage(Component.literal("§cCould not start trainer battle. Check server console."));
                return InteractionResult.FAIL;
            }
        });
    }
}
