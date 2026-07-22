package com.champutils.dex;

import com.champutils.util.CobblemonEventReflection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Locale;

public final class TradeEvolutionTrueDexListener {
    private static boolean registered = false;


    private TradeEvolutionTrueDexListener() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            List<Object> observables = getEvolutionObservables(eventsClass);
            int subscriptions = 0;
            for (Object observable : observables) {
                boolean subscribed = CobblemonEventReflection.subscribe(observable, event -> {
                    try { handleEvolutionEvent(event); } catch (Throwable throwable) { throwable.printStackTrace(); }
                });
                if (subscribed) subscriptions++;
            }
            System.out.println("[ChampUtils] Evolution TrueDex listener registered (" + subscriptions + " observable(s)).");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.out.println("[ChampUtils] Failed to register evolution TrueDex listener.");
        }
    }

    public static boolean markTradeEvolution(ServerPlayer player, Object evolvedPokemon) {
        if (player == null || evolvedPokemon == null) return false;
        String species = TrueCaughtDexManager.speciesId(unwrapPokemon(evolvedPokemon));
        boolean added = TrueCaughtDexManager.markTrueCaught(player, evolvedPokemon);
        if (added) {
            player.sendSystemMessage(Component.literal("TrueDex updated: ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(pretty(species)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal(" was registered from evolution.").withStyle(ChatFormatting.GRAY)));
        }
        return added;
    }

    private static void handleEvolutionEvent(Object event) {
        Object pokemon = extractPokemon(event);
        Object sourcePokemon = unwrapPokemon(firstValue(event, "sourcePokemon", "getSourcePokemon", "source_pokemon"));
        if (pokemon == null) return;

        // Defensive guard in case a future Cobblemon version reuses the completion event without
        // changing species. TrueDex is species-based, so same-species form changes do not add a row.
        String resultSpecies = TrueCaughtDexManager.speciesId(pokemon);
        String sourceSpecies = TrueCaughtDexManager.speciesId(sourcePokemon);
        if (resultSpecies == null || resultSpecies.isBlank()) return;
        if (sourcePokemon != null && resultSpecies.equalsIgnoreCase(sourceSpecies)) return;

        ServerPlayer player = extractPlayer(event, pokemon, sourcePokemon);
        if (player != null) {
            markTradeEvolution(player, pokemon);
            return;
        }

        // Some storage-backed evolutions fire before Cobblemon has finished restoring the
        // owner/store coordinates. Retry once on the server thread instead of silently losing it.
        var server = com.champutils.battle.ServerLifecycleBridge.getServer();
        if (server == null) return;
        CompletableFuture.runAsync(
                () -> server.execute(() -> {
                    ServerPlayer delayedOwner = extractPlayer(event, pokemon, sourcePokemon);
                    if (delayedOwner != null) markTradeEvolution(delayedOwner, pokemon);
                }),
                CompletableFuture.delayedExecutor(500L, TimeUnit.MILLISECONDS)
        );
    }

    private static ServerPlayer extractPlayer(Object event, Object pokemon, Object sourcePokemon) {
        Object player = firstValue(event, "player", "getPlayer", "owner", "getOwner", "trainer", "getTrainer", "evolver", "getEvolver");
        if (player instanceof ServerPlayer serverPlayer) return serverPlayer;

        for (Object candidate : List.of(pokemon, sourcePokemon == null ? pokemon : sourcePokemon)) {
            Object owner = firstValue(candidate, "ownerPlayer", "getOwnerPlayer", "player", "getPlayer");
            if (owner instanceof ServerPlayer serverPlayer) return serverPlayer;
        }

        UUID ownerUuid = extractOwnerUuid(sourcePokemon);
        if (ownerUuid == null) ownerUuid = extractOwnerUuid(pokemon);
        var server = com.champutils.battle.ServerLifecycleBridge.getServer();
        return ownerUuid == null || server == null ? null : server.getPlayerList().getPlayer(ownerUuid);
    }

    private static UUID extractOwnerUuid(Object pokemon) {
        Object raw = firstValue(pokemon, "ownerUUID", "getOwnerUUID", "ownerUuid", "getOwnerUuid");
        if (raw instanceof UUID uuid) return uuid;
        try { return raw == null ? null : UUID.fromString(String.valueOf(raw)); }
        catch (Exception ignored) { return null; }
    }

    private static Object extractPokemon(Object event) {
        Object pokemon = firstValue(event,
                "pokemon", "getPokemon", "evolvedPokemon", "getEvolvedPokemon", "result", "getResult",
                "pokemonEntity", "getPokemonEntity", "entity", "getEntity"
        );
        return unwrapPokemon(pokemon == null ? event : pokemon);
    }

    private static Object unwrapPokemon(Object value) {
        if (value == null) return null;
        Object nested = firstValue(value, "pokemon", "getPokemon");
        return nested == null ? value : nested;
    }

    private static List<Object> getEvolutionObservables(Class<?> eventsClass) {
        // Cobblemon 1.7.3 fires several evolution lifecycle events. Only EVOLUTION_COMPLETE
        // guarantees that the species has actually changed. Subscribing to display/tested events
        // caused TrueDex messages when an evolution prompt was merely checked or shown.
        List<Object> observables = new ArrayList<>();
        addObservable(eventsClass, observables, "EVOLUTION_COMPLETE");
        return observables;
    }

    private static void addObservable(Class<?> eventsClass, List<Object> observables, String name) {
        try {
            Field field = eventsClass.getField(name);
            Object value = field.get(null);
            if (value != null && !observables.contains(value)) observables.add(value);
        } catch (Throwable ignored) {}
    }

    private static Method findSubscribe(Object observable) {
        if (observable == null) return null;
        for (Method method : observable.getClass().getMethods()) {
            if (method.getName().equals("subscribe") && method.getParameterCount() == 1) return method;
        }
        return null;
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

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); } catch (Throwable ignored) { current = current.getSuperclass(); }
        }
        return null;
    }

    private static String pretty(String species) {
        String[] words = TrueCaughtDexManager.normalizeSpecies(species).replace('_', ' ').split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return builder.length() == 0 ? species : builder.toString();
    }
}
