package com.champutils.mixin;

import com.champutils.afk.AntiAfkManager;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AntiAfkServerGamePacketListenerMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"))
    private void champutils$antiAfkChat(ServerboundChatPacket packet, CallbackInfo ci) {
        AntiAfkManager.markRealActivity(player, "chat_packet");
    }

    @Inject(method = "handleChatCommand", at = @At("HEAD"))
    private void champutils$antiAfkCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        AntiAfkManager.markRealActivity(player, "command_packet");
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void champutils$antiAfkMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (player == null) return;
        AntiAfkManager.recordMove(
                player,
                packet.getX(player.getX()),
                packet.getY(player.getY()),
                packet.getZ(player.getZ()),
                packet.getYRot(player.getYRot()),
                packet.getXRot(player.getXRot())
        );
    }

    @Inject(method = "handleAnimate", at = @At("HEAD"))
    private void champutils$antiAfkSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
        AntiAfkManager.recordSwing(player);
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"))
    private void champutils$antiAfkUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        AntiAfkManager.markRealActivity(player, "use_item_on_packet");
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"))
    private void champutils$antiAfkUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        AntiAfkManager.markSoftActivity(player, "use_item_packet");
    }

    @Inject(method = "handleInteract", at = @At("HEAD"))
    private void champutils$antiAfkInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        AntiAfkManager.markRealActivity(player, "interact_packet");
    }
}
