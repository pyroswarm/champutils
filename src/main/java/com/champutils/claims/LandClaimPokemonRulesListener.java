package com.champutils.claims;

import com.champutils.util.CobblemonEventReflection;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Claim-specific Cobblemon rules kept separate from ordinary block/entity protection. */
public final class LandClaimPokemonRulesListener {
    private static boolean registered;

    private LandClaimPokemonRulesListener() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(world instanceof ServerLevel level) || !(entity instanceof PokemonEntity pokemonEntity)) return;
            LandClaimRepository.Claim claim = LandClaimRepository.findAt(level, entity.blockPosition());
            if (claim == null || claim.pokemonSpawningEnabled || !isUnownedWild(pokemonEntity)) return;
            entity.discard();
        });

        try {
            Class<?> eventsClass;
            try { eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents"); }
            catch (Throwable ignored) { eventsClass = Class.forName("com.cobblemon.mod.common.CobblemonEvents"); }
            int count = 0;
            for (Object observable : captureObservables(eventsClass)) {
                if (CobblemonEventReflection.subscribe(observable, LandClaimPokemonRulesListener::handleCaptureEvent)) count++;
            }
            System.out.println("[ChampUtils] Land claim Pokémon capture rules registered (" + count + " observable(s)).");
        } catch (Throwable throwable) {
            System.err.println("[ChampUtils] Could not reflect Cobblemon capture events for land claims: " + throwable.getMessage());
        }
    }

    private static boolean isUnownedWild(PokemonEntity entity) {
        try {
            if (entity.getOwnerUUID() != null || entity.getTethering() != null) return false;
            var pokemon = entity.getPokemon();
            return pokemon != null && pokemon.isWild() && !pokemon.isPlayerOwned() && !pokemon.isNPCOwned() && pokemon.getOwnerUUID() == null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void handleCaptureEvent(Object event) {
        Entity entity = firstEntity(event);
        ServerPlayer player = firstPlayer(event);
        if (entity == null || player == null || !(entity.level() instanceof ServerLevel level)) return;
        LandClaimRepository.Claim claim = LandClaimRepository.findAt(level, entity.blockPosition());
        if (claim == null || LandClaimRepository.canCatchPokemon(player, claim)) return;
        cancel(event);
        player.sendSystemMessage(Component.literal("§cYou cannot catch Pokémon inside " + claim.ownerName + "'s claim."));
    }

    private static List<Object> captureObservables(Class<?> eventsClass) {
        List<Object> observables = new ArrayList<>();
        for (Field field : eventsClass.getFields()) {
            String lower = field.getName().toLowerCase(Locale.ROOT);
            if (!(lower.contains("capture") || lower.contains("catch") || lower.contains("caught") || lower.contains("pokeball"))) continue;
            if (lower.contains("fail")) continue;
            try {
                Object value = field.get(null);
                if (value != null && !observables.contains(value)) observables.add(value);
            } catch (Throwable ignored) {}
        }
        return observables;
    }

    private static Entity firstEntity(Object source) {
        Object direct = firstValue(source, "pokemonEntity", "getPokemonEntity", "entity", "getEntity", "pokemon", "getPokemon", "target", "getTarget");
        if (direct instanceof Entity entity) return entity;
        Object nested = firstValue(direct, "entity", "getEntity");
        return nested instanceof Entity entity ? entity : null;
    }

    private static ServerPlayer firstPlayer(Object source) {
        Object value = firstValue(source, "player", "getPlayer", "thrower", "getThrower", "capturer", "getCapturer", "catcher", "getCatcher");
        return value instanceof ServerPlayer player ? player : null;
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            try {
                if (name.startsWith("get")) {
                    Method method = source.getClass().getMethod(name);
                    if (method.getParameterCount() == 0) {
                        Object value = method.invoke(source);
                        if (value != null) return value;
                    }
                } else {
                    Field field = findField(source.getClass(), name);
                    if (field != null) {
                        field.setAccessible(true);
                        Object value = field.get(source);
                        if (value != null) return value;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void cancel(Object event) {
        if (event == null) return;
        for (String name : List.of("cancel", "setCanceled", "setCancelled")) {
            for (Method method : event.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                try {
                    if (method.getParameterCount() == 0) { method.invoke(event); return; }
                    if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == boolean.class) { method.invoke(event, true); return; }
                } catch (Throwable ignored) {}
            }
        }
        for (String fieldName : List.of("cancelled", "canceled")) {
            try {
                Field field = findField(event.getClass(), fieldName);
                if (field != null) { field.setAccessible(true); field.setBoolean(event, true); return; }
            } catch (Throwable ignored) {}
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (Throwable ignored) { current = current.getSuperclass(); }
        }
        return null;
    }
}
