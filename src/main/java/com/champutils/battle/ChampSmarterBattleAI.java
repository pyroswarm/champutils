package com.champutils.battle;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.DefaultActionResponse;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.MoveTarget;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.battles.Targetable;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Radical-Red-style scoring wrapper for Cobblemon battles.
 *
 * What this deliberately does NOT do anymore:
 * - no same-move spam penalty that can force a worse move
 * - no post-decision "upgrade" override
 * - no move choice that ignores known absorb/immune abilities
 * - no explicit target on spread/self/field moves, which fixes Earthquake/Surf/Rock Slide target errors
 *
 * StrongBattleAI is kept as the emergency fallback and for force-switch fallback behavior.
 */
public final class ChampSmarterBattleAI implements BattleAI {
    private static final Set<String> PROTECT_MOVES = Set.of("protect", "detect", "spikyshield", "kingsshield", "banefulbunker", "silktrap", "burningbulwark", "obstruct");
    private static final Set<String> CORE_SETUP_MOVES = Set.of("dragondance", "swordsdance", "calmmind", "quiverdance");
    private static final Set<String> OTHER_SETUP_MOVES = Set.of("nastyplot", "shellsmash", "bulkup", "agility", "rockpolish", "growth", "workup", "irondefense", "cosmicpower", "coil");
    private static final Set<String> RECOVERY_MOVES = Set.of("recover", "roost", "slackoff", "softboiled", "milkdrink", "healorder", "shoreup", "synthesis", "morningsun", "moonlight", "strengthsap");
    private static final Set<String> HAZARD_MOVES = Set.of("stealthrock", "spikes", "toxicspikes", "stickyweb");
    private static final Set<String> HAZARD_CLEAR_MOVES = Set.of("rapidspin", "defog", "tidyup");
    private static final Set<String> STATUS_MOVES = Set.of("willowisp", "thunderwave", "toxic", "poisongas", "poisonpowder", "stunspore", "glare", "nuzzle", "hypnosis", "spore", "sleeppowder", "yawn", "confuseray", "leechseed");
    private static final Set<String> CHIP_STATUSES = Set.of("brn", "burn", "psn", "tox", "poison", "badlypoisoned", "poisonbadly");

    private final int skill;
    private final boolean competitiveLayer;
    private final BattleAI fallback;
    private final Map<UUID, Memory> memoryByPokemon = new HashMap<>();

    public ChampSmarterBattleAI(int skill, boolean competitiveLayer, boolean ignoredAntiSpamLayer) {
        this.skill = Math.max(0, Math.min(5, skill));
        this.competitiveLayer = competitiveLayer;
        this.fallback = new StrongBattleAI(this.skill);
    }

    @Override
    public ShowdownActionResponse choose(ActiveBattlePokemon active, PokemonBattle battle, BattleSide aiSide, ShowdownMoveset moveset, boolean forceSwitch) {
        if (active == null) return new DefaultActionResponse();

        if (forceSwitch || active.isGone()) {
            ShowdownActionResponse switched = bestSwitch(active, aiSide, true);
            if (switched != null) return switched;
            return fallbackOrSafe(active, battle, aiSide, moveset, true);
        }

        if (moveset == null) return new DefaultActionResponse();

        for (InBattleMove move : moveset.getMoves()) {
            if (move != null && move.mustBeUsed()) return legalMoveResponse(move, active, aiSide);
        }

        List<InBattleMove> usable = moveset.getMoves().stream().filter(m -> m != null && m.canBeUsed()).toList();
        if (usable.isEmpty()) return fallbackOrSafe(active, battle, aiSide, moveset, false);

        if (!competitiveLayer || skill <= 0) {
            return fallbackOrSafe(active, battle, aiSide, moveset, false);
        }

        try {
            Memory memory = memoryByPokemon.computeIfAbsent(pokemonKey(active), ignored -> new Memory());
            double currentMatchup = matchupScore(active, aiSide, null);
            ShowdownActionResponse switchChoice = null;
            if (!moveset.getTrapped() && !memory.firstTurn && currentMatchup < -35.0D) {
                switchChoice = bestSwitch(active, aiSide, false);
            }

            ScoredMove best = usable.stream()
                    .map(move -> new ScoredMove(move, scoreMove(move, active, aiSide, battle, currentMatchup, memory)))
                    .max(Comparator.comparingDouble(s -> s.score))
                    .orElse(null);

            if (switchChoice != null && (best == null || best.score < 80.0D)) {
                BattleAIDifficultyManager.debug("ScoringAI: switching bad matchup pokemon=" + pokemonKey(active) + " matchup=" + Math.round(currentMatchup));
                return switchChoice;
            }

            if (best != null) {
                String id = normalize(best.move.getId());
                memory.firstTurn = false;
                memory.lastMove = id;
                if (PROTECT_MOVES.contains(id)) memory.lastProtectTurn = battleTurn(battle);
                BattleAIDifficultyManager.debug("ScoringAI: chose move pokemon=" + pokemonKey(active) + " move=" + id + " score=" + Math.round(best.score));
                return legalMoveResponse(best.move, active, aiSide);
            }
        } catch (Throwable t) {
            BattleAIDifficultyManager.debug("ScoringAI: failed; using Cobblemon StrongBattleAI fallback error=" + t.getClass().getSimpleName());
        }

        return fallbackOrSafe(active, battle, aiSide, moveset, false);
    }

