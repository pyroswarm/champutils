package com.champutils.profession.actives;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class AutoReplantToggleAbility implements ProfessionActiveAbility {
    public String id() { return "auto_replant_toggle"; }
    public boolean use(ServerPlayer player, ItemStack stack) {
        ActiveEffectManager.toggleEffect(player, "auto_replant", "Auto Replant", stack);
        return true;
    }
}
