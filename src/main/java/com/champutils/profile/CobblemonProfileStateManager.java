package com.champutils.profile;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.database.DatabaseManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Profile-specific Cobblemon storage adapter.
 *
 * Snapshots the live Cobblemon party and PC by profile_id.
 *
 * The PC adapter is reflection-based on purpose because Cobblemon PCStore method names
 * have moved between versions. It tries direct PC set/get methods first, then box-level
 * methods, then falls back to add/remove style APIs.
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
                    "pc_json text not null default '[]', " +
                    "player_data_json text not null default '{}'::text, " +
                    "updated_at timestamptz not null default now())");
            statement.executeUpdate("alter table profile_cobblemon_state add column if not exists player_uuid uuid");
            statement.executeUpdate("alter table profile_cobblemon_state add column if not exists party_json text not null default '[]'");
            statement.executeUpdate("alter table profile_cobblemon_state add column if not exists pc_json text not null default '[]'");
            statement.executeUpdate("alter table profile_cobblemon_state add column if not exists player_data_json text not null default '{}'::text");
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
            JsonArray pcJson = serializePc(player);
            JsonObject playerDataJson = serializePlayerInstancedData(player);
            try (var ps = connection.prepareStatement("insert into profile_cobblemon_state (profile_id, player_uuid, party_json, pc_json, player_data_json, updated_at) values (?, ?, ?, ?, ?, now()) " +
                    "on conflict (profile_id) do update set party_json = excluded.party_json, pc_json = excluded.pc_json, player_data_json = excluded.player_data_json, updated_at = now()")) {
                ps.setObject(1, profileId);
                ps.setObject(2, player.getUUID());
                ps.setString(3, partyJson.toString());
                ps.setString(4, pcJson.toString());
                ps.setString(5, playerDataJson.toString());
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

            // Defensive wipe before loading the target profile. If anything in the
            // restore path fails, the player should get an empty party/PC rather
            // than the previous profile's live Cobblemon state.
            clearLive(player);

            try (var ps = connection.prepareStatement("select party_json, pc_json, player_data_json from profile_cobblemon_state where profile_id = ?")) {
                ps.setObject(1, profileId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        restorePlayerInstancedData(player, new JsonObject());
                        clearLive(player);
                        return;
                    }
                    String rawParty = rs.getString("party_json");
                    String rawPc = rs.getString("pc_json");
                    String rawPlayerData = rs.getString("player_data_json");
                    JsonArray partyArray = rawParty == null || rawParty.isBlank() ? new JsonArray() : JsonParser.parseString(rawParty).getAsJsonArray();
                    JsonArray pcArray = rawPc == null || rawPc.isBlank() ? new JsonArray() : JsonParser.parseString(rawPc).getAsJsonArray();
                    JsonObject playerDataObject = rawPlayerData == null || rawPlayerData.isBlank() ? new JsonObject() : JsonParser.parseString(rawPlayerData).getAsJsonObject();
                    restorePlayerInstancedData(player, playerDataObject);
                    restoreParty(player, partyArray);
                    restorePc(player, pcArray);
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load Cobblemon profile state for " + player.getGameProfile().getName());
            e.printStackTrace();
        }
    }

    public static void clearLive(ServerPlayer player) {
        clearLiveParty(player);
        clearLivePc(player);
        syncCobblemonStorage(player);
    }

    private static void clearLiveParty(ServerPlayer player) {
        try {
            PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            if (party == null) return;

            // Cobblemon's PartyStore has changed shape across 1.7.x builds.
            // Slot set(index, null) is preferred because it preserves slot positions,
            // but some implementations compact/remove instead. Use both paths so a
            // profile swap cannot leave the previous profile's live party behind.
            for (int pass = 0; pass < 3; pass++) {
                for (int i = 0; i < 6; i++) {
                    try { party.set(i, null); } catch (Throwable ignored) {}
                }

                List<Pokemon> remaining = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    try {
                        Pokemon pokemon = i < party.size() ? party.get(i) : null;
                        if (pokemon != null) remaining.add(pokemon);
                    } catch (Throwable ignored) {}
                }
                if (remaining.isEmpty()) break;

                for (Pokemon pokemon : remaining) {
                    try { party.remove(pokemon); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {
        } finally {
            syncCobblemonStorage(player);
        }
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


    private static JsonArray serializePc(ServerPlayer player) {
        JsonArray array = new JsonArray();
        Object pc = resolvePc(player);
        if (pc == null) return array;

        // Prefer direct PC slot access if Cobblemon exposes it.
        for (int box = 0; box < 64; box++) {
            boolean sawAnySlotInBox = false;
            for (int slot = 0; slot < 30; slot++) {
                Pokemon pokemon = getPcPokemon(pc, box, slot);
                if (pokemon != null) {
                    sawAnySlotInBox = true;
                    JsonObject entry = new JsonObject();
                    entry.addProperty("box", box);
                    entry.addProperty("slot", slot);
                    entry.add("pokemon", AuctionPokemonSerializer.toPayload(player, pokemon));
                    array.add(entry);
                }
            }
            // Once direct access returns nothing for a long stretch, stop scanning.
            if (!sawAnySlotInBox && box > 32) break;
        }

        if (array.size() > 0) return array;

        // Fallback: collect whatever Pokemon objects the PC/boxes expose and restore them by add().
        List<Pokemon> found = new ArrayList<>();
        collectPokemonObjects(pc, found, new IdentityHashMap<>(), 0);
        for (int i = 0; i < found.size(); i++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", i);
            entry.add("pokemon", AuctionPokemonSerializer.toPayload(player, found.get(i)));
            array.add(entry);
        }
        return array;
    }

    private static void restorePc(ServerPlayer player, JsonArray array) {
        Object pc = resolvePc(player);
        if (pc == null) return;
        clearLivePc(player);
        for (int i = 0; i < array.size(); i++) {
            try {
                JsonObject entry = array.get(i).getAsJsonObject();
                if (!entry.has("pokemon")) continue;
                Pokemon pokemon = AuctionPokemonSerializer.fromPayload(player, entry.getAsJsonObject("pokemon"));
                int box = entry.has("box") ? entry.get("box").getAsInt() : -1;
                int slot = entry.has("slot") ? entry.get("slot").getAsInt() : i;
                if (box >= 0 && trySetPcPokemon(pc, box, slot, pokemon)) continue;
                addPokemonToPc(pc, pokemon);
            } catch (Throwable t) {
                System.err.println("[ChampUtils] Failed to restore one Cobblemon PC slot.");
                t.printStackTrace();
            }
        }
        syncCobblemonStorage(player);
    }

    private static void clearLivePc(ServerPlayer player) {
        Object pc = resolvePc(player);
        if (pc == null) return;

        boolean clearedAny = false;
        for (int box = 0; box < 64; box++) {
            for (int slot = 0; slot < 30; slot++) {
                if (getPcPokemon(pc, box, slot) != null && trySetPcPokemon(pc, box, slot, null)) {
                    clearedAny = true;
                }
            }
        }
        if (clearedAny) return;

        List<Pokemon> found = new ArrayList<>();
        collectPokemonObjects(pc, found, new IdentityHashMap<>(), 0);
        for (Pokemon pokemon : found) removePokemonFromPc(pc, pokemon);
    }

    private static Object resolvePc(ServerPlayer player) {
        if (player == null) return null;
        try {
            Object storage = Cobblemon.INSTANCE.getStorage();
            for (String methodName : new String[] { "getPC", "getPc", "getPCStore", "getPcStore" }) {
                for (Method method : storage.getClass().getMethods()) {
                    if (!method.getName().equals(methodName) || method.getParameterCount() != 1) continue;
                    Object arg = argumentForPlayerMethod(player, method.getParameterTypes()[0]);
                    if (arg == null) continue;
                    Object pc = method.invoke(storage, arg);
                    if (pc != null) return pc;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object argumentForPlayerMethod(ServerPlayer player, Class<?> param) {
        if (param.isAssignableFrom(ServerPlayer.class)) return player;
        if (param.isAssignableFrom(UUID.class)) return player.getUUID();
        return null;
    }

    private static Pokemon getPcPokemon(Object pc, int box, int slot) {
        for (String name : new String[] { "get", "getPokemon", "getSlot" }) {
            for (Method method : pc.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                try {
                    if (method.getParameterCount() == 2) {
                        Object value = method.invoke(pc, box, slot);
                        if (value instanceof Pokemon pokemon) return pokemon;
                    }
                    if (method.getParameterCount() == 1) {
                        Object boxObj = method.invoke(pc, box);
                        Pokemon pokemon = getPokemonFromBox(boxObj, slot);
                        if (pokemon != null) return pokemon;
                    }
                } catch (Throwable ignored) {}
            }
        }
        Object boxObj = getBox(pc, box);
        return getPokemonFromBox(boxObj, slot);
    }

    private static Object getBox(Object pc, int box) {
        for (String name : new String[] { "getBox", "box", "get" }) {
            for (Method method : pc.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
                try {
                    Object value = method.invoke(pc, box);
                    if (value != null && !(value instanceof Pokemon)) return value;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static Pokemon getPokemonFromBox(Object boxObj, int slot) {
        if (boxObj == null) return null;
        for (String name : new String[] { "get", "getPokemon", "getSlot" }) {
            for (Method method : boxObj.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
                try {
                    Object value = method.invoke(boxObj, slot);
                    if (value instanceof Pokemon pokemon) return pokemon;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static boolean trySetPcPokemon(Object pc, int box, int slot, Pokemon pokemon) {
        for (String name : new String[] { "set", "setPokemon", "setSlot" }) {
            for (Method method : pc.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                try {
                    if (method.getParameterCount() == 3 && acceptsPokemonOrNullable(method.getParameterTypes()[2])) {
                        method.invoke(pc, box, slot, pokemon);
                        return true;
                    }
                    if (method.getParameterCount() == 2) {
                        Object boxObj = getBox(pc, box);
                        if (trySetPokemonInBox(boxObj, slot, pokemon)) return true;
                    }
                } catch (Throwable ignored) {}
            }
        }
        Object boxObj = getBox(pc, box);
        return trySetPokemonInBox(boxObj, slot, pokemon);
    }

    private static boolean trySetPokemonInBox(Object boxObj, int slot, Pokemon pokemon) {
        if (boxObj == null) return false;
        for (String name : new String[] { "set", "setPokemon", "setSlot" }) {
            for (Method method : boxObj.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 2) continue;
                if (!acceptsPokemonOrNullable(method.getParameterTypes()[1])) continue;
                try {
                    method.invoke(boxObj, slot, pokemon);
                    return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static boolean acceptsPokemonOrNullable(Class<?> type) {
        return !type.isPrimitive() && (type.isAssignableFrom(Pokemon.class) || Pokemon.class.isAssignableFrom(type) || Object.class.equals(type));
    }

    private static boolean addPokemonToPc(Object pc, Pokemon pokemon) {
        for (String name : new String[] { "add", "addPokemon" }) {
            for (Method method : pc.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(Pokemon.class)) continue;
                try {
                    Object result = method.invoke(pc, pokemon);
                    return !(result instanceof Boolean) || (Boolean) result;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static boolean removePokemonFromPc(Object pc, Pokemon pokemon) {
        for (String name : new String[] { "remove", "removePokemon" }) {
            for (Method method : pc.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(Pokemon.class)) continue;
                try {
                    Object result = method.invoke(pc, pokemon);
                    if (!(result instanceof Boolean) || (Boolean) result) return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static void collectPokemonObjects(Object source, List<Pokemon> output, Map<Object, Boolean> seen, int depth) {
        if (source == null || depth > 4 || seen.containsKey(source)) return;
        seen.put(source, Boolean.TRUE);
        if (source instanceof Pokemon pokemon) {
            output.add(pokemon);
            return;
        }
        if (source instanceof Iterable<?> iterable) {
            for (Object value : iterable) collectPokemonObjects(value, output, seen, depth + 1);
        }
        for (String methodName : new String[] { "getAll", "all", "getBoxes", "getBoxList", "getPokemon", "getSlots", "getStorage" }) {
            for (Method method : source.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 0) continue;
                try { collectPokemonObjects(method.invoke(source), output, seen, depth + 1); }
                catch (Throwable ignored) {}
            }
        }
    }



    /**
     * Saves Cobblemon's per-player instanced data that is not stored in party/PC:
     * starter/general flags, Pokédex data, TM move data, and any future cached data
     * factory Cobblemon registers. This is deliberately reflection-based so the mod
     * can keep working across Cobblemon 1.7.x storage refactors without directly
     * forking Cobblemon internals.
     */
    private static JsonObject serializePlayerInstancedData(ServerPlayer player) {
        JsonObject root = new JsonObject();
        try {
            Object manager = getCobblemonPlayerDataManager();
            if (manager == null) return root;
            Object factories = readField(manager, "factories");
            if (!(factories instanceof Map<?, ?> map)) return root;

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object dataType = entry.getKey();
                Object factory = entry.getValue();
                String key = String.valueOf(dataType);
                Object data = invokeFirst(factory, new String[] { "getForPlayer" }, new Class<?>[] { UUID.class }, new Object[] { player.getUUID() });
                Object backend = readField(factory, "backend");
                Object gsonObject = readField(backend, "gson");
                if (!(gsonObject instanceof Gson gson) || data == null) continue;
                root.addProperty(key, gson.toJson(data));
            }
        } catch (Throwable t) {
            System.err.println("[ChampUtils] Failed to serialize Cobblemon player-data stores for " + player.getGameProfile().getName());
            t.printStackTrace();
        }
        return root;
    }

    private static void restorePlayerInstancedData(ServerPlayer player, JsonObject root) {
        try {
            Object manager = getCobblemonPlayerDataManager();
            if (manager == null) return;
            Object factories = readField(manager, "factories");
            if (!(factories instanceof Map<?, ?> map)) return;

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object dataType = entry.getKey();
                Object factory = entry.getValue();
                String key = String.valueOf(dataType);
                Object backend = readField(factory, "backend");
                Object cache = readField(factory, "cache");
                if (cache instanceof Map<?, ?> cacheMap) {
                    ((Map<Object, Object>) cacheMap).remove(player.getUUID());
                }

                if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
                    // No saved profile data yet: leave the cache empty so Cobblemon creates defaults.
                    invokeFirst(factory, new String[] { "getForPlayer" }, new Class<?>[] { UUID.class }, new Object[] { player.getUUID() });
                    continue;
                }

                Object gsonObject = readField(backend, "gson");
                Object classTokenObject = readField(backend, "classToken");
                if (!(gsonObject instanceof Gson gson) || !(classTokenObject instanceof TypeToken<?> token)) continue;

                Object restored = gson.fromJson(root.get(key).getAsString(), token.getType());
                if (restored == null) continue;

                // Cobblemon data must remain keyed to the real player UUID in memory; profile isolation
                // is handled by SQL snapshots, not by changing the runtime player UUID.
                trySetUuid(restored, player.getUUID());
                invokeCompatible(backend, "initialize", restored);

                if (cache instanceof Map<?, ?> cacheMap) {
                    ((Map<Object, Object>) cacheMap).put(player.getUUID(), restored);
                }
            }
            invokeFirst(manager, new String[] { "syncAllToPlayer" }, new Class<?>[] { ServerPlayer.class }, new Object[] { player });
        } catch (Throwable t) {
            System.err.println("[ChampUtils] Failed to restore Cobblemon player-data stores for " + player.getGameProfile().getName());
            t.printStackTrace();
        }
    }

    private static Object getCobblemonPlayerDataManager() {
        try {
            for (String name : new String[] { "getPlayerDataManager", "playerDataManager" }) {
                for (Method method : Cobblemon.INSTANCE.getClass().getMethods()) {
                    if (method.getName().equals(name) && method.getParameterCount() == 0) return method.invoke(Cobblemon.INSTANCE);
                }
            }
            return readField(Cobblemon.INSTANCE, "playerDataManager");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readField(Object source, String name) {
        if (source == null) return null;
        Class<?> type = source.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(source);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Object invokeFirst(Object target, String[] names, Class<?>[] params, Object[] args) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name, params);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean invokeCompatible(Object target, String name, Object arg) {
        if (target == null || arg == null) return false;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            if (!method.getParameterTypes()[0].isAssignableFrom(arg.getClass())) continue;
            try {
                method.setAccessible(true);
                method.invoke(target, arg);
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static void trySetUuid(Object restored, UUID uuid) {
        if (restored == null || uuid == null) return;
        Class<?> type = restored.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("uuid");
                field.setAccessible(true);
                field.set(restored, uuid);
                return;
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
    }

    private static void restoreParty(ServerPlayer player, JsonArray array) {
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) return;

        // Only clear the party here. restorePc() is responsible for PC clearing/restoring.
        // Calling clearLive() from party restore made the two restore paths unnecessarily
        // coupled and increased the chance of stale Cobblemon storage state surviving.
        clearLiveParty(player);

        for (int i = 0; i < array.size(); i++) {
            try {
                JsonObject slot = array.get(i).getAsJsonObject();
                if (!slot.has("pokemon")) continue;
                int index = slot.has("slot") ? slot.get("slot").getAsInt() : i;
                if (index < 0 || index >= 6) continue;
                Pokemon pokemon = AuctionPokemonSerializer.fromPayload(player, slot.getAsJsonObject("pokemon"));

                boolean placed = false;
                try {
                    party.set(index, pokemon);
                    placed = true;
                } catch (Throwable ignored) {}

                // Fallback for PartyStore implementations that reject sparse slot setting.
                if (!placed) {
                    try { placed = party.add(pokemon); } catch (Throwable ignored) {}
                }

                if (!placed) {
                    System.err.println("[ChampUtils] Could not place restored Cobblemon party slot " + index + ".");
                }
            } catch (Throwable t) {
                System.err.println("[ChampUtils] Failed to restore one Cobblemon party slot.");
                t.printStackTrace();
            }
        }
        syncCobblemonStorage(player);
    }

    private static void syncCobblemonStorage(ServerPlayer player) {
        if (player == null) return;
        try {
            Object storage = Cobblemon.INSTANCE.getStorage();
            invokeFirst(storage, new String[] { "syncToPlayer", "syncParty", "updateParty", "saveParty" }, new Class<?>[] { ServerPlayer.class }, new Object[] { player });
            Object party = Cobblemon.INSTANCE.getStorage().getParty(player);
            invokeFirst(party, new String[] { "sync", "update", "markDirty", "save" }, new Class<?>[] {}, new Object[] {});
            Object pc = resolvePc(player);
            invokeFirst(pc, new String[] { "sync", "update", "markDirty", "save" }, new Class<?>[] {}, new Object[] {});
        } catch (Throwable ignored) {
        }
    }
}
