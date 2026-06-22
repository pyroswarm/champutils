package com.champutils.battle;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.DefaultActionResponse;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.MoveTarget;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.Targetable;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side ChampUtils AI wrapper.
 *
 * It keeps Cobblemon StrongBattleAI as the real decision engine and adds safe guardrails:
 * - wild battles can be smarter without being competitive/ladders-style
 * - gyms/trainers/bosses get anti-spam corrections on top of StrongBattleAI(5)
 * - if anything fails, the original StrongBattleAI answer is used
 */
public final class ChampSmarterBattleAI implements BattleAI {
    private static final Set<String> PROTECT_MOVES = Set.of(
            "protect", "detect", "spikyshield", "kingsshield", "banefulbunker", "silktrap", "burningbulwark", "obstruct"
    );

    private static final Set<String> STATUS_MOVES = Set.of(
            "willowisp", "thunderwave", "toxic", "poisongas", "poisonpowder", "stunspore", "glare", "nuzzle",
            "hypnosis", "spore", "sleeppowder", "yawn", "confuseray", "swagger", "supersonic", "teeterdance",
            "leechseed"
    );

    private static final Set<String> SELF_RECOVERY_MOVES = Set.of(
            "recover", "roost", "slackoff", "softboiled", "milkdrink", "healorder", "rest", "shoreup",
            "synthesis", "morningsun", "moonlight", "lifedew", "strengthsap"
    );

    private static final Set<String> COMMON_CHIP_STATUSES = Set.of(
            "brn", "burn", "psn", "tox", "badlypoisoned", "poison", "poisonbadly"
    );

    private static final Set<String> SETUP_MOVES = Set.of(
            "swordsdance", "dragondance", "nastyplot", "calmmind", "quiverdance", "shellsmash", "bulkup", "agility",
            "rockpolish", "growth", "workup", "irondefense", "cosmicpower", "curse", "coil"
    );

    private static final Set<String> TEAM_SETUP_MOVES = Set.of(
            "stealthrock", "spikes", "toxicspikes", "stickyweb", "reflect", "lightscreen", "auroraveil", "tailwind", "trickroom"
    );

    /**
     * Cobblemon's StrongBattleAI can occasionally value Recover/Roost too early.
     * This wrapper only allows direct self-healing once the Pokémon is meaningfully damaged.
     */
    private static final double SELF_RECOVERY_MAX_HP_FRACTION = 0.70D;

    private final BattleAI fallback;
    private final int skill;
    private final boolean competitiveLayer;
    private final boolean antiSpamLayer;
    private final Map<UUID, Memory> memoryByPokemon = new HashMap<>();

    public ChampSmarterBattleAI(int skill, boolean competitiveLayer, boolean antiSpamLayer) {
        this.skill = Math.max(0, Math.min(5, skill));
        this.competitiveLayer = competitiveLayer;
        this.antiSpamLayer = antiSpamLayer;
        this.fallback = new StrongBattleAI(this.skill);
    }

