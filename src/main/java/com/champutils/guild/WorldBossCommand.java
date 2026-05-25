package com.champutils.guild;

import com.champutils.permissions.LuckPermsHook;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class WorldBossCommand {
    private static final String FORCE_PERMISSION = "champutils.worldboss.force";
    private static final String ADMIN_PERMISSION = "champutils.worldboss.admin";
    private static final String GUILD_COOLDOWN_PERMISSION = "champutils.guildboss.cooldown";

    private WorldBossCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("worldboss")
                    .then(Commands.literal("claim")
                            .executes(context -> {
                                GuildBossManager.claimWorldRewards(context.getSource().getPlayerOrException());
                                return 1;
                            }))
                    .then(Commands.literal("force")
                            .requires(source -> hasBossPermission(source, FORCE_PERMISSION) || hasBossPermission(source, ADMIN_PERMISSION))
                            .executes(context -> {
                                CommandSourceStack source = context.getSource();
                                if (GuildBossManager.hasActiveWorldBoss()) {
                                    source.sendFailure(Component.literal("There is already an active world boss."));
                                    return 0;
                                }

                                boolean spawned = GuildBossManager.forceSpawnWorldBoss(source.getServer());
                                if (spawned) {
                                    source.sendSuccess(() -> Component.literal("Forced a world boss spawn.").withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                }

                                source.sendFailure(Component.literal("Could not force spawn a world boss. Check bosses.json spawn worlds and /pokespawn syntax."));
                                return 0;
                            }))
                    .then(Commands.literal("reload")
                            .requires(source -> hasBossPermission(source, ADMIN_PERMISSION))
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                BossConfig.load();
                                player.sendSystemMessage(Component.literal("Reloaded bosses.json.").withStyle(ChatFormatting.GREEN));
                                return 1;
                            }))
                    .then(Commands.literal("guildcooldown")
                            .requires(source -> hasBossPermission(source, GUILD_COOLDOWN_PERMISSION) || hasBossPermission(source, ADMIN_PERMISSION))
                            .executes(context -> {
                                context.getSource().sendSuccess(() -> Component.literal("Guild boss daily reset is " + GuildBossManager.getGuildBossResetInfo() + ".").withStyle(ChatFormatting.AQUA), false);
                                return 1;
                            })
                            .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                    .executes(context -> {
                                        int minutes = IntegerArgumentType.getInteger(context, "minutes");
                                        GuildBossManager.setGuildBossCooldownMinutes(minutes);
                                        context.getSource().sendSuccess(() -> Component.literal("Legacy guild boss cooldown value set to " + minutes + " minute(s), but guild boss availability now uses the shared daily reset.").withStyle(ChatFormatting.GREEN), true);
                                        return 1;
                                    })))
                    .then(Commands.literal("setspawn")
                            .requires(source -> hasBossPermission(source, ADMIN_PERMISSION))
                            .then(Commands.literal("here")
                                    .executes(context -> {
                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                        BossConfig.DATA.worldBoss.spawnLocation = new BossConfig.SpawnLocation(player.getX(), player.getY(), player.getZ());
                                        BossConfig.DATA.worldBoss.yaw = player.getYRot();
                                        BossConfig.save();
                                        player.sendSystemMessage(Component.literal("World boss spawn set to your exact location and facing direction for every configured spawn world.").withStyle(ChatFormatting.GREEN));
                                        return 1;
                                    }))
                            .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                    .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                            .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                    .executes(context -> {
                                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                                        double x = DoubleArgumentType.getDouble(context, "x");
                                                        double y = DoubleArgumentType.getDouble(context, "y");
                                                        double z = DoubleArgumentType.getDouble(context, "z");
                                                        BossConfig.DATA.worldBoss.spawnLocation = new BossConfig.SpawnLocation(x, y, z);
                                                        BossConfig.DATA.worldBoss.yaw = player.getYRot();
                                                        BossConfig.save();
                                                        player.sendSystemMessage(Component.literal("World boss spawn location set to X " + x + ", Y " + y + ", Z " + z + " using your current facing direction. This applies to every configured spawn world.").withStyle(ChatFormatting.GREEN));
                                                        return 1;
                                                    })
                                                    .then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                                                            .executes(context -> {
                                                                ServerPlayer player = context.getSource().getPlayerOrException();
                                                                double x = DoubleArgumentType.getDouble(context, "x");
                                                                double y = DoubleArgumentType.getDouble(context, "y");
                                                                double z = DoubleArgumentType.getDouble(context, "z");
                                                                float yaw = (float) DoubleArgumentType.getDouble(context, "yaw");
                                                                BossConfig.DATA.worldBoss.spawnLocation = new BossConfig.SpawnLocation(x, y, z);
                                                                BossConfig.DATA.worldBoss.yaw = yaw;
                                                                BossConfig.save();
                                                                player.sendSystemMessage(Component.literal("World boss spawn location set to X " + x + ", Y " + y + ", Z " + z + ", yaw " + yaw + ". This applies to every configured spawn world.").withStyle(ChatFormatting.GREEN));
                                                                return 1;
                                                            }))))))
                    .then(Commands.literal("setyaw")
                            .requires(source -> hasBossPermission(source, ADMIN_PERMISSION))
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                BossConfig.DATA.worldBoss.yaw = player.getYRot();
                                BossConfig.save();
                                player.sendSystemMessage(Component.literal("World boss facing direction set to your current yaw: " + BossConfig.DATA.worldBoss.yaw).withStyle(ChatFormatting.GREEN));
                                return 1;
                            })
                            .then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                                    .executes(context -> {
                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                        float yaw = (float) DoubleArgumentType.getDouble(context, "yaw");
                                        BossConfig.DATA.worldBoss.yaw = yaw;
                                        BossConfig.save();
                                        player.sendSystemMessage(Component.literal("World boss facing direction set to yaw: " + yaw).withStyle(ChatFormatting.GREEN));
                                        return 1;
                                    })))
                    .then(Commands.literal("addspawnworld")
                            .requires(source -> hasBossPermission(source, ADMIN_PERMISSION))
                            .then(Commands.argument("dimension", StringArgumentType.string())
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
                            .requires(source -> hasBossPermission(source, ADMIN_PERMISSION))
                            .then(Commands.argument("dimension", StringArgumentType.string())
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
                            .requires(source -> hasBossPermission(source, ADMIN_PERMISSION))
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                BossConfig.WorldBossSettings worldBoss = BossConfig.DATA.worldBoss;
                                BossConfig.SpawnLocation loc = worldBoss.spawnLocation;
                                player.sendSystemMessage(Component.literal("World boss shared spawn: X " + loc.x + ", Y " + loc.y + ", Z " + loc.z + ", yaw " + worldBoss.yaw).withStyle(ChatFormatting.AQUA));
                                player.sendSystemMessage(Component.literal("World boss spawn worlds: " + String.join(", ", worldBoss.spawnDimensions)).withStyle(ChatFormatting.AQUA));
                                player.sendSystemMessage(Component.literal("Last world boss spawn: " + GuildBossManager.formatLastWorldBossSpawnAgo()).withStyle(ChatFormatting.AQUA));
                                player.sendSystemMessage(Component.literal("Guild boss reset: " + GuildBossManager.getGuildBossResetInfo()).withStyle(ChatFormatting.AQUA));
                                return 1;
                            })));
        });
    }

    private static boolean hasBossPermission(CommandSourceStack source, String permission) {
        if (source == null) {
            return false;
        }
        if (source.hasPermission(4)) {
            return true;
        }
        try {
            ServerPlayer player = source.getPlayer();
            return LuckPermsHook.hasPermission(player, permission);
        } catch (Exception ignored) {
            return false;
        }
    }
}