    private double scoreMove(InBattleMove move, ActiveBattlePokemon active, BattleSide aiSide, PokemonBattle battle, double currentMatchup, Memory memory) {
        String id = normalize(move.getId());
        double hp = readHpFraction(active);
        List<?> opponents = opponentActives(aiSide);
        int oppRemaining = remainingPokemon(aiSide, false);
        int ownRemaining = remainingPokemon(aiSide, true);

        if (PROTECT_MOVES.contains(id)) {
            boolean recent = memory.lastProtectTurn >= 0 && battleTurn(battle) - memory.lastProtectTurn <= 2;
            if (recent) return -500.0D;
            boolean chipValue = opponents.stream().anyMatch(o -> CHIP_STATUSES.contains(readStatus(o)));
            return chipValue && hp <= 0.65D ? 45.0D : -120.0D;
        }

        double bestDamageScore = offensiveScore(move, active, aiSide);
        double bestDamageFraction = bestDamageFraction(move, active, aiSide);

        if (bestDamageFraction >= 1.0D) return 10000.0D + bestDamageScore;      // guaranteed/estimated KO
        if (bestDamageFraction >= 0.50D) return 7000.0D + bestDamageScore;      // estimated 2HKO

        if (CORE_SETUP_MOVES.contains(id) || OTHER_SETUP_MOVES.contains(id)) {
            boolean core = CORE_SETUP_MOVES.contains(id);
            boolean safeSetup = hp >= 0.995D && currentMatchup > 0.0D && canSurviveLikelyHit(active, aiSide);
            if (!safeSetup) return core ? -80.0D : -120.0D;
            return core ? 650.0D + currentMatchup : 260.0D + currentMatchup;
        }

        if (RECOVERY_MOVES.contains(id)) {
            if (hp > 0.52D) return -100.0D;
            if (!canSurviveLikelyHit(active, aiSide)) return -80.0D;
            return 520.0D + ((1.0D - hp) * 300.0D);
        }

        if (HAZARD_MOVES.contains(id)) {
            if (oppRemaining < 3) return -50.0D;
            if (sideHasHazard(aiSide, false, id)) return -90.0D;
            return 360.0D + (oppRemaining * 20.0D);
        }

        if (HAZARD_CLEAR_MOVES.contains(id)) {
            if (ownRemaining < 2 || !sideHasAnyHazard(aiSide, true)) return -65.0D;
            return 390.0D;
        }

        if (STATUS_MOVES.contains(id)) {
            if (opponents.stream().allMatch(o -> !readStatus(o).isBlank() && !"null".equals(readStatus(o)))) return -60.0D;
            if (bestDamageFraction >= 0.80D) return -20.0D;
            return 120.0D;
        }

        return bestDamageScore;
    }

