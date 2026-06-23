package com.champutils.megaboss;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MegaBossDamageProtectionListener {
    private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();

    private MegaBossDamageProtectionListener() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!MegaBossManager.isMegaBoss(entity)) return InteractionResult.PASS;
            try { entity.setInvulnerable(true); } catch (Throwable ignored) {}
            long now = System.currentTimeMillis();
            long last = LAST_MESSAGE.getOrDefault(serverPlayer.getUUID(), 0L);
            if (now - last > 1500L) {
                LAST_MESSAGE.put(serverPlayer.getUUID(), now);
                serverPlayer.sendSystemMessage(Component.literal("Mega bosses can only be defeated through Cobblemon battles.").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        });
    }
}
