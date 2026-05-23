package com.champutils.matchmaking;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class PokemonIconUtil {

    public static ItemStack getIcon(Pokemon p, int slot, boolean selected) {

        Item pokeBall = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")
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

    /**
     * Creates a Cobblemon Pokemon-item sprite for menus from a species id/name.
     * Falls back to the supplied item id if the species cannot be resolved.
     */
    public static ItemStack createPokemonIcon(String speciesName, boolean shiny, String fallbackItemId, boolean glint) {
        ItemStack stack = createPokemonIcon(speciesName, shiny);
        if (stack.isEmpty()) {
            stack = fallbackStack(fallbackItemId);
        }
        if (glint && !stack.isEmpty()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    public static ItemStack createPokemonIcon(String speciesName, boolean shiny) {
        Species species = findSpecies(speciesName);
        if (species == null) {
            return ItemStack.EMPTY;
        }

        try {
            Set<String> aspects = new HashSet<>();
            if (shiny) {
                aspects.add("shiny");
            }
            return PokemonItem.from(species, aspects, 1, null);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static Species findSpecies(String speciesName) {
        if (speciesName == null || speciesName.isBlank()) {
            return null;
        }

        String cleaned = speciesName.trim().toLowerCase(Locale.ROOT);

        try {
            if (cleaned.contains(":")) {
                Species namespaced = PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.parse(cleaned));
                if (namespaced != null) {
                    return namespaced;
                }
                cleaned = cleaned.substring(cleaned.indexOf(':') + 1);
            }
        } catch (Throwable ignored) {
        }

        cleaned = cleaned.replace("♀", "-f").replace("♂", "-m");
        cleaned = cleaned.replace(" ", "-").replace("_", "-");

        try {
            Species byName = PokemonSpecies.INSTANCE.getByName(cleaned);
            if (byName != null) {
                return byName;
            }
        } catch (Throwable ignored) {
        }

        try {
            return PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", cleaned));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemStack fallbackStack(String fallbackItemId) {
        try {
            ResourceLocation id = ResourceLocation.parse(fallbackItemId == null || fallbackItemId.isBlank() ? "minecraft:egg" : fallbackItemId);
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        } catch (Throwable ignored) {
        }
        return new ItemStack(CobblemonItems.POKE_BALL);
    }
}
