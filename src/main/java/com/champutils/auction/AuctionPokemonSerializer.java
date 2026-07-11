package com.champutils.auction;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.champutils.profile.CobblemonProfileStorageBridge;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.UUID;
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

        UUID expectedUuid = pokemon.getUuid();
        boolean removed = false;

        // Prefer removing by the exact party position. This recalls the Pokémon, clears store
        // coordinates, updates observers, and avoids relying on party shifting behavior.
        try {
            removed = party.remove(new PartyPosition(slotIndex));
        } catch (Throwable ignored) {
            removed = false;
        }

        if (!removed) {
            try {
                removed = party.remove(pokemon);
            } catch (Throwable ignored) {
                removed = false;
            }
        }

        // Last-resort hard clear for any stale/broken storeCoordinates edge case. This is
        // intentionally followed by a UUID scan so expedition/auction flows cannot silently
        // continue while the original Pokémon remains in the player's party.
        if (!removed && forceClearPartySlot(party, slotIndex, pokemon)) {
            removed = true;
        }

        if (!removed || partyContainsUuid(party, expectedUuid)) {
            throw new IllegalStateException("Cobblemon refused to safely remove Pokémon from party slot " + (slotIndex + 1) + ".");
        }

        afterPartyMutation(player, "auction-remove");
    }

    public static Pokemon getAndValidatePartyPokemon(ServerPlayer player, int slotIndex) {
        PartyStore party = getParty(player);
        if (party == null) throw new IllegalStateException("Could not access Cobblemon party.");
        if (slotIndex < 0 || slotIndex >= party.size()) throw new IllegalArgumentException("Party slot is out of bounds.");
        return party.get(slotIndex);
    }

    public static boolean hasPokemonInPartyOrPc(ServerPlayer player, UUID pokemonUuid) {
        if (player == null || pokemonUuid == null) return false;
        try {
            PartyStore party = getParty(player);
            if (party != null && partyContainsUuid(party, pokemonUuid)) return true;
        } catch (Throwable ignored) {
        }
        try {
            PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
            if (pc != null) {
                for (Pokemon pokemon : pc) {
                    if (pokemon != null && pokemonUuid.equals(pokemon.getUuid())) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static UUID uuidFromPayload(JsonObject payload) {
        if (payload == null) return null;
        try {
            if (payload.has("uuid")) return UUID.fromString(payload.get("uuid").getAsString());
        } catch (Throwable ignored) {
        }
        try {
            String encoded = payload.has("pokemonNbtBase64") ? payload.get("pokemonNbtBase64").getAsString() : "";
            if (encoded == null || encoded.isBlank()) return null;
            byte[] bytes = Base64.getDecoder().decode(encoded);
            CompoundTag tag = TagParser.parseTag(new String(bytes, StandardCharsets.UTF_8));
            if (tag.hasUUID("UUID")) return tag.getUUID("UUID");
            if (tag.hasUUID("uuid")) return tag.getUUID("uuid");
            if (tag.contains("UUID")) return UUID.fromString(tag.getString("UUID"));
            if (tag.contains("uuid")) return UUID.fromString(tag.getString("uuid"));
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean partyContainsUuid(PartyStore party, UUID expectedUuid) {
        if (party == null || expectedUuid == null) return false;
        for (int i = 0; i < party.size(); i++) {
            Pokemon current = party.get(i);
            if (current != null && expectedUuid.equals(current.getUuid())) return true;
        }
        return false;
    }

    private static boolean forceClearPartySlot(PartyStore party, int slotIndex, Pokemon pokemon) {
        try {
            if (pokemon != null) pokemon.recall();
        } catch (Throwable ignored) {
        }
        try {
            Method method = PartyStore.class.getDeclaredMethod("setAtPosition", PartyPosition.class, Pokemon.class);
            method.setAccessible(true);
            method.invoke(party, new PartyPosition(slotIndex), null);
            clearStoreCoordinates(pokemon);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Field slots = PartyStore.class.getDeclaredField("slots");
            slots.setAccessible(true);
            Object value = slots.get(party);
            if (value instanceof java.util.List<?> list && slotIndex >= 0 && slotIndex < list.size()) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> mutable = (java.util.List<Object>) list;
                mutable.set(slotIndex, null);
                clearStoreCoordinates(pokemon);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void clearStoreCoordinates(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            Object observable = pokemon.getStoreCoordinates();
            Method set = observable.getClass().getMethod("set", Object.class);
            set.invoke(observable, new Object[] { null });
        } catch (Throwable ignored) {
        }
    }


    private static void afterPartyMutation(ServerPlayer player, String reason) {
        if (player == null) return;
        try {
            CobblemonProfileStorageBridge.resyncActiveProfileParty(player, reason == null ? "party-mutation" : reason);
        } catch (Throwable ignored) {
        }
        try {
            CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player);
        } catch (Throwable ignored) {
        }
    }

    public static boolean replacePartySlot(ServerPlayer player, int slotIndex, Pokemon replacement) {
        if (player == null || replacement == null) return false;
        PartyStore party = getParty(player);
        if (party == null || slotIndex < 0 || slotIndex >= party.size()) return false;
        try {
            party.set(new PartyPosition(slotIndex), replacement);
            Pokemon current = party.get(slotIndex);
            if (current == null || !replacement.getUuid().equals(current.getUuid())) return false;
            afterPartyMutation(player, "breeding-hatch-replace");
            return true;
        } catch (Throwable directFailure) {
            try {
                Method method = PartyStore.class.getMethod("set", PartyPosition.class, Pokemon.class);
                method.invoke(party, new PartyPosition(slotIndex), replacement);
                Pokemon current = party.get(slotIndex);
                if (current == null || !replacement.getUuid().equals(current.getUuid())) return false;
                afterPartyMutation(player, "breeding-hatch-replace-reflection");
                return true;
            } catch (Throwable ignored) {
                directFailure.printStackTrace();
                return false;
            }
        }
    }

    public static boolean hasOpenPartySlot(ServerPlayer player) {
        PartyStore party = getParty(player);
        if (party == null) return false;

        try {
            for (int i = 0; i < party.size(); i++) {
                if (party.get(i) == null) return true;
            }
        } catch (Exception ignored) {}

        try {
            return party.size() < 6;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean addToFirstOpenPartySlot(ServerPlayer player, Pokemon pokemon) {
        PartyStore party = getParty(player);
        if (party == null || pokemon == null) return false;
        boolean added = party.add(pokemon);
        if (added) afterPartyMutation(player, "auction-add-party");
        return added;
    }

    public static boolean addToPc(ServerPlayer player, Pokemon pokemon) {
        if (player == null || pokemon == null) return false;
        try {
            Object storage = Cobblemon.INSTANCE.getStorage();
            Object pc = null;

            for (String methodName : new String[] { "getPC", "getPc", "getPCStore", "getPcStore" }) {
                for (Method method : storage.getClass().getMethods()) {
                    if (!method.getName().equals(methodName)) continue;
                    if (method.getParameterCount() != 1) continue;

                    try {
                        Class<?> param = method.getParameterTypes()[0];
                        Object arg;
                        if (param.isAssignableFrom(ServerPlayer.class)) {
                            arg = player;
                        } else if (param.isAssignableFrom(UUID.class)) {
                            arg = player.getUUID();
                        } else {
                            continue;
                        }

                        pc = method.invoke(storage, arg);
                        if (pc != null) break;
                    } catch (Throwable ignored) {}
                }
                if (pc != null) break;
            }

            if (pc == null) return false;

            for (Method method : pc.getClass().getMethods()) {
                if (!method.getName().equals("add")) continue;
                if (method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(Pokemon.class)) continue;

                Object result = method.invoke(pc, pokemon);
                return !(result instanceof Boolean) || (Boolean) result;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static DeliveryResult deliverToPartyOrPc(ServerPlayer player, Pokemon pokemon) {
        if (player == null || pokemon == null) return DeliveryResult.FAILED;

        // Never deliver a restored Pokémon if that exact UUID is already in the
        // player's party or PC. This is the safety net that prevents expedition
        // retries, failed starts, auction recovery, or PC-full edge cases from
        // creating a second copy of the same Pokémon.
        if (hasPokemonInPartyOrPc(player, pokemon.getUuid())) {
            return DeliveryResult.PARTY;
        }

        // Never call PartyStore#add unless we have positively verified an open party slot.
        // Some Cobblemon storage paths can behave unexpectedly when the party is full; for
        // reward/expedition delivery we want a strict PARTY-or-PC result with no duplicate
        // party insert attempts.
        if (hasOpenPartySlot(player) && addToFirstOpenPartySlot(player, pokemon)) {
            return DeliveryResult.PARTY;
        }
        if (addToPc(player, pokemon)) {
            return DeliveryResult.PC;
        }
        return DeliveryResult.FAILED;
    }

    public enum DeliveryResult {
        PARTY,
        PC,
        FAILED
    }

    public static JsonObject toPayload(ServerPlayer player, Pokemon pokemon) {
        if (player == null) throw new IllegalArgumentException("Player cannot be null.");
        if (pokemon == null) throw new IllegalArgumentException("Pokémon cannot be null.");

        JsonObject payload = new JsonObject();
        payload.addProperty("kind", "POKEMON");
        payload.addProperty("format", "cobblemon_pokemon_nbt_v2");
        payload.addProperty("species", safe(speciesId(pokemon)));
        payload.addProperty("uuid", pokemon.getUuid().toString());
        payload.addProperty("displayName", safe(pokemon.getDisplayName(true).getString()));
        payload.addProperty("level", pokemon.getLevel());
        payload.addProperty("shiny", pokemon.getShiny());
        payload.addProperty("gender", safe(String.valueOf(pokemon.getGender())));
        payload.addProperty("nature", readableNature(pokemon));
        payload.addProperty("ability", readableAbility(pokemon));
        String heldItem = heldItemName(pokemon);
        payload.addProperty("heldItem", heldItem);
        payload.addProperty("held_item", heldItem);
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

    private static String readableNature(Pokemon pokemon) {
        try {
            Object nature = pokemon.getNature();
            String cleaned = readableObjectName(nature, true);
            return cleaned.isBlank() ? "None" : cleaned;
        } catch (Exception e) {
            return "None";
        }
    }

    private static String readableAbility(Pokemon pokemon) {
        try {
            Object ability = pokemon.getAbility();
            String cleaned = readableObjectName(ability, false);
            return cleaned.isBlank() ? "None" : cleaned;
        } catch (Exception e) {
            return "None";
        }
    }

    private static String readableObjectName(Object source, boolean natureMode) {
        if (source == null) return "";

        for (String methodName : new String[] { "getDisplayName", "getName", "getPath", "asString", "getId", "getIdentifier", "getResourceIdentifier" }) {
            String cleaned = callStringMethod(source, methodName, natureMode);
            if (!cleaned.isBlank()) return cleaned;
        }

        for (String nestedMethod : new String[] { "getTemplate", "getEffect", "getAbility", "getNature" }) {
            try {
                Object nested = source.getClass().getMethod(nestedMethod).invoke(source);
                if (nested != null && nested != source) {
                    for (String methodName : new String[] { "getDisplayName", "getName", "getPath", "asString", "getId", "getIdentifier", "getResourceIdentifier" }) {
                        String cleaned = callStringMethod(nested, methodName, natureMode);
                        if (!cleaned.isBlank()) return cleaned;
                    }
                    String cleanedNested = cleanDisplayValue(nested, natureMode);
                    if (!cleanedNested.isBlank()) return cleanedNested;
                }
            } catch (Exception ignored) {}
        }

        return cleanDisplayValue(source, natureMode);
    }

    private static String callStringMethod(Object source, String methodName, boolean natureMode) {
        try {
            Object value = source.getClass().getMethod(methodName).invoke(source);
            return cleanDisplayValue(value, natureMode);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String cleanDisplayValue(Object value) {
        return cleanDisplayValue(value, false);
    }

    private static String cleanDisplayValue(Object value, boolean natureMode) {
        if (value == null) return "";
        String text = String.valueOf(value);
        if (text == null) return "";

        text = text.trim();
        if (text.isBlank() || text.equalsIgnoreCase("null")) return "";

        text = text.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
        text = text.replaceAll("@[a-fA-F0-9]+$", "");

        if (natureMode) {
            String knownNature = knownNatureName(text);
            if (!knownNature.isBlank()) return knownNature;
        }

        text = text
                .replace("translation{key='", "")
                .replace("translation{key=\"", "")
                .replace("literal{", "")
                .replace("}", "")
                .replace("'", "")
                .replace("\"", "")
                .trim();

        java.util.regex.Matcher kv = java.util.regex.Pattern
                .compile("(?:name|path|id|identifier|ability|nature)\\s*[=:]\\s*([A-Za-z0-9_:-]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (kv.find()) text = kv.group(1);

        int colon = text.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < text.length()) text = text.substring(colon + 1);

        java.util.regex.Matcher typed = java.util.regex.Pattern
                .compile("(?:nature|ability)[.$:]([A-Za-z0-9_-]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (typed.find()) text = typed.group(1);
        else {
            int dot = text.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < text.length()) text = text.substring(dot + 1);
            int slash = text.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < text.length()) text = text.substring(slash + 1);
        }

        text = text
                .replace("Nature", "")
                .replace("Ability", "")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();

        if (natureMode) {
            String knownNature = knownNatureName(text);
            if (!knownNature.isBlank()) return knownNature;
        }

        if (text.equalsIgnoreCase("runaway")) return "Run Away";
        if (text.equalsIgnoreCase("quickfeet")) return "Quick Feet";
        if (text.equalsIgnoreCase("lightningrod")) return "Lightning Rod";

        if (text.isBlank()) return "";
        return titleCase(text);
    }

    private static String knownNatureName(String raw) {
        if (raw == null) return "";
        String lower = raw.toLowerCase();
        String[] natures = {
                "hardy", "lonely", "brave", "adamant", "naughty",
                "bold", "docile", "relaxed", "impish", "lax",
                "timid", "hasty", "serious", "jolly", "naive",
                "modest", "mild", "quiet", "bashful", "rash",
                "calm", "gentle", "sassy", "careful", "quirky"
        };
        for (String nature : natures) {
            if (lower.matches(".*(^|[^a-z])" + nature + "($|[^a-z]).*")) {
                return titleCase(nature);
            }
        }
        return "";
    }

    private static String titleCase(String text) {
        if (text == null || text.isBlank()) return "";
        String spaced = text
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .trim();
        StringBuilder builder = new StringBuilder();
        boolean nextUpper = true;
        for (char c : spaced.toCharArray()) {
            if (Character.isWhitespace(c)) {
                builder.append(c);
                nextUpper = true;
            } else if (nextUpper) {
                builder.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
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

    private static Number cleanStatValue(Object value) {
        if (value == null) return 0;
        String cleaned = String.valueOf(value).replaceAll("[^0-9-]", "");
        if (cleaned.isBlank() || cleaned.equals("-")) return 0;
        try { return Integer.parseInt(cleaned); }
        catch (Exception e) { return 0; }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
