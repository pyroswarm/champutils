package com.champutils.buff;

import com.champutils.dex.PokemonOriginManager;
import com.champutils.dex.CatchStreakManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Central MMO buff pipeline.
 *
 * All systems should register BuffProvider instances here. Guilds are only one
 * provider. Cash shop server boosters, events, ranks, consumables, territories,
 * and future systems can stack safely through this same manager.
 */
public final class BuffManager {
    private static final List<BuffProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Long> PROCESSED_CATCHES = new ConcurrentHashMap<>();
    private static final long PROCESSED_TTL_MS = 120_000L;

    private BuffManager() {}

    public static void registerProvider(BuffProvider provider) {
        if (provider == null || provider.id() == null || provider.id().isBlank()) return;
        PROVIDERS.removeIf(existing -> existing.id().equalsIgnoreCase(provider.id()));
        PROVIDERS.add(provider);
        PROVIDERS.sort(Comparator
                .comparingInt(BuffProvider::priority)
                .thenComparing(source -> source.id().toLowerCase(Locale.ROOT))
        );
    }

    public static void unregisterProvider(String id) {
        if (id == null || id.isBlank()) return;
        PROVIDERS.removeIf(existing -> existing.id().equalsIgnoreCase(id));
    }

    public static void clearProviders() {
        PROVIDERS.clear();
    }

    public static double getTotalBuff(BuffContext context, BuffType type) {
        if (context == null || type == null || !context.allows(type)) return 0.0D;
        double total = 0.0D;
        for (BuffProvider provider : PROVIDERS) {
            try {
                total += Math.max(0.0D, provider.getBuff(context, type));
            } catch (Exception exception) {
                System.err.println("[ChampUtils] Buff provider failed: " + provider.id() + " / " + type.name());
                exception.printStackTrace();
            }
        }
        return Math.max(0.0D, Math.min(BuffRegistry.hardCap(type), total));
    }

    public static List<String> debugBreakdown(BuffContext context, BuffType type) {
        List<String> lines = new ArrayList<>();
        if (context == null || type == null || !context.allows(type)) return lines;
        for (BuffProvider provider : PROVIDERS) {
            double bonus = 0.0D;
            try {
                bonus = Math.max(0.0D, provider.getBuff(context, type));
            } catch (Exception ignored) {
            }
            if (bonus > 0.0D) lines.add(provider.id() + ": +" + percent(bonus));
        }
        double total = getTotalBuff(context, type);
        if (total > 0.0D) lines.add("total: +" + percent(total));
        return lines;
    }


    /**
     * Framework hook for future capture-rate integrations.
     * Returns decimal bonus from active providers, e.g. 0.10D = +10% catch chance.
     */
    public static double getCatchChanceBonus(ServerPlayer player, Pokemon pokemon) {
        return getTotalBuff(BuffContext.trueWildCatch(player, pokemon), BuffType.CATCH_CHANCE);
    }

    public static void applyCatchBuffs(ServerPlayer player, Pokemon pokemon) {
        applyCatchBuffs(BuffContext.trueWildCatch(player, pokemon));
    }


    /** Applies shiny/perfect-IV buff rolls when a legitimate wild Pokémon spawns near the player. */
    public static void applySpawnBuffs(BuffContext context) {
        applyCatchBuffs(context);
    }

