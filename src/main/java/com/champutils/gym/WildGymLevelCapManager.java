package com.champutils.gym;

import com.champutils.commands.WildSpawnCapCommand;
import com.champutils.emblem.EmblemManager;
import com.champutils.profile.IslanderSpawningConfig;
import com.champutils.util.CobblemonEventReflection;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
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

/**
 * Applies the progression cap once, when a genuinely new wild Pokemon entity spawns.
 *
 * This intentionally does not scan loaded entities or revisit Pokemon later. A Pokemon that was
 * already above a player's current cap remains at its original level; the cap only prevents new
 * natural spawns from being created above the nearby player's unlocked range.
 */
public final class WildGymLevelCapManager {
    private static final Set<UUID> PROCESSED = ConcurrentHashMap.newKeySet();
    private static boolean registered = false;

    private WildGymLevelCapManager() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        int subscriptions = 0;
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            for (Object observable : getSpawnObservables(eventsClass)) {
                if (CobblemonEventReflection.subscribe(observable, event -> {
                    try { handleSpawn(event); } catch (Throwable throwable) { throwable.printStackTrace(); }
                })) subscriptions++;
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        System.out.println("[ChampUtils] Wild gym level cap spawn listener registered (" + subscriptions + " observable(s)).");
    }

    private static void handleSpawn(Object event) {
        Entity entity = extractEntity(event);
        if (!(entity instanceof PokemonEntity pokemonEntity) || !(entity.level() instanceof ServerLevel level)) return;
        if (!markProcessed(entity.getUUID()) || !shouldCap(pokemonEntity)) return;

        int cap = strictestNearbyCap(level, entity);
        if (cap <= 0) return;
        Pokemon pokemon = pokemonEntity.getPokemon();
        int before = pokemon.getLevel();
        if (before > cap) pokemon.setLevel(cap);
        if (pokemon.getLevel() != before) entity.addTag("champutils_gym_cap_corrected_on_spawn");
    }

    private static boolean markProcessed(UUID uuid) {
        if (uuid == null) return true;
        if (PROCESSED.size() > 20000) PROCESSED.clear();
        return PROCESSED.add(uuid);
    }

    private static boolean shouldCap(PokemonEntity entity) {
        Pokemon pokemon = entity.getPokemon();
        return pokemon != null && pokemon.isWild() && !hasProtectedTag(entity);
    }

    private static boolean hasProtectedTag(Entity entity) {
        for (String tag : entity.getTags()) {
            String lower = tag.toLowerCase(Locale.ROOT);
            if (lower.contains("champutils_special_spawn")
                    || lower.contains("champutils_mega_boss")
                    || lower.contains("champutils_world_boss")
                    || lower.contains("champutils_guild_boss")
                    || lower.contains("champutils_expedition")) return true;
        }
        return false;
    }

    private static Entity extractEntity(Object event) {
        if (event instanceof Entity entity) return entity;
        Object value = firstValue(event, "pokemonEntity", "getPokemonEntity", "entity", "getEntity", "spawned", "getSpawned");
        if (value instanceof Entity entity) return entity;
        Object holder = firstValue(event, "pokemon", "getPokemon");
        value = firstValue(holder, "entity", "getEntity", "pokemonEntity", "getPokemonEntity");
        return value instanceof Entity entity ? entity : null;
    }

    private static List<Object> getSpawnObservables(Class<?> eventsClass) {
        List<Object> observables = new ArrayList<>();
        for (String name : new String[]{"POKEMON_ENTITY_SPAWN", "POKEMON_ENTITY_SPAWNED", "POKEMON_SPAWNED", "POKEMON_SPAWN"}) {
            addObservable(eventsClass, observables, name);
        }
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
            Object value = eventsClass.getField(name).get(null);
            if (value != null && !observables.contains(value)) observables.add(value);
        } catch (Throwable ignored) {}
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

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (Throwable ignored) { current = current.getSuperclass(); }
        }
        return null;
    }

    private static boolean isSpecialSpecies(String species) {
        return EmblemManager.isLegendary(species) || EmblemManager.isUltraBeast(species)
                || EmblemManager.isParadox(species) || isMythical(species);
    }

    private static boolean isMythical(String species) {
        return switch (species) {
            case "mew", "celebi", "jirachi", "deoxys", "phione", "manaphy", "darkrai", "shaymin", "arceus",
                    "victini", "keldeo", "meloetta", "genesect", "diancie", "hoopa", "volcanion", "magearna",
                    "marshadow", "zeraora", "meltan", "melmetal", "zarude", "pecharunt" -> true;
            default -> false;
        };
    }

    private static String normalizeSpecies(Pokemon pokemon) {
        try {
            if (pokemon.getSpecies() != null && pokemon.getSpecies().getResourceIdentifier() != null)
                return IslanderSpawningConfig.normalize(pokemon.getSpecies().getResourceIdentifier().toString());
            if (pokemon.getSpecies() != null) return IslanderSpawningConfig.normalize(pokemon.getSpecies().getName());
        } catch (Throwable ignored) {}
        return "";
    }

    private static int strictestNearbyCap(ServerLevel level, Entity entity) {
        // Wild entities are shared. Using only the nearest player allowed a high-progression player
        // to create over-cap spawns inside a lower-progression player's area. Enforce the lowest cap
        // of every nearby active player so the same shared spawn is legal for everyone who can see it.
        int cap = Integer.MAX_VALUE;
        boolean found = false;
        double radiusSquared = 192.0D * 192.0D;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.distanceToSqr(entity) > radiusSquared) continue;
            int playerCap = WildSpawnCapCommand.capFor(player);
            if (playerCap <= 0) continue;
            cap = Math.min(cap, playerCap);
            found = true;
        }
        return found ? Math.max(1, Math.min(100, cap)) : 0;
    }
}
