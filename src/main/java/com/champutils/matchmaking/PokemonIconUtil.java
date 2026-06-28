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
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PokemonIconUtil {

    public static ItemStack getIcon(Pokemon p, int slot, boolean selected) {
        ItemStack item = ItemStack.EMPTY;

        try {
            item = PokemonItem.from(p, 1);
        } catch (Throwable ignored) {
        }

        if (item == null || item.isEmpty() || item.getItem() == Items.AIR) {
            try {
                item = createPokemonIcon(p.getSpecies().getResourceIdentifier().toString(), p.getShiny());
            } catch (Throwable ignored) {
                item = ItemStack.EMPTY;
            }
        }

        if (item == null || item.isEmpty() || item.getItem() == Items.AIR) {
            item = new ItemStack(CobblemonItems.POKE_BALL);
        }

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

    public static boolean canResolveSpecies(String speciesName) {
        return findSpecies(speciesName) != null;
    }

    /**
     * Resolves shop/config species names into the actual Cobblemon species id path.
     * This intentionally accepts legacy names like ho_oh, type_null, tapu_koko,
     * wo_chien, iron_leaves, etc. Cobblemon stores many of those as hooh,
     * typenull, tapukoko, wochien, ironleaves, and so on.
     */
    public static String resolveSpeciesId(String speciesName) {
        Species species = findSpecies(speciesName);
        if (species == null) {
            return null;
        }
        try {
            return species.getResourceIdentifier().getPath();
        } catch (Throwable ignored) {
            return speciesName.trim();
        }
    }

    private static Species findSpecies(String speciesName) {
        if (speciesName == null || speciesName.isBlank()) {
            return null;
        }

        for (String candidate : speciesCandidates(speciesName)) {
            Species species = resolveCandidate(candidate);
            if (species != null) {
                return species;
            }
        }

        return null;
    }

    private static List<String> speciesCandidates(String speciesName) {
        String cleaned = speciesName.trim().toLowerCase(Locale.ROOT);
        if (cleaned.contains(":")) {
            cleaned = cleaned.substring(cleaned.indexOf(':') + 1);
        }

        cleaned = cleaned.replace("♀", "-f").replace("♂", "-m");

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, cleaned);
        addCandidate(candidates, cleaned.replace(" ", "-").replace("_", "-"));
        addCandidate(candidates, cleaned.replace(" ", "_").replace("-", "_"));
        addCandidate(candidates, cleaned.replace(" ", "").replace("_", "").replace("-", ""));

        String explicit = explicitSpeciesIconAlias(cleaned);
        addCandidate(candidates, explicit);
        if (explicit != null) {
            addCandidate(candidates, explicit.replace(" ", "-").replace("_", "-"));
            addCandidate(candidates, explicit.replace(" ", "").replace("_", "").replace("-", ""));
        }

        return new ArrayList<>(candidates);
    }

    private static void addCandidate(Set<String> candidates, String value) {
        if (value != null && !value.isBlank()) {
            candidates.add(value);
        }
    }

    private static Species resolveCandidate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        try {
            Species byName = PokemonSpecies.getByName(candidate);
            if (byName != null) {
                return byName;
            }
        } catch (Throwable ignored) {
        }

        try {
            Species byIdentifier = PokemonSpecies.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", candidate));
            if (byIdentifier != null) {
                return byIdentifier;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static String explicitSpeciesIconAlias(String cleaned) {
        if (cleaned == null) return null;
        return switch (cleaned) {
            case "nidoranmale", "nidoran-male", "nidoran_male", "nidoran-m", "nidoran_m", "nidoran♂" -> "nidoran-m";
            case "nidoranfemale", "nidoran-female", "nidoran_female", "nidoran-f", "nidoran_f", "nidoran♀" -> "nidoran-f";
            case "ho-oh", "ho_oh", "ho oh" -> "hooh";
            case "type-null", "type_null", "type null" -> "typenull";
            case "tapu-koko", "tapu_koko", "tapu koko" -> "tapukoko";
            case "tapu-lele", "tapu_lele", "tapu lele" -> "tapulele";
            case "tapu-bulu", "tapu_bulu", "tapu bulu" -> "tapubulu";
            case "tapu-fini", "tapu_fini", "tapu fini" -> "tapufini";
            case "wo-chien", "wo_chien", "wo chien" -> "wochien";
            case "chien-pao", "chien_pao", "chien pao" -> "chienpao";
            case "ting-lu", "ting_lu", "ting lu" -> "tinglu";
            case "chi-yu", "chi_yu", "chi yu" -> "chiyu";
            case "walking-wake", "walking_wake", "walking wake" -> "walkingwake";
            case "iron-leaves", "iron_leaves", "iron leaves" -> "ironleaves";
            case "gouging-fire", "gouging_fire", "gouging fire" -> "gougingfire";
            case "raging-bolt", "raging_bolt", "raging bolt" -> "ragingbolt";
            case "iron-boulder", "iron_boulder", "iron boulder" -> "ironboulder";
            case "iron-crown", "iron_crown", "iron crown" -> "ironcrown";
            case "iron-treads", "iron_treads", "iron treads" -> "irontreads";
            case "iron-bundle", "iron_bundle", "iron bundle" -> "ironbundle";
            case "iron-hands", "iron_hands", "iron hands" -> "ironhands";
            case "iron-jugulis", "iron_jugulis", "iron jugulis" -> "ironjugulis";
            case "iron-moth", "iron_moth", "iron moth" -> "ironmoth";
            case "iron-thorns", "iron_thorns", "iron thorns" -> "ironthorns";
            case "iron-valiant", "iron_valiant", "iron valiant" -> "ironvaliant";
            case "great-tusk", "great_tusk", "great tusk" -> "greattusk";
            case "scream-tail", "scream_tail", "scream tail" -> "screamtail";
            case "brute-bonnet", "brute_bonnet", "brute bonnet" -> "brutebonnet";
            case "flutter-mane", "flutter_mane", "flutter mane" -> "fluttermane";
            case "slither-wing", "slither_wing", "slither wing" -> "slitherwing";
            case "sandy-shocks", "sandy_shocks", "sandy shocks" -> "sandyshocks";
            case "roaring-moon", "roaring_moon", "roaring moon" -> "roaringmoon";
            default -> cleaned;
        };
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
