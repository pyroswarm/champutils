package com.champutils.dex;

import com.champutils.breeding.BreedingEggData;
import com.champutils.profile.PlayerProfileManager;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** OP-only administration commands for correcting TrueDex progress. */
public final class TrueDexAdminCommand {
    private TrueDexAdminCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("truedex")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("pokemon", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(speciesSuggestions(), builder))
                                        .executes(context -> unlock(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "pokemon")
                                        ))))));
    }

    private static int unlock(CommandSourceStack source, ServerPlayer target, String requestedSpecies) {
        SpeciesChoice choice = resolveSpecies(requestedSpecies);
        if (choice == null) {
            source.sendFailure(Component.literal("Unknown Pokémon: " + requestedSpecies)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean added = TrueCaughtDexManager.markTrueCaught(
                PlayerProfileManager.activeProfileId(target),
                choice.key()
        );

        if (!added) {
            source.sendFailure(Component.literal(target.getGameProfile().getName() + " already has "
                    + choice.displayName() + " unlocked in their active TrueDex profile.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Unlocked ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(choice.displayName()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" in " + target.getGameProfile().getName() + "'s TrueDex.")), true);

        target.sendSystemMessage(Component.literal("TrueDex updated: ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(choice.displayName()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" was unlocked by a server operator.").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static Collection<String> speciesSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (SpeciesChoice choice : allSpecies().values()) {
            suggestions.add(choice.key());
        }
        suggestions.sort(String.CASE_INSENSITIVE_ORDER);
        return suggestions;
    }

    private static SpeciesChoice resolveSpecies(String input) {
        String normalized = normalizeLookup(input);
        if (normalized.isBlank()) return null;

        Map<String, SpeciesChoice> species = allSpecies();
        SpeciesChoice exact = species.get(normalized);
        if (exact != null) return exact;

        for (SpeciesChoice choice : species.values()) {
            if (normalizeLookup(choice.displayName()).equals(normalized)) return choice;
        }
        return null;
    }

    private static Map<String, SpeciesChoice> allSpecies() {
        Map<String, SpeciesChoice> out = new LinkedHashMap<>();
        try {
            Class<?> pokemonSpeciesClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
            Object registry = pokemonSpeciesClass.getField("INSTANCE").get(null);
            for (String methodName : List.of("getSpecies", "getSpeciesList", "all", "allSpecies")) {
                try {
                    Method method = registry.getClass().getMethod(methodName);
                    collectSpecies(method.invoke(registry), out);
                    if (!out.isEmpty()) break;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void collectSpecies(Object result, Map<String, SpeciesChoice> out) {
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
        if (!(result instanceof Species species)) return;

        try {
            if (BreedingEggData.EGG_SPECIES.equals(String.valueOf(species.getResourceIdentifier()))) return;
        } catch (Throwable ignored) {
        }

        String key;
        try {
            key = TrueCaughtDexManager.normalizeSpecies(String.valueOf(species.getResourceIdentifier()));
        } catch (Throwable ignored) {
            key = TrueCaughtDexManager.normalizeSpecies(species.getName());
        }
        if (key.isBlank()) return;

        String displayName;
        try {
            displayName = title(species.getName());
        } catch (Throwable ignored) {
            displayName = title(key);
        }
        out.putIfAbsent(key, new SpeciesChoice(key, displayName));
    }

    private static String normalizeLookup(String value) {
        if (value == null) return "";
        return TrueCaughtDexManager.normalizeSpecies(value)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String title(String raw) {
        if (raw == null || raw.isBlank()) return "Unknown";
        String cleaned = raw.replace("cobblemon:", "").replace('_', ' ').replace('-', ' ').trim();
        StringBuilder result = new StringBuilder();
        for (String part : cleaned.split("\\s+")) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) result.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.isEmpty() ? "Unknown" : result.toString();
    }

    private record SpeciesChoice(String key, String displayName) {
    }
}
