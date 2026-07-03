package com.champutils.emblem;

import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.profession.ProfessionManager;

import eu.pb4.polymer.core.api.item.PolymerItem;

import com.cobblemon.mod.common.Cobblemon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class EmblemManager {

    private static final Map<String, Item> REGISTERED = new HashMap<>();

    private EmblemManager() {}

    public static void registerEmblems() {
        EmblemConfig.load();
        REGISTERED.clear();
        for (Map.Entry<String, EmblemConfig.EmblemData> entry : EmblemConfig.CONFIG.emblems.entrySet()) {
            EmblemConfig.EmblemData data = entry.getValue();
            if (data == null || data.id == null || data.id.isBlank()) continue;
            Item base = resolveItem(data.baseItem);
            Item item = new EmblemItem(normalize(data.id), base, new Item.Properties().stacksTo(64));
            try {
                Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("champutils", normalize(data.id) + "_emblem"), item);
                REGISTERED.put(normalize(data.id), item);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("[ChampUtils] Registered " + REGISTERED.size() + " emblem items.");
    }

    public static ItemStack createEmblemStack(String emblemId, int amount) {
        String id = normalize(emblemId);
        EmblemConfig.EmblemData data = EmblemConfig.CONFIG.emblems.get(id);
        Item item = REGISTERED.get(id);
        if (data == null || item == null || amount <= 0) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.min(64, amount));
        applyDisplay(stack, data);
        return stack;
    }

    private static void applyDisplay(ItemStack stack, EmblemConfig.EmblemData data) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(data.displayName == null ? "Emblem" : data.displayName).withStyle(colorForType(data.type), ChatFormatting.BOLD));
        List<Component> lore = new ArrayList<>();
        if (data.lore != null && !data.lore.isBlank()) lore.add(Component.literal(data.lore).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Right-click the matching Pokémon to use.").withStyle(ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        if (data.customModelData > 0) stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(data.customModelData));
    }

    public static String getEmblemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Map.Entry<String, Item> entry : REGISTERED.entrySet()) {
            if (stack.getItem() == entry.getValue()) return entry.getKey();
        }
        return null;
    }

    public static CraftResult craft(ServerPlayer player, String emblemId) {
        String id = normalize(emblemId);
        EmblemConfig.EmblemData data = EmblemConfig.CONFIG.emblems.get(id);
        if (player == null) return CraftResult.fail("Player missing.");
        if (data == null) return CraftResult.fail("Unknown emblem: " + emblemId);

        String fragmentKey = ProfessionFragmentConfig.normalizeRarity(data.fragment);
        int fragmentCost = Math.max(0, data.fragmentCost);
        int fragments = ProfessionFragmentManager.countFragments(player, fragmentKey);
        if (fragmentCost > 0 && fragments < fragmentCost) {
            return CraftResult.fail("You need " + fragmentCost + " " + ProfessionFragmentManager.formatWords(fragmentKey) + " fragments. You have " + fragments + ".");
        }

        for (EmblemConfig.ItemCost cost : data.itemCosts) {
            int need = Math.max(0, cost.amount);
            if (need <= 0) continue;
            Item item = resolveItem(cost.item);
            if (item == Items.AIR) return CraftResult.fail("Invalid configured item cost: " + cost.item);
            int have = countItem(player, item);
            if (have < need) return CraftResult.fail("You need " + need + "x " + item.getDescription().getString() + ". You have " + have + ".");
        }

        if (fragmentCost > 0 && !ProfessionManager.removeFragments(player, fragmentKey, fragmentCost)) return CraftResult.fail("Could not remove fragments.");
        for (EmblemConfig.ItemCost cost : data.itemCosts) removeItem(player, resolveItem(cost.item), Math.max(0, cost.amount));

        ItemStack emblem = createEmblemStack(id, 1);
        if (emblem.isEmpty()) return CraftResult.fail("Could not create emblem item.");
        if (!player.getInventory().add(emblem)) player.drop(emblem, false);
        ProfessionNotificationSettings.playSound(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.5F);
        return CraftResult.success(data.displayName == null ? id : data.displayName);
    }

    public static UseResult useOnPokemon(ServerPlayer player, ItemStack stack, Object pokemonOrEntity) {
        String emblemId = getEmblemId(stack);
        if (emblemId == null) return UseResult.pass();
        EmblemConfig.EmblemData data = EmblemConfig.CONFIG.emblems.get(emblemId);
        if (data == null) return UseResult.fail("This emblem is no longer configured.");
        Object pokemon = extractPokemon(pokemonOrEntity);
        if (pokemon == null) return UseResult.fail("Use this on a Pokémon.");
        if (!isOwnedByPlayer(player, pokemon)) return UseResult.fail("You can only use emblems on Pokémon you own.");
        String species = speciesId(pokemon);
        if (species.isBlank()) return UseResult.fail("Could not read that Pokémon species.");
        if (isShiny(pokemon) && !"MEGASTONE".equalsIgnoreCase(data.type)) return UseResult.fail("That Pokémon is already shiny.");

        String type = data.type == null ? "" : data.type.trim().toUpperCase(Locale.ROOT);
        switch (type) {
            case "REGULAR_SHINY" -> {
                if (isLegendary(species) || isUltraBeast(species) || isParadox(species)) return UseResult.fail("This emblem only works on regular Pokémon.");
                setShiny(pokemon, true); consume(stack, player); successEffects(player, "That Pokémon is now shiny!"); return UseResult.ok();
            }
            case "ULTRA_PARADOX_SHINY" -> {
                if (!isUltraBeast(species) && !isParadox(species)) return UseResult.fail("This emblem only works on Ultra Beasts or Paradox Pokémon.");
                setShiny(pokemon, true); consume(stack, player); successEffects(player, "That special Pokémon is now shiny!"); return UseResult.ok();
            }
            case "LEGENDARY_SHINY" -> {
                if (!isLegendary(species)) return UseResult.fail("This emblem only works on Legendary Pokémon.");
                setShiny(pokemon, true); consume(stack, player); successEffects(player, "That Legendary Pokémon is now shiny!"); return UseResult.ok();
            }
            case "MEGASTONE" -> {
                if (!hasMega(species)) return UseResult.fail("That Pokémon does not have a Mega Evolution configured.");
                ItemStack stone = createMegaStone(species);
                if (stone.isEmpty()) return UseResult.fail("The Mega Stone item for " + species + " is not installed or is configured incorrectly.");
                if (!player.getInventory().add(stone)) player.drop(stone, false);
                consume(stack, player); successEffects(player, "Created a Mega Stone for " + pretty(species) + "!"); return UseResult.ok();
            }
            default -> { return UseResult.fail("Unknown emblem type: " + data.type); }
        }
    }

    private static void consume(ItemStack stack, ServerPlayer player) {
        if (!player.isCreative()) stack.shrink(1);
    }

    private static void successEffects(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD));
        ProfessionNotificationSettings.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.3F);
    }

    public static boolean isLegendary(String s) { return contains(EmblemConfig.CONFIG.legendarySpecies, s); }
    public static boolean isUltraBeast(String s) { return contains(EmblemConfig.CONFIG.ultraBeastSpecies, s); }
    public static boolean isParadox(String s) { return contains(EmblemConfig.CONFIG.paradoxSpecies, s); }
    public static boolean hasMega(String s) { return contains(EmblemConfig.CONFIG.megaCapableSpecies, s); }
    private static boolean contains(Iterable<String> set, String value) { String n = normalizeSpecies(value); for (String s: set) if (normalizeSpecies(s).equals(n)) return true; return false; }

    private static ItemStack createMegaStone(String species) {
        String normalized = normalizeSpecies(species);
        String id = EmblemConfig.CONFIG.megaStoneOverrides.getOrDefault(normalized, null);
        if (id == null || id.isBlank()) id = (EmblemConfig.CONFIG.megaStoneItemPattern == null ? "genesisforms:%species%ite" : EmblemConfig.CONFIG.megaStoneItemPattern).replace("%species%", normalized);
        Item item = resolveItem(id);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, 1);
    }


    private static boolean isOwnedByPlayer(ServerPlayer player, Object pokemon) {
        if (player == null || pokemon == null) return false;

        UUID pokemonUuid = pokemonUuid(pokemon);
        if (pokemonUuid == null) return false;

        try {
            Object party = Cobblemon.INSTANCE.getStorage().getParty(player);
            if (storeContainsPokemonUuid(party, pokemonUuid)) return true;
        } catch (Throwable ignored) {}

        try {
            Object storage = Cobblemon.INSTANCE.getStorage();
            Object pc = null;
            for (String methodName : new String[] { "getPC", "getPc", "getPCStore", "getPcStore" }) {
                for (Method method : storage.getClass().getMethods()) {
                    if (!method.getName().equals(methodName) || method.getParameterCount() != 1) continue;
                    Class<?> parameter = method.getParameterTypes()[0];
                    Object argument;
                    if (parameter.isAssignableFrom(ServerPlayer.class)) {
                        argument = player;
                    } else if (parameter.isAssignableFrom(UUID.class)) {
                        argument = player.getUUID();
                    } else {
                        continue;
                    }
                    pc = method.invoke(storage, argument);
                    if (pc != null) break;
                }
                if (pc != null) break;
            }
            return storeContainsPokemonUuid(pc, pokemonUuid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static UUID pokemonUuid(Object pokemon) {
        Object value = firstValue(pokemon, "uuid", "getUuid", "getUUID");
        if (value instanceof UUID uuid) return uuid;
        if (value != null) {
            try { return UUID.fromString(String.valueOf(value)); } catch (Exception ignored) {}
        }
        return null;
    }

    private static boolean storeContainsPokemonUuid(Object store, UUID pokemonUuid) {
        if (store == null || pokemonUuid == null) return false;

        try {
            Object sizeValue = firstValue(store, "size", "getSize");
            int size = sizeValue instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(sizeValue));
            for (int i = 0; i < size; i++) {
                Object pokemon = getStorePokemonAt(store, i);
                if (pokemonUuid.equals(pokemonUuid(pokemon))) return true;
            }
        } catch (Throwable ignored) {}

        if (store instanceof Iterable<?> iterable) {
            for (Object pokemon : iterable) {
                if (pokemonUuid.equals(pokemonUuid(pokemon))) return true;
            }
        }

        try {
            for (Method method : store.getClass().getMethods()) {
                if (!method.getName().equals("iterator") || method.getParameterCount() != 0) continue;
                Object iteratorObject = method.invoke(store);
                if (!(iteratorObject instanceof Iterator<?> iterator)) continue;
                while (iterator.hasNext()) {
                    Object pokemon = iterator.next();
                    if (pokemonUuid.equals(pokemonUuid(pokemon))) return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static Object getStorePokemonAt(Object store, int index) {
        if (store == null) return null;
        for (String methodName : new String[] { "get", "getPokemon" }) {
            try {
                Method method = store.getClass().getMethod(methodName, int.class);
                return method.invoke(store, index);
            } catch (Throwable ignored) {}
            try {
                Method method = store.getClass().getMethod(methodName, Integer.class);
                return method.invoke(store, index);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object extractPokemon(Object source) {
        if (source == null) return null;
        Object nested = firstValue(source, "pokemon", "getPokemon");
        return nested == null ? source : nested;
    }

    private static String speciesId(Object pokemon) {
        Object species = firstValue(pokemon, "species", "getSpecies");
        if (species == null) return normalizeSpecies(String.valueOf(pokemon));
        Object id = firstValue(species, "resourceIdentifier", "getResourceIdentifier", "identifier", "getIdentifier", "id", "getId", "name", "getName");
        return normalizeSpecies(id == null ? String.valueOf(species) : String.valueOf(id));
    }

    private static boolean isShiny(Object pokemon) {
        Object val = firstValue(pokemon, "shiny", "getShiny", "isShiny");
        return val != null && String.valueOf(val).equalsIgnoreCase("true");
    }

    private static void setShiny(Object pokemon, boolean value) {
        if (pokemon == null) return;
        try { Method m = pokemon.getClass().getMethod("setShiny", boolean.class); m.invoke(pokemon, value); return; } catch (Exception ignored) {}
        try { Field f = pokemon.getClass().getDeclaredField("shiny"); f.setAccessible(true); f.setBoolean(pokemon, value); } catch (Exception ignored) {}
    }

    private static Object firstValue(Object source, String... names) {
        for (String name : names) {
            try {
                if (name.startsWith("get") || name.startsWith("is")) {
                    Method m = source.getClass().getMethod(name); if (m.getParameterCount() == 0) { Object v = m.invoke(source); if (v != null) return v; }
                } else {
                    Class<?> c = source.getClass();
                    while (c != null) {
                        try { Field f = c.getDeclaredField(name); f.setAccessible(true); Object v = f.get(source); if (v != null) return v; break; } catch (Exception ignored) { c = c.getSuperclass(); }
                    }
                    try { Method m = source.getClass().getMethod(name); if (m.getParameterCount() == 0) { Object v = m.invoke(source); if (v != null) return v; } } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Item resolveItem(String id) {
        if (id == null || id.isBlank()) return Items.AIR;
        try { Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); return item == null ? Items.AIR : item; } catch (Exception e) { return Items.AIR; }
    }

    private static int countItem(ServerPlayer player, Item item) {
        int count = 0;
        for (ItemStack s : player.getInventory().items) if (!s.isEmpty() && s.getItem() == item) count += s.getCount();
        return count;
    }

    private static void removeItem(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (ItemStack s : player.getInventory().items) {
            if (remaining <= 0) return;
            if (!s.isEmpty() && s.getItem() == item) { int take = Math.min(remaining, s.getCount()); s.shrink(take); remaining -= take; }
        }
    }

    public static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_"); }
    private static String normalizeSpecies(String raw) { if (raw == null) return ""; String v = raw.trim().toLowerCase(Locale.ROOT); int c = v.lastIndexOf(':'); if (c >= 0 && c + 1 < v.length()) v = v.substring(c + 1); return v.replaceAll("[^a-z0-9_]", ""); }
    private static String pretty(String raw) { String[] p = normalizeSpecies(raw).split("_"); StringBuilder b = new StringBuilder(); for (String x:p) { if (x.isBlank()) continue; if (b.length()>0) b.append(' '); b.append(Character.toUpperCase(x.charAt(0))).append(x.length()>1?x.substring(1):""); } return b.toString(); }
    private static ChatFormatting colorForType(String type) { if (type == null) return ChatFormatting.WHITE; return switch (type.toUpperCase(Locale.ROOT)) { case "REGULAR_SHINY" -> ChatFormatting.GOLD; case "ULTRA_PARADOX_SHINY" -> ChatFormatting.LIGHT_PURPLE; case "LEGENDARY_SHINY" -> ChatFormatting.AQUA; case "MEGASTONE" -> ChatFormatting.DARK_PURPLE; default -> ChatFormatting.WHITE; }; }

    public record CraftResult(boolean success, String error, String displayName) { public static CraftResult fail(String e){return new CraftResult(false,e,null);} public static CraftResult success(String d){return new CraftResult(true,null,d);} }
    public record UseResult(boolean handled, boolean success, String error) {
        public static UseResult pass(){return new UseResult(false,false,null);}
        public static UseResult fail(String e){return new UseResult(true,false,e);}
        public static UseResult ok(){return new UseResult(true,true,null);}
    }

    public static class EmblemItem extends Item implements PolymerItem {
        private final String emblemId; private final Item baseItem;
        public EmblemItem(String emblemId, Item baseItem, Properties properties) { super(properties); this.emblemId = emblemId; this.baseItem = baseItem == null ? Items.NETHER_STAR : baseItem; }
        @Override public Item getPolymerItem(ItemStack stack, ServerPlayer player) { return baseItem; }
    }
}
