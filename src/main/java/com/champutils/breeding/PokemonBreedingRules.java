package com.champutils.breeding;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generation IX-style breeding rules for ChampUtils.
 *
 * <p>Important distinction: a Nature Mint and Hyper Training change battle stats,
 * but do not change the underlying Nature or natural IVs that breeding inherits.</p>
 */
public final class PokemonBreedingRules {
    private static final Random RANDOM = new Random();
    private static final List<Stat> PERMANENT_STATS = List.of(
            Stats.HP, Stats.ATTACK, Stats.DEFENCE,
            Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    );
    private static final Map<String, Stat> POWER_ITEMS = Map.of(
            "cobblemon:power_weight", Stats.HP,
            "cobblemon:power_bracer", Stats.ATTACK,
            "cobblemon:power_belt", Stats.DEFENCE,
            "cobblemon:power_lens", Stats.SPECIAL_ATTACK,
            "cobblemon:power_band", Stats.SPECIAL_DEFENCE,
            "cobblemon:power_anklet", Stats.SPEED
    );
    private static final Set<String> REGIONAL_ASPECTS = Set.of(
            "alolan", "galarian", "hisuian", "paldean"
    );
    private static final Set<String> FORM_INHERITANCE_EXCEPTIONS = Set.of(
            "rotom", "scatterbug", "furfrou", "sinistea", "poltchageist"
    );
    private static final Set<String> NON_BREEDABLE_FORM_TOKENS = Set.of(
            "gmax", "gigantamax", "mega", "alpha", "jumbo", "mini"
    );

    private PokemonBreedingRules() {}

    public record Compatibility(boolean compatible, String reason) {}
    public record Result(Pokemon hatchling, int requiredSteps, Pokemon breedingParent, Pokemon otherParent,
                         boolean mysteryEgg, BreedingProfessionService.RarityTier rarity) {}

    public static Compatibility compatibility(Pokemon a, Pokemon b) {
        if (a == null || b == null) return new Compatibility(false, "Choose two occupied party slots.");
        if (a == b || a.getUuid().equals(b.getUuid())) return new Compatibility(false, "Choose two different Pokémon.");
        if (BreedingEggData.isEgg(a) || BreedingEggData.isEgg(b)) return new Compatibility(false, "Eggs cannot be used as parents.");
        if (!PokemonBreedability.isBreedable(a) || !PokemonBreedability.isBreedable(b)) return new Compatibility(false, "At least one parent is permanently unbreedable.");

        Set<EggGroup> aGroups = new HashSet<>(a.getForm().getEggGroups());
        Set<EggGroup> bGroups = new HashSet<>(b.getForm().getEggGroups());
        if (aGroups.contains(EggGroup.UNDISCOVERED) || bGroups.contains(EggGroup.UNDISCOVERED)) {
            return new Compatibility(false, "At least one parent is in the Undiscovered Egg Group.");
        }
        boolean aDitto = aGroups.contains(EggGroup.DITTO);
        boolean bDitto = bGroups.contains(EggGroup.DITTO);
        if (aDitto && bDitto) {
            return BreedingConfig.get().dittoPairEnabled
                    ? new Compatibility(true, "Compatible. The offspring will remain a mystery until it hatches.")
                    : new Compatibility(false, "Two Ditto cannot currently produce an Egg.");
        }
        if (aDitto || bDitto) return new Compatibility(true, "Compatible through Ditto.");

        Gender ag = a.getGender();
        Gender bg = b.getGender();
        if (ag == Gender.GENDERLESS || bg == Gender.GENDERLESS) {
            return new Compatibility(false, "Genderless Pokémon require Ditto.");
        }
        if (ag == bg) return new Compatibility(false, "Non-Ditto parents must be opposite genders.");
        if (Collections.disjoint(aGroups, bGroups)) {
            return new Compatibility(false, "The parents do not share an Egg Group.");
        }
        return new Compatibility(true, "The parents are compatible.");
    }

