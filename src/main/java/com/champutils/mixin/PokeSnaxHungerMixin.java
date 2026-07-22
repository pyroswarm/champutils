package com.champutils.mixin;

import com.champutils.profession.ProfessionTrinketManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PokeSnaxHungerMixin {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float champutils$reduceFoodExhaustion(float exhaustion) {
        if (!((Object) this instanceof ServerPlayer player)) return exhaustion;
        double reduction = ProfessionTrinketManager.hungerReduction(player);
        return (float) (exhaustion * Math.max(0.0D, 1.0D - reduction));
    }
}
