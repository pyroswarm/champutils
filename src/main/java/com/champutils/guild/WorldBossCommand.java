package com.champutils.guild;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class WorldBossCommand {
    private WorldBossCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("worldboss")
                    .then(Commands.literal("claim")
                            .executes(context -> {
                                GuildBossManager.claimWorldRewards(context.getSource().getPlayerOrException());
                                return 1;
                            }))
                    .then(Commands.literal("reload")
                            .requires(source -> source.hasPermission(4))
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                BossConfig.load();
                                player.sendSystemMessage(Component.literal("Reloaded bosses.json.").withStyle(ChatFormatting.GREEN));
                                return 1;
                            }))
                    .then(Commands.literal("setspawn")
                            .requires(source -> source.hasPermission(4))
                            .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                    .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                            .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                    .executes(context -> {
                                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                                        double x = DoubleArgumentType.getDouble(context, "x");
                                                        double y = DoubleArgumentType.getDouble(context, "y");
                                                        double z = DoubleArgumentType.getDouble(context, "z");
                                                        BossConfig.DATA.worldBoss.spawnLocation = new BossConfig.SpawnLocation(x, y, z);
                                                        BossConfig.save();
                                                        player.sendSystemMessage(Component.literal("World boss spawn location set to X " + x + ", Y " + y + ", Z " + z + ". This applies to every configured spawn world.").withStyle(ChatFormatting.GREEN));
                                                        return 1;
                                                    })))))
                    .then(Commands.literal("addspawnworld")
                            .requires(source -> source.hasPermission(4))
                            .then(Commands.argument("dimension", StringArgumentType.word())
                                    .executes(context -> {
                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                        String dimension = StringArgumentType.getString(context, "dimension");
                                        if (!BossConfig.DATA.worldBoss.spawnDimensions.contains(dimension)) {
                                            BossConfig.DATA.worldBoss.spawnDimensions.add(dimension);
                                            BossConfig.save();
                                            player.sendSystemMessage(Component.literal("Added world boss spawn world: " + dimension).withStyle(ChatFormatting.GREEN));
                                        } else {
                                            player.sendSystemMessage(Component.literal("That spawn world is already listed.").withStyle(ChatFormatting.YELLOW));
                                        }
                                        return 1;
                                    })))
                    .then(Commands.literal("removespawnworld")
                            .requires(source -> source.hasPermission(4))
                            .then(Commands.argument("dimension", StringArgumentType.word())
                                    .executes(context -> {
                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                        String dimension = StringArgumentType.getString(context, "dimension");
                                        if (BossConfig.DATA.worldBoss.spawnDimensions.remove(dimension)) {
                                            BossConfig.save();
                                            player.sendSystemMessage(Component.literal("Removed world boss spawn world: " + dimension).withStyle(ChatFormatting.GREEN));
                                        } else {
                                            player.sendSystemMessage(Component.literal("That spawn world was not listed.").withStyle(ChatFormatting.RED));
                                        }
                                        return 1;
                                    })))
                    .then(Commands.literal("info")
                            .requires(source -> source.hasPermission(4))
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                BossConfig.WorldBossSettings worldBoss = BossConfig.DATA.worldBoss;
                                BossConfig.SpawnLocation loc = worldBoss.spawnLocation;
                                player.sendSystemMessage(Component.literal("World boss shared spawn: X " + loc.x + ", Y " + loc.y + ", Z " + loc.z).withStyle(ChatFormatting.AQUA));
                                player.sendSystemMessage(Component.literal("World boss spawn worlds: " + String.join(", ", worldBoss.spawnDimensions)).withStyle(ChatFormatting.AQUA));
                                return 1;
                            })));
        });
    }
}
