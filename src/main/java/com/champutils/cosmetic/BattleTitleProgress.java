package com.champutils.cosmetic;

import com.champutils.database.ProfileBattleStatisticsRepository;
import com.champutils.profile.PlayerProfileManager;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Collection;

/** Lightweight persisted counters used by battle-related title milestones. */
public final class BattleTitleProgress {
    private BattleTitleProgress() {}

    public static void onPokemonFainted(BattleFaintedEvent event) {
        if (event == null || event.getKilled() == null) return;
        Object killed = event.getKilled();
        Object killedActor = invoke(killed, "getActor");
        boolean killedBelongsToPlayer = killedActor instanceof PlayerBattleActor;
        if (killedBelongsToPlayer) return;

        int defeatedLevel = pokemonLevel(killed);
        boolean wild = isWildActor(killedActor);

        for (Object actor : event.getBattle().getActors()) {
            if (!(actor instanceof PlayerBattleActor playerActor)) continue;
            ServerPlayer player = (ServerPlayer) playerActor.getEntity();
            if (player == null || actorOwns(actor, killed)) continue;

            int activeLevel = lowestActiveLevel(actor);
            int gap = defeatedLevel > 0 && activeLevel > 0 ? defeatedLevel - activeLevel : 0;
            var profileId = PlayerProfileManager.activeProfileId(player.getUUID());
            ProfileBattleStatisticsRepository.recordDefeat(profileId, wild, gap)
                    .thenAccept(snapshot -> player.getServer().execute(() -> evaluate(player, snapshot)));
        }
    }

    private static void evaluate(ServerPlayer player, ProfileBattleStatisticsRepository.Snapshot data) {
        for (TitleConfig.TitleDef def : TitleConfig.titles()) {
            if (def == null || def.unlock == null || !"battle_counter".equalsIgnoreCase(def.unlock.type)) continue;
            long value = switch (String.valueOf(def.unlock.key).toLowerCase()) {
                case "wild_defeats" -> data.wildPokemonDefeats();
                case "pokemon_defeats" -> data.pokemonDefeats();
                case "level_gap" -> data.highestLevelGapVictory();
                default -> 0L;
            };
            if (value >= Math.max(1, def.unlock.level)) TitleManager.unlock(player, def.id);
        }
    }

    private static boolean isWildActor(Object actor) {
        if (actor == null) return false;
        String name = actor.getClass().getName().toLowerCase();
        return name.contains("wild") || name.contains("pokemonbattleactor");
    }

    private static boolean actorOwns(Object actor, Object killed) {
        Object list = invoke(actor, "getPokemonList");
        return list instanceof Collection<?> c && c.contains(killed);
    }

    private static int lowestActiveLevel(Object actor) {
        Object active = invoke(actor, "getActivePokemon");
        int best = Integer.MAX_VALUE;
        if (active instanceof Collection<?> collection) {
            for (Object ap : collection) {
                Object bp = invoke(ap, "getBattlePokemon");
                int level = pokemonLevel(bp);
                if (level > 0) best = Math.min(best, level);
            }
        }
        if (best != Integer.MAX_VALUE) return best;
        Object list = invoke(actor, "getPokemonList");
        if (list instanceof Collection<?> collection) {
            for (Object bp : collection) {
                int level = pokemonLevel(bp);
                if (level > 0) best = Math.min(best, level);
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private static int pokemonLevel(Object battlePokemon) {
        if (battlePokemon == null) return 0;
        Object pokemon = invoke(battlePokemon, "getOriginalPokemon");
        if (pokemon == null) pokemon = invoke(battlePokemon, "getEffectedPokemon");
        if (pokemon == null) pokemon = invoke(battlePokemon, "getPokemon");
        Object level = invoke(pokemon, "getLevel");
        return level instanceof Number n ? n.intValue() : 0;
    }

    private static Object invoke(Object target, String method) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
