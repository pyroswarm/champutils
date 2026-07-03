package com.champutils.worldborder;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ChampWorldBorderCommand {

    private ChampWorldBorderCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("champborder")
                        .requires(source -> source.hasPermission(4))
                        .executes(ctx -> info(ctx.getSource(), null))
                        .then(literal("info")
                                .executes(ctx -> info(ctx.getSource(), null))
                                .then(argument("world", StringArgumentType.word())
                                        .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "world")))))
                        .then(literal("list")
                                .executes(ctx -> list(ctx.getSource())))
                        .then(literal("reload")
                                .executes(ctx -> reload(ctx.getSource())))
                        .then(literal("apply")
                                .executes(ctx -> apply(ctx.getSource())))
                        .then(literal("debug")
                                .executes(ctx -> debug(ctx.getSource())))
                        .then(literal("remove")
                                .then(argument("world", StringArgumentType.word())
                                        .executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "world")))))
                        .then(literal("set")
                                .then(argument("world", StringArgumentType.word())
                                        .then(argument("radius", DoubleArgumentType.doubleArg(1.0D))
                                                .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "world"), DoubleArgumentType.getDouble(ctx, "radius"), 0.0D, 0.0D))
                                                .then(argument("centerX", DoubleArgumentType.doubleArg())
                                                        .then(argument("centerZ", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "world"), DoubleArgumentType.getDouble(ctx, "radius"), DoubleArgumentType.getDouble(ctx, "centerX"), DoubleArgumentType.getDouble(ctx, "centerZ"))))))))
                        .then(literal("setcurrent")
                                .then(argument("radius", DoubleArgumentType.doubleArg(1.0D))
                                        .executes(ctx -> setCurrent(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "radius"), false))
                                        .then(literal("here")
                                                .executes(ctx -> setCurrent(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "radius"), true)))))
        ));
    }

    private static int info(CommandSourceStack source, String world) {
        String dimension = world;
        if (dimension == null) {
            ServerPlayer player = source.getPlayer();
            if (player != null) {
                dimension = player.serverLevel().dimension().location().toString();
            } else {
                dimension = "minecraft:overworld";
            }
        }

        String normalized = ChampWorldBorderConfig.normalizeDimension(dimension);
        ChampWorldBorderConfig.BorderEntry entry = ChampWorldBorderConfig.get(normalized);
        if (entry == null) {
            source.sendFailure(Component.literal("No ChampUtils border configured for " + normalized + "."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("ChampUtils border for " + normalized).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Radius: " + entry.radius + " | Diameter: " + (entry.radius * 2.0D) + " | Center: " + entry.centerX + ", " + entry.centerZ).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        if (ChampWorldBorderConfig.borders().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No ChampUtils world borders are configured.").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("ChampUtils world borders:").withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<String, ChampWorldBorderConfig.BorderEntry> entry : ChampWorldBorderConfig.borders().entrySet()) {
            ChampWorldBorderConfig.BorderEntry border = entry.getValue();
            source.sendSuccess(() -> Component.literal("- " + entry.getKey() + " radius=" + border.radius + " center=" + border.centerX + "," + border.centerZ).withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        ChampWorldBorderConfig.load();
        return apply(source);
    }

    private static int apply(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ChampWorldBorderManager.applyAll(server);
        source.sendSuccess(() -> Component.literal("Applied ChampUtils world borders to loaded worlds. Use /champborder debug to verify actual runtime border sizes.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int debug(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        source.sendSuccess(() -> Component.literal("Loaded worlds and current runtime borders:").withStyle(ChatFormatting.GOLD), false);
        for (ServerLevel level : server.getAllLevels()) {
            String id = level.dimension().location().toString();
            ChampWorldBorderConfig.BorderEntry configured = ChampWorldBorderConfig.get(id);
            String configuredText = configured == null
                    ? "configured=none"
                    : "configuredRadius=" + configured.radius + " configuredDiameter=" + (configured.radius * 2.0D) + " configuredCenter=" + configured.centerX + "," + configured.centerZ;
            source.sendSuccess(() -> Component.literal("- " + id + " | " + configuredText + " | " + ChampWorldBorderManager.actualBorderSummary(level)).withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    private static int remove(CommandSourceStack source, String world) {
        String normalized = ChampWorldBorderConfig.normalizeDimension(world);
        boolean removed = ChampWorldBorderConfig.remove(normalized);
        if (!removed) {
            source.sendFailure(Component.literal("No configured border existed for " + normalized + "."));
            return 0;
        }
        ChampWorldBorderManager.applyAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Removed ChampUtils border config for " + normalized + ". Restart or set a new vanilla border if you want to clear the currently applied visual border immediately.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int setCurrent(CommandSourceStack source, double radius, boolean here) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use /champborder setcurrent."));
            return 0;
        }

        String dimension = player.serverLevel().dimension().location().toString();
        double centerX = here ? player.getX() : 0.0D;
        double centerZ = here ? player.getZ() : 0.0D;
        return set(source, dimension, radius, centerX, centerZ);
    }

    private static int set(CommandSourceStack source, String world, double radius, double centerX, double centerZ) {
        String normalized = ChampWorldBorderConfig.normalizeDimension(world);
        ChampWorldBorderConfig.set(normalized, radius, centerX, centerZ);

        boolean applied = ChampWorldBorderManager.apply(source.getServer(), normalized);

        if (applied) {
            ServerLevel level = resolve(source.getServer(), normalized);
            String actual = level == null ? "unloaded" : ChampWorldBorderManager.actualBorderSummary(level);
            source.sendSuccess(() -> Component.literal("Set and applied ChampUtils border for " + normalized + " to radius " + radius + " centered at " + centerX + ", " + centerZ + ". " + actual).withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal("Saved ChampUtils border for " + normalized + ", but that world is not loaded or could not be resolved. Run /champborder debug to see loaded world IDs."));
            return 0;
        }
        return 1;
    }

    private static ServerLevel resolve(MinecraftServer server, String dimension) {
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }
}
