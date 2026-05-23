package com.champutils.hunt;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class PokemonHuntCatchListener {

    private static boolean registered = false;

    private PokemonHuntCatchListener() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Object observable = getCaptureObservable(eventsClass);
            if (observable == null) {
                System.out.println("[ChampUtils] Could not find Cobblemon capture event for Pokémon hunts. Hunts will not complete from trades/evolutions.");
                return;
            }

            Method subscribe = null;
            for (Method method : observable.getClass().getMethods()) {
                if (!method.getName().equals("subscribe")) continue;
                if (method.getParameterCount() == 1) {
                    subscribe = method;
                    break;
                }
            }

            if (subscribe == null) {
                System.out.println("[ChampUtils] Could not subscribe to Cobblemon capture event for Pokémon hunts.");
                return;
            }

            subscribe.invoke(observable, new Function1<Object, Unit>() {
                @Override
                public Unit invoke(Object event) {
                    try {
                        ServerPlayer player = PokemonHuntReflection.extractPlayer(event);
                        Object pokemon = PokemonHuntReflection.extractPokemon(event);
                        if (player != null && pokemon != null) {
                            PokemonHuntManager.handleCatch(player, pokemon);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return Unit.INSTANCE;
                }
            });

            System.out.println("[ChampUtils] Pokémon hunt capture-only listener registered.");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("[ChampUtils] Failed to register Pokémon hunt capture-only listener.");
        }
    }

    private static Object getCaptureObservable(Class<?> eventsClass) {
        String[] preferredNames = new String[] {
                "POKEMON_CAPTURED",
                "POKEMON_CAPTURED_EVENT",
                "POKEMON_CAPTURED_POST",
                "POKEMON_CAUGHT",
                "POKEMON_CATCH_SUCCEEDED"
        };

        for (String name : preferredNames) {
            try {
                Field field = eventsClass.getField(name);
                Object value = field.get(null);
                if (value != null) return value;
            } catch (Exception ignored) {}
        }

        for (Field field : eventsClass.getFields()) {
            String lower = field.getName().toLowerCase();
            if (!lower.contains("capture") && !lower.contains("caught") && !lower.contains("catch")) continue;
            if (lower.contains("pre") || lower.contains("attempt") || lower.contains("fail")) continue;
            try {
                Object value = field.get(null);
                if (value != null) return value;
            } catch (Exception ignored) {}
        }

        return null;
    }
}
