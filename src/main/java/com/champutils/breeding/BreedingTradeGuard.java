package com.champutils.breeding;

import com.champutils.util.CobblemonEventReflection;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class BreedingTradeGuard {
    private static boolean registered;

    private BreedingTradeGuard() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Object tradeObservable = eventsClass.getField("TRADE_EVENT_PRE").get(null);
            Object releaseObservable = eventsClass.getField("POKEMON_RELEASED_EVENT_PRE").get(null);
            boolean tradeRegistered = CobblemonEventReflection.subscribe(tradeObservable, BreedingTradeGuard::handleTrade);
            boolean releaseRegistered = CobblemonEventReflection.subscribe(releaseObservable, BreedingTradeGuard::handleRelease);
            if (!tradeRegistered || !releaseRegistered) {
                throw new IllegalStateException("No compatible subscribe method found for one or more Egg safety events.");
            }
            System.out.println("[ChampUtils][Breeding] Egg trade/release guards registered.");
        } catch (Throwable error) {
            System.err.println("[ChampUtils][Breeding] Failed to register Egg trade/release guards.");
            error.printStackTrace();
        }
    }

    private static void handleTrade(Object event) {
        Pokemon first = pokemon(event, "tradeParticipant1Pokemon", "getTradeParticipant1Pokemon");
        Pokemon second = pokemon(event, "tradeParticipant2Pokemon", "getTradeParticipant2Pokemon");
        if (!BreedingEggData.isEgg(first) && !BreedingEggData.isEgg(second)) return;
        cancel(event);
        Component message = Component.literal("Pokémon Eggs cannot be directly traded before they hatch. Use the Auction House to sell an Egg safely.").withStyle(ChatFormatting.RED);
        notifyParticipant(value(event, "tradeParticipant1", "getTradeParticipant1"), message);
        notifyParticipant(value(event, "tradeParticipant2", "getTradeParticipant2"), message);
    }

    private static void handleRelease(Object event) {
        Pokemon pokemon = pokemon(event, "pokemon", "getPokemon");
        if (!BreedingEggData.isEgg(pokemon)) return;
        cancel(event);
        Component message = Component.literal("Pokémon Eggs cannot be released before they hatch.").withStyle(ChatFormatting.RED);
        notifyParticipant(value(event, "player", "getPlayer"), message);
    }

    private static Pokemon pokemon(Object source, String field, String getter) {
        Object value = value(source, field, getter);
        return value instanceof Pokemon pokemon ? pokemon : null;
    }

    private static void notifyParticipant(Object participant, Component message) {
        if (participant == null) return;
        if (participant instanceof ServerPlayer player) {
            player.sendSystemMessage(message);
            return;
        }
        Object uuidValue = value(participant, "uuid", "getUuid", "getUUID");
        UUID uuid = null;
        if (uuidValue instanceof UUID found) uuid = found;
        else if (uuidValue != null) {
            try { uuid = UUID.fromString(String.valueOf(uuidValue)); } catch (Throwable ignored) {}
        }
        var server = com.champutils.battle.ServerLifecycleBridge.getServer();
        ServerPlayer player = server == null || uuid == null ? null : server.getPlayerList().getPlayer(uuid);
        if (player != null) player.sendSystemMessage(message);
    }

    private static void cancel(Object event) {
        try { event.getClass().getMethod("cancel").invoke(event); return; } catch (Throwable ignored) {}
        try { event.getClass().getMethod("setCanceled", boolean.class).invoke(event, true); return; } catch (Throwable ignored) {}
        Class<?> type = event.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("isCanceled");
                field.setAccessible(true);
                field.setBoolean(event, true);
                return;
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
    }

    private static Object value(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            try {
                if (name.startsWith("get")) {
                    Method method = source.getClass().getMethod(name);
                    Object value = method.invoke(source);
                    if (value != null) return value;
                } else {
                    Class<?> type = source.getClass();
                    while (type != null) {
                        try {
                            Field field = type.getDeclaredField(name);
                            field.setAccessible(true);
                            Object value = field.get(source);
                            if (value != null) return value;
                            break;
                        } catch (NoSuchFieldException ignored) {
                            type = type.getSuperclass();
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