    public static void applyCatchBuffs(BuffContext context) {
        if (context == null || !context.allowsPokemonCatchBuffs()) return;
        ServerPlayer player = context.player;
        Pokemon pokemon = context.pokemon;

        UUID pokemonUuid = pokemonUuid(pokemon);
        if (pokemonUuid != null && !markProcessed(pokemonUuid)) return;

        boolean changed = false;
        double shinyMultiplierBonus = getTotalBuff(context, BuffType.SHINY_CHANCE);
        double shinyProcChance = relativeShinyProcChance(shinyMultiplierBonus);
        if (shinyProcChance > 0.0D && ThreadLocalRandom.current().nextDouble() < shinyProcChance && !isShiny(pokemon)) {
            changed = setShiny(pokemon, true) || changed;
            player.sendSystemMessage(Component.literal("[Buff] A shiny chance buff affected a wild spawn!").withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        double perfectIvBonus = getTotalBuff(context, BuffType.PERFECT_IV_CHANCE);
        int battlingLevel = Math.max(1, com.champutils.profession.ProfessionManager.getLevel(player, com.champutils.profession.ProfessionType.BATTLING));
        perfectIvBonus += Math.min(0.10D, (battlingLevel / 10) * 0.01D);
        if (perfectIvBonus > 0.0D && ThreadLocalRandom.current().nextDouble() < perfectIvBonus) {
            String stat = upgradeRandomIvToPerfect(pokemon);
            if (stat != null) {
                changed = true;
                player.sendSystemMessage(Component.literal("[Buff] A buff perfected " + stat + " on this catch!").withStyle(ChatFormatting.AQUA));
            }
        }

        if (changed) {
            PokemonOriginManager.markOrigin(pokemon, PokemonOriginManager.ORIGIN_WILD_CAPTURE);
        }
    }

    /**
     * Shiny buffs are relative multipliers, not flat shiny odds.
     * Example: 0.01D means the current/base shiny chance is increased by 1%,
     * so normal 1/4096 odds only gain an extra 1/409600 roll.
     */
    private static double relativeShinyProcChance(double multiplierBonus) {
        if (multiplierBonus <= 0.0D) return 0.0D;
        double baseChance = 1.0D / 4096.0D;
        try {
            if (CatchStreakManager.CONFIG != null && CatchStreakManager.CONFIG.baseShinyChance > 0.0D) {
                baseChance = CatchStreakManager.CONFIG.baseShinyChance;
            }
        } catch (Throwable ignored) {
        }
        return Math.max(0.0D, Math.min(1.0D, baseChance * multiplierBonus));
    }

    public static String percent(double value) {
        double percent = Math.max(0.0D, value) * 100.0D;
        if (percent >= 1.0D) return String.format(Locale.US, "%.2f%%", percent).replaceAll("0+%$", "%").replace(".%", "%");
        return String.format(Locale.US, "%.3f%%", percent).replaceAll("0+%$", "%").replace(".%", "%");
    }

    private static boolean markProcessed(UUID pokemonUuid) {
        long now = System.currentTimeMillis();
        PROCESSED_CATCHES.entrySet().removeIf(entry -> now - entry.getValue() > PROCESSED_TTL_MS);
        return PROCESSED_CATCHES.putIfAbsent(pokemonUuid, now) == null;
    }

    private static boolean isShiny(Pokemon pokemon) {
        Object value = firstValue(pokemon, "getShiny", "isShiny", "shiny");
        if (value instanceof Boolean bool) return bool;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static boolean setShiny(Pokemon pokemon, boolean shiny) {
        for (String methodName : new String[] { "setShiny" }) {
            try {
                Method method = pokemon.getClass().getMethod(methodName, boolean.class);
                method.setAccessible(true);
                method.invoke(pokemon, shiny);
                return true;
            } catch (Throwable ignored) {
            }
        }
        try {
            Field field = findField(pokemon.getClass(), "shiny");
            if (field != null) {
                field.setAccessible(true);
                field.setBoolean(pokemon, shiny);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String upgradeRandomIvToPerfect(Pokemon pokemon) {
        Object ivs = firstValue(pokemon, "getIvs", "getIVs", "ivs", "ivStore");
        if (ivs == null) return null;

        Map<String, Object> statObjects = new LinkedHashMap<>();
        for (String stat : new String[] { "hp", "attack", "defence", "defense", "special_attack", "specialAttack", "special_defence", "specialDefense", "speed" }) {
            Object statKey = findStatObject(stat);
            int value = getIvValue(ivs, stat, statKey);
            if (value >= 0 && value < 31) {
                statObjects.put(stat, statKey == null ? stat : statKey);
            }
        }

        if (statObjects.isEmpty()) return null;
        List<Map.Entry<String, Object>> eligible = new ArrayList<>(statObjects.entrySet());
        Map.Entry<String, Object> picked = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        if (setIvValue(ivs, picked.getKey(), picked.getValue(), 31)) {
            return prettyStat(picked.getKey());
        }
        return null;
    }

    private static int getIvValue(Object ivs, String statName, Object statObject) {
        for (String methodName : new String[] { "get", "getOrDefault" }) {
            try {
                Method method = ivs.getClass().getMethod(methodName, statObject == null ? String.class : statObject.getClass());
                Object value = method.invoke(ivs, statObject == null ? statName : statObject);
                Integer parsed = asInt(value);
                if (parsed != null) return parsed;
            } catch (Throwable ignored) {
            }
        }
        Object fieldValue = firstValue(ivs, statName, statName.toUpperCase(Locale.ROOT));
        Integer parsed = asInt(fieldValue);
        return parsed == null ? -1 : parsed;
    }

    private static boolean setIvValue(Object ivs, String statName, Object statObject, int value) {
        for (Method method : ivs.getClass().getMethods()) {
            if (!method.getName().equals("set") && !method.getName().equals("put")) continue;
            if (method.getParameterCount() != 2) continue;
            try {
                method.setAccessible(true);
                Class<?> first = method.getParameterTypes()[0];
                Object key = statObject != null && first.isInstance(statObject) ? statObject : statName;
                if (first == String.class || first.isInstance(key)) {
                    method.invoke(ivs, key, value);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Field field = findField(ivs.getClass(), statName);
            if (field != null) {
                field.setAccessible(true);
                field.set(ivs, value);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Object findStatObject(String statName) {
        for (String className : new String[] {
                "com.cobblemon.mod.common.api.pokemon.stats.Stats",
                "com.cobblemon.mod.common.api.pokemon.stats.Stat"
        }) {
            try {
                Class<?> clazz = Class.forName(className);
                for (String fieldName : new String[] { statName, statName.toUpperCase(Locale.ROOT), statName.replace("_", "").toUpperCase(Locale.ROOT) }) {
                    try {
                        Field field = clazz.getField(fieldName);
                        field.setAccessible(true);
                        Object value = field.get(null);
                        if (value != null) return value;
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String prettyStat(String stat) {
        String normalized = stat.toLowerCase(Locale.ROOT).replace("defense", "defence");
        return switch (normalized) {
            case "hp" -> "HP";
            case "attack" -> "Attack";
            case "defence" -> "Defense";
            case "special_attack", "specialattack" -> "Sp. Atk";
            case "special_defence", "specialdefense" -> "Sp. Def";
            case "speed" -> "Speed";
            default -> stat;
        };
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try { return Integer.parseInt(String.valueOf(value)); } catch (Throwable ignored) { return null; }
    }

    private static UUID pokemonUuid(Pokemon pokemon) {
        Object value = firstValue(pokemon, "getUuid", "getUUID", "uuid");
        if (value instanceof UUID uuid) return uuid;
        if (value != null) {
            try { return UUID.fromString(String.valueOf(value)); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            Object value = call(source, name);
            if (value == null) value = field(source, name);
            if (value != null) return value;
        }
        return null;
    }

    private static Object call(Object source, String methodName) {
        try {
            Method method = source.getClass().getMethod(methodName);
            method.setAccessible(true);
            if (method.getParameterCount() == 0) return method.invoke(source);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object field(Object source, String fieldName) {
        try {
            Field field = findField(source.getClass(), fieldName);
            if (field == null) return null;
            field.setAccessible(true);
            return field.get(source);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getDeclaredField(name);
            } catch (Throwable ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

}