package com.champutils.survival;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.EntityType;

/**
 * Server-side phantom removal.
 *
 * Phantoms are an insomnia/natural-spawn annoyance on multiplayer servers, so ChampUtils
 * removes them as soon as they enter a server world. This also clears any phantoms that
 * were saved in chunks before this patch.
 */
public final class PhantomSpawnBlocker {
    private static boolean registered = false;

    private PhantomSpawnBlocker() {}

    public static void register() {
        if (registered) return;
        registered = true;

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getType() == EntityType.PHANTOM) {
                entity.discard();
            }
        });
    }
}
