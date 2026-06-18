package com.champutils.megaboss;

import com.champutils.util.CobblemonEventReflection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Defensive reflection hook for Cobblemon capture events. The normal battle item
 * blocker handles player ball use, but this also cancels known/preferred capture
 * observables when the spawned entity is tagged as a ChampUtils Mega Boss.
 */
public final class MegaBossCaptureBlocker {
    private static boolean registered = false;

    private MegaBossCaptureBlocker() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> eventsClass;
            try { eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents"); }
            catch (Throwable ignored) { eventsClass = Class.forName("com.cobblemon.mod.common.CobblemonEvents"); }
            int count = 0;
            for (Object observable : captureObservables(eventsClass)) {
                boolean subscribed = CobblemonEventReflection.subscribe(observable, event -> {
                    try { cancelIfMegaBoss(event); } catch (Throwable ignored) {}
                });
                if (subscribed) count++;
            }
            System.out.println("[ChampUtils] Mega boss capture blocker registered (" + count + " observable(s)).");
        } catch (Throwable throwable) {
            System.out.println("[ChampUtils] Mega boss capture blocker could not reflect Cobblemon capture events; battle ball-use blocking remains active.");
        }
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

    private static void cancelIfMegaBoss(Object event) {
        Entity entity = firstEntity(event);
        if (!MegaBossManager.isMegaBoss(entity)) return;
        setReason(event, Component.literal("Mega Boss Pokémon cannot be caught."));
        cancel(event);
        ServerPlayer player = firstPlayer(event);
        if (player != null) player.sendSystemMessage(Component.literal("§cMega Boss Pokémon cannot be caught. Defeat them for rewards instead."));
        if (entity != null && entity.isRemoved()) return;
    }

    private static Entity firstEntity(Object source) {
        Object direct = firstValue(source, "pokemonEntity", "getPokemonEntity", "entity", "getEntity", "pokemon", "getPokemon", "target", "getTarget");
        if (direct instanceof Entity e) return e;
        Object nested = firstValue(direct, "entity", "getEntity");
        return nested instanceof Entity e ? e : null;
    }

    private static ServerPlayer firstPlayer(Object source) {
        Object value = firstValue(source, "player", "getPlayer", "thrower", "getThrower", "capturer", "getCapturer", "catcher", "getCatcher");
        return value instanceof ServerPlayer p ? p : null;
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            try {
                if (name.startsWith("get")) {
                    Method method = source.getClass().getMethod(name);
                    method.setAccessible(true);
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
                Field cancelled = findField(event.getClass(), fieldName);
                if (cancelled != null) { cancelled.setAccessible(true); cancelled.setBoolean(event, true); }
            } catch (Throwable ignored) {}
        }
        for (String name : List.of("setShouldCapture", "setSuccessful", "setSuccess")) {
            for (Method method : event.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                try { if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == boolean.class) method.invoke(event, false); } catch (Throwable ignored) {}
            }
        }
    }

    private static void setReason(Object event, Component reason) {
        for (String fieldName : List.of("reason", "cancelReason", "message")) {
            try {
                Field field = findField(event.getClass(), fieldName);
                if (field != null && field.getType().isAssignableFrom(reason.getClass())) {
                    field.setAccessible(true);
                    field.set(event, reason);
                    return;
                }
            } catch (Throwable ignored) {}
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); } catch (Throwable ignored) { current = current.getSuperclass(); }
        }
        return null;
    }

    private static Method subscribeMethod(Object observable) {
        if (observable == null) return null;
        for (Method method : observable.getClass().getMethods()) {
            if (method.getName().equals("subscribe") && method.getParameterCount() == 1) return method;
        }
        return null;
    }
}
