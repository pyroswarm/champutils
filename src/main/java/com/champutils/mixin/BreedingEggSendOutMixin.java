package com.champutils.mixin;

import com.champutils.breeding.BreedingEggData;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.net.messages.server.SendOutPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.cobblemon.mod.common.net.serverhandling.storage.SendOutPokemonHandler", remap = false)
public abstract class BreedingEggSendOutMixin {
    @Inject(
            method = "handle(Lcom/cobblemon/mod/common/net/messages/server/SendOutPokemonPacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void champutils$blockEggSendOut(SendOutPokemonPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (packet == null || player == null) return;
        int slot = packet.getSlot();
        if (slot < 0 || slot > 5) return;
        Pokemon pokemon;
        try { pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot); }
        catch (Throwable ignored) { return; }
        if (!BreedingEggData.isEgg(pokemon)) return;
        player.sendSystemMessage(Component.literal("That Egg cannot be sent out. Keep it in your party and walk to hatch it.")
                .withStyle(ChatFormatting.YELLOW));
        ci.cancel();
    }
}
