package com.champutils.commands;

import com.champutils.roaming.RoamingTrainerConfig;
import com.champutils.roaming.RoamingTrainerManager;
import com.champutils.roaming.RoamingTrainerRarity;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RoamingTrainerCommand {

    private RoamingTrainerCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("roamingtrainer")
                        .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.staff"))
                        .then(Commands.literal("reload")
                                .executes(ctx -> reload(ctx.getSource())))
                        .then(Commands.literal("spawn")
                                .executes(ctx -> spawn(ctx.getSource(), RoamingTrainerRarity.COMMON))
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (RoamingTrainerRarity rarity : RoamingTrainerRarity.values()) {
                                                builder.suggest(rarity.name().toLowerCase());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> spawn(
                                                ctx.getSource(),
                                                RoamingTrainerRarity.parse(StringArgumentType.getString(ctx, "rarity"), RoamingTrainerRarity.COMMON)
                                        ))))
                        .then(Commands.literal("despawn")
                                .then(Commands.literal("all")
                                        .executes(ctx -> despawnAll(ctx.getSource())))
                                .then(Commands.literal("nearby")
                                        .executes(ctx -> despawnNearby(ctx.getSource(), 64.0D))
                                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 512.0D))
                                                .executes(ctx -> despawnNearby(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "radius"))))))
        ));
    }

    private static int reload(CommandSourceStack source) {
        RoamingTrainerConfig.load();
        source.sendSuccess(() -> Component.literal("§aReloaded roaming_trainers.json."), true);
        return 1;
    }

    private static int spawn(CommandSourceStack source, RoamingTrainerRarity rarity) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            boolean ok = RoamingTrainerManager.spawnManual(player, rarity);
            if (!ok) {
                source.sendFailure(Component.literal("§cCould not spawn a roaming trainer near you."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("§aSpawned a " + rarity.name().toLowerCase() + " roaming trainer nearby."), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cOnly players can use /roamingtrainer spawn."));
            return 0;
        }
    }

    private static int despawnAll(CommandSourceStack source) {
        int count = RoamingTrainerManager.despawnAll(source.getServer());
        source.sendSuccess(() -> Component.literal("§aDespawned " + count + " roaming trainer(s)."), true);
        return count;
    }

    private static int despawnNearby(CommandSourceStack source, double radius) {
        int count = RoamingTrainerManager.despawnNearby(source.getLevel(), source.getPosition(), radius);
        source.sendSuccess(() -> Component.literal("§aDespawned " + count + " nearby roaming trainer(s)."), true);
        return count;
    }
}
