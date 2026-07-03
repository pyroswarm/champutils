package com.champutils.commands;

import com.champutils.wiki.PokemonWikiIndex;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Set;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class PokemonWikiCommand {
    private PokemonWikiCommand() {}

    private static final SuggestionProvider<CommandSourceStack> POKEMON_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (String species : PokemonWikiIndex.speciesSuggestions()) {
            if (species.startsWith(remaining)) builder.suggest(species);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> TOPIC_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (String topic : PokemonWikiIndex.topicSuggestions()) {
            if (topic.startsWith(remaining)) builder.suggest(topic);
        }
        return builder.buildFuture();
    };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("wiki")
                .then(argument("pokemon", StringArgumentType.word())
                        .suggests(POKEMON_SUGGESTIONS)
                        .executes(ctx -> {
                            sendSummary(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "pokemon"));
                            return 1;
                        })
                        .then(argument("topic", StringArgumentType.word())
                                .suggests(TOPIC_SUGGESTIONS)
                                .executes(ctx -> {
                                    sendTopic(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "pokemon"), StringArgumentType.getString(ctx, "topic"));
                                    return 1;
                                })
                        )
                )
                .then(literal("reload")
                        .requires(source -> source.hasPermission(4))
                        .executes(ctx -> {
                            PokemonWikiIndex.reload(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Reloaded Pokémon wiki index."), false);
                            return 1;
                        }))
        ));
    }

    private static void sendSummary(ServerPlayer player, String pokemon) {
        player.sendSystemMessage(Component.literal("§6" + prettyPokemon(pokemon) + " Wiki"));
        player.sendSystemMessage(Component.literal("§7Use: §e/wiki " + pokemon.toLowerCase(Locale.ROOT) + " <type|ability|biome|time|level|rarity|egg_moves|drops>"));
        sendTopic(player, pokemon, "type");
        sendTopic(player, pokemon, "ability");
        sendTopic(player, pokemon, "biome");
        sendTopic(player, pokemon, "time");
        sendTopic(player, pokemon, "rarity");
        sendTopic(player, pokemon, "drops");
    }

    private static void sendTopic(ServerPlayer player, String pokemon, String topic) {
        PokemonWikiIndex.Info info = PokemonWikiIndex.get(pokemon);
        String t = topic.toLowerCase(Locale.ROOT);
        String label;
        String value;
        switch (t) {
            case "biome", "biomes", "spawn", "spawns" -> { label = "Spawn biomes"; value = join(info == null ? null : info.biomes, "No natural spawn biome data found. This Pokémon may come from events, fossils, evolution, special configs, or another system."); }
            case "time", "times" -> { label = "Time"; value = join(info == null ? null : info.times, "Any time, or no time restriction was found."); }
            case "level", "levels" -> { label = "Wild level"; value = join(info == null ? null : info.levels, "No wild level range found."); }
            case "rarity", "bucket" -> { label = "Rarity"; value = join(info == null ? null : info.rarity, "No rarity bucket found."); }
            case "block", "blocks" -> { label = "Nearby blocks"; value = join(info == null ? null : info.blocks, "No nearby block requirement found."); }
            case "structure", "structures" -> { label = "Structures"; value = join(info == null ? null : info.structures, "No structure requirement found."); }
            case "weather" -> { label = "Weather"; value = join(info == null ? null : info.weather, "No weather restriction found."); }
            case "extra", "requirements" -> { label = "Extra requirements"; value = join(info == null ? null : info.extra, "No extra requirements found."); }
            case "ability", "abilities" -> { label = "Abilities"; value = PokemonWikiIndex.abilities(pokemon); }
            case "type", "types" -> { label = "Type"; value = PokemonWikiIndex.types(pokemon); }
            case "egg", "eggs", "eggmove", "eggmoves", "egg_moves" -> { label = "Egg moves"; value = PokemonWikiIndex.eggMoves(pokemon); }
            case "drop", "drops", "loot" -> { label = "Wild battle drops"; value = PokemonWikiIndex.drops(pokemon); }
            default -> { label = "Unknown topic"; value = "Try type, ability, biome, time, level, rarity, egg_moves, drops, block, structure, or weather."; }
        }
        player.sendSystemMessage(Component.literal("§6" + prettyPokemon(pokemon) + " §e" + label + ": §f" + value));
    }

    private static String join(Set<String> values, String fallback) {
        if (values == null || values.isEmpty()) return fallback;
        return values.stream()
                .map(PokemonWikiIndex::prettyId)
                .filter(value -> !value.isBlank())
                .reduce((a, b) -> a + "§7, §f" + b)
                .orElse(fallback);
    }

    private static String prettyPokemon(String raw) {
        return PokemonWikiIndex.prettyId(raw);
    }
}