    public static Result createEgg(ServerPlayer player, Pokemon a, Pokemon b) {
        Compatibility compatibility = compatibility(a, b);
        if (!compatibility.compatible()) throw new IllegalArgumentException(compatibility.reason());

        boolean dittoPair = isDitto(a) && isDitto(b);
        Pokemon breedingParent = breedingParent(a, b);
        Pokemon otherParent = breedingParent == a ? b : a;
        BreedingProfessionService.DittoSelection dittoSelection = dittoPair
                ? BreedingProfessionService.selectDittoOffspring(player)
                : null;
        Species offspringSpecies = dittoPair
                ? dittoSelection.species()
                : resolveOffspringSpecies(breedingParent, otherParent);
        Pokemon child = PokemonProperties.Companion
                .parse("species=\"" + offspringSpecies.getResourceIdentifier() + "\" level=1")
                .create();

        if (!dittoPair) inheritForm(child, breedingParent, otherParent);
        inheritNature(child, a, b);
        inheritIvs(child, a, b);
        BreedingProfessionService.applyExtraPerfectIv(player, child);
        if (dittoPair) BreedingProfessionService.rollDittoAbility(player, child);
        else inheritAbility(player, child, breedingParent);
        if (dittoPair) child.setCaughtBall(PokeBalls.getPokeBall());
        else inheritBall(child, a, b, breedingParent);
        inheritMoves(child, a, b, breedingParent, otherParent);
        rollShiny(player, child, a, b);

        child.setLevel(1);
        child.setOriginalTrainer(player.getUUID());
        child.setOriginalTrainerName(player.getGameProfile().getName());
        child.setTradeable(true);
        BreedingOriginLanguageTracker.tagSilently(child, player);
        child.heal();

        int cycles = Math.max(1, child.getSpecies().getEggCycles());
        int steps = Math.max(1, cycles * BreedingConfig.get().stepsPerEggCycle);
        BreedingProfessionService.RarityTier rarity = dittoSelection == null
                ? BreedingProfessionService.rarityFor(child)
                : dittoSelection.rarity();
        return new Result(child, steps, breedingParent, otherParent, dittoPair, rarity);
    }

    private static Pokemon breedingParent(Pokemon a, Pokemon b) {
        boolean aDitto = isDitto(a);
        boolean bDitto = isDitto(b);
        if (aDitto && bDitto) return a;
        if (aDitto) return b;
        if (bDitto) return a;
        return a.getGender() == Gender.FEMALE ? a : b;
    }

    private static Species resolveOffspringSpecies(Pokemon breedingParent, Pokemon otherParent) {
        String parentPath = path(breedingParent.getSpecies());
        String otherPath = path(otherParent.getSpecies());

        if ((parentPath.equals("manaphy") && isDitto(otherParent))
                || (otherPath.equals("manaphy") && isDitto(breedingParent))) {
            Species phione = species("cobblemon:phione");
            if (phione != null) return phione;
        }
        if (parentPath.startsWith("nidoran") || parentPath.equals("nidorina") || parentPath.equals("nidoqueen")
                || parentPath.equals("nidorino") || parentPath.equals("nidoking")) {
            Species special = species(RANDOM.nextBoolean() ? "cobblemon:nidoranf" : "cobblemon:nidoranm");
            if (special != null) return special;
        }
        if (parentPath.equals("volbeat") || parentPath.equals("illumise")) {
            Species special = species(RANDOM.nextBoolean() ? "cobblemon:volbeat" : "cobblemon:illumise");
            if (special != null) return special;
        }

        Species current = breedingParent.getSpecies();
        Set<ResourceLocation> visited = new HashSet<>();
        while (current != null && current.getPreEvolution() != null && visited.add(current.getResourceIdentifier())) {
            Species previous = current.getPreEvolution().getSpecies();
            if (previous == null || previous == current) break;
            current = previous;
        }
        return current == null ? breedingParent.getSpecies() : current;
    }

    /**
     * Most inheritable forms follow the mother/non-Ditto parent. Regional forms instead
     * require an Everstone on a parent from the same evolutionary line; if both eligible
     * parents hold one, the mother/non-Ditto parent takes priority.
     */
    private static void inheritForm(Pokemon child, Pokemon breedingParent, Pokemon otherParent) {
        boolean breedingParentEligible = holdsEverstone(breedingParent)
                && sameEvolutionaryLine(breedingParent.getSpecies(), child.getSpecies());
        boolean otherParentEligible = holdsEverstone(otherParent)
                && sameEvolutionaryLine(otherParent.getSpecies(), child.getSpecies());
        boolean regionalSituation = (breedingParentEligible && isRegionalForm(breedingParent))
                || (otherParentEligible && isRegionalForm(otherParent));

        if (regionalSituation) {
            Pokemon source = breedingParentEligible ? breedingParent : otherParent;
            applyForm(child, source, true);
            return;
        }

        String childPath = path(child.getSpecies());
        if (FORM_INHERITANCE_EXCEPTIONS.contains(childPath)) return;
        if (!sameEvolutionaryLine(breedingParent.getSpecies(), child.getSpecies())) return;
        applyForm(child, breedingParent, false);
    }

