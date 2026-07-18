package com.champutils.roaming;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

/**
 * Hard-cancels Minecraft damage to roaming trainers. NPCEntity's normal invulnerability
 * still permits sources tagged as bypassing invulnerability, including environmental edge
 * cases such as drowning, so the roaming tag must be checked at the Fabric damage event.
 */
public final class RoamingTrainerDamageProtectionListener {
    private RoamingTrainerDamageProtectionListener() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof NPCEntity npc && RoamingTrainerManager.isRoamingTrainerEntity(npc)) {
                RoamingTrainerManager.refreshImmediateProtections(npc);
                return false;
            }
            return true;
        });
    }
}
