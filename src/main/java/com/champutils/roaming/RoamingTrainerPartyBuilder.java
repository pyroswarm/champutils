package com.champutils.roaming;

import com.champutils.util.CobblemonHeldItemUtil;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
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
            // Roaming trainers should be weaker than gym leaders. Champion-tier AI is reserved for gyms, E4, bosses, and events.
            try { npc.setSkill(Math.max(2, Math.min(5, settings.aiSkill))); } catch (Exception ignored) {}
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
            applyTierLegalMoves(pokemon, species, level, rarity);

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
            case EPIC -> null;
            case LEGENDARY -> null;
            case MYTHIC -> null;
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

    private static void applyTierLegalMoves(Pokemon pokemon, String speciesName, int level, RoamingTrainerRarity rarity) {
        if (pokemon == null) return;
        String species = sanitize(speciesName);
        List<String> candidates = legalMoveCandidates(species, level, rarity);
        List<String> selected = selectSmartMoves(pokemon, candidates);
        if (selected.isEmpty()) return;
        try {
            pokemon.getMoveSet().clear();
            int learned = 0;
            for (String move : selected) {
                if (learned >= 4) break;
                try {
                    MoveTemplate template = Moves.getByName(sanitizeMove(move));
                    if (template == null) continue;
                    pokemon.getMoveSet().add(template.create());
                    learned++;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static List<String> legalMoveCandidates(String species, int level, RoamingTrainerRarity rarity) {
        List<String> moves = new ArrayList<>();
        moves.addAll(levelUpMoves(species, level));
        if (level >= 21 && rarity.ordinal() >= RoamingTrainerRarity.UNCOMMON.ordinal()) moves.addAll(tmStyleMoves(species));
        if (level >= 51 && rarity.ordinal() >= RoamingTrainerRarity.RARE.ordinal()) moves.addAll(eggStyleMoves(species));
        if (moves.isEmpty()) moves.addAll(List.of("tackle", "quickattack", "growl", "leer"));
        return moves;
    }

    private static List<String> selectSmartMoves(Pokemon pokemon, List<String> candidates) {
        List<String> clean = new ArrayList<>();
        for (String move : candidates) {
            String m = sanitizeMove(move);
            if (!m.isBlank() && !clean.contains(m) && Moves.getByName(m) != null) clean.add(m);
        }
        List<String> selected = new ArrayList<>();
        // 1 STAB if possible. Pokemon#getTypes() returns Iterable, not Collection, in Cobblemon 1.7.3.
        for (String m : clean) {
            MoveTemplate t = Moves.getByName(m);
            try {
                if (t == null) continue;
                ElementalType moveType = t.getEffectiveElementalType(pokemon);
                for (ElementalType pokemonType : pokemon.getTypes()) {
                    if (pokemonType != null && pokemonType.equals(moveType)) {
                        selected.add(m);
                        break;
                    }
                }
                if (!selected.isEmpty() && selected.get(selected.size() - 1).equals(m)) break;
            } catch (Exception ignored) {}
        }
        // Strong/simple damaging moves.
        clean.stream().filter(m -> !selected.contains(m)).filter(RoamingTrainerPartyBuilder::isDamagingMove).limit(2).forEach(selected::add);
        // One utility/status max.
        clean.stream().filter(m -> !selected.contains(m)).filter(m -> !isDamagingMove(m)).limit(1).forEach(selected::add);
        // Fill remaining with damage/neutral.
        clean.stream().filter(m -> !selected.contains(m)).limit(4 - selected.size()).forEach(selected::add);
        return selected.size() > 4 ? selected.subList(0, 4) : selected;
    }

    private static boolean isDamagingMove(String move) {
        try {
            MoveTemplate t = Moves.getByName(sanitizeMove(move));
            return t != null && t.getPower() > 0;
        } catch (Exception ignored) { return true; }
    }

    private static List<String> levelUpMoves(String species, int level) {
        return switch (species) {
            case "charmander" -> level < 10 ? List.of("scratch", "growl", "ember") : level < 20 ? List.of("scratch", "ember", "smokescreen", "dragonbreath") : List.of("ember", "firefang", "slash", "dragonbreath");
            case "charmeleon", "charizard" -> List.of("flamethrower", "slash", "dragonbreath", "smokescreen");
            case "bulbasaur" -> level < 10 ? List.of("tackle", "growl", "vinewhip") : List.of("vinewhip", "razorleaf", "sleeppowder", "takedown");
            case "ivysaur", "venusaur" -> List.of("razorleaf", "sleeppowder", "seedbomb", "growth");
            case "squirtle" -> level < 10 ? List.of("tackle", "tailwhip", "watergun") : List.of("watergun", "bite", "rapidspin", "protect");
            case "wartortle", "blastoise" -> List.of("waterpulse", "bite", "aquatail", "protect");
            case "pikachu", "raichu" -> List.of("thundershock", "quickattack", "thunderwave", level >= 26 ? "thunderbolt" : "spark");
            case "eevee" -> List.of("tackle", "quickattack", "swift", "sandattack");
            case "vaporeon" -> List.of("watergun", "aurorabeam", "quickattack", "babydolleyes");
            case "jolteon" -> List.of("thundershock", "quickattack", "doublekick", "thunderwave");
            case "flareon" -> List.of("ember", "quickattack", "bite", "firespin");
            case "pidgey", "pidgeotto" -> List.of("gust", "quickattack", "sandattack", "wingattack");
            case "spearow", "fearow" -> List.of("peck", "leer", "furyattack", "aerialace");
            case "zubat", "golbat" -> List.of("absorb", "bite", "wingattack", "confuseray");
            case "geodude", "golem" -> List.of("tackle", "rockthrow", "bulldoze", "defensecurl");
            case "gastly", "haunter", "gengar" -> List.of("lick", "hypnosis", "nightshade", "shadowpunch");
            case "machop", "machoke", "machamp" -> List.of("karatechop", "lowkick", "seismictoss", "leer");
            case "psyduck", "golduck" -> List.of("watergun", "confusion", "furyswipes", "disable");
            default -> fallbackByLevel(level);
        };
    }

    private static List<String> fallbackByLevel(int level) {
        if (level <= 10) return List.of("tackle", "growl", "quickattack");
        if (level <= 20) return List.of("tackle", "quickattack", "bite", "leer");
        if (level <= 50) return List.of("quickattack", "bite", "slash", "protect");
        return List.of("slash", "crunch", "protect", "quickattack");
    }

    private static List<String> tmStyleMoves(String species) {
        return switch (species) {
            case "pikachu", "raichu" -> List.of("thunderbolt", "voltswitch");
            case "charmeleon", "charizard" -> List.of("flamecharge", "aerialace");
            case "wartortle", "blastoise", "vaporeon", "psyduck", "golduck" -> List.of("waterpulse", "icywind");
            case "ivysaur", "venusaur" -> List.of("magicalleaf", "venoshock");
            case "geodude", "golem" -> List.of("rocktomb", "bulldoze");
            default -> List.of("protect", "facade");
        };
    }

    private static List<String> eggStyleMoves(String species) {
        return switch (species) {
            case "charmander", "charmeleon", "charizard" -> List.of("dragonrush", "ancientpower");
            case "pikachu", "raichu" -> List.of("fakeout");
            case "eevee" -> List.of("wish", "yawn");
            default -> List.of();
        };
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
            evs.set(Stats.HP, 32);
            evs.set(Stats.ATTACK, 32);
            evs.set(Stats.DEFENCE, 32);
            evs.set(Stats.SPECIAL_ATTACK, 32);
            evs.set(Stats.SPECIAL_DEFENCE, 32);
            evs.set(Stats.SPEED, 32);
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