    @Override
    public ShowdownActionResponse choose(
            ActiveBattlePokemon activeBattlePokemon,
            PokemonBattle battle,
            BattleSide aiSide,
            ShowdownMoveset moveset,
            boolean forceSwitch
    ) {
        ShowdownActionResponse chosen;
        try {
            chosen = fallback.choose(activeBattlePokemon, battle, aiSide, moveset, forceSwitch);
        } catch (Throwable t) {
            BattleAIDifficultyManager.debug("Fallback: custom AI failed before decision, selecting safe legal move. error=" + t.getClass().getSimpleName());
            try {
                ShowdownActionResponse emergency = new StrongBattleAI(skill).choose(activeBattlePokemon, battle, aiSide, moveset, forceSwitch);
                return ensureValidChoice(emergency, activeBattlePokemon, moveset, forceSwitch);
            } catch (Throwable ignored) {
                return safestAction(activeBattlePokemon, moveset, forceSwitch);
            }
        }

        if (!antiSpamLayer || moveset == null || forceSwitch || activeBattlePokemon == null || activeBattlePokemon.isGone()) {
            chosen = ensureValidChoice(chosen, activeBattlePokemon, moveset, forceSwitch);
            remember(activeBattlePokemon, chosen);
            return chosen;
        }

        try {
            boolean badSwitch = isSwitchResponse(chosen) && !forceSwitch;
            String chosenMove = readMoveId(chosen);
            if ((chosenMove == null || chosenMove.isBlank()) && !badSwitch) {
                remember(activeBattlePokemon, chosen);
                return chosen;
            }
            if (chosenMove == null) chosenMove = "";

            UUID pokemonId = pokemonKey(activeBattlePokemon);
            Memory memory = memoryByPokemon.computeIfAbsent(pokemonId, ignored -> new Memory());
            String normalized = normalize(chosenMove);
            double hpFraction = readHpFraction(activeBattlePokemon);

            boolean protectMove = PROTECT_MOVES.contains(normalized);
            boolean protectSpam = ChampBattleAIConfig.DATA.antiSpam.preventProtectSpam
                    && protectMove
                    && (memory.protectCooldown > 0 || normalized.equals(memory.lastMove));

            boolean lowValueProtect = ChampBattleAIConfig.DATA.antiSpam.preventLowValueProtect
                    && protectMove
                    && !hasStrategicProtectReason(activeBattlePokemon, aiSide, hpFraction);

            boolean sameMoveLoop = competitiveLayer
                    && ChampBattleAIConfig.DATA.antiSpam.preventSameMoveLoops
                    && normalized.equals(memory.lastMove)
                    && memory.sameMoveCount >= ChampBattleAIConfig.DATA.antiSpam.sameMoveSoftLimit;

            boolean wastefulRecovery = SELF_RECOVERY_MOVES.contains(normalized)
                    && hpFraction >= SELF_RECOVERY_MAX_HP_FRACTION;
            boolean badAbsorbMove = isMoveIntoKnownAbsorbAbility(normalized, activeBattlePokemon, aiSide);
            boolean redundantStatusMove = STATUS_MOVES.contains(normalized) && opponentAlreadyHasStatus(activeBattlePokemon, aiSide);

            if (protectSpam || lowValueProtect || sameMoveLoop || wastefulRecovery || badAbsorbMove || redundantStatusMove || badSwitch) {
                InBattleMove replacement = findReplacementMove(moveset, normalized, protectSpam || lowValueProtect || wastefulRecovery || badAbsorbMove || redundantStatusMove || badSwitch, hpFraction);
                if (replacement != null) {
                    if (badSwitch) {
                        BattleAIDifficultyManager.debug("AntiSwitch: blocked unnecessary switch pokemon=" + pokemonId);
                    } else if (badAbsorbMove) {
                        BattleAIDifficultyManager.debug("AntiAbsorb: blocked immune/absorbed move pokemon=" + pokemonId + " move=" + normalized);
                    } else if (redundantStatusMove) {
                        BattleAIDifficultyManager.debug("AntiWaste: blocked redundant status move pokemon=" + pokemonId + " move=" + normalized);
                    } else if (protectSpam) {
                        BattleAIDifficultyManager.debug("AntiSpam: blocked repeated Protect from pokemon=" + pokemonId
                                + " lastMove=" + memory.lastMove + " repeatCount=" + memory.sameMoveCount);
                    } else if (lowValueProtect) {
                        BattleAIDifficultyManager.debug("AntiWaste: blocked low-value Protect from pokemon=" + pokemonId
                                + " hp=" + Math.round(hpFraction * 100.0D) + "%");
                    } else if (wastefulRecovery) {
                        BattleAIDifficultyManager.debug("AntiWaste: blocked early recovery pokemon=" + pokemonId
                                + " move=" + normalized + " hp=" + Math.round(hpFraction * 100.0D) + "%");
                    } else {
                        BattleAIDifficultyManager.debug("AntiSpam: penalized same move pokemon=" + pokemonId
                                + " move=" + normalized + " repeatCount=" + memory.sameMoveCount);
                    }
                    chosen = legalMoveResponse(replacement, activeBattlePokemon);
                    normalized = normalize(replacement.getId());
                }
            }

            InBattleMove strategic = findStrategicMove(moveset, activeBattlePokemon, aiSide, memory, hpFraction);
            if (strategic != null && !normalize(strategic.getId()).equals(normalized)) {
                BattleAIDifficultyManager.debug("SmartAI: upgraded decision pokemon=" + pokemonId + " from=" + normalized + " to=" + normalize(strategic.getId()));
                chosen = legalMoveResponse(strategic, activeBattlePokemon);
                normalized = normalize(strategic.getId());
            }

            if (PROTECT_MOVES.contains(normalized)) {
                memory.protectCooldown = ChampBattleAIConfig.DATA.antiSpam.protectRepeatPenaltyTurns;
            } else if (memory.protectCooldown > 0) {
                memory.protectCooldown--;
            }

            if (normalized.equals(memory.lastMove)) {
                memory.sameMoveCount++;
            } else {
                memory.lastMove = normalized;
                memory.sameMoveCount = 1;
            }

            return ensureValidChoice(chosen, activeBattlePokemon, moveset, forceSwitch);
        } catch (Throwable t) {
            BattleAIDifficultyManager.debug("Fallback: custom AI wrapper failed, validating original StrongBattleAI(" + skill + ") decision. error=" + t.getClass().getSimpleName());
            return ensureValidChoice(chosen, activeBattlePokemon, moveset, forceSwitch);
        }
    }

