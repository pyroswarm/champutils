package com.champutils.teleport;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

/** Saves a player's death location as their /back destination. */
public final class DeathBackListener {
    private DeathBackListener() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                BackManager.remember(player);
            }
        });
    }
}
