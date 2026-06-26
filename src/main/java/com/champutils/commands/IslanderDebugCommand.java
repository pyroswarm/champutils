package com.champutils.commands;

import com.champutils.profile.IslanderDebugManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class IslanderDebugCommand {
    private IslanderDebugCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("islanderdebug")
                .executes(ctx -> snapshot(ctx.getSource()))
                .then(Commands.literal("on").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    IslanderDebugManager.enableFor(player);
                    ctx.getSource().sendSuccess(() -> Component.literal("Islander debug enabled for you for 10 minutes."), false);
                    IslanderDebugManager.sendSnapshot(player);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    IslanderDebugManager.disableFor(player);
                    ctx.getSource().sendSuccess(() -> Component.literal("Islander debug disabled for you."), false);
                    return 1;
                }))
                .then(Commands.literal("global_on").requires(source -> source.hasPermission(2)).executes(ctx -> {
                    IslanderDebugManager.setGlobalEnabled(true);
                    ctx.getSource().sendSuccess(() -> Component.literal("Global Islander debug enabled."), true);
                    return 1;
                }))
                .then(Commands.literal("global_off").requires(source -> source.hasPermission(2)).executes(ctx -> {
                    IslanderDebugManager.setGlobalEnabled(false);
                    ctx.getSource().sendSuccess(() -> Component.literal("Global Islander debug disabled."), true);
                    return 1;
                }))
        );
    }

    private static int snapshot(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        IslanderDebugManager.sendSnapshot(player);
        return 1;
    }
}