    private static void applyForm(Pokemon child, Pokemon source, boolean allowStandardForm) {
        Set<String> aspects = sanitizedFormAspects(source);
        if (aspects.stream().map(PokemonBreedingRules::normalize).anyMatch(NON_BREEDABLE_FORM_TOKENS::contains)) {
            return;
        }

        if (aspects.isEmpty()) {
            if (allowStandardForm) {
                child.setForcedAspects(Set.of());
                child.updateAspects();
                child.updateForm();
            }
            return;
        }

        FormData matchingForm = child.getSpecies().getForm(aspects);
        if (matchingForm == child.getSpecies().getStandardForm()) return;
        child.setForcedAspects(Set.copyOf(matchingForm.getAspects()));
        child.updateAspects();
        child.updateForm();
    }

    private static Set<String> sanitizedFormAspects(Pokemon pokemon) {
        try {
            return Set.copyOf(pokemon.getForm().getAspects());
        } catch (Throwable ignored) {
            return Set.of();
        }
    }

    private static boolean isRegionalForm(Pokemon pokemon) {
        return sanitizedFormAspects(pokemon).stream()
                .map(PokemonBreedingRules::normalize)
                .anyMatch(REGIONAL_ASPECTS::contains);
    }

    private static boolean sameEvolutionaryLine(Species a, Species b) {
        Species rootA = rootSpecies(a);
        Species rootB = rootSpecies(b);
        return rootA != null && rootB != null
                && rootA.getResourceIdentifier().equals(rootB.getResourceIdentifier());
    }

    private static Species rootSpecies(Species species) {
        Species current = species;
        Set<ResourceLocation> visited = new HashSet<>();
        while (current != null && current.getPreEvolution() != null && visited.add(current.getResourceIdentifier())) {
            Species previous = current.getPreEvolution().getSpecies();
            if (previous == null || previous == current) break;
            current = previous;
        }
        return current;
    }

    /** Uses the underlying Nature, not the Mint-modified effective Nature. */
    private static void inheritNature(Pokemon child, Pokemon a, Pokemon b) {
        List<Pokemon> everstoneParents = new ArrayList<>();
        if (holdsEverstone(a)) everstoneParents.add(a);
        if (holdsEverstone(b)) everstoneParents.add(b);
        if (everstoneParents.isEmpty()) {
            child.setNature(Natures.getRandomNature());
        } else {
            child.setNature(everstoneParents.get(RANDOM.nextInt(everstoneParents.size())).getNature());
        }
    }

    /** Uses natural IVs only; Hyper Training values are intentionally never inherited. */
    private static void inheritIvs(Pokemon child, Pokemon a, Pokemon b) {
        for (Stat stat : PERMANENT_STATS) child.getIvs().set(stat, RANDOM.nextInt(32));

        int inheritedCount = holdsDestinyKnot(a) || holdsDestinyKnot(b) ? 5 : 3;
        Set<Stat> inherited = new HashSet<>();

        List<Map.Entry<Pokemon, Stat>> forced = new ArrayList<>();
        Stat aPower = POWER_ITEMS.get(heldId(a));
        Stat bPower = POWER_ITEMS.get(heldId(b));
        if (aPower != null) forced.add(Map.entry(a, aPower));
        if (bPower != null) forced.add(Map.entry(b, bPower));
        if (!forced.isEmpty()) {
            Map.Entry<Pokemon, Stat> chosen = forced.get(RANDOM.nextInt(forced.size()));
            child.getIvs().set(chosen.getValue(), chosen.getKey().getIvs().getOrDefault(chosen.getValue()));
            inherited.add(chosen.getValue());
        }

        List<Stat> shuffled = new ArrayList<>(PERMANENT_STATS);
        Collections.shuffle(shuffled, RANDOM);
        for (Stat stat : shuffled) {
            if (inherited.size() >= inheritedCount) break;
            if (!inherited.add(stat)) continue;
            Pokemon source = RANDOM.nextBoolean() ? a : b;
            child.getIvs().set(stat, source.getIvs().getOrDefault(stat));
        }
    }

