package com.champutils.mixin;

import com.champutils.profession.ProfessionGearManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerUnderwaterMiningMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void champutils$aquaAffinityUnderwaterMining(BlockState state, CallbackInfoReturnable<Float> cir) {
        Player player = (Player) (Object) this;
        float multiplier = ProfessionGearManager.underwaterMiningMultiplier(player);
        if (multiplier <= 1.0F) return;
        Float original = cir.getReturnValue();
        if (original == null || original <= 0.0F) return;
        cir.setReturnValue(original * multiplier);
    }
}
