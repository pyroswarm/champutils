package com.champutils.commands;

import com.champutils.config.Config;
import com.champutils.matchmaking.ArenaManager;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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

public final class ArenaCommand {

    private static final SuggestionProvider<CommandSourceStack> ARENA_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(arenaIds(), builder);

    private ArenaCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("arena")
                        .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource())))
                        .then(Commands.literal("create")
                                .then(Commands.argument("arenaId", StringArgumentType.word())
                                        .executes(context -> create(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "arenaId")
                                        ))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("arenaId", StringArgumentType.word())
                                        .suggests(ARENA_SUGGESTIONS)
                                        .executes(context -> delete(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "arenaId")
                                        ))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("arenaId", StringArgumentType.word())
                                        .suggests(ARENA_SUGGESTIONS)
                                        .executes(context -> setCenterHere(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "arenaId")
                                        ))))
                        .then(Commands.literal("setcenter")
                                .then(Commands.argument("arenaId", StringArgumentType.word())
                                        .suggests(ARENA_SUGGESTIONS)
                                        .executes(context -> setCenterHere(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "arenaId")
                                        ))
                                        .then(Commands.argument("centerX", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("centerZ", DoubleArgumentType.doubleArg())
                                                                .executes(context -> setCenter(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "arenaId"),
                                                                        DoubleArgumentType.getDouble(context, "centerX"),
                                                                        DoubleArgumentType.getDouble(context, "y"),
                                                                        DoubleArgumentType.getDouble(context, "centerZ")
                                                                )))))))
                        .then(Commands.literal("theme")
                                .then(Commands.argument("arenaId", StringArgumentType.word())
                                        .suggests(ARENA_SUGGESTIONS)
                                        .then(Commands.argument("theme", StringArgumentType.greedyString())
                                                .executes(context -> setTheme(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "arenaId"),
                                                        StringArgumentType.getString(context, "theme")
                                                )))))
                        .then(Commands.literal("music")
                                .then(Commands.argument("arenaId", StringArgumentType.word())
                                        .suggests(ARENA_SUGGESTIONS)
                                        .then(Commands.argument("music", StringArgumentType.greedyString())
                                                .executes(context -> setMusic(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "arenaId"),
                                                        StringArgumentType.getString(context, "music")
                                                )))))
        ));
    }

    private static int create(CommandSourceStack source, String arenaId) {
        ArenaManager.Arena arena = Config.createArena(arenaId);
        source.sendSuccess(() -> Component.literal("Created arena: " + arena.id).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int delete(CommandSourceStack source, String arenaId) {
        boolean removed = Config.deleteArena(arenaId);
        if (!removed) {
            source.sendFailure(Component.literal("Unknown arena: " + arenaId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Deleted arena: " + arenaId.toLowerCase()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setCenterHere(CommandSourceStack source, String arenaId) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can set arena centers from their location."));
            return 0;
        }

        ArenaManager.Arena arena = getOrCreate(arenaId);
        arena.world = player.serverLevel().dimension().location().toString();
        arena.centerX = player.getX();
        arena.y = player.getY();
        arena.centerZ = player.getZ();
        Config.save();

        source.sendSuccess(() -> Component.literal("Set " + arena.id + " center to your current location in " + arena.world + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setCenter(CommandSourceStack source, String arenaId, double x, double y, double z) {
        ServerPlayer player = source.getPlayer();
        ArenaManager.Arena arena = getOrCreate(arenaId);

        if (player != null) {
            arena.world = player.serverLevel().dimension().location().toString();
        } else if (arena.world == null || arena.world.isBlank()) {
            arena.world = "multiworld:spawn1";
        }

        arena.centerX = x;
        arena.y = y;
        arena.centerZ = z;
        Config.save();

        source.sendSuccess(() -> Component.literal("Set " + arena.id + " center to " + format(x) + " " + format(y) + " " + format(z) + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setTheme(CommandSourceStack source, String arenaId, String theme) {
        ArenaManager.Arena arena = getOrCreate(arenaId);
        arena.theme = theme.trim();
        Config.save();
        source.sendSuccess(() -> Component.literal("Set " + arena.id + " theme to: " + arena.theme).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setMusic(CommandSourceStack source, String arenaId, String music) {
        ArenaManager.Arena arena = getOrCreate(arenaId);
        arena.music = music.trim();
        Config.save();
        source.sendSuccess(() -> Component.literal("Set " + arena.id + " music to: " + arena.music).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        Config.ensureDefaultArenas();

        source.sendSuccess(() -> Component.literal("PvP Arenas:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (ArenaManager.Arena arena : Config.arenas) {
            if (arena == null) continue;

            source.sendSuccess(() -> Component.literal("- " + arena.id + " | " + safe(arena.world) + " | "
                            + format(arena.centerX) + " " + format(arena.y) + " " + format(arena.centerZ)
                            + " | Theme: " + safe(arena.theme)
                            + " | Music: " + safe(arena.music))
                    .withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    private static ArenaManager.Arena getOrCreate(String arenaId) {
        Config.ensureDefaultArenas();
        ArenaManager.Arena arena = Config.getArena(arenaId);
        return arena == null ? Config.createArena(arenaId) : arena;
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

        return ids.toArray(new String[0]);
    }

    private static String format(double value) {
        return String.format("%.1f", value);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }
}
