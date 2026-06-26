package com.champutils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Registration-level hard deny for risky vanilla commands that should not be usable by default players.
 * Ops/console remain able to use the original command paths where needed.
 */
public final class CommandBlocker {
    private CommandBlocker() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("script")
                    .requires(source -> source.hasPermission(4))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .executes(ctx -> 0))
                    .executes(ctx -> 0));

            dispatcher.register(Commands.literal("trigger")
                    .requires(source -> source.hasPermission(4))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .executes(ctx -> 0))
                    .executes(ctx -> 0));
        });
    }

    public static boolean isBlockedRoot(String command) {
        if (command == null) return false;
        String cleaned = command.trim();
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1).trim();
        if (cleaned.isEmpty()) return false;
        int split = cleaned.indexOf(' ');
        String root = (split >= 0 ? cleaned.substring(0, split) : cleaned).toLowerCase(java.util.Locale.ROOT);
        return root.equals("script") || root.equals("trigger");
    }

    public static Component denyMessage() {
        return Component.literal("§cThat command is disabled on this server.");
    }
}