    private double offensiveScore(InBattleMove move, ActiveBattlePokemon active, BattleSide aiSide) {
        String id = normalize(move.getId());
        int power = readMovePower(id);
        if (power <= 0) return 0.0D;
        String type = moveType(id);
        if (type.isBlank()) return power;
        double best = 0.0D;
        for (Object opponent : opponentActives(aiSide)) {
            if (isTypeBlockedByKnownAbility(type, opponent)) continue;
            double multiplier = typeMultiplier(type, readTypes(opponent));
            if (multiplier <= 0.0D) continue;
            double stab = hasType(active, type) ? 1.5D : 1.0D;
            double accuracy = readMoveAccuracy(id);
            double priority = priorityBonus(id);
            double hpPressure = 1.0D + (1.0D - readHpFraction(opponent));
            best = Math.max(best, power * multiplier * stab * accuracy * hpPressure + priority);
        }
        return best;
    }

    private double bestDamageFraction(InBattleMove move, ActiveBattlePokemon active, BattleSide aiSide) {
        String id = normalize(move.getId());
        int power = readMovePower(id);
        if (power <= 0) return 0.0D;
        String type = moveType(id);
        double best = 0.0D;
        for (Object opponent : opponentActives(aiSide)) {
            if (isTypeBlockedByKnownAbility(type, opponent)) continue;
            double multiplier = typeMultiplier(type, readTypes(opponent));
            if (multiplier <= 0.0D) continue;
            double stab = hasType(active, type) ? 1.5D : 1.0D;
            double estimate = (power / 100.0D) * multiplier * stab * readMoveAccuracy(id);
            best = Math.max(best, estimate / Math.max(0.05D, readHpFraction(opponent)));
        }
        return best;
    }

