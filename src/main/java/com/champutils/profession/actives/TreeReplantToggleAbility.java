package com.champutils.profession.actives;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class TreeReplantToggleAbility implements ProfessionActiveAbility {
    public String id() { return "tree_replant_toggle"; }
    public boolean use(ServerPlayer player, ItemStack stack) {
        ActiveEffectManager.toggleEffect(player, "tree_replant", "Tree Replant", stack);
        return true;
    }
}
