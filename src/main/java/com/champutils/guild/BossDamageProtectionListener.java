package com.champutils.guild;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossDamageProtectionListener {
    private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();

    private BossDamageProtectionListener() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!GuildBossManager.isBossNpcEntity(entity)) return InteractionResult.PASS;
            long now = System.currentTimeMillis();
            long last = LAST_MESSAGE.getOrDefault(serverPlayer.getUUID(), 0L);
            if (now - last > 1500L) {
                LAST_MESSAGE.put(serverPlayer.getUUID(), now);
                serverPlayer.sendSystemMessage(Component.literal("Bosses can only be defeated through their Cobblemon battle. Right-click to challenge them.").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        });
    }
}
