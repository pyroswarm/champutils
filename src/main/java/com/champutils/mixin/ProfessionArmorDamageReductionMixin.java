package com.champutils.mixin;

import com.champutils.profession.ProfessionGearManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class ProfessionArmorDamageReductionMixin {
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float champutils$applyProfessionChestplateReduction(float amount) {
        Object self = this;
        if (!(self instanceof ServerPlayer player)) return amount;
        return ProfessionGearManager.applyChestplateDamageReduction(player, null, amount);
    }
}
