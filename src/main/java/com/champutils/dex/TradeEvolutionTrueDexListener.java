package com.champutils.dex;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TradeEvolutionTrueDexListener {
    private static boolean registered = false;

    private static final Set<String> TRADE_EVOLUTION_RESULTS = Set.of(
            "alakazam", "machamp", "golem", "golem_alola", "gengar", "politoed", "slowking", "slowking_galar",
            "steelix", "scizor", "kingdra", "porygon2", "porygon_z", "huntail", "gorebyss", "milotic",
            "rhyperior", "electivire", "magmortar", "dusknoir", "conkeldurr", "gigalith", "escavalier",
            "accelgor", "trevenant", "gourgeist", "aromatisse", "slurpuff"
    );

    private TradeEvolutionTrueDexListener() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            List<Object> observables = getEvolutionObservables(eventsClass);
            int subscriptions = 0;
            for (Object observable : observables) {
                Method subscribe = findSubscribe(observable);
                if (subscribe == null) continue;
                subscribe.invoke(observable, new Function1<Object, Unit>() {
                    @Override public Unit invoke(Object event) {
                        try { handleEvolutionEvent(event); } catch (Throwable throwable) { throwable.printStackTrace(); }
                        return Unit.INSTANCE;
                    }
                });
                subscriptions++;
            }
            System.out.println("[ChampUtils] Trade evolution true dex listener registered (" + subscriptions + " observable(s)).");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.out.println("[ChampUtils] Failed to register trade evolution true dex listener.");
        }
    }

    public static boolean markTradeEvolution(ServerPlayer player, Object evolvedPokemon) {
        if (player == null || evolvedPokemon == null) return false;
        String species = TrueCaughtDexManager.speciesId(unwrapPokemon(evolvedPokemon));
        if (!TRADE_EVOLUTION_RESULTS.contains(species)) return false;
        boolean added = TrueCaughtDexManager.markTrueCaught(player, evolvedPokemon);
        if (added) {
            player.sendSystemMessage(Component.literal("TrueDex updated: ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(pretty(species)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal(" was registered from a trade evolution.").withStyle(ChatFormatting.GRAY)));
        }
        return added;
    }

    private static void handleEvolutionEvent(Object event) {
        ServerPlayer player = extractPlayer(event);
        Object pokemon = extractPokemon(event);
        if (player != null && pokemon != null) markTradeEvolution(player, pokemon);
    }

    private static ServerPlayer extractPlayer(Object event) {
        Object player = firstValue(event, "player", "getPlayer", "owner", "getOwner", "trainer", "getTrainer", "evolver", "getEvolver");
        if (player instanceof ServerPlayer serverPlayer) return serverPlayer;
        Object pokemon = extractPokemon(event);
        Object owner = firstValue(pokemon, "ownerPlayer", "getOwnerPlayer", "player", "getPlayer");
        return owner instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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
        List<Object> observables = new ArrayList<>();
        String[] preferredNames = {
                "POKEMON_EVOLVED", "POKEMON_EVOLUTION", "POKEMON_EVOLUTION_COMPLETE", "POKEMON_EVOLUTION_POST",
                "EVOLUTION_COMPLETE", "EVOLUTION_COMPLETED", "EVOLUTION_POST"
        };
        for (String name : preferredNames) addObservable(eventsClass, observables, name);
        for (Field field : eventsClass.getFields()) {
            String lower = field.getName().toLowerCase(Locale.ROOT);
            if (!lower.contains("evol")) continue;
            if (lower.contains("pre") || lower.contains("start") || lower.contains("attempt") || lower.contains("fail") || lower.contains("cancel")) continue;
            try {
                Object value = field.get(null);
                if (value != null && !observables.contains(value)) observables.add(value);
            } catch (Throwable ignored) {}
        }
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
