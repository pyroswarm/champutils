package com.champutils.profession.actives;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Toggleable Silk Touch for profession pickaxes, shovels, and axes. */
public class SilkTouchToggleAbility implements ProfessionActiveAbility {
    @Override public String id() { return "silk_touch_toggle"; }

    @Override
    public boolean use(ServerPlayer player, ItemStack stack) {
        boolean enabled = ActiveEffectManager.toggleEffect(player, "silk_touch", "Silk Touch", stack);
        try {
            Holder<Enchantment> silk = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.SILK_TOUCH);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stack.getEnchantments());
            mutable.set(silk, enabled ? 1 : 0);
            stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
            player.getInventory().setChanged();
        } catch (Throwable ignored) {
            // A registry failure must not make the active unusable.
        }
        return true;
    }
}