    private ShowdownActionResponse ensureValidChoice(ShowdownActionResponse response, ActiveBattlePokemon activeBattlePokemon, ShowdownMoveset moveset, boolean forceSwitch) {
        try {
            String moveId = readMoveId(response);

            // Cobblemon/Showdown can reject a move after isValid() if we send a target for
            // targetless spread moves like Earthquake. Normalize every move response here so
            // the final choice matches the move target mode Showdown expects.
            if (moveId != null && moveset != null && !forceSwitch) {
                InBattleMove selectedMove = findMoveById(moveset, moveId);
                if (selectedMove != null && selectedMove.canBeUsed()) {
                    MoveActionResponse normalized = legalMoveResponse(selectedMove, activeBattlePokemon);
                    if (normalized.isValid(activeBattlePokemon, moveset, false)) return normalized;
                }
            }

            if (response != null && response.isValid(activeBattlePokemon, moveset, forceSwitch)) return response;
            BattleAIDifficultyManager.debug("SafeAI: replacing invalid action " + (moveId == null ? response : moveId));
            if (moveset != null && !forceSwitch) {
                for (InBattleMove move : moveset.getMoves()) {
                    if (move == null || !move.canBeUsed()) continue;
                    MoveActionResponse candidate = legalMoveResponse(move, activeBattlePokemon);
                    if (candidate.isValid(activeBattlePokemon, moveset, false)) return candidate;
                }
            }
        } catch (Throwable ignored) {
        }
        return safestAction(activeBattlePokemon, moveset, forceSwitch);
    }

    private InBattleMove findStrategicMove(ShowdownMoveset moveset, ActiveBattlePokemon active, BattleSide aiSide, Memory memory, double hpFraction) {
        if (!competitiveLayer || moveset == null || active == null || active.isGone()) return null;
        List<InBattleMove> usable = moveset.getMoves().stream().filter(m -> m != null && m.canBeUsed()).toList();
        if (usable.isEmpty()) return null;

        boolean firstTurn = memory == null || memory.lastMove == null || memory.lastMove.isBlank();
        if (firstTurn) {
            InBattleMove leadSetup = usable.stream()
                    .filter(m -> TEAM_SETUP_MOVES.contains(normalize(m.getId())) || SETUP_MOVES.contains(normalize(m.getId())))
                    .filter(m -> !wouldBeRedundantUtility(m, active, aiSide))
                    .findFirst().orElse(null);
            if (leadSetup != null && hpFraction > 0.45D) return leadSetup;
        }

        InBattleMove bestDamage = null;
        double bestScore = -1.0D;
        for (InBattleMove move : usable) {
            String id = normalize(move.getId());
            if (PROTECT_MOVES.contains(id) || SELF_RECOVERY_MOVES.contains(id)) continue;
            double score = offensiveMoveScore(move, active, aiSide);
            if (score > bestScore) {
                bestScore = score;
                bestDamage = move;
            }
        }

        if (bestDamage != null && bestScore >= 35.0D) return bestDamage;

        InBattleMove utility = usable.stream()
                .filter(m -> STATUS_MOVES.contains(normalize(m.getId())) || TEAM_SETUP_MOVES.contains(normalize(m.getId())) || SETUP_MOVES.contains(normalize(m.getId())))
                .filter(m -> !wouldBeRedundantUtility(m, active, aiSide))
                .findFirst().orElse(null);
        return utility;
    }

