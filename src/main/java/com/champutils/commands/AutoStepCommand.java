package com.champutils.commands;

import com.champutils.profession.ProfessionGearManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AutoStepCommand {
    private AutoStepCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("autostep")
                        .executes(context -> status(context.getSource()))
                        .then(Commands.literal("on").executes(context -> set(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> set(context.getSource(), false)))
                        .then(Commands.literal("toggle").executes(context -> toggle(context.getSource())))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
        ));
    }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ProfessionGearManager.hasAutoStepArmor(player)) {
            player.sendSystemMessage(Component.literal("§cYou need to be wearing profession leggings with Auto Step to use /autostep."));
            return 0;
        }
        boolean enabled = ProfessionGearManager.isAutoStepEnabled(player);
        player.sendSystemMessage(Component.literal(enabled
                ? "§aAuto Step is ON. Use §f/autostep off§a to disable it until you re-equip the leggings."
                : "§cAuto Step is OFF. Use §f/autostep on§c to turn it back on."));
        return 1;
    }

    private static int set(CommandSourceStack source, boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ProfessionGearManager.setAutoStep(player, enabled)) {
            player.sendSystemMessage(Component.literal("§cYou need to be wearing profession leggings with Auto Step to use /autostep."));
            return 0;
        }
        player.sendSystemMessage(Component.literal(enabled
                ? "§aAuto Step enabled."
                : "§cAuto Step disabled. It will turn back on automatically if you remove and re-equip the leggings."));
        return 1;
    }

    private static int toggle(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ProfessionGearManager.hasAutoStepArmor(player)) {
            player.sendSystemMessage(Component.literal("§cYou need to be wearing profession leggings with Auto Step to use /autostep."));
            return 0;
        }
        return set(source, !ProfessionGearManager.isAutoStepEnabled(player));
    }
}
