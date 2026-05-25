package com.champutils.validation;

import com.champutils.config.Format;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server-side clause validation driven by rules.json -> formats.<format>.battle_rules.
 *
 * Cobblemon/Showdown receives these same rule names through PvPBattleFormatRules, but these
 * checks give ChampUtils a stable, readable rejection before the battle starts and cover custom
 * clauses that the embedded Showdown format may not enforce for our generated formats.
 */
public final class BattleClauseValidator {

    private static final Map<String, ClauseDefinition> CLAUSES = new HashMap<>();

    static {
        register(new ClauseDefinition(
                "Moody Clause",
                Set.of(),
                Set.of("moody"),
                Set.of()
        ));

        register(new ClauseDefinition(
                "Baton Pass Clause",
                Set.of("batonpass", "baton_pass", "baton-pass", "Baton Pass"),
                Set.of(),
                Set.of()
        ));

        register(new ClauseDefinition(
                "Swagger Clause",
                Set.of("swagger"),
                Set.of(),
                Set.of()
        ));

        register(new ClauseDefinition(
                "OHKO Clause",
                Set.of("fissure", "guillotine", "horndrill", "horn_drill", "horn-drill", "Horn Drill", "sheercold", "sheer_cold", "sheer-cold", "Sheer Cold"),
                Set.of(),
                Set.of()
        ));

        register(new ClauseDefinition(
                "Evasion Moves Clause",
                Set.of("doubleteam", "double_team", "double-team", "Double Team", "minimize"),
                Set.of(),
                Set.of()
        ));

        // Keep Acupressure as a separate practical ranked safety ban if the format opts into it with banned_moves.
        // Showdown's Evasion Moves Clause normally targets direct evasion moves, not every move that can raise evasion.
    }

    private BattleClauseValidator() {
    }

    public static String validate(Pokemon pokemon, Format format) {
        if (pokemon == null || format == null || format.battle_rules == null || format.battle_rules.isEmpty()) {
            return null;
        }

        Set<ClauseDefinition> activeClauses = activeClauses(format.battle_rules);
        if (activeClauses.isEmpty()) {
            return null;
        }

        for (ClauseDefinition clause : activeClauses) {
            String abilityViolation = validateAbility(pokemon, clause);
            if (abilityViolation != null) {
                return abilityViolation;
            }

            String moveViolation = validateMoves(pokemon, clause);
            if (moveViolation != null) {
                return moveViolation;
            }
        }

        return null;
    }

    public static String validatePartyWide(List<Pokemon> party, Format format) {
        if (party == null || format == null || format.battle_rules == null || format.battle_rules.isEmpty()) {
            return null;
        }

        if (!hasClause(format.battle_rules, "Species Clause")) {
            return null;
        }

        Set<String> seenSpecies = new HashSet<>();
        for (Pokemon pokemon : party) {
            if (pokemon == null || pokemon.getSpecies() == null || pokemon.getSpecies().getResourceIdentifier() == null) {
                continue;
            }

            String species = normalizeId(pokemon.getSpecies().getResourceIdentifier().getPath());
            if (!seenSpecies.add(species)) {
                return "Species Clause: duplicate Pokémon species are not allowed (" + pokemon.getSpecies().getName() + ").";
            }
        }

        return null;
    }

    private static String validateAbility(Pokemon pokemon, ClauseDefinition clause) {
        if (pokemon.getAbility() == null || clause.bannedAbilities().isEmpty()) {
            return null;
        }

        String ability = normalizeId(pokemon.getAbility().getName());
        if (clause.bannedAbilities().contains(ability)) {
            return clause.displayName() + ": " + pokemon.getAbility().getName() + " is banned.";
        }

        return null;
    }

    private static String validateMoves(Pokemon pokemon, ClauseDefinition clause) {
        if (pokemon.getMoveSet() == null || pokemon.getMoveSet().getMoves() == null || clause.bannedMoves().isEmpty()) {
            return null;
        }

        for (var move : pokemon.getMoveSet().getMoves()) {
            if (move == null || move.getTemplate() == null) {
                continue;
            }

            String moveId = normalizeId(move.getTemplate().getName());
            if (clause.bannedMoves().contains(moveId)) {
                return clause.displayName() + ": " + move.getTemplate().getName() + " is banned.";
            }
        }

        return null;
    }

    private static Set<ClauseDefinition> activeClauses(List<String> battleRules) {
        Set<ClauseDefinition> active = new HashSet<>();
        for (String rule : battleRules) {
            ClauseDefinition clause = CLAUSES.get(normalizeRule(rule));
            if (clause != null) {
                active.add(clause);
            }
        }
        return active;
    }

    private static boolean hasClause(List<String> battleRules, String clauseName) {
        String expected = normalizeRule(clauseName);
        for (String rule : battleRules) {
            if (expected.equals(normalizeRule(rule))) {
                return true;
            }
        }
        return false;
    }

    private static void register(ClauseDefinition definition) {
        CLAUSES.put(normalizeRule(definition.displayName()), definition);
    }

    public static String normalizeId(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        int namespace = trimmed.indexOf(':');
        if (namespace >= 0 && namespace + 1 < trimmed.length()) {
            trimmed = trimmed.substring(namespace + 1);
        }

        return trimmed
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace("'", "")
                .replace(".", "");
    }

    private static String normalizeRule(String value) {
        return normalizeId(value).replace("clausemod", "clause");
    }

    private record ClauseDefinition(
            String displayName,
            Set<String> rawBannedMoves,
            Set<String> rawBannedAbilities,
            Set<String> rawBannedItems
    ) {
        private Set<String> bannedMoves() {
            return normalizeSet(rawBannedMoves);
        }

        private Set<String> bannedAbilities() {
            return normalizeSet(rawBannedAbilities);
        }

        @SuppressWarnings("unused")
        private Set<String> bannedItems() {
            return normalizeSet(rawBannedItems);
        }

        private static Set<String> normalizeSet(Set<String> values) {
            Set<String> normalized = new HashSet<>();
            for (String value : values) {
                normalized.add(normalizeId(value));
            }
            return normalized;
        }
    }
}
