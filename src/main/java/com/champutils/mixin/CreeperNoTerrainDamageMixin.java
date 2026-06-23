package com.champutils.mixin;

import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps creeper damage/knockback but prevents block destruction even when mobGriefing is true. */
@Mixin(Creeper.class)
public abstract class CreeperNoTerrainDamageMixin {
    @Shadow private int explosionRadius;
    @Shadow public abstract boolean isPowered();

    @Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
    private void champutils$explodeWithoutTerrainDamage(CallbackInfo ci) {
        Creeper creeper = (Creeper) (Object) this;
        if (creeper.level().isClientSide) return;
        float power = (float) this.explosionRadius * (this.isPowered() ? 2.0F : 1.0F);
        creeper.level().explode(creeper, creeper.getX(), creeper.getY(), creeper.getZ(), power, Level.ExplosionInteraction.NONE);
        creeper.discard();
        ci.cancel();
    }
}
