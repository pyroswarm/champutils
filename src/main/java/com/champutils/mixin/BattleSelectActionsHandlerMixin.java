package com.champutils.mixin;

import com.champutils.afk.AntiAfkManager;
import com.champutils.afk.PvPBattleStallManager;
import com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.cobblemon.mod.common.net.serverhandling.battle.BattleSelectActionsHandler")
public abstract class BattleSelectActionsHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private void champutils$battleChoiceActivity(BattleSelectActionsPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (player == null || packet == null) return;
        AntiAfkManager.markRealActivity(player, "battle_choice");
        PvPBattleStallManager.choiceMade(player, packet.getBattleId());
    }
}