    /**
     * Modern games inherit an ability slot rather than an ability name. This matters when
     * an evolved parent and its offspring use different ability names in the same slot.
     */
    private static void inheritAbility(ServerPlayer player, Pokemon child, Pokemon breedingParent) {
        List<PotentialAbility> childCommon = new ArrayList<>();
        List<PotentialAbility> childHidden = new ArrayList<>();
        for (PotentialAbility potential : child.getForm().getAbilities()) {
            if (potential instanceof HiddenAbility) childHidden.add(potential);
            else childCommon.add(potential);
        }
        if (childCommon.isEmpty() && childHidden.isEmpty()) return;

        PotentialAbility parentPotential = currentPotential(breedingParent);
        boolean parentHidden = parentPotential instanceof HiddenAbility;
        PotentialAbility chosen;

        if (parentHidden) {
            if (!childHidden.isEmpty() && RANDOM.nextDouble() < Math.min(1.0D, 0.60D + BreedingProfessionService.hiddenAbilityBonus(player, child))) {
                int hiddenIndex = Math.max(0, breedingParent.getAbility().getIndex());
                chosen = childHidden.get(Math.min(hiddenIndex, childHidden.size() - 1));
            } else {
                chosen = random(childCommon, childHidden.isEmpty() ? null : childHidden.get(0));
            }
        } else {
            int parentSlot = regularAbilitySlot(breedingParent, parentPotential);
            PotentialAbility sameSlot = childCommon.isEmpty()
                    ? null
                    : childCommon.get(Math.min(Math.max(0, parentSlot), childCommon.size() - 1));
            if (sameSlot != null && RANDOM.nextDouble() < 0.80D) {
                chosen = sameSlot;
            } else {
                chosen = randomExcluding(childCommon, sameSlot);
                if (chosen == null) chosen = sameSlot != null ? sameSlot : random(childHidden, null);
            }
        }

        if (chosen != null) {
            child.updateAbility(chosen.getTemplate().create(false, chosen.getPriority()));
        }
    }

