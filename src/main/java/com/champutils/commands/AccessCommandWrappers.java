package com.champutils.commands;

import com.champutils.permissions.PermissionUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Profile-aware wrappers for external Cobblemon utility commands gated by gym rewards. */
public final class AccessCommandWrappers {
    private AccessCommandWrappers() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("pc")
                    .executes(ctx -> run(ctx.getSource(), "champutils.command.pc", "cobblemonextras:pc")));
            dispatcher.register(Commands.literal("pokeheal")
                    .executes(ctx -> run(ctx.getSource(), "champutils.command.pokeheal", "cobblemonextras:pokeheal"))
                    .then(Commands.argument("target", StringArgumentType.greedyString())
                            .requires(source -> source.hasPermission(4))
                            .executes(ctx -> runRaw(ctx.getSource(), "cobblemonextras:pokeheal " + StringArgumentType.getString(ctx, "target")))));
        });
    }

    private static int run(net.minecraft.commands.CommandSourceStack source, String permission, String namespacedCommand) {
        if (source == null) return 0;
        if (!PermissionUtil.has(source, permission)) {
            try {
                ServerPlayer player = source.getPlayerOrException();
                player.sendSystemMessage(Component.literal("§cYou have not unlocked this command yet."));
            } catch (Exception ignored) {}
            return 0;
        }
        return runRaw(source, namespacedCommand);
    }

    private static int runRaw(net.minecraft.commands.CommandSourceStack source, String command) {
        if (source.getServer() == null) return 0;
        source.getServer().getCommands().performPrefixedCommand(source.withSuppressedOutput().withPermission(4), command);
        return 1;
    }
}