    private ShowdownActionResponse bestSwitch(ActiveBattlePokemon active, BattleSide aiSide, boolean forced) {
        try {
            Object actor = active.getActor();
            Object list = actor.getClass().getMethod("getPokemonList").invoke(actor);
            if (!(list instanceof Iterable<?> iterable)) return null;
            BattlePokemon bestPokemon = null;
            double bestScore = -999999.0D;
            for (Object o : iterable) {
                if (!(o instanceof BattlePokemon pokemon)) continue;
                if (!pokemon.canBeSentOut()) continue;
                double score = matchupScore(active, aiSide, pokemon);
                if (score > bestScore) {
                    bestScore = score;
                    bestPokemon = pokemon;
                }
            }
            double current = matchupScore(active, aiSide, null);
            if (bestPokemon != null && (forced || bestScore > current + 45.0D)) {
                bestPokemon.setWillBeSwitchedIn(true);
                return new SwitchActionResponse(bestPokemon.getUuid());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private double matchupScore(ActiveBattlePokemon active, BattleSide aiSide, BattlePokemon candidate) {
        Object self = candidate == null ? active : candidate;
        double score = 0.0D;
        for (Object opponent : opponentActives(aiSide)) {
            double ourBest = bestTypePressure(self, opponent);
            double theirBest = bestTypePressure(opponent, self);
            score += ourBest * 35.0D;
            score -= theirBest * 35.0D;
            score += (readHpFraction(self) - readHpFraction(opponent)) * 20.0D;
        }
        return score;
    }

    private double bestTypePressure(Object attacker, Object defender) {
        double best = 1.0D;
        for (String type : readMoveTypes(attacker)) {
            if (isTypeBlockedByKnownAbility(type, defender)) continue;
            best = Math.max(best, typeMultiplier(type, readTypes(defender)));
        }
        return best;
    }

    private boolean canSurviveLikelyHit(ActiveBattlePokemon active, BattleSide aiSide) {
        double hp = readHpFraction(active);
        double threat = 0.0D;
        for (Object opponent : opponentActives(aiSide)) threat = Math.max(threat, bestTypePressure(opponent, active));
        return hp >= 0.70D || threat <= 1.0D;
    }

    private MoveActionResponse legalMoveResponse(InBattleMove move, ActiveBattlePokemon active, BattleSide aiSide) {
        if (move == null) return new MoveActionResponse("struggle", null, null);
        try {
            // Native target handling: spread, field, self, random and all-adjacent moves are sent targetless.
            if (move.mustBeUsed() || !requiresExplicitTarget(move.getTarget())) {
                return new MoveActionResponse(move.getId(), null, null);
            }
            List<Targetable> targets = move.getTargets(active);
            if (targets == null || targets.isEmpty()) return new MoveActionResponse(move.getId(), null, null);
            String type = moveType(normalize(move.getId()));
            Targetable best = null;
            double bestScore = -1.0D;
            for (Targetable target : targets) {
                if (target == null || !target.hasPokemon() || target.isAllied(active)) continue;
                double score = isTypeBlockedByKnownAbility(type, target) ? 0.0D : typeMultiplier(type, readTypes(target));
                if (score > bestScore) {
                    bestScore = score;
                    best = target;
                }
            }
            if (best == null) {
                for (Targetable target : targets) {
                    if (target != null && target.hasPokemon()) { best = target; break; }
                }
            }
            return best == null ? new MoveActionResponse(move.getId(), null, null) : new MoveActionResponse(move.getId(), best.getPNX(), null);
        } catch (Throwable ignored) {
            return new MoveActionResponse(move.getId(), null, null);
        }
    }

    private boolean requiresExplicitTarget(MoveTarget target) {
        return target == MoveTarget.any || target == MoveTarget.normal || target == MoveTarget.adjacentFoe || target == MoveTarget.adjacentAlly || target == MoveTarget.adjacentAllyOrSelf;
    }

    private ShowdownActionResponse fallbackOrSafe(ActiveBattlePokemon active, PokemonBattle battle, BattleSide aiSide, ShowdownMoveset moveset, boolean forceSwitch) {
        try {
            ShowdownActionResponse response = fallback.choose(active, battle, aiSide, moveset, forceSwitch);
            String moveId = readMoveId(response);
            if (moveId != null && moveset != null && !forceSwitch) {
                for (InBattleMove move : moveset.getMoves()) {
                    if (move != null && normalize(move.getId()).equals(normalize(moveId))) return legalMoveResponse(move, active, aiSide);
                }
            }
            return response == null ? new DefaultActionResponse() : response;
        } catch (Throwable ignored) {
            if (moveset != null && !forceSwitch) {
                for (InBattleMove move : moveset.getMoves()) if (move != null && move.canBeUsed()) return legalMoveResponse(move, active, aiSide);
            }
            return new DefaultActionResponse();
        }
    }

    private boolean sideHasHazard(BattleSide aiSide, boolean allied, String hazard) {
        try {
            Object side = allied ? aiSide : aiSide.getClass().getMethod("getOppositeSide").invoke(aiSide);
            Object hazards = readObject(side, "getSideHazards");
            return hazards != null && normalize(hazards.toString()).contains(normalize(hazard));
        } catch (Throwable ignored) { return false; }
    }

    private boolean sideHasAnyHazard(BattleSide aiSide, boolean allied) {
        for (String hazard : HAZARD_MOVES) if (sideHasHazard(aiSide, allied, hazard)) return true;
        return false;
    }

    private int remainingPokemon(BattleSide aiSide, boolean allied) {
        try {
            Object side = allied ? aiSide : aiSide.getClass().getMethod("getOppositeSide").invoke(aiSide);
            Object actors = readObject(side, "getActors");
            int count = 0;
            if (actors instanceof Iterable<?> iterable) {
                for (Object actor : iterable) {
                    Object pokemonList = readObject(actor, "getPokemonList");
                    if (pokemonList instanceof Iterable<?> mons) for (Object mon : mons) if (canBattle(mon)) count++;
                }
            }
            return count;
        } catch (Throwable ignored) { return 1; }
    }

    private boolean canBattle(Object mon) {
        try {
            Object can = mon.getClass().getMethod("canBeSentOut").invoke(mon);
            if (can instanceof Boolean b) return b;
        } catch (Throwable ignored) {}
        return readHpFraction(mon) > 0.0D;
    }

    private List<?> opponentActives(BattleSide aiSide) {
        try {
            Object opposite = aiSide.getClass().getMethod("getOppositeSide").invoke(aiSide);
            Object activePokemon = opposite.getClass().getMethod("getActivePokemon").invoke(opposite);
            if (activePokemon instanceof List<?> list) return list;
            if (activePokemon instanceof Iterable<?> iterable) {
                ArrayList<Object> out = new ArrayList<>();
                for (Object o : iterable) out.add(o);
                return out;
            }
        } catch (Throwable ignored) {}
        return List.of();
    }

    private List<String> readTypes(Object activeOrPokemon) {
        ArrayList<String> types = new ArrayList<>();
        Object bp = readObject(activeOrPokemon, "getBattlePokemon");
        if (bp == null) bp = activeOrPokemon;
        Object pokemon = readObject(bp, "getEffectedPokemon");
        if (pokemon == null) pokemon = readObject(bp, "getPokemon");
        Object species = readObject(pokemon == null ? bp : pokemon, "getSpecies");
        Object form = readObject(pokemon == null ? bp : pokemon, "getForm");
        Object rawTypes = readObject(form, "getTypes");
        if (rawTypes == null) rawTypes = readObject(species, "getTypes");
        if (rawTypes instanceof Iterable<?> iterable) {
            for (Object t : iterable) {
                Object name = readObject(t, "getName");
                String clean = normalize(name == null ? String.valueOf(t) : String.valueOf(name));
                if (!clean.isBlank()) types.add(clean);
            }
        }
        return types;
    }

    private List<String> readMoveTypes(Object activeOrPokemon) {
        ArrayList<String> types = new ArrayList<>();
        Object bp = readObject(activeOrPokemon, "getBattlePokemon");
        if (bp == null) bp = activeOrPokemon;
        Object pokemon = readObject(bp, "getEffectedPokemon");
        if (pokemon == null) pokemon = readObject(bp, "getPokemon");
        Object moves = readObject(pokemon, "getMoveSet");
        if (moves instanceof Iterable<?> iterable) {
            for (Object move : iterable) {
                Object type = readObject(move, "getType");
                Object name = readObject(type, "getName");
                String clean = normalize(name == null ? String.valueOf(type) : String.valueOf(name));
                if (!clean.isBlank()) types.add(clean);
            }
        }
        if (types.isEmpty()) types.addAll(readTypes(activeOrPokemon));
        return types;
    }

    private boolean hasType(Object activeOrPokemon, String type) { return type != null && readTypes(activeOrPokemon).contains(normalize(type)); }

    private boolean isTypeBlockedByKnownAbility(String type, Object opponent) {
        type = normalize(type);
        String ability = readAbilityName(opponent);
        return (type.equals("water") && (ability.equals("waterabsorb") || ability.equals("stormdrain") || ability.equals("dryskin")))
                || (type.equals("electric") && (ability.equals("voltabsorb") || ability.equals("motordrive") || ability.equals("lightningrod") || ability.equals("lightingrod")))
                || (type.equals("fire") && ability.equals("flashfire"))
                || (type.equals("grass") && ability.equals("sapsipper"))
                || (type.equals("ground") && (ability.equals("levitate") || ability.equals("eartheater")));
    }

    private String readAbilityName(Object activeOrBattlePokemon) {
        Object bp = readObject(activeOrBattlePokemon, "getBattlePokemon");
        if (bp == null) bp = activeOrBattlePokemon;
        Object pokemon = readObject(bp, "getEffectedPokemon");
        if (pokemon == null) pokemon = readObject(bp, "getPokemon");
        Object ability = readObject(pokemon, "getAbility");
        Object name = readObject(ability, "getName");
        return normalize(name == null ? String.valueOf(ability) : String.valueOf(name));
    }

    private String readStatus(Object activeOrBattlePokemon) {
        Object bp = readObject(activeOrBattlePokemon, "getBattlePokemon");
        if (bp == null) bp = activeOrBattlePokemon;
        Object pokemon = readObject(bp, "getEffectedPokemon");
        if (pokemon == null) pokemon = readObject(bp, "getPokemon");
        Object status = readObject(pokemon, "getStatus");
        if (status == null) return "";
        Object inner = readObject(status, "getStatus");
        Object showdown = readObject(inner == null ? status : inner, "getShowdownName");
        return normalize(showdown == null ? status.toString() : showdown.toString());
    }

    private double readHpFraction(Object activeBattlePokemon) {
        try {
            Object bp = readObject(activeBattlePokemon, "getBattlePokemon");
            if (bp == null) bp = activeBattlePokemon;
            Number health = readNumber(bp, "getHealth");
            Number maxHealth = readNumber(bp, "getMaxHealth");
            if (health == null || maxHealth == null || maxHealth.doubleValue() <= 0.0D) return 1.0D;
            return Math.max(0.0D, Math.min(1.0D, health.doubleValue() / maxHealth.doubleValue()));
        } catch (Throwable ignored) { return 1.0D; }
    }

    private Object readObject(Object target, String methodName) {
        if (target == null) return null;
        try { return target.getClass().getMethod(methodName).invoke(target); } catch (Throwable ignored) { return null; }
    }

    private Number readNumber(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof Number n ? n : null;
        } catch (Throwable ignored) { return null; }
    }

    private String moveType(String moveId) {
        try {
            Class<?> movesClass = Class.forName("com.cobblemon.mod.common.api.moves.Moves");
            Method getByName = movesClass.getMethod("getByName", String.class);
            Object template = getByName.invoke(null, normalize(moveId));
            if (template == null) return "";
            Object type = template.getClass().getMethod("getType").invoke(template);
            Object name = readObject(type, "getName");
            return normalize(name == null ? type.toString() : name.toString());
        } catch (Throwable ignored) { return ""; }
    }

    private int readMovePower(String moveId) {
        try {
            Class<?> movesClass = Class.forName("com.cobblemon.mod.common.api.moves.Moves");
            Method getByName = movesClass.getMethod("getByName", String.class);
            Object template = getByName.invoke(null, normalize(moveId));
            if (template == null) return 0;
            Object value = template.getClass().getMethod("getPower").invoke(template);
            return value instanceof Number n ? n.intValue() : 0;
        } catch (Throwable ignored) { return 0; }
    }

    private double readMoveAccuracy(String moveId) {
        try {
            Class<?> movesClass = Class.forName("com.cobblemon.mod.common.api.moves.Moves");
            Method getByName = movesClass.getMethod("getByName", String.class);
            Object template = getByName.invoke(null, normalize(moveId));
            if (template == null) return 1.0D;
            Object value = template.getClass().getMethod("getAccuracy").invoke(template);
            if (value instanceof Number n) return Math.max(0.5D, Math.min(1.0D, n.doubleValue() / 100.0D));
        } catch (Throwable ignored) {}
        return 1.0D;
    }

    private double priorityBonus(String id) {
        return switch (normalize(id)) {
            case "suckerpunch", "extremespeed", "aquajet", "bulletpunch", "shadowsneak", "quickattack", "iceshard", "machpunch", "vacuumwave" -> 35.0D;
            default -> 0.0D;
        };
    }

    private int battleTurn(PokemonBattle battle) {
        try {
            Number n = readNumber(battle, "getTurn");
            return n == null ? 0 : n.intValue();
        } catch (Throwable ignored) { return 0; }
    }

    private UUID pokemonKey(ActiveBattlePokemon active) {
        try { if (active.getBattlePokemon() != null) return active.getBattlePokemon().getUuid(); } catch (Throwable ignored) {}
        return new UUID(0L, System.identityHashCode(active));
    }

    private static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "").trim(); }

