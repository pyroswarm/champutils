package com.champutils.commands;

import com.champutils.config.Config;
import com.champutils.matchmaking.ArenaLocationConfig;
import com.champutils.matchmaking.ArenaManager;
import com.champutils.util.ServerLocation;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ArenaCommand {

    private static final SuggestionProvider<CommandSourceStack> ARENA_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(arenaIds(), builder);

    private ArenaCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("arena")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.literal("set")
                                .then(Commands.argument("arenaId", StringArgumentType.word())
                                        .suggests(ARENA_SUGGESTIONS)
                                        .executes(context -> setArena(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "arenaId")
                                        ))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("arenaId", StringArgumentType.word())
                                        .suggests(ARENA_SUGGESTIONS)
                                        .executes(context -> deleteArena(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "arenaId")
                                        ))))
                        .then(Commands.literal("list")
                                .executes(context -> listArenas(context.getSource())))
        ));
    }

    private static int setArena(CommandSourceStack source, String arenaId) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can set arena locations."));
            return 0;
        }

        ArenaLocationConfig.setArena(arenaId, player);

        source.sendSuccess(
                () -> Component.literal("Set arena center '" + arenaId.toLowerCase() + "' to your current location in " + player.serverLevel().dimension().location() + ".")
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        return 1;
    }

    private static int deleteArena(CommandSourceStack source, String arenaId) {
        boolean removed = ArenaLocationConfig.deleteArena(arenaId);

        if (!removed) {
            source.sendFailure(Component.literal("No saved arena location found for: " + arenaId));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Deleted saved arena location: " + arenaId.toLowerCase()).withStyle(ChatFormatting.GREEN),
                true
        );

        return 1;
    }

    private static int listArenas(CommandSourceStack source) {
        Map<String, ServerLocation> arenas = ArenaLocationConfig.arenas();

        if (arenas.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("No arena locations have been saved yet. Use /arena set <arenaId>.").withStyle(ChatFormatting.YELLOW),
                    false
            );
            return 1;
        }

        source.sendSuccess(
                () -> Component.literal("Saved arena locations:").withStyle(ChatFormatting.GOLD),
                false
        );

        arenas.forEach((id, location) -> source.sendSuccess(
                () -> Component.literal("- " + id + " -> " + location.world + " "
                        + format(location.x) + " "
                        + format(location.y) + " "
                        + format(location.z))
                        .withStyle(ChatFormatting.GRAY),
                false
        ));

        return 1;
    }

    private static String[] arenaIds() {
        List<String> ids = new ArrayList<>();

        if (Config.arenas != null) {
            for (ArenaManager.Arena arena : Config.arenas) {
                if (arena != null && arena.id != null && !arena.id.isBlank()) {
                    ids.add(arena.id);
                }
            }
        }

        ids.addAll(ArenaLocationConfig.arenas().keySet());

        return ids.toArray(new String[0]);
    }

    private static String format(double value) {
        return String.format("%.1f", value);
    }
}
