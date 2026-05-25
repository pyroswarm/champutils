package com.champutils.dex;

import com.champutils.hunt.PokemonHuntReflection;
import com.champutils.buff.BuffContext;
import com.champutils.buff.BuffManager;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class TrueCaughtDexListener {

    private static boolean registered = false;

    private TrueCaughtDexListener() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Object observable = getCaptureObservable(eventsClass);
            if (observable == null) {
                System.out.println("[ChampUtils] Could not find Cobblemon capture event for true caught dex tracking.");
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
                System.out.println("[ChampUtils] Could not subscribe to Cobblemon capture event for true caught dex tracking.");
                return;
            }

            subscribe.invoke(observable, new Function1<Object, Unit>() {
                @Override
                public Unit invoke(Object event) {
                    try {
                        ServerPlayer player = PokemonHuntReflection.extractPlayer(event);
                        Object pokemon = PokemonHuntReflection.extractPokemon(event);
                        if (player != null && pokemon != null) {
                            TrueCaughtDexManager.markTrueCaught(player, pokemon);
                            CatchStreakManager.handleCatch(player, pokemon);
                            if (pokemon instanceof com.cobblemon.mod.common.pokemon.Pokemon p) {
                                PokemonOriginManager.markOrigin(p, PokemonOriginManager.ORIGIN_WILD_CAPTURE);
                                BuffManager.applyCatchBuffs(BuffContext.trueWildCatch(player, p));
                            }
                        }
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                    return Unit.INSTANCE;
                }
            });

            System.out.println("[ChampUtils] True caught dex capture-only listener registered.");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.out.println("[ChampUtils] Failed to register true caught dex listener.");
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
            } catch (Throwable ignored) {
            }
        }

        for (Field field : eventsClass.getFields()) {
            String lower = field.getName().toLowerCase(java.util.Locale.ROOT);
            if (!lower.contains("capture") && !lower.contains("caught") && !lower.contains("catch")) continue;
            if (lower.contains("pre") || lower.contains("attempt") || lower.contains("fail")) continue;
            try {
                Object value = field.get(null);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }

        return null;
    }
}
