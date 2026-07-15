package com.champutils.specialspawn;

import com.champutils.dex.CatchStreakManager;
import com.champutils.dex.TrueCaughtDexManager;
import com.champutils.emblem.EmblemManager;
import com.champutils.util.CobblemonEventReflection;
import com.champutils.badge.BadgeManager;
import com.champutils.badge.BadgeType;
import com.champutils.gym.GymConfig;
import com.champutils.gym.GymLevelCapUtil;
import com.champutils.profession.ProfessionTrinketManager;
import com.champutils.profile.IslanderSpawnInfluence;
import com.champutils.commands.WildSpawnCapCommand;
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
import java.util.concurrent.ThreadLocalRandom;

/** Blocks natural forbidden spawns and announces shiny spawns near players. */
public final class ForbiddenNaturalPokemonSpawnGuard {
    private static boolean registered = false;
    private static final double SHINY_NOTIFY_RADIUS = 96.0D;

    private ForbiddenNaturalPokemonSpawnGuard() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            int subscriptions = 0;
            for (Object observable : getSpawnObservables(eventsClass)) {
                if (CobblemonEventReflection.subscribe(observable, event -> {
                    try { handleSpawn(event); } catch (Throwable throwable) { throwable.printStackTrace(); }
                })) subscriptions++;
            }
            System.out.println("[ChampUtils] Forbidden natural Pokemon spawn guard registered (" + subscriptions + " observable(s)).");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.out.println("[ChampUtils] Failed to register forbidden natural Pokemon spawn guard.");
        }
    }

    private static void handleSpawn(Object event) {
        Object holder = extractPokemonHolder(event);
        Object pokemon = CatchStreakManager.unwrapPokemon(holder);
        Entity entity = holder instanceof Entity e ? e : event instanceof Entity e ? e : firstEntity(event);
        if (pokemon == null || entity == null || !(entity.level() instanceof ServerLevel level)) return;

        String species = TrueCaughtDexManager.normalizeSpecies(TrueCaughtDexManager.speciesId(pokemon));
        boolean specialSpecies = isForbiddenSpecialSpecies(species);
        boolean allowedSpecial = hasAllowedSpecialTag(entity);
        if (specialSpecies && !allowedSpecial) {
            entity.discard();
            return;
        }
        // Scripted/configured special encounters keep their authored level.
        if (specialSpecies && allowedSpecial) return;

        ServerPlayer nearest = nearestPlayer(level, entity);
        if (nearest != null && !entity.getTags().contains("champutils_spawn_boost_checked")) {
            if (pokemon instanceof Pokemon typedPokemon) WildSpawnCapCommand.applyToWildSpawn(nearest, typedPokemon);
            ProfessionTrinketManager.tryApplyWildSpawnShiny(nearest, pokemon, entity);
            applyLevelCharm(nearest, pokemon);
            entity.addTag("champutils_spawn_boost_checked");
        }

        if (CatchStreakManager.isShiny(pokemon)) {
            announceShiny(level, entity, species);
        }
    }

    private static boolean isForbiddenSpecialSpecies(String species) {
        return EmblemManager.isLegendary(species) || EmblemManager.isUltraBeast(species) || EmblemManager.isParadox(species) || isMythicalByName(species);
    }

    private static boolean isMythicalByName(String species) {
        return switch (species) {
            case "mew", "celebi", "jirachi", "deoxys", "phione", "manaphy", "darkrai", "shaymin", "arceus",
                    "victini", "keldeo", "meloetta", "genesect", "diancie", "hoopa", "volcanion", "magearna",
                    "marshadow", "zeraora", "meltan", "melmetal", "zarude", "pecharunt" -> true;
            default -> false;
        };
    }

    private static boolean hasAllowedSpecialTag(Entity entity) {
        if (entity == null) return false;
        for (String tag : entity.getTags()) {
            String lower = tag.toLowerCase(Locale.ROOT);
            if (lower.contains("champutils_special_spawn") || lower.contains("champutils_mega_boss") || lower.contains("champutils_world_boss") || lower.contains("champutils_guild_boss")) return true;
        }
        return false;
    }


    private static ServerPlayer nearestPlayer(ServerLevel level, Entity entity) {
        ServerPlayer best = null;
        double bestDistance = SHINY_NOTIFY_RADIUS * SHINY_NOTIFY_RADIUS;
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(entity);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static void applyLevelCharm(ServerPlayer player, Object pokemon) {
        double percent = ProfessionTrinketManager.levelCharmGymCapPercent(player);
        if (percent <= 0.0D) return;
        int cap = currentGymCap(player);
        if (cap <= 0) return;

        int minimumLevel = Math.max(1, Math.min(cap, (int)Math.floor(cap * percent)));
        int targetLevel = ThreadLocalRandom.current().nextInt(minimumLevel, cap + 1);
        Object target = CatchStreakManager.unwrapPokemon(pokemon);
        targetLevel = Math.max(readLevel(target), targetLevel);
        if (target instanceof Pokemon typedPokemon) {
            int speciesMinimum = IslanderSpawnInfluence.minimumSpawnLevelForPokemon(typedPokemon);
            if (speciesMinimum <= cap) targetLevel = Math.max(targetLevel, speciesMinimum);
            typedPokemon.setLevel(targetLevel);
        } else {
            setLevel(target, targetLevel);
        }
        // Level Charm level corrections are intentionally quiet to avoid chat spam.
    }

    private static int currentGymCap(ServerPlayer player) {
        return GymLevelCapUtil.currentWildCap(player);
    }

    private static int readLevel(Object pokemon) {
        Object value = firstValue(pokemon, "getLevel", "level");
        return value instanceof Number n ? n.intValue() : 1;
    }

    private static void setLevel(Object pokemon, int level) {
        try { Method method = pokemon.getClass().getMethod("setLevel", int.class); method.invoke(pokemon, level); return; } catch (Throwable ignored) {}
        try { Method method = pokemon.getClass().getMethod("setLevel", Integer.class); method.invoke(pokemon, level); return; } catch (Throwable ignored) {}
        Class<?> c = pokemon.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField("level"); f.setAccessible(true); f.setInt(pokemon, level); return; } catch (Throwable ignored) { c = c.getSuperclass(); }
        }
    }

    private static void announceShiny(ServerLevel level, Entity entity, String species) {
        if (entity.getTags().contains("champutils_shiny_coords_announced")) return;
        ServerPlayer target = nearestPlayer(level, entity);
        if (target == null) return;
        Component msg = Component.literal("A shiny " + pretty(species) + " spawned near you! [X: " + entity.blockPosition().getX() + ", Y: " + entity.blockPosition().getY() + ", Z: " + entity.blockPosition().getZ() + "]").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        target.sendSystemMessage(msg);
        entity.addTag("champutils_shiny_coords_announced");
    }

    private static Object extractPokemonHolder(Object event) {
        Object direct = firstValue(event, "pokemonEntity", "getPokemonEntity", "entity", "getEntity", "pokemon", "getPokemon", "spawned", "getSpawned");
        return direct != null ? direct : event;
    }

    private static Entity firstEntity(Object source) {
        Object value = firstValue(source, "entity", "getEntity", "pokemonEntity", "getPokemonEntity");
        return value instanceof Entity e ? e : null;
    }

    private static List<Object> getSpawnObservables(Class<?> eventsClass) {
        List<Object> observables = new ArrayList<>();
        for (String name : new String[]{"POKEMON_ENTITY_SPAWN", "POKEMON_ENTITY_SPAWNED", "POKEMON_SPAWNED", "POKEMON_SPAWN"}) addObservable(eventsClass, observables, name);
        for (Field field : eventsClass.getFields()) {
            String lower = field.getName().toLowerCase(Locale.ROOT);
            if (!lower.contains("pokemon") || !lower.contains("spawn")) continue;
            if (lower.contains("despawn") || lower.contains("attempt") || lower.contains("fail")) continue;
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
        String[] words = species.replace('_', ' ').split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return builder.toString();
    }
}
