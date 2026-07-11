package com.champutils.mixin;

import com.champutils.breeding.BreedingEggData;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(value = PartyStore.class, remap = false)
public abstract class BreedingEggBattleTeamMixin {
    @Inject(
            method = "toBattleTeam(ZZLjava/util/UUID;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void champutils$removeEggsFromBattleTeam(boolean clone, boolean healPokemon, UUID leadingPokemon,
                                                      CallbackInfoReturnable<List<BattlePokemon>> cir) {
        List<BattlePokemon> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;
        List<BattlePokemon> filtered = original.stream()
                .filter(pokemon -> pokemon != null && !BreedingEggData.isEgg(pokemon.getOriginalPokemon()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (filtered.size() != original.size()) cir.setReturnValue(filtered);
    }
}
