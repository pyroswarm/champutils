package com.champutils.antilag;

import com.champutils.util.CobblemonEventReflection;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CatchAttemptProtectionListener {
    private static final long PROTECT_MS = 5L * 60L * 1000L;
    private static boolean registered = false;

    private CatchAttemptProtectionListener() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            int subscriptions = 0;
            for (Object observable : captureObservables(eventsClass)) {
                if (CobblemonEventReflection.subscribe(observable, event -> {
                    Entity entity = extractEntity(event);
                    if (entity != null) protect(entity);
                })) subscriptions++;
            }
            System.out.println("[ChampUtils] Catch cleanup protection listener registered (" + subscriptions + " capture hooks).");
        } catch (Throwable t) {
            System.out.println("[ChampUtils] Catch cleanup protection listener could not register; cleanup still protects visible capture states.");
        }
    }

    private static List<Object> captureObservables(Class<?> eventsClass) {
        List<Object> out = new ArrayList<>();
        for (Field field : eventsClass.getFields()) {
            String lower = field.getName().toLowerCase(Locale.ROOT);
            if (!(lower.contains("capture") || lower.contains("catch") || lower.contains("pokeball") || lower.contains("poke_ball"))) continue;
            try {
                Object value = field.get(null);
                if (value != null && !out.contains(value)) out.add(value);
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static Entity extractEntity(Object event) {
        Object direct = firstValue(event, "entity", "getEntity", "pokemonEntity", "getPokemonEntity");
        if (direct instanceof Entity entity) return entity;
        Object pokemon = firstValue(event, "pokemon", "getPokemon", "captured", "getCaptured");
        Object fromPokemon = firstValue(pokemon, "entity", "getEntity");
        return fromPokemon instanceof Entity entity ? entity : null;
    }

    private static Object firstValue(Object src, String... names) {
        if (src == null) return null;
        for (String name : names) {
            try {
                if (name.startsWith("get") || name.startsWith("is")) {
                    Method method = src.getClass().getMethod(name);
                    method.setAccessible(true);
                    Object value = method.invoke(src);
                    if (value != null) return value;
                } else {
                    Field field = src.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    Object value = field.get(src);
                    if (value != null) return value;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void protect(Entity entity) {
        long until = System.currentTimeMillis() + PROTECT_MS;
        entity.getTags().removeIf(tag -> tag != null && tag.toLowerCase(Locale.ROOT).startsWith("champutils_catch_protected_until_"));
        entity.addTag("champutils_catch_protected_until_" + until);
    }
}
