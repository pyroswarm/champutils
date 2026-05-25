package com.champutils.roaming;

import com.champutils.util.CobblemonHeldItemUtil;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
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
            // Roaming trainers should be real competitive PvE threats now. The AI wrapper prevents bad switch loops.
            try { npc.setSkill(Math.max(3, Math.min(5, settings.aiSkill))); } catch (Exception ignored) {}
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
            int level = Math.max(1, Math.min(100, baseLevel));
            Pokemon pokemon = PokemonProperties.Companion.parse("species=\"cobblemon:" + sanitize(species) + "\" level=" + level).create();

            applyBestIVs(pokemon);
            applyBestEVs(pokemon);
            applyCompetitiveMoves(pokemon, species);

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

        String forced = forcedSpeciesForSlot(rarity, slot);
        if (forced != null && !forced.isBlank()) return forced;

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

    private static String forcedSpeciesForSlot(RoamingTrainerRarity rarity, int slot) {
        return switch (rarity) {
            // Epic: exactly 1 legendary, then strong/elite regular Pokemon.
            case EPIC -> slot == 0 ? pick(RoamingTrainerConfig.DATA.legendarySpeciesPool) : null;
            // Legendary: exactly 1 legendary + 1 ultra beast/paradox, then strong regular Pokemon.
            case LEGENDARY -> {
                if (slot == 0) yield pick(RoamingTrainerConfig.DATA.legendarySpeciesPool);
                if (slot == 1) yield pickSpecialNonLegendaryBossSlot(false);
                yield null;
            }
            // Mythic rarity trainer: 3 legendary + 1 mythic/paradox, then strong regular Pokemon.
            case MYTHIC -> {
                if (slot >= 0 && slot <= 2) yield pick(RoamingTrainerConfig.DATA.legendarySpeciesPool);
                if (slot == 3) yield pickSpecialNonLegendaryBossSlot(true);
                yield null;
            }
            default -> null;
        };
    }

    private static String pickSpecialNonLegendaryBossSlot(boolean preferMythic) {
        List<String> pool = new ArrayList<>();
        if (preferMythic && RoamingTrainerConfig.DATA.mythicSpeciesPool != null) pool.addAll(RoamingTrainerConfig.DATA.mythicSpeciesPool);
        if (RoamingTrainerConfig.DATA.paradoxSpeciesPool != null) pool.addAll(RoamingTrainerConfig.DATA.paradoxSpeciesPool);
        if (!preferMythic && RoamingTrainerConfig.DATA.ultraBeastSpeciesPool != null) pool.addAll(RoamingTrainerConfig.DATA.ultraBeastSpeciesPool);
        if (preferMythic && pool.isEmpty() && RoamingTrainerConfig.DATA.ultraBeastSpeciesPool != null) pool.addAll(RoamingTrainerConfig.DATA.ultraBeastSpeciesPool);
        String picked = pick(pool);
        return picked == null || picked.isBlank() ? null : picked;
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


    private static void applyCompetitiveMoves(Pokemon pokemon, String speciesName) {
        if (pokemon == null) return;
        try {
            pokemon.getMoveSet().clear();
            List<String> moves = competitiveMovesFor(sanitize(speciesName));
            int learned = 0;
            for (String move : moves) {
                if (learned >= 4) break;
                try { pokemon.getMoveSet().add(Moves.getByName(sanitizeMove(move)).create()); learned++; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static List<String> competitiveMovesFor(String species) {
        return switch (species) {
            case "garchomp" -> List.of("earthquake", "dragonclaw", "stoneedge", "swordsdance");
            case "dragonite" -> List.of("dragondance", "earthquake", "extremespeed", "dualwingbeat");
            case "volcarona" -> List.of("quiverdance", "fierydance", "bugbuzz", "gigadrain");
            case "greninja" -> List.of("hydropump", "darkpulse", "icebeam", "watershuriken");
            case "gyarados" -> List.of("dragondance", "waterfall", "earthquake", "crunch");
            case "lucario" -> List.of("swordsdance", "closecombat", "meteormash", "extremespeed");
            case "tyranitar" -> List.of("stoneedge", "crunch", "earthquake", "dragondance");
            case "metagross" -> List.of("meteormash", "zenheadbutt", "earthquake", "agility");
            case "mimikyu" -> List.of("swordsdance", "playrough", "shadowclaw", "shadowsneak");
            case "dragapult" -> List.of("dragondarts", "phantomforce", "uturn", "willowisp");
            case "kingambit" -> List.of("kowtowcleave", "suckerpunch", "ironhead", "swordsdance");
            case "annihilape" -> List.of("ragefist", "drainpunch", "bulkup", "taunt");
            default -> List.of("earthquake", "thunderbolt", "flamethrower", "icebeam");
        };
    }

    private static String sanitizeMove(String value) {
        if (value == null) return "tackle";
        return value.trim().toLowerCase(Locale.ROOT).replace("cobblemon:", "").replaceAll("[^a-z0-9]", "");
    }

    private static void applyBestIVs(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            var ivs = pokemon.getIvs();
            ivs.set(Stats.HP, 31);
            ivs.set(Stats.ATTACK, 31);
            ivs.set(Stats.DEFENCE, 31);
            ivs.set(Stats.SPECIAL_ATTACK, 31);
            ivs.set(Stats.SPECIAL_DEFENCE, 31);
            ivs.set(Stats.SPEED, 31);
        } catch (Exception ignored) {}
    }

    private static void applyBestEVs(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            var evs = pokemon.getEvs();
            // 510 total EVs, spread evenly so every randomly selected Pokemon is battle-ready
            // even when we do not know whether it is a physical, special, mixed, or bulky set.
            evs.set(Stats.HP, 85);
            evs.set(Stats.ATTACK, 85);
            evs.set(Stats.DEFENCE, 85);
            evs.set(Stats.SPECIAL_ATTACK, 85);
            evs.set(Stats.SPECIAL_DEFENCE, 85);
            evs.set(Stats.SPEED, 85);
        } catch (Exception ignored) {}
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
