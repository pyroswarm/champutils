package com.champutils.dungeon;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class PokemonIconUtil {

    private PokemonIconUtil() {
    }

    public static ItemStack createPokemonIcon(String speciesName, boolean shiny, String configuredFallbackItem, boolean announceFallback) {
        ItemStack pokemonModel = createCobblemonPokemonModel(speciesName, shiny);
        if (!pokemonModel.isEmpty() && pokemonModel.getItem() != Items.AIR) {
            return pokemonModel;
        }

        ItemStack configured = createConfiguredFallback(configuredFallbackItem);
        if (!configured.isEmpty() && configured.getItem() != Items.AIR) {
            return configured;
        }

        return new ItemStack(announceFallback ? Items.NETHER_STAR : Items.EGG);
    }

    private static ItemStack createCobblemonPokemonModel(String speciesName, boolean shiny) {
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
                Species namespaced = PokemonSpecies.getByIdentifier(ResourceLocation.parse(cleaned));
                if (namespaced != null) {
                    return namespaced;
                }
                cleaned = cleaned.substring(cleaned.indexOf(':') + 1);
            }
        } catch (Throwable ignored) {
        }

        try {
            return PokemonSpecies.getByName(cleaned);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemStack createConfiguredFallback(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }

        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId.trim()));
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }
}
