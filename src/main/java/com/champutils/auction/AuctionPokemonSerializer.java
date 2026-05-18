package com.champutils.auction;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public final class AuctionPokemonSerializer {

    private AuctionPokemonSerializer() {}

    public static PartyStore getParty(ServerPlayer player) {
        if (player == null) return null;
        return Cobblemon.INSTANCE.getStorage().getParty(player);
    }

    public static Pokemon getPartyPokemon(ServerPlayer player, int slotIndex) {
        PartyStore party = getParty(player);
        if (party == null) return null;
        if (slotIndex < 0 || slotIndex >= party.size()) return null;
        return party.get(slotIndex);
    }

    public static void clearPartySlot(ServerPlayer player, int slotIndex) {
        PartyStore party = getParty(player);
        if (party == null) throw new IllegalStateException("Could not access Cobblemon party.");
        if (slotIndex < 0 || slotIndex >= party.size()) throw new IllegalArgumentException("Party slot is out of bounds.");

        Pokemon pokemon = party.get(slotIndex);
        if (pokemon == null) return;

        boolean removed = party.remove(pokemon);
        if (!removed) {
            throw new IllegalStateException("Cobblemon refused to remove Pokémon from party slot " + (slotIndex + 1) + ".");
        }
    }

    public static boolean hasOpenPartySlot(ServerPlayer player) {
        if (player == null) return false;
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (int i = 0; i < 6; i++) {
            if (party.get(i) == null) return true;
        }
        return false;
    }

    public static boolean addToFirstOpenPartySlot(ServerPlayer player, Pokemon pokemon) {
        PartyStore party = getParty(player);
        if (party == null || pokemon == null) return false;
        return party.add(pokemon);
    }

    public static JsonObject toPayload(ServerPlayer player, Pokemon pokemon) {
        if (player == null) throw new IllegalArgumentException("Player cannot be null.");
        if (pokemon == null) throw new IllegalArgumentException("Pokémon cannot be null.");

        JsonObject payload = new JsonObject();
        payload.addProperty("kind", "POKEMON");
        payload.addProperty("format", "cobblemon_pokemon_nbt_v2");
        payload.addProperty("species", safe(speciesId(pokemon)));
        payload.addProperty("displayName", safe(pokemon.getDisplayName(true).getString()));
        payload.addProperty("level", pokemon.getLevel());
        payload.addProperty("shiny", pokemon.getShiny());
        payload.addProperty("gender", safe(String.valueOf(pokemon.getGender())));
        payload.addProperty("nature", safe(String.valueOf(pokemon.getNature())));
        payload.addProperty("ability", pokemon.getAbility() == null ? "none" : safe(pokemon.getAbility().getName()));
        payload.addProperty("heldItem", heldItemName(pokemon));
        payload.add("moves", moves(pokemon));
        payload.add("ivs", statsObject(pokemon.getIvs()));
        payload.add("evs", statsObject(pokemon.getEvs()));
        payload.addProperty("pokemonNbtBase64", savePokemonBase64(player, pokemon));
        return payload;
    }

    public static Pokemon fromPayload(ServerPlayer player, JsonObject payload) {
        if (player == null) throw new IllegalArgumentException("Player cannot be null.");
        if (payload == null) return null;

        String species = payload.has("species") ? payload.get("species").getAsString() : "cobblemon:pikachu";
        Pokemon pokemon = PokemonProperties.Companion.parse("species=\"" + species + "\"").create();

        String encoded = payload.has("pokemonNbtBase64") ? payload.get("pokemonNbtBase64").getAsString() : "";
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("Auction Pokémon payload is missing saved Cobblemon NBT.");
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            CompoundTag tag = TagParser.parseTag(new String(bytes, StandardCharsets.UTF_8));
            loadPokemonNbt(player, pokemon, tag);
            return pokemon;
        } catch (Exception e) {
            throw new RuntimeException("Could not restore Pokémon payload.", e);
        }
    }

    private static void loadPokemonNbt(ServerPlayer player, Pokemon pokemon, CompoundTag tag) throws Exception {
        for (Method method : pokemon.getClass().getMethods()) {
            String name = method.getName();
            if (!name.equals("loadFromNBT") && !name.equals("loadFromNbt")) continue;
            try {
                method.setAccessible(true);
                Class<?>[] params = method.getParameterTypes();

                if (params.length == 2 && acceptsCompound(params[0])) {
                    Object registry = registryArgument(player, params[1]);
                    if (registry != null) {
                        method.invoke(pokemon, tag, registry);
                        return;
                    }
                }

                if (params.length == 2 && acceptsCompound(params[1])) {
                    Object registry = registryArgument(player, params[0]);
                    if (registry != null) {
                        method.invoke(pokemon, registry, tag);
                        return;
                    }
                }

                if (params.length == 1 && acceptsCompound(params[0])) {
                    method.invoke(pokemon, tag);
                    return;
                }
            } catch (Exception ignored) {}
        }
        throw new IllegalStateException("Could not find compatible Cobblemon Pokémon loadFromNBT method.");
    }

    private static boolean acceptsCompound(Class<?> type) {
        return type.isAssignableFrom(CompoundTag.class) || CompoundTag.class.isAssignableFrom(type);
    }

    private static Object registryArgument(ServerPlayer player, Class<?> expectedType) {
        try {
            Object registryAccess = player.registryAccess();
            if (registryAccess != null && expectedType.isAssignableFrom(registryAccess.getClass())) return registryAccess;
        } catch (Exception ignored) {}
        return null;
    }

    private static String savePokemonBase64(ServerPlayer player, Pokemon pokemon) {
        try {
            CompoundTag tag = pokemon.saveToNBT(player.registryAccess(), new CompoundTag());
            if (tag == null || tag.isEmpty()) {
                throw new IllegalStateException("Cobblemon returned empty Pokémon NBT.");
            }
            return Base64.getEncoder().encodeToString(tag.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Could not save Cobblemon Pokémon NBT.", e);
        }
    }

    private static String speciesId(Pokemon pokemon) {
        try { return pokemon.getSpecies().getResourceIdentifier().toString(); }
        catch (Exception e) { return "unknown"; }
    }

    private static String heldItemName(Pokemon pokemon) {
        try {
            ItemStack held = pokemon.heldItem();
            return held == null || held.isEmpty() ? "none" : held.getHoverName().getString();
        } catch (Exception e) { return "none"; }
    }

    private static JsonArray moves(Pokemon pokemon) {
        JsonArray array = new JsonArray();
        try {
            Object moveSet = pokemon.getMoveSet();
            Object moves = moveSet.getClass().getMethod("getMoves").invoke(moveSet);
            if (moves instanceof Iterable<?> iterable) {
                for (Object move : iterable) {
                    if (move == null) continue;
                    Object template = move.getClass().getMethod("getTemplate").invoke(move);
                    Object name = template.getClass().getMethod("getName").invoke(template);
                    array.add(String.valueOf(name));
                }
            }
        } catch (Exception ignored) {}
        return array;
    }

    private static JsonObject statsObject(Object stats) {
        JsonObject object = new JsonObject();
        if (stats == null) return object;

        try {
            if (stats instanceof Iterable<?> iterable) {
                for (Object entryObj : iterable) {
                    if (entryObj instanceof Map.Entry<?, ?> entry) {
                        object.addProperty(cleanStatName(entry.getKey()), cleanStatValue(entry.getValue()));
                    }
                }
            }
        } catch (Exception ignored) {}

        if (object.size() == 0) {
            object.addProperty("raw", stats.toString());
        }
        return object;
    }

    private static String cleanStatName(Object key) {
        if (key == null) return "unknown";
        String text = String.valueOf(key);
        int colon = text.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < text.length()) text = text.substring(colon + 1);
        int dot = text.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < text.length()) text = text.substring(dot + 1);
        return text.toLowerCase().replace(' ', '_');
    }

    private static String cleanStatValue(Object value) {
        if (value == null) return "0";
        return String.valueOf(value).replaceAll("[^0-9]", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
