package com.champutils.mixin;

import com.champutils.profile.CobblemonProfileStorageBridge;
import com.cobblemon.mod.common.api.storage.player.InstancedPlayerData;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreManager;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreType;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Routes only Cobblemon's native Pokédex store through the active profile UUID. */
@Mixin(value = PlayerInstancedDataStoreManager.class, remap = false)
public abstract class CobblemonPlayerDataStoreManagerMixin {
    @Inject(method = "get(Ljava/util/UUID;Lcom/cobblemon/mod/common/api/storage/player/PlayerInstancedDataStoreType;)Lcom/cobblemon/mod/common/api/storage/player/InstancedPlayerData;", at = @At("HEAD"), cancellable = true)
    private void champutils$getProfilePokedex(UUID playerId, PlayerInstancedDataStoreType dataType, CallbackInfoReturnable<InstancedPlayerData> cir) {
        if (dataType != PlayerInstancedDataStoreTypes.INSTANCE.getPOKEDEX() || !CobblemonProfileStorageBridge.shouldRedirect(playerId)) return;
        UUID profileId = CobblemonProfileStorageBridge.storageKey(playerId);
        cir.setReturnValue(((PlayerInstancedDataStoreManager) (Object) this).get(profileId, dataType));
    }

    @Inject(method = "get(Lnet/minecraft/world/entity/player/Player;Lcom/cobblemon/mod/common/api/storage/player/PlayerInstancedDataStoreType;)Lcom/cobblemon/mod/common/api/storage/player/InstancedPlayerData;", at = @At("HEAD"), cancellable = true)
    private void champutils$getProfilePokedexForPlayer(Player player, PlayerInstancedDataStoreType dataType, CallbackInfoReturnable<InstancedPlayerData> cir) {
        if (player == null || dataType != PlayerInstancedDataStoreTypes.INSTANCE.getPOKEDEX() || !CobblemonProfileStorageBridge.shouldRedirect(player.getUUID())) return;
        UUID profileId = CobblemonProfileStorageBridge.storageKey(player.getUUID());
        cir.setReturnValue(((PlayerInstancedDataStoreManager) (Object) this).get(profileId, dataType));
    }
}
