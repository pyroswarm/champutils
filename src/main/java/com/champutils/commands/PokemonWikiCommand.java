package com.champutils.commands;

import com.champutils.wiki.PokemonWikiIndex;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class PokemonWikiCommand {
    private PokemonWikiCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("wiki")
                .then(argument("pokemon", StringArgumentType.word())
                        .executes(ctx -> {
                            sendSummary(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "pokemon"));
                            return 1;
                        })
                        .then(argument("topic", StringArgumentType.word())
                                .executes(ctx -> {
                                    sendTopic(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "pokemon"), StringArgumentType.getString(ctx, "topic"));
                                    return 1;
                                })
                        )
                )
                .then(literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            PokemonWikiIndex.reload(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Reloaded Pokémon wiki index."), false);
                            return 1;
                        }))
        ));
    }

    private static void sendSummary(ServerPlayer player, String pokemon) {
        player.sendSystemMessage(Component.literal("§6/wiki " + pokemon + " topics: §ebiome, time, ability, type, level, rarity, blocks, structures"));
        sendTopic(player, pokemon, "biome");
    }

    private static void sendTopic(ServerPlayer player, String pokemon, String topic) {
        PokemonWikiIndex.Info info = PokemonWikiIndex.get(pokemon);
        String t = topic.toLowerCase();
        String value;
        switch (t) {
            case "biome", "biomes", "spawn", "spawns" -> value = join(info == null ? null : info.biomes, "No biome spawn data found. This Pokémon may not spawn naturally or may be special-config only.");
            case "time", "times" -> value = join(info == null ? null : info.times, "Any time / no time restriction found.");
            case "level", "levels" -> value = join(info == null ? null : info.levels, "No level range found.");
            case "rarity", "bucket" -> value = join(info == null ? null : info.rarity, "No rarity bucket found.");
            case "block", "blocks" -> value = join(info == null ? null : info.blocks, "No nearby block requirement found.");
            case "structure", "structures" -> value = join(info == null ? null : info.structures, "No structure requirement found.");
            case "ability", "abilities" -> value = PokemonWikiIndex.abilities(pokemon);
            case "type", "types" -> value = PokemonWikiIndex.types(pokemon);
            default -> value = "Unknown topic. Try biome, time, ability, type, level, rarity, blocks, or structures.";
        }
        player.sendSystemMessage(Component.literal("§6" + pretty(pokemon) + " §e" + topic + ": §f" + value));
    }

    private static String join(Set<String> values, String fallback) {
        if (values == null || values.isEmpty()) return fallback;
        return String.join("§7, §f", values);
    }

    private static String pretty(String raw) {
        String value = raw == null ? "" : raw.replace('_', ' ').replace('-', ' ');
        StringBuilder out = new StringBuilder();
        for (String p : value.split(" ")) {
            if (p.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(p.charAt(0))).append(p.length() > 1 ? p.substring(1).toLowerCase() : "");
        }
        return out.toString();
    }
}
