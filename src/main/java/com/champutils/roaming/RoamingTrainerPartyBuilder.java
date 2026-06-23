package com.champutils.roaming;

import com.champutils.util.CobblemonHeldItemUtil;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
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
import java.util.Collections;

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
            RoamingTrainerConfig.PokemonPoolEntry configured = pickConfiguredSet(settings, slot);
            String species = configured != null ? configured.species : pickSpecies(rarity, settings, slot);
            int level = Math.max(1, Math.min(100, baseLevel));
            Pokemon pokemon = PokemonProperties.Companion.parse("species=\"cobblemon:" + sanitize(species) + "\" level=" + level).create();

            if (configured != null) {
                applyConfiguredIVsOrPerfect(pokemon, configured);
                int learned = applyConfiguredMoves(pokemon, configured.moves);
                if (learned <= 0) applyTierLegalMoves(pokemon, species, level, rarity);
                applyConfiguredEVsOrBest(pokemon, configured);
                applyAbility(pokemon, configured.ability);
                applyNature(pokemon, configured.nature);
                applyHeldItem(pokemon, configured.heldItem);
            } else {
                applyBestIVs(pokemon);
                applyTierLegalMoves(pokemon, species, level, rarity);
                applyBestEVs(pokemon);
                applyNature(pokemon, bestNatureForCurrentMoves(pokemon));
                applyHeldItem(pokemon, bestHeldItemForCurrentMoves(pokemon, slot));
            }

            if (RANDOM.nextDouble() < Math.max(0.0D, settings.shinyChance)) {
                try { pokemon.setShiny(true); } catch (Exception ignored) {}
            }

            try { pokemon.heal(); } catch (Exception ignored) {}
            return pokemon;
        } catch (Exception e) {
            return null;
        }
    }

    private static RoamingTrainerConfig.PokemonPoolEntry pickConfiguredSet(RoamingTrainerConfig.RaritySettings settings, int slot) {
        if (settings == null || settings.pool == null || settings.pool.isEmpty()) return null;

        List<RoamingTrainerConfig.PokemonPoolEntry> usable = new ArrayList<>();
        for (RoamingTrainerConfig.PokemonPoolEntry entry : settings.pool) {
            if (entry != null && entry.species != null && !entry.species.isBlank()) usable.add(entry);
        }
        if (usable.isEmpty()) return null;

        // Prefer a lead in slot 0 and an anchor in the final slot when configured, like gym pools do.
        if (slot == 0) {
            RoamingTrainerConfig.PokemonPoolEntry lead = pickTagged(usable, "lead");
            if (lead != null) return lead;
        }
        if (slot >= Math.max(1, settings.pokemonCount) - 1) {
            RoamingTrainerConfig.PokemonPoolEntry anchor = pickTagged(usable, "anchor");
            if (anchor != null) return anchor;
        }

        double total = 0.0D;
        for (RoamingTrainerConfig.PokemonPoolEntry entry : usable) total += Math.max(0.0D, entry.weight);
        if (total <= 0.0D) return usable.get(RANDOM.nextInt(usable.size()));
        double roll = RANDOM.nextDouble() * total;
        for (RoamingTrainerConfig.PokemonPoolEntry entry : usable) {
            roll -= Math.max(0.0D, entry.weight);
            if (roll <= 0.0D) return entry;
        }
        return usable.get(usable.size() - 1);
    }

    private static RoamingTrainerConfig.PokemonPoolEntry pickTagged(List<RoamingTrainerConfig.PokemonPoolEntry> usable, String tag) {
        List<RoamingTrainerConfig.PokemonPoolEntry> tagged = new ArrayList<>();
        for (RoamingTrainerConfig.PokemonPoolEntry entry : usable) {
            if (entry.tags == null) continue;
            for (String value : entry.tags) {
                if (value != null && value.equalsIgnoreCase(tag)) {
                    tagged.add(entry);
                    break;
                }
            }
        }
        if (tagged.isEmpty()) return null;
        return tagged.get(RANDOM.nextInt(tagged.size()));
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
        if (RoamingTrainerConfig.DATA.allowCompetitiveMoves) moves.addAll(competitiveMovesFor(species));
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
        // Strong/simple damaging moves. Shuffle equal-value moves so every trainer does not collapse into the same Crunch/fallback loop.
        List<String> damaging = new ArrayList<>();
        clean.stream().filter(m -> !selected.contains(m)).filter(RoamingTrainerPartyBuilder::isDamagingMove).forEach(damaging::add);
        Collections.shuffle(damaging, RANDOM);
        damaging.stream().limit(2).forEach(selected::add);
        // One utility/status max.
        List<String> utility = new ArrayList<>();
        clean.stream().filter(m -> !selected.contains(m)).filter(m -> !isDamagingMove(m)).forEach(utility::add);
        Collections.shuffle(utility, RANDOM);
        utility.stream().limit(1).forEach(selected::add);
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
        if (level <= 10) return List.of("tackle", "growl", "quickattack", "sandattack");
        if (level <= 20) return List.of("tackle", "quickattack", "bite", "leer", "swift", "protect");
        if (level <= 50) return List.of("quickattack", "bite", "slash", "protect", "facade", "rocktomb", "bulldoze");
        return List.of("slash", "crunch", "protect", "quickattack", "facade", "bodyslam", "brickbreak", "shadowclaw", "xscissor", "aerialace", "uturn");
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


    private static boolean applyAbility(Pokemon pokemon, String ability) {
        try {
            if (pokemon == null || ability == null || ability.isBlank()) return false;
            pokemon.updateAbility(Abilities.INSTANCE.getOrException(sanitizeMove(ability)).create(false, Priority.NORMAL));
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static int applyConfiguredMoves(Pokemon pokemon, List<String> configuredMoves) {
        int learned = 0;
        if (pokemon == null || configuredMoves == null || configuredMoves.isEmpty()) return 0;
        try { pokemon.getMoveSet().clear(); } catch (Exception ignored) {}
        for (String move : configuredMoves) {
            if (learned >= 4) break;
            try {
                MoveTemplate template = Moves.getByName(sanitizeMove(move));
                if (template == null) continue;
                pokemon.getMoveSet().add(template.create());
                learned++;
            } catch (Exception ignored) {}
        }
        return learned;
    }

    private static void applyConfiguredIVsOrPerfect(Pokemon pokemon, RoamingTrainerConfig.PokemonPoolEntry entry) {
        applyBestIVs(pokemon);
        if (pokemon == null || entry == null || entry.ivs == null || entry.ivs.isEmpty()) return;
        try {
            var ivs = pokemon.getIvs();
            setStatValue(ivs, Stats.HP, entry.ivs.get("hp"));
            setStatValue(ivs, Stats.ATTACK, entry.ivs.get("atk"));
            setStatValue(ivs, Stats.DEFENCE, entry.ivs.get("def"));
            setStatValue(ivs, Stats.SPECIAL_ATTACK, entry.ivs.get("spa"));
            setStatValue(ivs, Stats.SPECIAL_DEFENCE, entry.ivs.get("spd"));
            setStatValue(ivs, Stats.SPEED, entry.ivs.get("spe"));
        } catch (Exception ignored) {}
    }

    private static void applyConfiguredEVsOrBest(Pokemon pokemon, RoamingTrainerConfig.PokemonPoolEntry entry) {
        if (pokemon == null || entry == null || entry.evs == null || entry.evs.isEmpty()) {
            applyBestEVs(pokemon);
            return;
        }
        try {
            var evs = pokemon.getEvs();
            setStatValue(evs, Stats.HP, entry.evs.get("hp"));
            setStatValue(evs, Stats.ATTACK, entry.evs.get("atk"));
            setStatValue(evs, Stats.DEFENCE, entry.evs.get("def"));
            setStatValue(evs, Stats.SPECIAL_ATTACK, entry.evs.get("spa"));
            setStatValue(evs, Stats.SPECIAL_DEFENCE, entry.evs.get("spd"));
            setStatValue(evs, Stats.SPEED, entry.evs.get("spe"));
        } catch (Exception ignored) { applyBestEVs(pokemon); }
    }

    @SuppressWarnings("unchecked")
    private static void setStatValue(Object stats, Stat stat, Integer value) {
        if (stats == null || stat == null || value == null) return;
        int clamped = Math.max(0, Math.min(252, value));
        try {
            stats.getClass().getMethod("set", Stat.class, int.class).invoke(stats, stat, clamped);
        } catch (Exception ignored) {
            try { stats.getClass().getMethod("set", Stat.class, Integer.class).invoke(stats, stat, clamped); } catch (Exception ignoredAgain) {}
        }
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
            int physical = 0;
            int special = 0;
            try {
                for (Object moveObj : pokemon.getMoveSet()) {
                    String id = normalizeMoveObject(moveObj);
                    MoveTemplate t = Moves.getByName(id);
                    if (t == null || t.getPower() <= 0) continue;
                    String category = String.valueOf(t.getDamageCategory()).toLowerCase(Locale.ROOT);
                    if (category.contains("special")) special++; else physical++;
                }
            } catch (Exception ignored) {}

            evs.set(Stats.HP, 0);
            evs.set(Stats.ATTACK, 0);
            evs.set(Stats.DEFENCE, 4);
            evs.set(Stats.SPECIAL_ATTACK, 0);
            evs.set(Stats.SPECIAL_DEFENCE, 0);
            evs.set(Stats.SPEED, 252);
            if (special > physical) evs.set(Stats.SPECIAL_ATTACK, 252);
            else evs.set(Stats.ATTACK, 252);
        } catch (Exception ignored) {}
    }

    private static String bestNatureForCurrentMoves(Pokemon pokemon) {
        int physical = 0;
        int special = 0;
        try {
            for (Object moveObj : pokemon.getMoveSet()) {
                String id = normalizeMoveObject(moveObj);
                MoveTemplate t = Moves.getByName(id);
                if (t == null || t.getPower() <= 0) continue;
                String category = String.valueOf(t.getDamageCategory()).toLowerCase(Locale.ROOT);
                if (category.contains("special")) special++; else physical++;
            }
        } catch (Exception ignored) {}
        return special > physical ? "timid" : "jolly";
    }

    private static String bestHeldItemForCurrentMoves(Pokemon pokemon, int slot) {
        boolean hasSetup = false;
        boolean special = false;
        boolean physical = false;
        try {
            for (Object moveObj : pokemon.getMoveSet()) {
                String id = normalizeMoveObject(moveObj);
                if (id.contains("dance") || id.contains("plot") || id.contains("mind") || id.contains("smash") || id.contains("agility")) hasSetup = true;
                MoveTemplate t = Moves.getByName(id);
                if (t != null && t.getPower() > 0 && String.valueOf(t.getDamageCategory()).toLowerCase(Locale.ROOT).contains("special")) special = true;
                if (t != null && t.getPower() > 0 && String.valueOf(t.getDamageCategory()).toLowerCase(Locale.ROOT).contains("physical")) physical = true;
            }
        } catch (Exception ignored) {}
        List<String> configured = RoamingTrainerConfig.DATA.competitiveHeldItems;
        if (configured != null && !configured.isEmpty()) {
            List<String> clean = new ArrayList<>();
            for (String item : configured) if (item != null && !item.isBlank()) clean.add(item.trim());
            if (!clean.isEmpty()) {
                if (hasSetup && clean.stream().anyMatch(i -> sanitize(i).contains("focus_sash"))) return "focus_sash";
                if (special && !physical && clean.stream().anyMatch(i -> sanitize(i).contains("choice_specs"))) return "choice_specs";
                if (physical && !special && clean.stream().anyMatch(i -> sanitize(i).contains("choice_band"))) return "choice_band";
                return clean.get(Math.floorMod(slot + RANDOM.nextInt(clean.size()), clean.size()));
            }
        }
        if (hasSetup) return "focus_sash";
        if (special && !physical) return slot % 2 == 0 ? "choice_specs" : "wise_glasses";
        if (physical && !special) return slot % 2 == 0 ? "choice_band" : "muscle_band";
        return switch (Math.floorMod(slot, 5)) {
            case 0 -> "life_orb";
            case 1 -> "leftovers";
            case 2 -> "expert_belt";
            case 3 -> "focus_sash";
            default -> "sitrus_berry";
        };
    }

    private static String normalizeMoveObject(Object moveObj) {
        if (moveObj == null) return "";
        for (String methodName : List.of("getName", "getId", "getTemplate")) {
            try {
                Object value = moveObj.getClass().getMethod(methodName).invoke(moveObj);
                if (value != null && value != moveObj) return sanitizeMove(value.toString());
            } catch (Exception ignored) {}
        }
        return sanitizeMove(moveObj.toString());
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
