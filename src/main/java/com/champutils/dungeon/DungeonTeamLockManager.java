package com.champutils.dungeon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class DungeonTeamLockManager {

    private DungeonTeamLockManager() {
    }

    public static void saveSnapshot(ServerPlayer player, DungeonSession session) {
        if (player == null || session == null) return;
        session.lockedPokemonIds = readPartyIdentity(player);
    }

    public static boolean matchesSnapshot(ServerPlayer player, DungeonSession session) {
        if (player == null || session == null) return true;
        if (session.lockedPokemonIds == null || session.lockedPokemonIds.isEmpty()) return true;

        List<String> current = readPartyIdentity(player);
        return session.lockedPokemonIds.equals(current);
    }

    public static void clear(ServerPlayer player) {
        // Snapshots live inside DungeonSession, so active session cleanup is enough.
        // This method exists to keep cleanup calls explicit and future-proof.
    }

    private static List<String> readPartyIdentity(ServerPlayer player) {
        List<String> ids = new ArrayList<>();

        try {
            PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            if (party == null) return ids;

            for (int i = 0; i < party.size(); i++) {
                Pokemon pokemon = party.get(i);
                if (pokemon == null) continue;

                String id = getPokemonUuid(pokemon);
                if (id == null || id.isBlank()) {
                    id = fallbackIdentity(pokemon, i);
                }

                ids.add(id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collections.sort(ids);
        return ids;
    }

    private static String getPokemonUuid(Pokemon pokemon) {
        Object value = invokeNoArg(pokemon, "getUuid");
        if (value == null) value = invokeNoArg(pokemon, "getUUID");
        if (value == null) value = invokeNoArg(pokemon, "getId");
        if (value == null) value = readField(pokemon, "uuid");
        if (value == null) value = readField(pokemon, "id");

        if (value instanceof UUID uuid) {
            return uuid.toString();
        }

        return value == null ? null : String.valueOf(value);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String fallbackIdentity(Pokemon pokemon, int slot) {
        try {
            String species = pokemon.getSpecies().getResourceIdentifier().toString();
            return "slot:" + slot + ":" + species;
        } catch (Exception ignored) {
            return "slot:" + slot + ":unknown";
        }
    }
}
