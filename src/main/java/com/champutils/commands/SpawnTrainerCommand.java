package com.champutils.commands;

import com.champutils.badge.BadgeType;
import com.champutils.gym.GymRegistry;
import com.champutils.trainer.ChampTrainerProtectionManager;
import com.champutils.trainer.ChampTrainerSpawner;
import com.champutils.worldevent.WorldEventBindingRegistry;
import com.champutils.worldevent.WorldEventConfig;

import com.cobblemon.mod.common.entity.npc.NPCEntity;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class SpawnTrainerCommand {

    private SpawnTrainerCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    Commands.literal("spawntrainer")
                            .requires(source -> source.hasPermission(2))
                            .then(Commands.argument("trainerId", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        suggestIds(builder);
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> spawnAtPlayer(
                                            ctx.getSource(),
                                            StringArgumentType.getString(ctx, "trainerId")
                                    ))
                                    .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                            .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                    .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                            .executes(ctx -> spawnAtCoords(
                                                                    ctx.getSource(),
                                                                    StringArgumentType.getString(ctx, "trainerId"),
                                                                    DoubleArgumentType.getDouble(ctx, "x"),
                                                                    DoubleArgumentType.getDouble(ctx, "y"),
                                                                    DoubleArgumentType.getDouble(ctx, "z")
                                                            ))))))
            );

            dispatcher.register(
                    Commands.literal("despawntrainer")
                            .requires(source -> source.hasPermission(2))
                            .executes(ctx -> despawnNearest(ctx.getSource(), 8.0D))
                            .then(Commands.literal("nearest")
                                    .executes(ctx -> despawnNearest(ctx.getSource(), 8.0D))
                                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 128.0D))
                                            .executes(ctx -> despawnNearest(
                                                    ctx.getSource(),
                                                    DoubleArgumentType.getDouble(ctx, "radius")
                                            ))))
                            .then(Commands.literal("radius")
                                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 256.0D))
                                            .executes(ctx -> despawnRadius(
                                                    ctx.getSource(),
                                                    DoubleArgumentType.getDouble(ctx, "radius")
                                            ))))
            );
        });
    }

    private static void suggestIds(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        builder.suggest("gym1");
        builder.suggest("gym2");
        builder.suggest("gym3");
        builder.suggest("gym4");
        builder.suggest("gym5");
        builder.suggest("gym6");
        builder.suggest("gym7");
        builder.suggest("gym8");
        builder.suggest("elite4-1");
        builder.suggest("elite4-2");
        builder.suggest("elite4-3");
        builder.suggest("elite4-4");
        builder.suggest("champion");
        for (BadgeType badge : BadgeType.values()) builder.suggest(badge.name().toLowerCase());
        for (String id : WorldEventConfig.EVENTS.keySet()) builder.suggest(id);
    }

    private static int spawnAtPlayer(CommandSourceStack source, String trainerId) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            return spawn(source, player.serverLevel(), player.position(), player.getYRot(), trainerId);
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cConsole must provide coordinates: /spawntrainer <id> <x> <y> <z>"));
            return 0;
        }
    }

    private static int spawnAtCoords(CommandSourceStack source, String trainerId, double x, double y, double z) {
        ServerLevel level = source.getLevel();
        float yaw = 0.0F;
        try {
            yaw = source.getPlayerOrException().getYRot();
        } catch (Exception ignored) {}
        return spawn(source, level, new Vec3(x + 0.5D, y, z + 0.5D), yaw, trainerId);
    }

    private static int spawn(CommandSourceStack source, ServerLevel level, Vec3 pos, float yaw, String trainerId) {
        ChampTrainerSpawner.SpawnResult result = ChampTrainerSpawner.spawn(level, pos, yaw, trainerId);
        if (!result.success) {
            source.sendFailure(Component.literal("§c" + result.message));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§a" + result.message + " §8(" + result.npc.getUUID() + ")"), true);
        return 1;
    }

    private static int despawnNearest(CommandSourceStack source, double radius) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();

        List<NPCEntity> trainers = findNativeTrainerNpcs(level, pos, radius);
        if (trainers.isEmpty()) {
            source.sendFailure(Component.literal("§cNo ChampUtils trainer NPC found within " + format(radius) + " blocks."));
            return 0;
        }

        NPCEntity nearest = trainers.stream()
                .min(Comparator.comparingDouble(npc -> npc.distanceToSqr(pos)))
                .orElse(null);

        if (nearest == null) {
            source.sendFailure(Component.literal("§cNo ChampUtils trainer NPC found within " + format(radius) + " blocks."));
            return 0;
        }

        String label = describeTrainer(nearest.getUUID());
        UUID uuid = nearest.getUUID();
        despawnTrainer(nearest);
        source.sendSuccess(() -> Component.literal("§aDespawned trainer NPC " + label + " §8(" + uuid + ")"), true);
        return 1;
    }

    private static int despawnRadius(CommandSourceStack source, double radius) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();

        List<NPCEntity> trainers = findNativeTrainerNpcs(level, pos, radius);
        if (trainers.isEmpty()) {
            source.sendFailure(Component.literal("§cNo ChampUtils trainer NPCs found within " + format(radius) + " blocks."));
            return 0;
        }

        int count = 0;
        for (NPCEntity npc : new ArrayList<>(trainers)) {
            despawnTrainer(npc);
            count++;
        }

        final int removed = count;
        source.sendSuccess(() -> Component.literal("§aDespawned " + removed + " ChampUtils trainer NPC(s)."), true);
        return removed;
    }

    private static List<NPCEntity> findNativeTrainerNpcs(ServerLevel level, Vec3 center, double radius) {
        double r = Math.max(1.0D, radius);
        AABB box = new AABB(
                center.x - r, center.y - r, center.z - r,
                center.x + r, center.y + r, center.z + r
        );

        List<NPCEntity> found = new ArrayList<>();
        for (NPCEntity npc : level.getEntitiesOfClass(NPCEntity.class, box)) {
            UUID uuid = npc.getUUID();
            if (isNativeTrainer(uuid)) {
                found.add(npc);
            }
        }
        return found;
    }

    private static boolean isNativeTrainer(UUID uuid) {
        if (uuid == null) return false;
        return ChampTrainerProtectionManager.isTracked(uuid)
                || GymRegistry.isGymNpc(uuid)
                || WorldEventBindingRegistry.isBoundNpc(uuid);
    }

    private static String describeTrainer(UUID uuid) {
        if (uuid == null) return "";

        BadgeType badge = GymRegistry.getBadgeForNpc(uuid);
        if (badge != null) return badge.name();

        String eventId = WorldEventBindingRegistry.getEventIdForNpc(uuid);
        if (eventId != null && !eventId.isBlank()) return eventId;

        return "";
    }

    private static void despawnTrainer(NPCEntity npc) {
        if (npc == null) return;

        UUID uuid = npc.getUUID();

        ChampTrainerProtectionManager.untrack(uuid);

        BadgeType badge = GymRegistry.getBadgeForNpc(uuid);
        if (badge != null) {
            GymRegistry.unbindBadge(badge);
        }

        String eventId = WorldEventBindingRegistry.getEventIdForNpc(uuid);
        if (eventId != null && !eventId.isBlank()) {
            WorldEventBindingRegistry.unbind(eventId);
        }

        try { npc.setInvulnerable(false); } catch (Exception ignored) {}
        try { npc.setNoAi(false); } catch (Exception ignored) {}
        try { npc.remove(Entity.RemovalReason.DISCARDED); } catch (Exception ignored) {}
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
