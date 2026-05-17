package com.champutils.commands;

import com.champutils.worldevent.WorldEventBindingRegistry;
import com.champutils.worldevent.WorldEventConfig;
import com.champutils.worldevent.WorldEventManager;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.cobblemon.mod.common.entity.npc.NPCEntity;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class WorldEventCommand {

    private WorldEventCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("worldevent")
                        .then(Commands.literal("teleport")
                                .then(Commands.argument("eventId", StringArgumentType.word())
                                        .executes(ctx -> teleport(
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "eventId")
                                        ))))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("eventId", StringArgumentType.word())
                                        .executes(ctx -> teleport(
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "eventId")
                                        ))))
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("bindings")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> bindings(ctx.getSource())))
                        .then(Commands.literal("bind")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("eventId", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (String id : WorldEventConfig.EVENTS.keySet()) builder.suggest(id);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> bind(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "eventId")
                                        ))))
                        .then(Commands.literal("unbind")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("eventId", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (String id : WorldEventBindingRegistry.getAll().keySet()) builder.suggest(id);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> unbind(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "eventId")
                                        ))))
                        .then(Commands.literal("start")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("eventId", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (String id : WorldEventConfig.EVENTS.keySet()) builder.suggest(id);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> start(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "eventId")
                                        )))
                                .then(Commands.literal("random")
                                        .executes(ctx -> startRandom(ctx.getSource()))))
                        .then(Commands.literal("stop")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("eventId", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (WorldEventManager.ActiveEvent active : WorldEventManager.getActiveEvents()) builder.suggest(active.eventId);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> stop(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "eventId")
                                        )))
                                .then(Commands.literal("all")
                                        .executes(ctx -> stopAll(ctx.getSource()))))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> reload(ctx.getSource())))
        ));
    }

    private static int teleport(ServerPlayer player, String eventId) {
        if (!WorldEventManager.teleport(player, eventId)) {
            player.sendSystemMessage(Component.literal("§cThat world event is no longer active."));
            return 0;
        }
        return 1;
    }

    private static int list(CommandSourceStack source) {
        var events = WorldEventManager.getActiveEvents();
        if (events.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No world events are active."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("§6Active World Events:"), false);
        for (WorldEventManager.ActiveEvent active : events) {
            source.sendSuccess(() -> Component.literal("§7- §e" + active.eventId + " §8| §f" + active.displayName + " §8@ §a" + active.pos.getX() + ", " + active.pos.getY() + ", " + active.pos.getZ()), false);
        }
        return 1;
    }

    private static int bindings(CommandSourceStack source) {
        Map<String, WorldEventBindingRegistry.Binding> bindings = WorldEventBindingRegistry.getAll();
        if (bindings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No world event NPC bindings exist."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("§6World Event NPC Bindings:"), false);
        for (WorldEventBindingRegistry.Binding binding : bindings.values()) {
            source.sendSuccess(() -> Component.literal("§7- §e" + binding.eventId + " §8-> §f" + binding.npcUuid + " §8(" + binding.world + ")"), false);
        }
        return 1;
    }

    private static int bind(CommandSourceStack source, String eventId) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cOnly a player can bind the nearest NPC."));
            return 0;
        }

        if (!WorldEventConfig.EVENTS.containsKey(eventId)) {
            source.sendFailure(Component.literal("§cUnknown world event: " + eventId));
            return 0;
        }

        List<Entity> nearby = player.level().getEntities(
                player,
                new AABB(player.blockPosition()).inflate(8)
        );

        Entity nearestNpc = nearby.stream()
                .filter(entity -> entity instanceof NPCEntity || entity.getClass().getSimpleName().contains("NPC"))
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
                .orElse(null);

        if (nearestNpc == null) {
            source.sendFailure(Component.literal("§cNo NPC found nearby."));
            return 0;
        }

        WorldEventBindingRegistry.bind(eventId, nearestNpc);
        source.sendSuccess(() -> Component.literal("§aBound world event §e" + eventId + " §ato NPC §f" + nearestNpc.getUUID()), true);
        return 1;
    }

    private static int unbind(CommandSourceStack source, String eventId) {
        if (WorldEventBindingRegistry.unbind(eventId)) {
            source.sendSuccess(() -> Component.literal("§cUnbound world event: " + eventId), true);
            return 1;
        }
        source.sendFailure(Component.literal("§cNo binding found for world event: " + eventId));
        return 0;
    }

    private static int start(CommandSourceStack source, String eventId) {
        boolean started = WorldEventManager.start(source.getServer(), eventId, true);
        if (started) {
            source.sendSuccess(() -> Component.literal("§aStarted world event: " + eventId), true);
            return 1;
        }
        String reason = WorldEventManager.getLastStartFailure();
        source.sendFailure(Component.literal("§cCould not start world event: " + eventId + (reason == null || reason.isBlank() ? "" : " §7(" + reason + ")")));
        return 0;
    }

    private static int startRandom(CommandSourceStack source) {
        boolean started = WorldEventManager.startRandom(source.getServer(), true);
        if (started) {
            source.sendSuccess(() -> Component.literal("§aStarted a random world event."), true);
            return 1;
        }
        String reason = WorldEventManager.getLastStartFailure();
        source.sendFailure(Component.literal("§cCould not start a random world event." + (reason == null || reason.isBlank() ? "" : " §7(" + reason + ")")));
        return 0;
    }

    private static int stop(CommandSourceStack source, String eventId) {
        boolean stopped = WorldEventManager.stop(source.getServer(), eventId, true);
        if (stopped) {
            source.sendSuccess(() -> Component.literal("§aStopped world event: " + eventId), true);
            return 1;
        }
        source.sendFailure(Component.literal("§cNo active world event found: " + eventId));
        return 0;
    }

    private static int stopAll(CommandSourceStack source) {
        WorldEventManager.stopAll(source.getServer());
        source.sendSuccess(() -> Component.literal("§aStopped all world events."), true);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        WorldEventConfig.load();
        WorldEventBindingRegistry.load();
        source.sendSuccess(() -> Component.literal("§aReloaded world_events.json."), true);
        return 1;
    }
}