    private double offensiveMoveScore(InBattleMove move, ActiveBattlePokemon active, BattleSide aiSide) {
        String id = normalize(move == null ? null : move.getId());
        int power = readMovePower(id);
        if (power <= 0) return 0.0D;
        String type = moveType(id);
        double multiplier = 1.0D;
        for (Object opponent : opponentActives(active, aiSide)) {
            multiplier = Math.max(multiplier, typeMultiplier(type, readTypes(opponent)));
        }
        double stab = hasType(active, type) ? 1.5D : 1.0D;
        double accuracy = readMoveAccuracy(id);
        double priority = id.contains("suckerpunch") || id.contains("extremespeed") || id.contains("aquajet") || id.contains("bulletpunch") || id.contains("shadowsneak") || id.contains("quickattack") ? 20.0D : 0.0D;
        return power * multiplier * stab * accuracy + priority;
    }

    private boolean wouldBeRedundantUtility(InBattleMove move, ActiveBattlePokemon active, BattleSide aiSide) {
        String id = normalize(move == null ? null : move.getId());
        if (id.isBlank()) return true;
        if (STATUS_MOVES.contains(id) && opponentAlreadyHasStatus(active, aiSide)) return true;
        try {
            Object opposite = aiSide.getClass().getMethod("getOppositeSide").invoke(aiSide);
            String alliedScreen = normalize(String.valueOf(readObject(aiSide, "getScreenCondition")));
            String alliedTailwind = normalize(String.valueOf(readObject(aiSide, "getTailwindCondition")));
            if ((id.equals("reflect") || id.equals("lightscreen") || id.equals("auroraveil")) && !alliedScreen.isBlank() && !alliedScreen.equals("null")) return true;
            if (id.equals("tailwind") && !alliedTailwind.isBlank() && !alliedTailwind.equals("null")) return true;
            if (id.equals("stealthrock") || id.equals("spikes") || id.equals("toxicspikes") || id.equals("stickyweb")) {
                Object hazards = readObject(opposite, "getSideHazards");
                if (hazards != null && normalize(hazards.toString()).contains(id)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private double readMoveAccuracy(String moveId) {
        try {
            Class<?> movesClass = Class.forName("com.cobblemon.mod.common.api.moves.Moves");
            Method getByName = movesClass.getMethod("getByName", String.class);
            Object template = getByName.invoke(null, moveId);
            if (template == null) return 1.0D;
            Object value = template.getClass().getMethod("getAccuracy").invoke(template);
            if (value instanceof Number n) return Math.max(0.5D, Math.min(1.0D, n.doubleValue() / 100.0D));
        } catch (Throwable ignored) {}
        return 1.0D;
    }

    private boolean hasType(Object activeOrPokemon, String type) {
        if (type == null || type.isBlank()) return false;
        return readTypes(activeOrPokemon).contains(type);
    }

    private List<String> readTypes(Object activeOrPokemon) {
        java.util.ArrayList<String> types = new java.util.ArrayList<>();
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

    private double typeMultiplier(String attackType, List<String> defenderTypes) {
        if (attackType == null || attackType.isBlank() || defenderTypes == null || defenderTypes.isEmpty()) return 1.0D;
        double multiplier = 1.0D;
        for (String defenderType : defenderTypes) multiplier *= singleTypeMultiplier(attackType, defenderType);
        return multiplier;
    }

    private double singleTypeMultiplier(String a, String d) {
        if (a.equals("normal") && d.equals("ghost")) return 0.0D;
        if (a.equals("electric") && d.equals("ground")) return 0.0D;
        if (a.equals("ground") && d.equals("flying")) return 0.0D;
        if (a.equals("psychic") && d.equals("dark")) return 0.0D;
        if (a.equals("ghost") && d.equals("normal")) return 0.0D;
        if (a.equals("dragon") && d.equals("fairy")) return 0.0D;
        if (a.equals("poison") && d.equals("steel")) return 0.0D;
        if ((a.equals("fire") && List.of("grass","ice","bug","steel").contains(d)) || (a.equals("water") && List.of("fire","ground","rock").contains(d)) || (a.equals("grass") && List.of("water","ground","rock").contains(d)) || (a.equals("electric") && List.of("water","flying").contains(d)) || (a.equals("ice") && List.of("grass","ground","flying","dragon").contains(d)) || (a.equals("fighting") && List.of("normal","ice","rock","dark","steel").contains(d)) || (a.equals("ground") && List.of("fire","electric","poison","rock","steel").contains(d)) || (a.equals("flying") && List.of("grass","fighting","bug").contains(d)) || (a.equals("psychic") && List.of("fighting","poison").contains(d)) || (a.equals("bug") && List.of("grass","psychic","dark").contains(d)) || (a.equals("rock") && List.of("fire","ice","flying","bug").contains(d)) || (a.equals("ghost") && List.of("psychic","ghost").contains(d)) || (a.equals("dragon") && d.equals("dragon")) || (a.equals("dark") && List.of("psychic","ghost").contains(d)) || (a.equals("steel") && List.of("ice","rock","fairy").contains(d)) || (a.equals("fairy") && List.of("fighting","dragon","dark").contains(d)) || (a.equals("poison") && List.of("grass","fairy").contains(d))) return 2.0D;
        if ((a.equals("fire") && List.of("fire","water","rock","dragon").contains(d)) || (a.equals("water") && List.of("water","grass","dragon").contains(d)) || (a.equals("grass") && List.of("fire","grass","poison","flying","bug","dragon","steel").contains(d)) || (a.equals("electric") && List.of("electric","grass","dragon").contains(d)) || (a.equals("ice") && List.of("fire","water","ice","steel").contains(d)) || (a.equals("fighting") && List.of("poison","flying","psychic","bug","fairy").contains(d)) || (a.equals("ground") && List.of("grass","bug").contains(d)) || (a.equals("flying") && List.of("electric","rock","steel").contains(d)) || (a.equals("psychic") && List.of("psychic","steel").contains(d)) || (a.equals("bug") && List.of("fire","fighting","poison","flying","ghost","steel","fairy").contains(d)) || (a.equals("rock") && List.of("fighting","ground","steel").contains(d)) || (a.equals("ghost") && d.equals("dark")) || (a.equals("dragon") && d.equals("steel")) || (a.equals("dark") && List.of("fighting","dark","fairy").contains(d)) || (a.equals("steel") && List.of("fire","water","electric","steel").contains(d)) || (a.equals("fairy") && List.of("fire","poison","steel").contains(d)) || (a.equals("poison") && List.of("poison","ground","rock","ghost").contains(d))) return 0.5D;
        return 1.0D;
    }

    private ShowdownActionResponse safestAction(ActiveBattlePokemon activeBattlePokemon, ShowdownMoveset moveset, boolean forceSwitch) {
        if (moveset != null && !forceSwitch) {
            for (InBattleMove move : moveset.getMoves()) {
                if (move == null || !move.canBeUsed()) continue;
                MoveActionResponse candidate = legalMoveResponse(move, activeBattlePokemon);
                try {
                    if (candidate.isValid(activeBattlePokemon, moveset, false)) return candidate;
                } catch (Throwable ignored) {
                }
            }
            for (InBattleMove move : moveset.getMoves()) {
                if (move != null && "struggle".equalsIgnoreCase(move.getId())) {
                    return new MoveActionResponse("struggle", null, null);
                }
            }
        }
        return new DefaultActionResponse();
    }

    private MoveActionResponse legalMoveResponse(InBattleMove move, ActiveBattlePokemon activeBattlePokemon) {
        try {
            if (move == null) return new MoveActionResponse("struggle", null, null);

            // Showdown only accepts an explicit target for selectable-target moves.
            // Spread/field/self/random moves must be sent as just "move <id>".
            if (move.mustBeUsed() || !requiresExplicitTarget(move.getTarget())) {
                return new MoveActionResponse(move.getId(), null, null);
            }

            List<Targetable> targets = move.getTargets(activeBattlePokemon);
            if (targets == null || targets.isEmpty()) {
                targets = activeBattlePokemon.getAdjacentOpponents();
            }
            if (targets == null || targets.isEmpty()) {
                return new MoveActionResponse(move.getId(), null, null);
            }
            Targetable chosenTarget = null;
            for (Targetable target : targets) {
                if (target != null && target.hasPokemon() && !target.isAllied(activeBattlePokemon)) {
                    chosenTarget = target;
                    break;
                }
            }
            if (chosenTarget == null) {
                for (Targetable target : targets) {
                    if (target != null && target.hasPokemon()) {
                        chosenTarget = target;
                        break;
                    }
                }
            }
            if (chosenTarget == null) return new MoveActionResponse(move.getId(), null, null);
            return new MoveActionResponse(move.getId(), chosenTarget.getPNX(), null);
        } catch (Throwable ignored) {
            return new MoveActionResponse(move.getId(), null, null);
        }
    }

    private boolean requiresExplicitTarget(MoveTarget target) {
        return target == MoveTarget.any
                || target == MoveTarget.normal
                || target == MoveTarget.adjacentFoe
                || target == MoveTarget.adjacentAlly
                || target == MoveTarget.adjacentAllyOrSelf;
    }

    private InBattleMove findMoveById(ShowdownMoveset moveset, String moveId) {
        if (moveset == null || moveId == null) return null;
        String normalizedMoveId = normalize(moveId);
        for (InBattleMove move : moveset.getMoves()) {
            if (move != null && normalize(move.getId()).equals(normalizedMoveId)) return move;
        }
        return null;
    }

    private void remember(ActiveBattlePokemon activeBattlePokemon, ShowdownActionResponse chosen) {
        try {
            if (activeBattlePokemon == null) return;
            String move = readMoveId(chosen);
            if (move == null) return;
            UUID pokemonId = pokemonKey(activeBattlePokemon);
            Memory memory = memoryByPokemon.computeIfAbsent(pokemonId, ignored -> new Memory());
            String normalized = normalize(move);
            if (PROTECT_MOVES.contains(normalized)) memory.protectCooldown = ChampBattleAIConfig.DATA.antiSpam.protectRepeatPenaltyTurns;
            else if (memory.protectCooldown > 0) memory.protectCooldown--;
            if (normalized.equals(memory.lastMove)) memory.sameMoveCount++;
            else {
                memory.lastMove = normalized;
                memory.sameMoveCount = 1;
            }
        } catch (Throwable ignored) {
        }
    }

    private UUID pokemonKey(ActiveBattlePokemon activeBattlePokemon) {
        try {
            if (activeBattlePokemon.getBattlePokemon() != null) {
                return activeBattlePokemon.getBattlePokemon().getUuid();
            }
        } catch (Throwable ignored) {
        }
        return new UUID(0L, System.identityHashCode(activeBattlePokemon));
    }

    private InBattleMove findReplacementMove(ShowdownMoveset moveset, String blockedMove, boolean avoidStallMoves, double hpFraction) {
        List<InBattleMove> moves = moveset.getMoves();
        InBattleMove firstUsable = null;
        InBattleMove firstDamaging = null;

        for (InBattleMove move : moves) {
            if (move == null || !move.canBeUsed()) continue;
            String id = normalize(move.getId());
            if (id.equals(blockedMove)) continue;
            if (avoidStallMoves && PROTECT_MOVES.contains(id)) continue;
            if (hpFraction >= SELF_RECOVERY_MAX_HP_FRACTION && SELF_RECOVERY_MOVES.contains(id)) continue;
            if (firstUsable == null) firstUsable = move;

            int power = readMovePower(id);
            if (power > 0 && firstDamaging == null) firstDamaging = move;
        }

        return firstDamaging != null ? firstDamaging : firstUsable;
    }


    private boolean hasStrategicProtectReason(ActiveBattlePokemon active, BattleSide aiSide, double hpFraction) {
        // Protect should feel smart, not random. Allow it mainly when it buys real value:
        // poison/burn chip, scouting at low HP, or stalling enemy screens/tailwind/weather-like pressure.
        if (hpFraction <= 0.25D) return true;

        String ownStatus = readStatus(active);
        if (COMMON_CHIP_STATUSES.contains(ownStatus)) return true;

        for (Object opponent : opponentActives(active, aiSide)) {
            String status = readStatus(opponent);
            if (COMMON_CHIP_STATUSES.contains(status)) return true;
        }

        try {
            Object opposite = aiSide.getClass().getMethod("getOppositeSide").invoke(aiSide);
            String screen = normalize(String.valueOf(readObject(opposite, "getScreenCondition")));
            String tailwind = normalize(String.valueOf(readObject(opposite, "getTailwindCondition")));
            if (!screen.isBlank() && !screen.equals("null")) return true;
            if (!tailwind.isBlank() && !tailwind.equals("null")) return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    private boolean isSwitchResponse(ShowdownActionResponse response) {
        return response != null && response.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("switch");
    }

    private boolean isMoveIntoKnownAbsorbAbility(String moveId, ActiveBattlePokemon active, BattleSide aiSide) {
        String type = moveType(moveId);
        if (type.isBlank()) return false;
        for (Object opponent : opponentActives(active, aiSide)) {
            String ability = readAbilityName(opponent);
            if ((type.equals("water") && (ability.equals("waterabsorb") || ability.equals("stormdrain") || ability.equals("dryskin")))
                    || (type.equals("electric") && (ability.equals("voltabsorb") || ability.equals("motordrive") || ability.equals("lightningrod")))
                    || (type.equals("fire") && ability.equals("flashfire"))
                    || (type.equals("grass") && ability.equals("sapsipper"))
                    || (type.equals("ground") && (ability.equals("levitate") || ability.equals("eartheater")))) {
                return true;
            }
        }
        return false;
    }

    private boolean opponentAlreadyHasStatus(ActiveBattlePokemon active, BattleSide aiSide) {
        for (Object opponent : opponentActives(active, aiSide)) {
            String status = readStatus(opponent);
            if (!status.isBlank() && !status.equals("null")) return true;
        }
        return false;
    }

    private List<?> opponentActives(ActiveBattlePokemon active, BattleSide aiSide) {
        try {
            Object opposite = aiSide.getClass().getMethod("getOppositeSide").invoke(aiSide);
            Object activePokemon = opposite.getClass().getMethod("getActivePokemon").invoke(opposite);
            if (activePokemon instanceof List<?> list) return list;
            if (activePokemon instanceof Iterable<?> iterable) {
                java.util.ArrayList<Object> out = new java.util.ArrayList<>();
                for (Object o : iterable) out.add(o);
                return out;
            }
        } catch (Throwable ignored) {}
        return java.util.Collections.emptyList();
    }

    private String readAbilityName(Object activeOrBattlePokemon) {
        Object bp = readObject(activeOrBattlePokemon, "getBattlePokemon");
        if (bp == null) bp = activeOrBattlePokemon;
        Object pokemon = readObject(bp, "getEffectedPokemon");
        if (pokemon == null) pokemon = readObject(bp, "getPokemon");
        Object ability = readObject(pokemon, "getAbility");
        if (ability == null) return "";
        Object name = readObject(ability, "getName");
        return normalize(name == null ? ability.toString() : name.toString());
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

    private Object readObject(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) { return null; }
    }

    private String moveType(String moveId) {
        try {
            Class<?> movesClass = Class.forName("com.cobblemon.mod.common.api.moves.Moves");
            Method getByName = movesClass.getMethod("getByName", String.class);
            Object template = getByName.invoke(null, moveId);
            if (template == null) return "";
            Object type = template.getClass().getMethod("getType").invoke(template);
            Object name = readObject(type, "getName");
            return normalize(name == null ? type.toString() : name.toString());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private double readHpFraction(ActiveBattlePokemon activeBattlePokemon) {
        try {
            Object battlePokemon = activeBattlePokemon.getBattlePokemon();
            if (battlePokemon == null) return 0.0D;

            Number health = readNumber(battlePokemon, "getHealth");
            Number maxHealth = readNumber(battlePokemon, "getMaxHealth");
            if (health == null || maxHealth == null || maxHealth.doubleValue() <= 0.0D) {
                return 0.0D;
            }

            return Math.max(0.0D, Math.min(1.0D, health.doubleValue() / maxHealth.doubleValue()));
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    private Number readNumber(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Number n ? n : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int readMovePower(String moveId) {
        try {
            Class<?> movesClass = Class.forName("com.cobblemon.mod.common.api.moves.Moves");
            Method getByName = movesClass.getMethod("getByName", String.class);
            Object template = getByName.invoke(null, moveId);
            if (template == null) return 0;
            Method getPower = template.getClass().getMethod("getPower");
            Object value = getPower.invoke(template);
            return value instanceof Number n ? n.intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String normalize(String moveId) {
        return moveId == null ? "" : moveId.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
    }

    private static String readMoveId(ShowdownActionResponse response) {
        if (response == null) return null;
        String className = response.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (!className.contains("move")) return null;

        for (String methodName : List.of("getMove", "getMoveId", "getId")) {
            try {
                Method method = response.getClass().getMethod(methodName);
                Object value = method.invoke(response);
                if (value instanceof String s) return s;
            } catch (Throwable ignored) {
            }
        }

        Class<?> current = response.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() != String.class) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(response);
                    if (value instanceof String s && !s.isBlank()) return s;
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static final class Memory {
        String lastMove = "";
        int sameMoveCount = 0;
        int protectCooldown = 0;
    }
}
