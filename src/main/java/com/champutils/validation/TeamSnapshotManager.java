package com.champutils.validation;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class TeamSnapshotManager {

    private static final Map<UUID, List<String>> SNAPSHOTS = new HashMap<>();

    // ========================
    // SAVE SNAPSHOT
    // ========================
    public static void saveSnapshot(ServerPlayer player) {

        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) return;

        List<String> snapshot = new ArrayList<>();

        for (int i = 0; i < party.size(); i++) {

            Pokemon p = party.get(i);
            if (p == null) continue;

            snapshot.add(serialize(p));
        }

        SNAPSHOTS.put(player.getUUID(), snapshot);
    }

    // ========================
    // COMPARE SNAPSHOT
    // ========================
    public static boolean matchesSnapshot(ServerPlayer player) {

        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) return true;

        List<String> oldSnap = SNAPSHOTS.get(player.getUUID());
        if (oldSnap == null) return true;

        List<String> current = new ArrayList<>();

        for (int i = 0; i < party.size(); i++) {

            Pokemon p = party.get(i);
            if (p == null) continue;

            current.add(serialize(p));
        }

        return oldSnap.equals(current);
    }

    // ========================
    // CLEAR
    // ========================
    public static void clear(ServerPlayer player) {
        SNAPSHOTS.remove(player.getUUID());
    }

    // ========================
    // SERIALIZE POKEMON
    // ========================
    private static String serialize(Pokemon p) {

        String species = p.getSpecies()
                .getResourceIdentifier()
                .toString();

        int level = p.getLevel();

        String ability = p.getAbility() != null
                ? p.getAbility().getName()
                : "none";

        String item = (p.heldItem() != null && !p.heldItem().isEmpty())
                ? p.heldItem().getItem().toString()
                : "none";

        List<String> moves = new ArrayList<>();

        try {
            var moveSet = p.getMoveSet();
            if (moveSet != null && moveSet.getMoves() != null) {

                for (var move : moveSet.getMoves()) {
                    if (move != null && move.getTemplate() != null) {
                        moves.add(move.getTemplate().getName());
                    }
                }
            }
        } catch (Exception ignored) {}

        Collections.sort(moves);

        return species + "|" + level + "|" + ability + "|" + item + "|" + String.join(",", moves);
    }
}