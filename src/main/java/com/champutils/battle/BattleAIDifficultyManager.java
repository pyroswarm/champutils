package com.champutils.battle;

import com.champutils.debug.ChampDebugManager;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Applies ChampUtils PvE AI rules.
 *
 * Defaults:
 * - Wild battles: StrongBattleAI(3) + light anti-spam only. Smarter, but not competitive.
 * - NPC trainers, gym leaders, guild bosses, world bosses: StrongBattleAI(5) + ChampUtils guardrails.
 *
 * This intentionally never touches PlayerBattleActor and always falls back to Cobblemon's StrongBattleAI.
 */
public final class BattleAIDifficultyManager {

    private static final int MAX_COBBLEMON_AI_SKILL = 5;

    private static LastAssignment lastAssignment = LastAssignment.empty();

    private BattleAIDifficultyManager() {}

    public static void register() {
        ChampBattleAIConfig.load();

        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {
            if (event instanceof BattleStartedEvent.Pre pre) {
                applyAI(pre.getBattle());
            }
        });

        CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> {
            if (event instanceof BattleStartedEvent.Post post) {
                applyAI(post.getBattle());
            }
        });
    }

    public static void reloadConfig() {
        ChampBattleAIConfig.load();
    }

    public static void setDebug(boolean debug) {
        if (debug) {
            ChampDebugManager.setOnly(ChampDebugManager.Category.AI);
        } else {
            ChampDebugManager.disable("ai");
        }
        ChampBattleAIConfig.DATA.debug = debug;
        ChampBattleAIConfig.save();
    }

    public static LastAssignment getLastAssignment() {
        return lastAssignment;
    }

    private static void applyAI(Object battle) {
        if (battle == null || !ChampBattleAIConfig.DATA.enabled) return;

        try {
            Method getActors = battle.getClass().getMethod("getActors");
            Object actorsObject = getActors.invoke(battle);
            if (!(actorsObject instanceof Iterable<?> actors)) return;
            for (Object actor : actors) {
                applyAIToActor(battle, actor);
            }
        } catch (Exception e) {
            debug("Fallback: failed to inspect battle actors: " + e.getClass().getSimpleName());
        }
    }

    public static void prepareNpc(NPCEntity npc) {
        if (npc == null) return;
        try {
            npc.setSkill(MAX_COBBLEMON_AI_SKILL);
        } catch (Exception ignored) {
        }
    }

    private static void applyAIToActor(Object battle, Object actor) {
        if (actor == null || actor instanceof PlayerBattleActor) return;

        ActorType type = getActorType(actor);
        String detectedType = detectTypeLabel(type, actor);
        ChampBattleAIConfig.BattleBucket bucket = bucketFor(type, actor);
        if (bucket == null || !bucket.enabled) return;

        if (actor instanceof NPCBattleActor npcActor) {
            try {
                prepareNpc(npcActor.getEntity());
            } catch (Exception ignored) {
            }
        }

        int resolvedSkill = resolveSkill(detectedType, actor, bucket.skill);
        boolean wrapper = bucket.antiSpamLayer || bucket.competitiveLayer || "rctapi".equalsIgnoreCase(String.valueOf(ChampBattleAIConfig.DATA.engine));
        BattleAI ai = wrapper
                ? new ChampSmartBattleAI(resolvedSkill, bucket.competitiveLayer, ChampBattleAIConfig.DATA.fallbackToStrongAi, ChampBattleAIConfig.DATA.fallbackToRandomAi)
                : new StrongBattleAI(resolvedSkill);

        boolean applied = setBattleAI(actor, ai);
        String battleId = readBattleId(battle);
        String player = readFirstPlayerName(battle);
        lastAssignment = new LastAssignment(player, battleId, detectedType, String.valueOf(type), resolvedSkill, wrapper, false, applied);

        if (applied) {
            debug("Battle detected: type=" + detectedType + " player=" + player + " battleId=" + battleId
                    + " aiSkill=" + resolvedSkill + " competitiveLayer=" + bucket.competitiveLayer);
            debug("Using " + (detectedType.equals("WILD") ? "SmartWildAI" : "HardTrainerAI")
                    + " -> " + (wrapper ? "ChampSmartBattleAI" : "StrongBattleAI") + "(" + resolvedSkill + ")");
            debugBattleSheet(battleId, detectedType, actor, resolvedSkill, wrapper);
        } else {
            debug("Fallback: could not replace BattleAI field for actor=" + actor.getClass().getName()
                    + ", Cobblemon actor skill/fallback will remain in control.");
        }
    }

    private static int resolveSkill(String detectedType, Object actor, int fallbackSkill) {
        int skill = Math.max(0, Math.min(MAX_COBBLEMON_AI_SKILL, fallbackSkill));
        if (!"TRAINER".equals(detectedType)) {
            return skill;
        }
        String marker = actorMarker(actor);
        if (marker.contains("mythic") || marker.contains("legendary") || marker.contains("epic")) return 5;
        if (marker.contains("rare")) return 4;
        if (marker.contains("uncommon")) return 3;
        if (marker.contains("common")) return 2;
        return skill;
    }

    private static String actorMarker(Object actor) {
        String marker = String.valueOf(actor).toLowerCase(Locale.ROOT);
        try {
            if (actor instanceof NPCBattleActor npcActor) {
                NPCEntity npc = npcActor.getEntity();
                marker += " " + String.join(",", npc.getTags()).toLowerCase(Locale.ROOT) + " " + String.valueOf(npc.getCustomName()).toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {}
        return marker;
    }

    private static ChampBattleAIConfig.BattleBucket bucketFor(ActorType type, Object actor) {
        if (type == ActorType.WILD) return ChampBattleAIConfig.DATA.wildBattles;
        if (type == ActorType.NPC) {
            String label = detectTypeLabel(type, actor);
            if (label.equals("GYM_LEADER")) return ChampBattleAIConfig.DATA.gymBattles;
            if (label.equals("GUILD_BOSS")) return ChampBattleAIConfig.DATA.guildBossBattles;
            if (label.equals("WORLD_BOSS")) return ChampBattleAIConfig.DATA.worldBossBattles;
            return ChampBattleAIConfig.DATA.trainerBattles;
        }
        return new ChampBattleAIConfig.BattleBucket();
    }

    private static ActorType getActorType(Object actor) {
        try {
            Method getType = actor.getClass().getMethod("getType");
            Object value = getType.invoke(actor);
            if (value instanceof ActorType type) return type;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean setBattleAI(Object actor, BattleAI ai) {
        Class<?> current = actor.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!BattleAI.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    field.set(actor, ai);
                    return true;
                } catch (Exception ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static String detectTypeLabel(ActorType type, Object actor) {
        if (type == ActorType.WILD) return "WILD";
        if (type != ActorType.NPC) return type == null ? "UNKNOWN" : type.name();

        String marker = actorMarker(actor);

        if (marker.contains("world") && marker.contains("boss")) return "WORLD_BOSS";
        if (marker.contains("guild") && marker.contains("boss")) return "GUILD_BOSS";
        if (marker.contains("gym") || marker.contains("champutils_ai_test_gym")) return "GYM_LEADER";
        if (marker.contains("boss")) return "GUILD_BOSS";
        return "TRAINER";
    }

    private static String readBattleId(Object battle) {
        if (battle == null) return "unknown";
        for (String methodName : new String[]{"getBattleId", "getId"}) {
            try {
                Object value = battle.getClass().getMethod(methodName).invoke(battle);
                if (value != null) return String.valueOf(value);
            } catch (Exception ignored) {}
        }
        return "unknown";
    }

    private static String readFirstPlayerName(Object battle) {
        try {
            Object actorsObject = battle.getClass().getMethod("getActors").invoke(battle);
            if (!(actorsObject instanceof Iterable<?> actors)) return "unknown";
            for (Object actor : actors) {
                if (!(actor instanceof PlayerBattleActor)) continue;
                try {
                    Object name = actor.getClass().getMethod("getName").invoke(actor);
                    return String.valueOf(name);
                } catch (Exception ignored) {
                    return String.valueOf(actor);
                }
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private static void debugBattleSheet(String battleId, String detectedType, Object actor, int skill, boolean wrapper) {
        if (!isDebugEnabled()) return;
        if (!("GYM_LEADER".equals(detectedType) || "TRAINER".equals(detectedType) || "GUILD_BOSS".equals(detectedType) || "WORLD_BOSS".equals(detectedType))) {
            return;
        }

        debug("BattleSheet: battleId=" + battleId + " type=" + detectedType + " skill=" + skill + " wrapper=" + wrapper + " actor=" + actor.getClass().getSimpleName());
        Object pokemonList = readObject(actor, "getPokemonList");
        if (!(pokemonList instanceof Iterable<?> mons)) {
            debug("BattleSheet: unable to read actor pokemon list");
            return;
        }

        int slot = 1;
        for (Object mon : mons) {
            debug("BattleSheet: slot=" + slot++ + " " + pokemonDebugLine(mon));
        }
    }

    private static String pokemonDebugLine(Object battlePokemonOrPokemon) {
        Object pokemon = readObject(battlePokemonOrPokemon, "getEffectedPokemon");
        if (pokemon == null) pokemon = readObject(battlePokemonOrPokemon, "getPokemon");
        if (pokemon == null) pokemon = battlePokemonOrPokemon;

        Object species = readObject(pokemon, "getSpecies");
        Object speciesName = readObject(species, "getName");
        Object form = readObject(pokemon, "getForm");
        Object formName = readObject(form, "getName");
        Object level = readObject(pokemon, "getLevel");
        Object ability = readObject(pokemon, "getAbility");
        Object abilityName = readObject(ability, "getName");
        Object item = readObject(pokemon, "heldItem");
        if (item == null) item = readObject(pokemon, "getHeldItem");

        return "species=" + cleanDebug(speciesName == null ? species : speciesName)
                + " form=" + cleanDebug(formName == null ? form : formName)
                + " level=" + cleanDebug(level)
                + " ability=" + cleanDebug(abilityName == null ? ability : abilityName)
                + " held=" + cleanDebug(item)
                + " moves=" + readMoveList(pokemon);
    }

    private static List<String> readMoveList(Object pokemon) {
        List<String> moves = new ArrayList<>();
        Object moveSet = readObject(pokemon, "getMoveSet");
        if (moveSet instanceof Iterable<?> iterable) {
            for (Object move : iterable) {
                Object name = readObject(move, "getName");
                if (name == null) name = readObject(move, "getId");
                if (name == null) name = readObject(move, "getShowdownId");
                String clean = cleanDebug(name == null ? move : name);
                if (!clean.isBlank() && !"null".equals(clean)) moves.add(clean);
            }
        }
        return moves;
    }

    private static Object readObject(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) return null;
        try { return target.getClass().getMethod(methodName).invoke(target); } catch (Throwable ignored) { return null; }
    }

    private static String cleanDebug(Object value) {
        if (value == null) return "unknown";
        String text = String.valueOf(value).replace("TextComponent{text='", "").replace("', siblings=[], style=Style{} }", "");
        return text.length() > 96 ? text.substring(0, 96) + "..." : text;
    }

    public static boolean isDebugEnabled() {
        return ChampDebugManager.isEnabled(ChampDebugManager.Category.AI);
    }

    public static void debug(String message) {
        ChampDebugManager.log(ChampDebugManager.Category.AI, "[ChampUtils AI] " + message);
    }

    public record LastAssignment(
            String player,
            String battleId,
            String detectedType,
            String actorType,
            int skill,
            boolean wrapperUsed,
            boolean fallbackUsed,
            boolean applied
    ) {
        static LastAssignment empty() {
            return new LastAssignment("none", "none", "none", "none", -1, false, false, false);
        }
    }
}

