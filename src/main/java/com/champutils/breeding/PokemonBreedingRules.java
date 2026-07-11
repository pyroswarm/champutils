package com.champutils.breeding;

import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
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

    private PokemonBreedingRules() {}

    public record Compatibility(boolean compatible, String reason) {}
    public record Result(Pokemon hatchling, int requiredSteps, Pokemon breedingParent, Pokemon otherParent) {}

    public static Compatibility compatibility(Pokemon a, Pokemon b) {
        if (a == null || b == null) return new Compatibility(false, "Choose two occupied party slots.");
        if (a == b || a.getUuid().equals(b.getUuid())) return new Compatibility(false, "Choose two different Pokémon.");
        if (BreedingEggData.isEgg(a) || BreedingEggData.isEgg(b)) return new Compatibility(false, "Eggs cannot be used as parents.");

        Set<EggGroup> aGroups = new HashSet<>(a.getForm().getEggGroups());
        Set<EggGroup> bGroups = new HashSet<>(b.getForm().getEggGroups());
        if (aGroups.contains(EggGroup.UNDISCOVERED) || bGroups.contains(EggGroup.UNDISCOVERED)) {
            return new Compatibility(false, "At least one parent is in the Undiscovered Egg Group.");
        }
        boolean aDitto = aGroups.contains(EggGroup.DITTO);
        boolean bDitto = bGroups.contains(EggGroup.DITTO);
        if (aDitto && bDitto) return new Compatibility(false, "Two Ditto cannot produce an Egg.");
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

        Pokemon breedingParent = breedingParent(a, b);
        Pokemon otherParent = breedingParent == a ? b : a;
        Species offspringSpecies = resolveOffspringSpecies(breedingParent, otherParent);
        Pokemon child = PokemonProperties.Companion
                .parse("species=\"" + offspringSpecies.getResourceIdentifier() + "\" level=1")
                .create();

        inheritRegionalForm(child, breedingParent, otherParent);
        inheritNature(child, a, b);
        inheritIvs(child, a, b);
        inheritAbility(child, breedingParent);
        inheritBall(child, a, b, breedingParent);
        inheritMoves(child, a, b);
        rollShiny(player, child, a, b);

        child.setLevel(1);
        child.setOriginalTrainer(player.getUUID());
        child.setOriginalTrainerName(player.getGameProfile().getName());
        child.setTradeable(true);
        BreedingOriginLanguageTracker.tagSilently(child, player);
        child.heal();

        int cycles = Math.max(1, child.getSpecies().getEggCycles());
        int steps = Math.max(1, cycles * BreedingConfig.get().stepsPerEggCycle);
        return new Result(child, steps, breedingParent, otherParent);
    }

    private static Pokemon breedingParent(Pokemon a, Pokemon b) {
        boolean aDitto = a.getForm().getEggGroups().contains(EggGroup.DITTO);
        boolean bDitto = b.getForm().getEggGroups().contains(EggGroup.DITTO);
        if (aDitto) return b;
        if (bDitto) return a;
        return a.getGender() == Gender.FEMALE ? a : b;
    }

    private static Species resolveOffspringSpecies(Pokemon breedingParent, Pokemon otherParent) {
        String parentPath = path(breedingParent.getSpecies());
        String otherPath = path(otherParent.getSpecies());

        if ((parentPath.equals("manaphy") && isDitto(otherParent)) || (otherPath.equals("manaphy") && isDitto(breedingParent))) {
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

    private static void inheritRegionalForm(Pokemon child, Pokemon breedingParent, Pokemon otherParent) {
        List<Pokemon> eligible = new ArrayList<>();
        if (heldId(breedingParent).equals("cobblemon:everstone")) eligible.add(breedingParent);
        // When both parents are the same species, either parent's form can be inherited.
        // Ditto and unrelated male parents must never imprint their aspects onto the child.
        if (sameSpecies(breedingParent, otherParent)
                && heldId(otherParent).equals("cobblemon:everstone")) {
            eligible.add(otherParent);
        }
        if (eligible.isEmpty()) return;
        Pokemon source = eligible.get(RANDOM.nextInt(eligible.size()));
        try {
            if (!source.getForcedAspects().isEmpty()) {
                child.setForcedAspects(Set.copyOf(source.getForcedAspects()));
                child.updateAspects();
                child.updateForm();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void inheritNature(Pokemon child, Pokemon a, Pokemon b) {
        List<Pokemon> everstoneParents = new ArrayList<>();
        if (heldId(a).equals("cobblemon:everstone")) everstoneParents.add(a);
        if (heldId(b).equals("cobblemon:everstone")) everstoneParents.add(b);
        if (everstoneParents.isEmpty()) {
            child.setNature(Natures.getRandomNature());
        } else {
            child.setNature(everstoneParents.get(RANDOM.nextInt(everstoneParents.size())).getNature());
        }
    }

    private static void inheritIvs(Pokemon child, Pokemon a, Pokemon b) {
        for (Stat stat : PERMANENT_STATS) child.getIvs().set(stat, RANDOM.nextInt(32));

        int inheritedCount = heldId(a).equals("cobblemon:destiny_knot") || heldId(b).equals("cobblemon:destiny_knot") ? 5 : 3;
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

    private static void inheritAbility(Pokemon child, Pokemon breedingParent) {
        List<PotentialAbility> common = new ArrayList<>();
        List<PotentialAbility> hidden = new ArrayList<>();
        for (PotentialAbility potential : child.getForm().getAbilities()) {
            if (potential instanceof HiddenAbility) hidden.add(potential);
            else common.add(potential);
        }
        if (common.isEmpty() && hidden.isEmpty()) return;

        String parentAbility = normalize(breedingParent.getAbility().getName());
        PotentialAbility matchingHidden = hidden.stream()
                .filter(value -> normalize(value.getTemplate().getName()).equals(parentAbility))
                .findFirst().orElse(null);
        PotentialAbility matchingCommon = common.stream()
                .filter(value -> normalize(value.getTemplate().getName()).equals(parentAbility))
                .findFirst().orElse(null);

        PotentialAbility chosen;
        if (matchingHidden != null) {
            chosen = RANDOM.nextDouble() < 0.60D ? matchingHidden : random(common, matchingHidden);
        } else if (matchingCommon != null) {
            chosen = RANDOM.nextDouble() < 0.80D ? matchingCommon : randomExcluding(common, matchingCommon);
        } else {
            chosen = random(common, hidden.isEmpty() ? null : hidden.get(0));
        }
        if (chosen != null) {
            child.updateAbility(chosen.getTemplate().create(false, chosen.getPriority()));
        }
    }

    private static PotentialAbility random(List<PotentialAbility> preferred, PotentialAbility fallback) {
        return preferred.isEmpty() ? fallback : preferred.get(RANDOM.nextInt(preferred.size()));
    }

    private static PotentialAbility randomExcluding(List<PotentialAbility> values, PotentialAbility excluded) {
        List<PotentialAbility> choices = values.stream().filter(value -> value != excluded).toList();
        return choices.isEmpty() ? excluded : choices.get(RANDOM.nextInt(choices.size()));
    }

    private static void inheritBall(Pokemon child, Pokemon a, Pokemon b, Pokemon breedingParent) {
        Pokemon source;
        if (isDitto(a)) source = b;
        else if (isDitto(b)) source = a;
        else if (sameSpecies(a, b)) source = RANDOM.nextBoolean() ? a : b;
        else source = breedingParent;

        String ball = source.getCaughtBall().getName().toString();
        if (ball.endsWith(":master_ball") || ball.endsWith(":cherish_ball")) {
            child.setCaughtBall(PokeBalls.getPokeBall());
        } else {
            child.setCaughtBall(source.getCaughtBall());
        }
    }

    private static void inheritMoves(Pokemon child, Pokemon a, Pokemon b) {
        Set<String> parentAMoves = new HashSet<>();
        Set<String> parentBMoves = new HashSet<>();
        a.getMoveSet().forEach(move -> { if (move != null) parentAMoves.add(normalize(move.getName())); });
        b.getMoveSet().forEach(move -> { if (move != null) parentBMoves.add(normalize(move.getName())); });
        Set<String> eitherParentMoves = new HashSet<>(parentAMoves);
        eitherParentMoves.addAll(parentBMoves);

        LinkedHashMap<String, MoveTemplate> ordered = new LinkedHashMap<>();
        child.getMoveSet().forEach(move -> {
            if (move != null) ordered.put(normalize(move.getName()), move.getTemplate());
        });

        // Main-series inheritance: a move in the offspring's level-up learnset can be
        // inherited when both parents currently know it, regardless of the child's level.
        child.getForm().getMoves().getLevelUpMoves().values().stream()
                .flatMap(List::stream)
                .forEach(levelMove -> {
                    String id = normalize(levelMove.getName());
                    if (parentAMoves.contains(id) && parentBMoves.contains(id)) ordered.put(id, levelMove);
                });

        // Current-generation egg moves may be passed by either parent.
        for (MoveTemplate eggMove : child.getForm().getMoves().getEggMoves()) {
            if (eitherParentMoves.contains(normalize(eggMove.getName()))) ordered.put(normalize(eggMove.getName()), eggMove);
        }

        String childPath = path(child.getSpecies());
        if (childPath.equals("pichu") && (holdsLightBall(a) || holdsLightBall(b))) {
            MoveTemplate voltTackle = com.cobblemon.mod.common.api.moves.Moves.getByName("volttackle");
            if (voltTackle != null) ordered.put("volttackle", voltTackle);
        }

        List<MoveTemplate> selected = new ArrayList<>(ordered.values());
        if (selected.size() > 4) selected = selected.subList(selected.size() - 4, selected.size());
        child.getMoveSet().clear();
        for (MoveTemplate move : selected) child.getMoveSet().add(move.create());
        if (child.getMoveSet().getMoves().isEmpty()) child.initializeMoveset(true);
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
        child.setShiny(shiny);
    }

    private static String languageTag(Pokemon pokemon) {
        return BreedingOriginLanguageTracker.get(pokemon);
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
        try { return species.getResourceIdentifier().getPath().toLowerCase(Locale.ROOT); }
        catch (Throwable ignored) { return ""; }
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
