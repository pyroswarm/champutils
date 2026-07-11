package com.champutils.riding;

import com.cobblemon.mod.common.api.events.CobblemonEvents;

/**
 * Makes all mounted Pokémon use Cobblemon's supported infinite-stamina mode.
 * Cobblemon represents infinite stamina as -1 and exposes setInfiniteStamina()
 * specifically for listeners on RIDE_EVENT_APPLY_STAMINA.
 */
public final class InfiniteRideStaminaListener {
    private static boolean registered;

    private InfiniteRideStaminaListener() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CobblemonEvents.RIDE_EVENT_APPLY_STAMINA.subscribe(event -> {
            event.setInfiniteStamina();
        });
        System.out.println("[ChampUtils] Infinite mounted Pokémon stamina enabled.");
    }
}
