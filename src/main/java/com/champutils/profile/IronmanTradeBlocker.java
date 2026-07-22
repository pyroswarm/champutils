package com.champutils.profile;

import com.champutils.util.CobblemonEventReflection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class IronmanTradeBlocker {
    private static boolean registered = false;

    private IronmanTradeBlocker() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Field field = eventsClass.getField("TRADE_EVENT_PRE");
            Object observable = field.get(null);
            CobblemonEventReflection.subscribe(observable, event -> {
                try { handle(event); } catch (Throwable throwable) { throwable.printStackTrace(); }
            });
            System.out.println("[ChampUtils] Restricted-profile Cobblemon trade blocker registered.");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.out.println("[ChampUtils] Failed to register restricted-profile Cobblemon trade blocker.");
        }
    }

    private static void handle(Object event) {
        ServerPlayer p1 = playerFromParticipant(firstValue(event, "tradeParticipant1", "getTradeParticipant1"));
        ServerPlayer p2 = playerFromParticipant(firstValue(event, "tradeParticipant2", "getTradeParticipant2"));
        if (p1 == null || p2 == null) return;

        ProfileGameMode mode1 = PlayerProfileManager.gameMode(p1);
        ProfileGameMode mode2 = PlayerProfileManager.gameMode(p2);
        boolean islander1 = mode1 == ProfileGameMode.ISLANDER;
        boolean islander2 = mode2 == ProfileGameMode.ISLANDER;

        Component message = null;
        if (islander1 != islander2) {
            message = Component.literal("Islander profiles may only trade Pokémon with other Islander profiles.").withStyle(ChatFormatting.RED);
        } else if (mode1.usesIronmanRules() || mode2.usesIronmanRules()) {
            ProfileGameMode blockedMode = mode1.usesIronmanRules() ? mode1 : mode2;
            message = Component.literal(blockedMode.displayName() + " profiles cannot trade Pokémon with other players.").withStyle(ChatFormatting.RED);
        }

        // Two Islanders are intentionally allowed, including trades containing Eggs.
        if (message == null) return;
        cancel(event);
        p1.sendSystemMessage(message);
        p2.sendSystemMessage(message);
    }

    private static ServerPlayer playerFromParticipant(Object participant) {
        if (participant == null) return null;
        Object uuidRaw = firstValue(participant, "uuid", "getUuid", "getUUID");
        UUID uuid = null;
        if (uuidRaw instanceof UUID u) uuid = u;
        else if (uuidRaw != null) {
            try { uuid = UUID.fromString(String.valueOf(uuidRaw)); } catch (Throwable ignored) {}
        }
        if (uuid == null) return null;
        return com.champutils.battle.ServerLifecycleBridge.getServer() == null ? null : com.champutils.battle.ServerLifecycleBridge.getServer().getPlayerList().getPlayer(uuid);
    }

    private static void cancel(Object event) {
        try {
            Method method = event.getClass().getMethod("cancel");
            method.invoke(event);
            return;
        } catch (Throwable ignored) {}
        try {
            Method method = event.getClass().getMethod("setCanceled", boolean.class);
            method.invoke(event, true);
            return;
        } catch (Throwable ignored) {}
        try {
            Field field = event.getClass().getSuperclass().getDeclaredField("isCanceled");
            field.setAccessible(true);
            field.setBoolean(event, true);
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
}