    private static PotentialAbility currentPotential(Pokemon pokemon) {
        try {
            Priority priority = pokemon.getAbility().getPriority();
            List<PotentialAbility> samePriority = pokemon.getForm().getAbilities().getMapping().get(priority);
            int index = pokemon.getAbility().getIndex();
            if (samePriority != null && index >= 0 && index < samePriority.size()) {
                PotentialAbility indexed = samePriority.get(index);
                if (indexed.getTemplate().equals(pokemon.getAbility().getTemplate())) return indexed;
            }
            for (PotentialAbility potential : pokemon.getForm().getAbilities()) {
                if (potential.getTemplate().equals(pokemon.getAbility().getTemplate())) return potential;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int regularAbilitySlot(Pokemon pokemon, PotentialAbility current) {
        if (current != null && !(current instanceof HiddenAbility)) {
            List<PotentialAbility> common = new ArrayList<>();
            for (PotentialAbility potential : pokemon.getForm().getAbilities()) {
                if (!(potential instanceof HiddenAbility)) common.add(potential);
            }
            int index = common.indexOf(current);
            if (index >= 0) return index;
        }
        return Math.max(0, pokemon.getAbility().getIndex());
    }

    private static PotentialAbility random(List<PotentialAbility> preferred, PotentialAbility fallback) {
        return preferred.isEmpty() ? fallback : preferred.get(RANDOM.nextInt(preferred.size()));
    }

    private static PotentialAbility randomExcluding(List<PotentialAbility> values, PotentialAbility excluded) {
        List<PotentialAbility> choices = values.stream().filter(value -> value != excluded).toList();
        if (!choices.isEmpty()) return choices.get(RANDOM.nextInt(choices.size()));
        return excluded;
    }

    private static void inheritBall(Pokemon child, Pokemon a, Pokemon b, Pokemon breedingParent) {
        Pokemon source;
        if (isDitto(a)) source = b;
        else if (isDitto(b)) source = a;
        else if (sameSpecies(a, b)) source = RANDOM.nextBoolean() ? a : b;
        else source = breedingParent;

        String ball = source.getCaughtBall().getName().toString();
        if (ball.endsWith(":master_ball") || ball.endsWith(":cherish_ball") || ball.endsWith(":strange_ball")) {
            child.setCaughtBall(PokeBalls.getPokeBall());
        } else {
            child.setCaughtBall(source.getCaughtBall());
        }
    }

    private static void inheritMoves(Pokemon child,
                                     Pokemon a,
                                     Pokemon b,
                                     Pokemon breedingParent,
                                     Pokemon otherParent) {
        Set<String> parentAMoves = moveNames(a);
        Set<String> parentBMoves = moveNames(b);
        Set<String> breedingParentMoves = moveNames(breedingParent);
        Set<String> otherParentMoves = moveNames(otherParent);

        LinkedHashMap<String, MoveTemplate> ordered = new LinkedHashMap<>();
        child.getMoveSet().forEach(move -> {
            if (move != null) putWithPriority(ordered, move.getTemplate());
        });

        // A level-up move is inherited when both parents currently know it.
        child.getForm().getMoves().getLevelUpMoves().values().stream()
                .flatMap(List::stream)
                .forEach(levelMove -> {
                    String id = normalize(levelMove.getName());
                    if (parentAMoves.contains(id) && parentBMoves.contains(id)) {
                        putWithPriority(ordered, levelMove);
                    }
                });

        // Add the other parent's Egg Moves first, then the mother/non-Ditto parent's.
        // Later entries have priority when the hatchling would otherwise know over four moves.
        for (MoveTemplate eggMove : child.getForm().getMoves().getEggMoves()) {
            if (otherParentMoves.contains(normalize(eggMove.getName()))) putWithPriority(ordered, eggMove);
        }
        for (MoveTemplate eggMove : child.getForm().getMoves().getEggMoves()) {
            if (breedingParentMoves.contains(normalize(eggMove.getName()))) putWithPriority(ordered, eggMove);
        }

        if (path(child.getSpecies()).equals("pichu") && (holdsLightBall(a) || holdsLightBall(b))) {
            MoveTemplate voltTackle = com.cobblemon.mod.common.api.moves.Moves.getByName("volttackle");
            if (voltTackle != null) putWithPriority(ordered, voltTackle);
        }

        List<MoveTemplate> selected = new ArrayList<>(ordered.values());
        if (selected.size() > 4) selected = selected.subList(selected.size() - 4, selected.size());
        child.getMoveSet().clear();
        for (MoveTemplate move : selected) child.getMoveSet().add(move.create());
        if (child.getMoveSet().getMoves().isEmpty()) child.initializeMoveset(true);
    }

    private static Set<String> moveNames(Pokemon pokemon) {
        Set<String> moves = new HashSet<>();
        pokemon.getMoveSet().forEach(move -> {
            if (move != null) moves.add(normalize(move.getName()));
        });
        return moves;
    }

    private static void putWithPriority(LinkedHashMap<String, MoveTemplate> ordered, MoveTemplate move) {
        String id = normalize(move.getName());
        ordered.remove(id);
        ordered.put(id, move);
    }

    private static void rollShiny(ServerPlayer player, Pokemon child, Pokemon a, Pokemon b) {
        BreedingConfig.Values config = BreedingConfig.get();
        int rolls = 1;
        String aLanguage = languageTag(a);
        String bLanguage = languageTag(b);
        if (!aLanguage.isBlank() && !bLanguage.isBlank() && !aLanguage.equalsIgnoreCase(bLanguage)) {
            rolls += config.masudaExtraRolls;
        }
        if (player != null && !config.shinyCharmPermission.isBlank()
                && com.champutils.permissions.LuckPermsHook.hasPermission(player, config.shinyCharmPermission)) {
            rolls += config.shinyCharmExtraRolls;
        }
        boolean shiny = false;
        for (int i = 0; i < rolls && !shiny; i++) {
            shiny = RANDOM.nextInt(Math.max(1, config.shinyDenominator)) == 0;
        }
        if (!shiny) {
            shiny = BreedingProfessionService.rollShinyProfessionBonus(player, child, Math.max(1, config.shinyDenominator), rolls);
        }
        child.setShiny(shiny);
    }

    private static String languageTag(Pokemon pokemon) {
        return BreedingOriginLanguageTracker.get(pokemon);
    }

    private static boolean holdsDestinyKnot(Pokemon pokemon) {
        return heldId(pokemon).equals("cobblemon:destiny_knot");
    }

    private static boolean holdsEverstone(Pokemon pokemon) {
        return heldId(pokemon).equals("cobblemon:everstone");
    }

    private static boolean holdsLightBall(Pokemon pokemon) {
        return heldId(pokemon).equals("cobblemon:light_ball");
    }

    private static String heldId(Pokemon pokemon) {
        try {
            ItemStack held = pokemon.heldItem();
            if (held == null || held.isEmpty()) return "";
            return BuiltInRegistries.ITEM.getKey(held.getItem()).toString().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean sameSpecies(Pokemon a, Pokemon b) {
        return a.getSpecies().getResourceIdentifier().equals(b.getSpecies().getResourceIdentifier());
    }

    private static boolean isDitto(Pokemon pokemon) {
        return pokemon.getForm().getEggGroups().contains(EggGroup.DITTO);
    }

    private static Species species(String id) {
        try {
            return com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.parse(id));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String path(Species species) {
        try {
            return species.getResourceIdentifier().getPath().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
