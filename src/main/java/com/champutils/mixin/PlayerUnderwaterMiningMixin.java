package com.champutils.mixin;

import com.champutils.profession.ProfessionGearManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Legacy fallback for profession helmets with the Aqua Affinity stat.
 * Current helmets receive the real vanilla Aqua Affinity enchantment with glint
 * suppressed, so this only compensates old helmets before the server tick has
 * refreshed their enchantment component.
 */
@Mixin(Player.class)
public abstract class PlayerUnderwaterMiningMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void champutils$professionHelmetAquaAffinity(BlockState state, CallbackInfoReturnable<Float> cir) {
        Player player = (Player)(Object)this;
        float multiplier = ProfessionGearManager.underwaterMiningMultiplier(player);
        if (multiplier <= 1.0F) return;
        cir.setReturnValue(cir.getReturnValueF() * multiplier);
    }
}
