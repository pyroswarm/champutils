package com.champutils.mixin;

import com.champutils.profile.CobblemonProfileStorageBridge;
import com.cobblemon.mod.common.api.storage.PokemonStoreManager;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import net.minecraft.core.RegistryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Makes Cobblemon's native party/PC lookups profile-aware before the default
 * store factory is queried. This keeps Cobblemon storage keyed by profile UUID
 * instead of account UUID.
 */
@Mixin(value = PokemonStoreManager.class, remap = false)
public abstract class CobblemonPokemonStoreManagerMixin {
    @Inject(method = "getParty(Ljava/util/UUID;Lnet/minecraft/core/RegistryAccess;)Lcom/cobblemon/mod/common/api/storage/party/PlayerPartyStore;", at = @At("HEAD"), cancellable = true)
    private void champutils$getProfileParty(UUID playerID, RegistryAccess registryAccess, CallbackInfoReturnable<PlayerPartyStore> cir) {
        if (!CobblemonProfileStorageBridge.shouldRedirect(playerID)) return;
        UUID profileId = CobblemonProfileStorageBridge.storageKey(playerID);
        cir.setReturnValue(((PokemonStoreManager) (Object) this).getParty(profileId, registryAccess));
    }

    @Inject(method = "getPC(Ljava/util/UUID;Lnet/minecraft/core/RegistryAccess;)Lcom/cobblemon/mod/common/api/storage/pc/PCStore;", at = @At("HEAD"), cancellable = true)
    private void champutils$getProfilePc(UUID playerID, RegistryAccess registryAccess, CallbackInfoReturnable<PCStore> cir) {
        if (!CobblemonProfileStorageBridge.shouldRedirect(playerID)) return;
        UUID profileId = CobblemonProfileStorageBridge.storageKey(playerID);
        cir.setReturnValue(((PokemonStoreManager) (Object) this).getPC(profileId, registryAccess));
    }

    @Inject(method = "getParties", at = @At("HEAD"), cancellable = true)
    private void champutils$getProfileParties(UUID playerID, RegistryAccess registryAccess, CallbackInfoReturnable<Iterable<PartyStore>> cir) {
        if (!CobblemonProfileStorageBridge.shouldRedirect(playerID)) return;
        UUID profileId = CobblemonProfileStorageBridge.storageKey(playerID);
        cir.setReturnValue(java.util.List.of(((PokemonStoreManager) (Object) this).getParty(profileId, registryAccess)));
    }

    @Inject(method = "getPCs", at = @At("HEAD"), cancellable = true)
    private void champutils$getProfilePcs(UUID playerID, RegistryAccess registryAccess, CallbackInfoReturnable<Iterable<PCStore>> cir) {
        if (!CobblemonProfileStorageBridge.shouldRedirect(playerID)) return;
        UUID profileId = CobblemonProfileStorageBridge.storageKey(playerID);
        cir.setReturnValue(java.util.List.of(((PokemonStoreManager) (Object) this).getPC(profileId, registryAccess)));
    }
}