    private static String readMoveId(ShowdownActionResponse response) {
        if (response == null || !response.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("move")) return null;
        for (String methodName : List.of("getMove", "getMoveId", "getId")) {
            try {
                Object value = response.getClass().getMethod(methodName).invoke(response);
                if (value instanceof String s) return s;
            } catch (Throwable ignored) {}
        }
        Class<?> current = response.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() != String.class) continue;
                try { field.setAccessible(true); Object value = field.get(response); if (value instanceof String s && !s.isBlank()) return s; } catch (Throwable ignored) {}
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private double typeMultiplier(String attackType, List<String> defenderTypes) {
        if (attackType == null || attackType.isBlank() || defenderTypes == null || defenderTypes.isEmpty()) return 1.0D;
        double multiplier = 1.0D;
        for (String defenderType : defenderTypes) multiplier *= singleTypeMultiplier(normalize(attackType), normalize(defenderType));
        return multiplier;
    }

    private double singleTypeMultiplier(String a, String d) {
        if (a.equals("normal") && d.equals("ghost")) return 0.0D; if (a.equals("electric") && d.equals("ground")) return 0.0D; if (a.equals("ground") && d.equals("flying")) return 0.0D; if (a.equals("psychic") && d.equals("dark")) return 0.0D; if (a.equals("ghost") && d.equals("normal")) return 0.0D; if (a.equals("dragon") && d.equals("fairy")) return 0.0D; if (a.equals("poison") && d.equals("steel")) return 0.0D;
        if ((a.equals("fire") && List.of("grass","ice","bug","steel").contains(d)) || (a.equals("water") && List.of("fire","ground","rock").contains(d)) || (a.equals("grass") && List.of("water","ground","rock").contains(d)) || (a.equals("electric") && List.of("water","flying").contains(d)) || (a.equals("ice") && List.of("grass","ground","flying","dragon").contains(d)) || (a.equals("fighting") && List.of("normal","ice","rock","dark","steel").contains(d)) || (a.equals("ground") && List.of("fire","electric","poison","rock","steel").contains(d)) || (a.equals("flying") && List.of("grass","fighting","bug").contains(d)) || (a.equals("psychic") && List.of("fighting","poison").contains(d)) || (a.equals("bug") && List.of("grass","psychic","dark").contains(d)) || (a.equals("rock") && List.of("fire","ice","flying","bug").contains(d)) || (a.equals("ghost") && List.of("psychic","ghost").contains(d)) || (a.equals("dragon") && d.equals("dragon")) || (a.equals("dark") && List.of("psychic","ghost").contains(d)) || (a.equals("steel") && List.of("ice","rock","fairy").contains(d)) || (a.equals("fairy") && List.of("fighting","dragon","dark").contains(d)) || (a.equals("poison") && List.of("grass","fairy").contains(d))) return 2.0D;
        if ((a.equals("fire") && List.of("fire","water","rock","dragon").contains(d)) || (a.equals("water") && List.of("water","grass","dragon").contains(d)) || (a.equals("grass") && List.of("fire","grass","poison","flying","bug","dragon","steel").contains(d)) || (a.equals("electric") && List.of("electric","grass","dragon").contains(d)) || (a.equals("ice") && List.of("fire","water","ice","steel").contains(d)) || (a.equals("fighting") && List.of("poison","flying","psychic","bug","fairy").contains(d)) || (a.equals("ground") && List.of("grass","bug").contains(d)) || (a.equals("flying") && List.of("electric","rock","steel").contains(d)) || (a.equals("psychic") && List.of("psychic","steel").contains(d)) || (a.equals("bug") && List.of("fire","fighting","poison","flying","ghost","steel","fairy").contains(d)) || (a.equals("rock") && List.of("fighting","ground","steel").contains(d)) || (a.equals("ghost") && d.equals("dark")) || (a.equals("dragon") && d.equals("steel")) || (a.equals("dark") && List.of("fighting","dark","fairy").contains(d)) || (a.equals("steel") && List.of("fire","water","electric","steel").contains(d)) || (a.equals("fairy") && List.of("fire","poison","steel").contains(d)) || (a.equals("poison") && List.of("poison","ground","rock","ghost").contains(d))) return 0.5D;
        return 1.0D;
    }

    private record ScoredMove(InBattleMove move, double score) {}
    private static final class Memory { String lastMove = ""; boolean firstTurn = true; int lastProtectTurn = -100; }
}
