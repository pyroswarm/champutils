package com.champutils.mixin;

import com.champutils.xplock.XpLockManager;
import com.champutils.battle.ServerLifecycleBridge;
import com.champutils.buff.BuffContext;
import com.champutils.buff.BuffManager;
import com.champutils.buff.BuffType;
import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Method;
import com.cobblemon.mod.common.api.pokemon.experience.StandardExperienceCalculator;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(StandardExperienceCalculator.class)
public abstract class StandardExperienceCalculatorMixin {

    @ModifyVariable(method = "calculate", at = @At("STORE"), name = "term4", remap = false)
    private double champutils$blockExperienceForLockedPokemon(double term4, BattlePokemon battlePokemon) {
        if (XpLockManager.isLocked(battlePokemon.getOriginalPokemon())) return 0.0D;
        ServerPlayer player = findPlayer(battlePokemon);
        if (player == null) return term4;
        double bonus = BuffManager.getTotalBuff(BuffContext.builder(player, BuffContext.Source.NPC_BATTLE).pokemon(battlePokemon.getOriginalPokemon()).build(), BuffType.POKEMON_XP);
        return term4 * (1.0D + Math.max(0.0D, bonus));
    }

    private ServerPlayer findPlayer(BattlePokemon battlePokemon) {
        try {
            Object actor = battlePokemon.getClass().getMethod("getActor").invoke(battlePokemon);
            Object entity = read(actor, "getEntity");
            if (entity instanceof ServerPlayer sp) return sp;
            Object uuid = read(actor, "getUuid");
            if (uuid instanceof java.util.UUID id) {
                ServerPlayer player = ServerLifecycleBridge.getPlayer(id);
                if (player != null) return player;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Object read(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Throwable ignored) { return null; }
    }
}
