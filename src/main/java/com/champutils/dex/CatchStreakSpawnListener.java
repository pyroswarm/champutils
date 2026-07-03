package com.champutils.dex;

import com.champutils.util.CobblemonEventReflection;
import com.champutils.buff.BuffContext;
import com.champutils.buff.BuffManager;
import com.champutils.buff.BuffType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CatchStreakSpawnListener {
    private static boolean registered = false;
    private static final Set<UUID> PROCESSED_SPAWN_ENTITIES = ConcurrentHashMap.newKeySet();
    private CatchStreakSpawnListener() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            List<Object> observables = getSpawnObservables(eventsClass);
            int subscriptions = 0;
            for (Object observable : observables) {
                boolean subscribed = CobblemonEventReflection.subscribe(observable, event -> {
                    try { handleSpawn(event); } catch (Throwable throwable) { throwable.printStackTrace(); }
                });
                if (subscribed) subscriptions++;
            }
            System.out.println("[ChampUtils] Catch streak shiny spawn listener registered (" + subscriptions + " observable(s)).");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.out.println("[ChampUtils] Failed to register catch streak shiny spawn listener.");
        }
    }

    private static void handleSpawn(Object event) {
        Object pokemonHolder = extractPokemonHolder(event);
        Object pokemon = CatchStreakManager.unwrapPokemon(pokemonHolder);
        if (pokemon == null || CatchStreakManager.isShiny(pokemon)) return;
        Entity entity = pokemonHolder instanceof Entity e ? e : event instanceof Entity e ? e : firstEntity(event);
        if (entity == null || !(entity.level() instanceof ServerLevel level)) return;
        if (!markProcessed(entity.getUUID())) return;
        ServerPlayer buffPlayer = nearestPlayerWithSpawnBuff(level, entity, pokemon);
        if (buffPlayer != null && pokemon instanceof Pokemon cobblemonPokemon) {
            boolean wasShiny = CatchStreakManager.isShiny(pokemon);
            BuffManager.applySpawnBuffs(BuffContext.trueWildCatch(buffPlayer, cobblemonPokemon));
            if (!wasShiny && CatchStreakManager.isShiny(pokemon)) {
                sendShinyCoords(buffPlayer, entity, TrueCaughtDexManager.speciesId(pokemon));
                return;
            }
            if (CatchStreakManager.isShiny(pokemon)) return;
        }

        ServerPlayer player = nearestPlayerWithMatchingStreak(level, entity, pokemon);
        if (player == null) return;
        if (CatchStreakManager.shouldForceShiny(player, pokemon) && CatchStreakManager.setShiny(pokemon, true)) {
            if (CatchStreakManager.CONFIG.announceShinyBoostProc) {
                sendShinyCoords(player, entity, TrueCaughtDexManager.speciesId(pokemon));
            }
        }
    }

    private static boolean markProcessed(UUID uuid) {
        if (uuid == null) return true;
        if (PROCESSED_SPAWN_ENTITIES.size() > 10000) {
            PROCESSED_SPAWN_ENTITIES.clear();
        }
        return PROCESSED_SPAWN_ENTITIES.add(uuid);
    }



    private static void sendShinyCoords(ServerPlayer player, Entity entity, String species) {
        if (player == null || entity == null) return;
        player.sendSystemMessage(Component.literal("A shiny " + pretty(species) + " spawned near you! [X: " + entity.blockPosition().getX() + ", Y: " + entity.blockPosition().getY() + ", Z: " + entity.blockPosition().getZ() + "]")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        entity.addTag("champutils_shiny_coords_announced");
    }

    private static ServerPlayer nearestPlayerWithSpawnBuff(ServerLevel level, Entity entity, Object pokemon) {
        if (!(pokemon instanceof Pokemon cobblemonPokemon)) return null;
        double radius = Math.max(8, CatchStreakManager.CONFIG.nearbyPlayerSpawnRadius);
        double radiusSq = radius * radius;
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(entity);
            if (distance > radiusSq || distance >= bestDistance) continue;
            double shiny = BuffManager.getTotalBuff(BuffContext.trueWildCatch(player, cobblemonPokemon), BuffType.SHINY_CHANCE);
            double perfectIv = BuffManager.getTotalBuff(BuffContext.trueWildCatch(player, cobblemonPokemon), BuffType.PERFECT_IV_CHANCE);
            if (shiny <= 0.0D && perfectIv <= 0.0D) continue;
            best = player;
            bestDistance = distance;
        }
        return best;
    }

    private static ServerPlayer nearestPlayerWithMatchingStreak(ServerLevel level, Entity entity, Object pokemon) {
        String species = TrueCaughtDexManager.speciesId(pokemon);
        double radius = Math.max(8, CatchStreakManager.CONFIG.nearbyPlayerSpawnRadius);
        double radiusSq = radius * radius;
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(entity);
            if (distance > radiusSq || distance >= bestDistance) continue;
            if (CatchStreakManager.getShinyChance(player, species) <= CatchStreakManager.CONFIG.baseShinyChance) continue;
            best = player;
            bestDistance = distance;
        }
        return best;
    }

    private static Object extractPokemonHolder(Object event) {
        Object direct = firstValue(event, "pokemonEntity", "getPokemonEntity", "entity", "getEntity", "pokemon", "getPokemon", "spawned", "getSpawned");
        if (direct != null) return direct;
        return event;
    }

    private static Entity firstEntity(Object source) {
        Object value = firstValue(source, "entity", "getEntity", "pokemonEntity", "getPokemonEntity");
        return value instanceof Entity e ? e : null;
    }

    private static List<Object> getSpawnObservables(Class<?> eventsClass) {
        List<Object> observables = new ArrayList<>();
        String[] preferredNames = { "POKEMON_ENTITY_SPAWN", "POKEMON_ENTITY_SPAWNED", "POKEMON_SPAWNED", "POKEMON_SPAWN" };
        for (String name : preferredNames) addObservable(eventsClass, observables, name);
        for (Field field : eventsClass.getFields()) {
            String lower = field.getName().toLowerCase(Locale.ROOT);
            if (!lower.contains("pokemon") || !lower.contains("spawn")) continue;
            if (lower.contains("despawn") || lower.contains("pre") || lower.contains("attempt") || lower.contains("fail")) continue;
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
        return builder.toString();
    }
}
