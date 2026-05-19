package com.champutils.teleport;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class SpawnWarpCommand {

    private static final SuggestionProvider<CommandSourceStack> WARP_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(TeleportConfig.warpNames(), builder);


    private SpawnWarpCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("spawn")
                    .executes(ctx -> spawn(ctx.getSource())));

            dispatcher.register(literal("setspawn")
                    .requires(source -> source.hasPermission(4))
                    .executes(ctx -> setSpawn(ctx.getSource())));

            dispatcher.register(literal("warp")
                    .executes(ctx -> listWarps(ctx.getSource()))
                    .then(argument("name", StringArgumentType.word())
                            .suggests(WARP_SUGGESTIONS)
                            .executes(ctx -> warp(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));

            dispatcher.register(literal("setwarp")
                    .requires(source -> source.hasPermission(4))
                    .then(argument("name", StringArgumentType.word())
                            .executes(ctx -> setWarp(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));

            dispatcher.register(literal("delwarp")
                    .requires(source -> source.hasPermission(4))
                    .then(argument("name", StringArgumentType.word())
                            .executes(ctx -> delWarp(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));
        });
    }

    private static int spawn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeleportLocation spawn = TeleportConfig.getSpawn();
        if (spawn == null) {
            player.sendSystemMessage(Component.literal("Spawn has not been set yet. Ask an admin to use /setspawn.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!TeleportConfig.teleport(player, spawn)) {
            player.sendSystemMessage(Component.literal("Spawn dimension is missing or not loaded.").withStyle(ChatFormatting.RED));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Teleported to spawn.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setSpawn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeleportConfig.setSpawn(TeleportConfig.capture(player));
        player.sendSystemMessage(Component.literal("Set server spawn to your current location.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int listWarps(CommandSourceStack source) {
        if (TeleportConfig.warps().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No warps have been set yet.").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        String names = TeleportConfig.warps().keySet().stream().sorted().collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal("Warps: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(names).withStyle(ChatFormatting.AQUA)), false);
        return 1;
    }

    private static int warp(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeleportLocation warp = TeleportConfig.getWarp(name);
        if (warp == null) {
            player.sendSystemMessage(Component.literal("Unknown warp: " + name).withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!TeleportConfig.teleport(player, warp)) {
            player.sendSystemMessage(Component.literal("Warp dimension is missing or not loaded.").withStyle(ChatFormatting.RED));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Warped to " + name.toLowerCase() + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setWarp(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeleportConfig.setWarp(name, TeleportConfig.capture(player));
        player.sendSystemMessage(Component.literal("Set warp: " + name.toLowerCase()).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int delWarp(CommandSourceStack source, String name) {
        boolean removed = TeleportConfig.deleteWarp(name);
        if (removed) {
            source.sendSuccess(() -> Component.literal("Deleted warp: " + name.toLowerCase()).withStyle(ChatFormatting.GREEN), false);
        }
        else {
            source.sendFailure(Component.literal("Unknown warp: " + name.toLowerCase()).withStyle(ChatFormatting.RED));
        }
        return removed ? 1 : 0;
    }
}
