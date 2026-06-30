package com.champutils.specialspawn;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

/**
 * Prevents ChampUtils special-pool Pokemon from dying to normal Minecraft damage.
 *
 * Special spawns are already marked by SpecialWildSpawnManager with the
 * champutils_special_spawn scoreboard tag. This listener deliberately reuses
 * that single source of truth so legendary/mythical, paradox, ultra beast,
 * and Islander variants all get the same protection without introducing a
 * second marker or duplicate spawn path. They can still be removed by the
 * special-spawn despawn cleanup, capture flow, or Cobblemon battle handling.
 */
public final class SpecialSpawnDamageProtectionListener {

    private SpecialSpawnDamageProtectionListener() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!SpecialWildSpawnManager.isSpecialSpawnEntity(entity)) return true;

            SpecialWildSpawnManager.protectSpecialSpawnEntity(entity);
            return false;
        });
    }
}
