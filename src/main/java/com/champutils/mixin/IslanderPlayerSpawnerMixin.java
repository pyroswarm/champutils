package com.champutils.mixin;

import com.champutils.profile.IslanderProfileManager;
import com.champutils.profile.IslanderMineManager;
import com.champutils.profile.IslanderSpawningConfig;
import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;
import java.util.UUID;

/**
 * Islander-only close spawn zone override.
 *
 * Cobblemon's PlayerSpawner chooses a zone using global Cobblemon config values. For Islander worlds
 * we want closer, survival-style wild spawns without changing every other world on the server.
 */
@Mixin(value = PlayerSpawner.class, remap = false)
public abstract class IslanderPlayerSpawnerMixin {
    private static final Random CHAMPUTILS_ISLANDER_RANDOM = new Random();

    @Shadow
    public abstract UUID getUuid();

    @Inject(method = "getZoneInput", at = @At("HEAD"), cancellable = true)
    private void champutils$islanderCloseZone(SpawnCause cause, CallbackInfoReturnable<SpawningZoneInput> cir) {
        if (!IslanderSpawningConfig.CONFIG.enabled || !IslanderSpawningConfig.CONFIG.closeSpawnerEnabled) return;
        if (cause == null || !(cause.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getUUID().equals(getUuid())) return;
        if (IslanderMineManager.isMineWorld(player.serverLevel())) {
            cir.setReturnValue(null);
            return;
        }
        if (!IslanderProfileManager.isIslanderWorld((ServerLevel) player.level())) return;
        if (!IslanderProfileManager.isIslanderWorld(player.serverLevel())) return;

        int zoneDiameter = Math.max(8, IslanderSpawningConfig.CONFIG.closeSpawnerZoneDiameter);
        int zoneHeight = Math.max(8, IslanderSpawningConfig.CONFIG.closeSpawnerZoneHeight);
        double minDistance = Math.max(1, IslanderSpawningConfig.CONFIG.closeSpawnerMinDistance);
        double maxDistance = Math.max(minDistance, IslanderSpawningConfig.CONFIG.closeSpawnerMaxDistance);

        Vec3 center = player.position();
        Vec3 velocity = player.getDeltaMovement();

        double r = minDistance + CHAMPUTILS_ISLANDER_RANDOM.nextDouble() * (maxDistance - minDistance);
        double theta;
        if (velocity.horizontalDistance() < 0.1D) {
            theta = CHAMPUTILS_ISLANDER_RANDOM.nextDouble() * 2.0D * Math.PI;
        } else {
            double thetatemp = Math.atan(velocity.z / velocity.x) + (-Math.PI / 2.0D + CHAMPUTILS_ISLANDER_RANDOM.nextDouble() * Math.PI);
            theta = velocity.x < 0 ? Math.PI - thetatemp : thetatemp;
        }

        double x = center.x + r * Math.cos(theta);
        double z = center.z + r * Math.sin(theta);

        cir.setReturnValue(new SpawningZoneInput(
                cause,
                player.serverLevel(),
                Mth.ceil(x - zoneDiameter / 2.0F),
                Mth.ceil(center.y - zoneHeight / 2.0F),
                Mth.ceil(z - zoneDiameter / 2.0F),
                zoneDiameter,
                zoneHeight,
                zoneDiameter
        ));
    }
}
