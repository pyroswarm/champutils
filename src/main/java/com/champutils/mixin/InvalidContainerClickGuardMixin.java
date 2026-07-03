package com.champutils.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Defensive packet guard for broken/third-party containers that emit slot -1
 * clicks. On 1.21.1 this can hit AbstractContainerMenu state with an invalid
 * index and disconnect the player before normal event guards can recover.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class InvalidContainerClickGuardMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void champutils$ignoreInvalidContainerSlot(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (packet == null) return;
        if (packet.getSlotNum() != -1) return;

        ci.cancel();
        if (player != null && player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
    }
}
