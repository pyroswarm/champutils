package com.champutils.dex;

import com.champutils.matchmaking.PokemonIconUtil;
import com.champutils.menu.MenuUtil;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.pokemon.Species;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TrueDexMenu {

    private static final int PAGE_SIZE = 45;

    private TrueDexMenu() {
    }

    public static void open(ServerPlayer player) {
        open(player, 0);
    }

    public static void open(ServerPlayer player, int page) {
        if (player == null) return;

        List<DexSpeciesEntry> species = allSpecies();
        int maxPage = Math.max(0, (species.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(maxPage, page));

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        int caught = DexProgressManager.getCaughtCount(player);
        int total = DexProgressManager.getTotalPokemon();
        gui.setTitle(Component.literal("True Caught Dex " + caught + "/" + total));

        int start = safePage * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index >= species.size()) break;

            DexSpeciesEntry entry = species.get(index);
            boolean caughtEntry = TrueCaughtDexManager.hasTrueCaught(player, entry.key());
            gui.setSlot(slot, entryButton(entry, caughtEntry));
        }

        gui.setSlot(45, new GuiElementBuilder(Items.BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("§bTrue Caught Dex"))
                .addLoreLine(Component.literal("§7This is the dex used by §e/dexrewards§7."))
                .addLoreLine(Component.literal("§7Only real wild catches count."))
                .addLoreLine(Component.literal("§7Trades, wondertrade, crates, gifts, and commands do not count."))
                .addLoreLine(Component.literal("§7Progress: §f" + caught + "§7/§f" + total + " §8(" + String.format(Locale.ROOT, "%.2f", DexProgressManager.getCompletionPercent(player)) + "%)")));

        if (safePage > 0) {
            gui.setSlot(48, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§ePrevious Page"))
                    .addLoreLine(Component.literal("§7Page " + safePage + " / " + (maxPage + 1)))
                    .setCallback((i, c, t) -> open(player, safePage - 1)));
        }

        gui.setSlot(49, new GuiElementBuilder(CobblemonItems.POKE_BALL)
                .hideDefaultTooltip()
                .setName(Component.literal("§fPage " + (safePage + 1) + " / " + (maxPage + 1)))
                .addLoreLine(Component.literal("§7Caught Pokémon show their sprite."))
                .addLoreLine(Component.literal("§7Uncaught Pokémon show a Poké Ball.")));

        if (safePage < maxPage) {
            gui.setSlot(50, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§eNext Page"))
                    .addLoreLine(Component.literal("§7Page " + (safePage + 2) + " / " + (maxPage + 1)))
                    .setCallback((i, c, t) -> open(player, safePage + 1)));
        }

        gui.setSlot(53, new GuiElementBuilder(Items.EMERALD)
                .hideDefaultTooltip()
                .setName(Component.literal("§aOpen Dex Rewards"))
                .addLoreLine(Component.literal("§7View and claim reward milestones."))
                .setCallback((i, c, t) -> DexRewardsMenu.open(player)));

        gui.open();
    }

    private static GuiElementBuilder entryButton(DexSpeciesEntry entry, boolean caught) {
        ItemStack icon;
        if (caught) {
            icon = PokemonIconUtil.createPokemonIcon(entry.key(), false, "cobblemon:poke_ball", false);
            if (icon.isEmpty()) icon = new ItemStack(CobblemonItems.POKE_BALL);
        } else {
            icon = new ItemStack(CobblemonItems.POKE_BALL);
        }

        String prefix = caught ? "§a" : "§7";
        icon.set(DataComponents.CUSTOM_NAME, Component.literal(prefix + "#" + entry.dexNumberText() + " " + entry.displayName()));

        GuiElementBuilder builder = new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal(prefix + "#" + entry.dexNumberText() + " " + entry.displayName()))
                .addLoreLine(Component.literal(caught ? "§aTrue caught by wild catch." : "§7Not true caught yet."));

        if (!caught) {
            builder.addLoreLine(Component.literal("§8Catch this Pokémon in the wild to count it."));
        }

        return builder;
    }

    private static List<DexSpeciesEntry> allSpecies() {
        List<DexSpeciesEntry> out = new ArrayList<>();
        try {
            Class<?> pokemonSpeciesClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
            Object registry = pokemonSpeciesClass.getField("INSTANCE").get(null);
            for (String methodName : List.of("getSpecies", "getSpeciesList", "all", "allSpecies")) {
                try {
                    Method method = registry.getClass().getMethod(methodName);
                    Object result = method.invoke(registry);
                    collectSpecies(result, out);
                    if (!out.isEmpty()) break;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        out.sort(Comparator
                .comparingInt((DexSpeciesEntry entry) -> entry.dexNumber() <= 0 ? Integer.MAX_VALUE : entry.dexNumber())
                .thenComparing(DexSpeciesEntry::displayName));
        return out;
    }

    private static void collectSpecies(Object result, List<DexSpeciesEntry> out) {
        if (result == null) return;
        if (result instanceof Map<?, ?> map) {
            for (Object value : map.values()) collectSpecies(value, out);
            return;
        }
        if (result instanceof Iterable<?> iterable) {
            for (Object value : iterable) collectSpecies(value, out);
            return;
        }
        if (result.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(result); i++) collectSpecies(Array.get(result, i), out);
            return;
        }
        if (result instanceof Species species) {
            String key = speciesKey(species);
            if (key.isBlank()) return;
            int number = dexNumber(species);
            String name = niceName(species, key);
            out.add(new DexSpeciesEntry(number, key, name));
        }
    }

    private static String speciesKey(Species species) {
        try {
            return TrueCaughtDexManager.normalizeSpecies(String.valueOf(species.getResourceIdentifier()));
        } catch (Throwable ignored) {
        }
        try {
            return TrueCaughtDexManager.normalizeSpecies(String.valueOf(species.getName()));
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int dexNumber(Species species) {
        Object value = firstValue(species,
                "nationalPokedexNumber", "getNationalPokedexNumber",
                "nationalDexNumber", "getNationalDexNumber",
                "pokedexNumber", "getPokedexNumber",
                "dexNumber", "getDexNumber");
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).replaceAll("[^0-9]", ""));
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static String niceName(Species species, String fallbackKey) {
        try {
            String name = species.getName();
            if (name != null && !name.isBlank()) return title(name);
        } catch (Throwable ignored) {
        }
        return title(fallbackKey);
    }

    private static String title(String raw) {
        if (raw == null || raw.isBlank()) return "Unknown";
        String cleaned = raw.replace("cobblemon:", "").replace('_', ' ').replace('-', ' ').trim();
        String[] parts = cleaned.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) builder.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return builder.isEmpty() ? "Unknown" : builder.toString();
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            Object value = null;
            if (name.startsWith("get")) value = call(source, name);
            if (value == null) value = field(source, name);
            if (value == null) value = call(source, name);
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
        Class<?> type = source.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private record DexSpeciesEntry(int dexNumber, String key, String displayName) {
        String dexNumberText() {
            return dexNumber <= 0 ? "???" : String.format(Locale.ROOT, "%04d", dexNumber);
        }
    }
}
