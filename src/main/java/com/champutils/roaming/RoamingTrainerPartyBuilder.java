package com.champutils.roaming;

import com.champutils.util.CobblemonHeldItemUtil;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

public final class RoamingTrainerPartyBuilder {

    private static final Random RANDOM = new Random();

    private RoamingTrainerPartyBuilder() {}

    public static boolean apply(NPCEntity npc, RoamingTrainerManager.RoamingTrainerData data) {
        if (npc == null || data == null) return false;

        try {
            RoamingTrainerConfig.RaritySettings settings = RoamingTrainerConfig.settings(data.rarity);
            int count = Math.max(1, Math.min(6, settings.pokemonCount));
            int baseLevel = Math.max(1, Math.min(100, data.targetLevel));

            npc.initialize(baseLevel);
            NPCPartyStore party = new NPCPartyStore(npc);

            for (int slot = 0; slot < count; slot++) {
                Pokemon pokemon = createPokemon(data.rarity, settings, baseLevel, slot);
                if (pokemon == null) continue;
                try { pokemon.heal(); } catch (Exception ignored) {}
                party.set(slot, pokemon);
            }

            party.initialize();
            npc.setParty(party);
            // Roaming trainers use team quality for difficulty. Capping AI skill prevents the high-skill AI
            // from getting stuck in repeated defensive switch loops.
            try { npc.setSkill(Math.max(0, Math.min(2, settings.aiSkill))); } catch (Exception ignored) {}
            try { npc.setCustomName(Component.literal(data.displayName).withStyle(data.rarity.color)); } catch (Exception ignored) {}
            try { npc.setCustomNameVisible(true); } catch (Exception ignored) {}
            try { npc.setHealth(npc.getMaxHealth()); } catch (Exception ignored) {}
            try { npc.setPersistenceRequired(); } catch (Exception ignored) {}
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Pokemon createPokemon(RoamingTrainerRarity rarity, RoamingTrainerConfig.RaritySettings settings, int baseLevel, int slot) {
        try {
            String species = pickSpecies(rarity, settings, slot);
            int offset = randomBetween(settings.levelOffsetMin, settings.levelOffsetMax);
            int level = Math.max(1, Math.min(100, baseLevel + offset));
            Pokemon pokemon = PokemonProperties.Companion.parse("species=\"cobblemon:" + sanitize(species) + "\" level=" + level).create();

            if (RANDOM.nextDouble() < Math.max(0.0D, settings.shinyChance)) {
                try { pokemon.setShiny(true); } catch (Exception ignored) {}
            }

            if (RANDOM.nextDouble() < Math.max(0.0D, settings.competitiveNatureChance)) {
                applyNature(pokemon, pick(RoamingTrainerConfig.DATA.competitiveNatures));
            }

            if (RANDOM.nextDouble() < Math.max(0.0D, settings.heldItemChance)) {
                applyHeldItem(pokemon, pick(RoamingTrainerConfig.DATA.competitiveHeldItems));
            }

            try { pokemon.heal(); } catch (Exception ignored) {}
            return pokemon;
        } catch (Exception e) {
            return null;
        }
    }

    private static String pickSpecies(RoamingTrainerRarity rarity, RoamingTrainerConfig.RaritySettings settings, int slot) {
        if (settings.speciesPool != null && !settings.speciesPool.isEmpty()) {
            String custom = pick(settings.speciesPool);
            return custom == null || custom.isBlank() ? "eevee" : custom;
        }

        int legendarySlots = Math.max(0, Math.min(6, settings.legendaryPokemonCount));
        if (slot < legendarySlots && RoamingTrainerConfig.DATA.legendarySpeciesPool != null && !RoamingTrainerConfig.DATA.legendarySpeciesPool.isEmpty()) {
            return pick(RoamingTrainerConfig.DATA.legendarySpeciesPool);
        }

        if (RoamingTrainerConfig.DATA.allowAllPokemonFromCobblemonRegistry
                && RANDOM.nextDouble() < Math.max(0.0D, Math.min(1.0D, settings.allPokemonChance))) {
            String any = pickAnyRegisteredSpecies();
            if (any != null && !any.isBlank()) return any;
        }

        List<String> pool;
        double roll = RANDOM.nextDouble();
        switch (rarity) {
            case COMMON -> pool = RoamingTrainerConfig.DATA.basicSpeciesPool;
            case UNCOMMON -> pool = roll < 0.70D ? RoamingTrainerConfig.DATA.basicSpeciesPool : RoamingTrainerConfig.DATA.strongSpeciesPool;
            case RARE -> pool = roll < 0.20D ? RoamingTrainerConfig.DATA.basicSpeciesPool : RoamingTrainerConfig.DATA.strongSpeciesPool;
            case EPIC -> pool = roll < 0.65D ? RoamingTrainerConfig.DATA.strongSpeciesPool : RoamingTrainerConfig.DATA.eliteSpeciesPool;
            case LEGENDARY -> pool = roll < 0.35D ? RoamingTrainerConfig.DATA.strongSpeciesPool : RoamingTrainerConfig.DATA.eliteSpeciesPool;
            case MYTHIC -> pool = RoamingTrainerConfig.DATA.eliteSpeciesPool;
            default -> pool = RoamingTrainerConfig.DATA.defaultSpeciesPool;
        }

        String selected = pick(pool);
        return selected == null || selected.isBlank() ? "eevee" : selected;
    }

    private static String pickAnyRegisteredSpecies() {
        try {
            List<String> all = getRegisteredSpeciesNames();
            if (all.isEmpty()) return "";
            return all.get(RANDOM.nextInt(all.size()));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static List<String> getRegisteredSpeciesNames() {
        List<String> names = new ArrayList<>();
        try {
            Object instance = null;
            Class<?> clazz = Class.forName("com.cobblemon.mod.common.api.pokemon.CobblemonSpecies");
            try {
                Field field = clazz.getField("INSTANCE");
                instance = field.get(null);
            } catch (Exception ignored) {}

            Object speciesCollection = null;
            for (String methodName : List.of("getSpecies", "species", "all", "getAll")) {
                try {
                    Method method = clazz.getMethod(methodName);
                    speciesCollection = method.invoke(instance);
                    break;
                } catch (Exception ignored) {}
            }

            if (speciesCollection instanceof Map<?, ?> map) {
                for (Object value : map.values()) addSpeciesName(names, value);
            } else if (speciesCollection instanceof Collection<?> collection) {
                for (Object value : collection) addSpeciesName(names, value);
            }
        } catch (Exception ignored) {}

        if (names.isEmpty()) names.addAll(RoamingTrainerConfig.DATA.defaultSpeciesPool);
        names.removeIf(name -> name == null || name.isBlank() || isBlacklisted(name));
        return names;
    }

    private static void addSpeciesName(List<String> names, Object species) {
        if (species == null) return;
        String value = null;
        for (String methodName : List.of("getName", "getResourceIdentifier", "getIdentifier", "getShowdownId")) {
            try {
                Object result = species.getClass().getMethod(methodName).invoke(species);
                if (result != null) {
                    value = result.toString();
                    break;
                }
            } catch (Exception ignored) {}
        }
        if (value == null || value.isBlank()) value = species.toString();
        value = sanitize(value);
        if (!value.isBlank() && !isBlacklisted(value) && !names.contains(value)) names.add(value);
    }

    private static boolean isBlacklisted(String species) {
        String clean = sanitize(species);
        if (RoamingTrainerConfig.DATA.blacklistedPokemon == null) return false;
        for (String blocked : RoamingTrainerConfig.DATA.blacklistedPokemon) {
            if (blocked == null || blocked.isBlank()) continue;
            if (clean.equals(sanitize(blocked))) return true;
        }
        return false;
    }

    private static void applyNature(Pokemon pokemon, String nature) {
        try {
            if (pokemon == null || nature == null || nature.isBlank()) return;
            pokemon.setNature(Natures.INSTANCE.getNature(ResourceLocation.parse("cobblemon:" + sanitize(nature))));
        } catch (Exception ignored) {}
    }

    private static void applyHeldItem(Pokemon pokemon, String heldItemId) {
        try {
            if (pokemon == null || heldItemId == null || heldItemId.isBlank()) return;
            ItemStack heldItem = CobblemonHeldItemUtil.createHeldItemStack(heldItemId);
            if (heldItem == null || heldItem.isEmpty()) return;
            pokemon.swapHeldItem(heldItem, false, false);
        } catch (Exception ignored) {}
    }

    private static String pick(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        List<String> clean = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) clean.add(value.trim());
        }
        if (clean.isEmpty()) return "";
        return clean.get(RANDOM.nextInt(clean.size()));
    }

    private static int randomBetween(int min, int max) {
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return min + RANDOM.nextInt((max - min) + 1);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return "eevee";
        return value.trim().toLowerCase(Locale.ROOT).replace("cobblemon:", "").replaceAll("[^a-z0-9_\\-]", "");
    }
}
