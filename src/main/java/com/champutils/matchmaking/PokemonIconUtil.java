package com.champutils.matchmaking;

import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class PokemonIconUtil {

    public static ItemStack getIcon(Pokemon p, int slot, boolean selected) {

        Item pokeBall = BuiltInRegistries.ITEM.get(
                new ResourceLocation("cobblemon", "poke_ball")
        );

        ItemStack item = new ItemStack(pokeBall);

        String name = p.getDisplayName(true).getString();

        item.set(
            DataComponents.CUSTOM_NAME,
            Component.literal((selected ? "§a▶ " : "§f") + name)
        );

        if (selected) {
            item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }

        return item;
    }
}