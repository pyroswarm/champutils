package com.champutils.hunt;


import com.champutils.util.CobblemonEventReflection;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PokemonHuntCatchListener {

    private static boolean registered = false;

    private PokemonHuntCatchListener() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            List<Object> observables = getCaptureObservables(eventsClass);
            if (observables.isEmpty()) {
                System.out.println("[ChampUtils] Could not find Cobblemon capture event for Pokémon hunts.");
                return;
            }

            int subscriptions = 0;
            for (Object observable : observables) {
                boolean subscribed = CobblemonEventReflection.subscribe(observable, event -> {
                    try {
                        ServerPlayer player = PokemonHuntReflection.extractPlayer(event);
                        Object pokemon = PokemonHuntReflection.extractPokemon(event);
                        if (player != null && pokemon != null) {
                            PokemonHuntManager.handleCatch(player, pokemon);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                if (subscribed) subscriptions++;
            }

            if (subscriptions <= 0) {
                System.out.println("[ChampUtils] Could not subscribe to Cobblemon capture event for Pokémon hunts.");
                return;
            }

            System.out.println("[ChampUtils] Pokémon hunt capture-only listener registered.");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("[ChampUtils] Failed to register Pokémon hunt capture-only listener.");
        }
    }

    private static List<Object> getCaptureObservables(Class<?> eventsClass) {
        List<Object> observables = new ArrayList<>();
        String[] preferredNames = new String[] {
                "POKEMON_CAPTURED",
                "POKEMON_CAPTURED_EVENT",
                "POKEMON_CAPTURED_POST",
                "POKEMON_CAUGHT",
                "POKEMON_CATCH_SUCCEEDED",
                "POKEMON_CAPTURED_EVENT_POST"
        };

        for (String name : preferredNames) {
            try {
                Field field = eventsClass.getField(name);
                Object value = field.get(null);
                if (value != null && !observables.contains(value)) observables.add(value);
            } catch (Exception ignored) {}
        }

        for (Field field : eventsClass.getFields()) {
            String lower = field.getName().toLowerCase(Locale.ROOT);
            if (!lower.contains("capture") && !lower.contains("caught") && !lower.contains("catch")) continue;
            if (lower.contains("pre") || lower.contains("attempt") || lower.contains("fail")) continue;
            try {
                Object value = field.get(null);
                if (value != null && !observables.contains(value)) observables.add(value);
            } catch (Exception ignored) {}
        }

        return observables;
    }
}
