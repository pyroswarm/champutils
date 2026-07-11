package com.champutils.breeding;

import com.cobblemon.mod.common.api.events.pokemon.HatchEggEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class BreedingEventBridge {
    private BreedingEventBridge() {}

    public static void postHatch(ServerPlayer player, Pokemon pokemon) {
        if (player == null || pokemon == null) return;
        try {
            HatchEggEvent.Post event = new HatchEggEvent.Post(player, pokemon);
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Field field = eventsClass.getField("HATCH_EGG_POST");
            Object observable = field.get(null);
            for (Method method : observable.getClass().getMethods()) {
                if (!method.getName().equals("post") || method.getParameterCount() < 1) continue;
                try {
                    method.setAccessible(true);
                    if (method.getParameterCount() == 1) {
                        method.invoke(observable, event);
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
            // Hatching must never fail because an optional compatibility event changed shape.
        }
    }
}
