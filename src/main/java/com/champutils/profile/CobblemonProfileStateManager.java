package com.champutils.profile;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.database.DatabaseManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * Profile-specific Cobblemon storage adapter.
 *
 * This currently snapshots the live party by profile_id. PC storage is deliberately
 * not guessed here because Cobblemon's PCStore API is version-sensitive; leaving the
 * account PC shared is the exact remaining issue to solve with a PCStore-specific pass.
 */
public final class CobblemonProfileStateManager {
    private CobblemonProfileStateManager() {}

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure profile cobblemon state table", CobblemonProfileStateManager::ensureSchema);
    }

    private static void ensureSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists profile_cobblemon_state (" +
                    "profile_id uuid primary key references player_profiles(id) on delete cascade, " +
                    "player_uuid uuid references players(uuid) on delete cascade, " +
                    "party_json text not null default '[]', " +
                    "updated_at timestamptz not null default now())");
            statement.executeUpdate("alter table profile_cobblemon_state add column if not exists player_uuid uuid");
            statement.executeUpdate("alter table profile_cobblemon_state add column if not exists party_json text not null default '[]'");
            statement.executeUpdate("alter table profile_cobblemon_state add column if not exists updated_at timestamptz not null default now()");
        }
    }

    public static void save(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled() || !PlayerProfileManager.hasActiveProfile(player)) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            JsonArray partyJson = serializeParty(player);
            try (var ps = connection.prepareStatement("insert into profile_cobblemon_state (profile_id, player_uuid, party_json, updated_at) values (?, ?, ?, now()) " +
                    "on conflict (profile_id) do update set party_json = excluded.party_json, updated_at = now()")) {
                ps.setObject(1, profileId);
                ps.setObject(2, player.getUUID());
                ps.setString(3, partyJson.toString());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save Cobblemon profile state for " + player.getGameProfile().getName());
            e.printStackTrace();
        }
    }

    public static void load(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled() || !PlayerProfileManager.hasActiveProfile(player)) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            try (var ps = connection.prepareStatement("select party_json from profile_cobblemon_state where profile_id = ?")) {
                ps.setObject(1, profileId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        clearLive(player);
                        return;
                    }
                    String raw = rs.getString("party_json");
                    JsonArray array = raw == null || raw.isBlank() ? new JsonArray() : JsonParser.parseString(raw).getAsJsonArray();
                    restoreParty(player, array);
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load Cobblemon profile state for " + player.getGameProfile().getName());
            e.printStackTrace();
        }
    }

    public static void clearLive(ServerPlayer player) {
        try {
            PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            if (party == null) return;
            for (int i = 0; i < Math.max(6, party.size()); i++) {
                try { party.set(i, null); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static JsonArray serializeParty(ServerPlayer player) {
        JsonArray array = new JsonArray();
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) return array;
        for (int i = 0; i < Math.max(6, party.size()); i++) {
            JsonObject slot = new JsonObject();
            slot.addProperty("slot", i);
            try {
                Pokemon pokemon = i < party.size() ? party.get(i) : null;
                if (pokemon != null) {
                    slot.add("pokemon", AuctionPokemonSerializer.toPayload(player, pokemon));
                }
            } catch (Throwable ignored) {}
            array.add(slot);
        }
        return array;
    }

    private static void restoreParty(ServerPlayer player, JsonArray array) {
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) return;
        clearLive(player);
        for (int i = 0; i < array.size(); i++) {
            try {
                JsonObject slot = array.get(i).getAsJsonObject();
                if (!slot.has("pokemon")) continue;
                int index = slot.has("slot") ? slot.get("slot").getAsInt() : i;
                Pokemon pokemon = AuctionPokemonSerializer.fromPayload(player, slot.getAsJsonObject("pokemon"));
                party.set(index, pokemon);
            } catch (Throwable t) {
                System.err.println("[ChampUtils] Failed to restore one Cobblemon party slot.");
                t.printStackTrace();
            }
        }
    }
}
