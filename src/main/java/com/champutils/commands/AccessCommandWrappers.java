package com.champutils.commands;

import com.champutils.permissions.PermissionUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Wrappers for external Cobblemon utility commands gated by LuckPerms/VIP permissions. */
public final class AccessCommandWrappers {
    private AccessCommandWrappers() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("pc")
                    .executes(ctx -> runAny(ctx.getSource(), "champutils.command.pc", "VIP", "cobblemon:pc", "cobblemonextras:pc")));
            dispatcher.register(Commands.literal("pokeheal")
                    .executes(ctx -> run(ctx.getSource(), "champutils.command.pokeheal", "cobblemon:healpokemon", "VIP"))
                    .then(Commands.argument("target", StringArgumentType.greedyString())
                            .requires(source -> source.hasPermission(4))
                            .executes(ctx -> runRaw(ctx.getSource(), "cobblemon:healpokemon " + StringArgumentType.getString(ctx, "target")))));
            dispatcher.register(Commands.literal("healpokemon")
                    .executes(ctx -> run(ctx.getSource(), "champutils.command.pokeheal", "cobblemon:healpokemon", "VIP"))
                    .then(Commands.argument("target", StringArgumentType.greedyString())
                            .requires(source -> source.hasPermission(4))
                            .executes(ctx -> runRaw(ctx.getSource(), "cobblemon:healpokemon " + StringArgumentType.getString(ctx, "target")))));
            dispatcher.register(Commands.literal("pokeivs")
                    .executes(ctx -> run(ctx.getSource(), "champutils.command.pokeivs", "cobblemonextras:pokeivs", "VIP+"))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .requires(source -> source.hasPermission(4))
                            .executes(ctx -> runRaw(ctx.getSource(), "cobblemonextras:pokeivs " + StringArgumentType.getString(ctx, "args")))));
        });
    }

    private static int run(net.minecraft.commands.CommandSourceStack source, String permission, String namespacedCommand, String featureName) {
        if (source == null) return 0;
        if (!PermissionUtil.has(source, permission)) {
            try {
                ServerPlayer player = source.getPlayerOrException();
                player.sendSystemMessage(Component.literal("§cThis is a " + featureName + " feature. Unlock it with /accountupgrade."));
            } catch (Exception ignored) {}
            return 0;
        }
        return runRaw(source, namespacedCommand);
    }

    private static int runAny(net.minecraft.commands.CommandSourceStack source, String permission, String featureName, String... commands) {
        if (source == null) return 0;
        if (!PermissionUtil.has(source, permission)) {
            try {
                ServerPlayer player = source.getPlayerOrException();
                player.sendSystemMessage(Component.literal("§cThis is a " + featureName + " feature. Unlock it with /accountupgrade."));
            } catch (Exception ignored) {}
            return 0;
        }
        int result = 0;
        for (String command : commands) {
            try {
                result = runRaw(source, command);
                if (result > 0) return result;
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    private static int runRaw(net.minecraft.commands.CommandSourceStack source, String command) {
        if (source.getServer() == null) return 0;
        source.getServer().getCommands().performPrefixedCommand(source.withSuppressedOutput().withPermission(4), command);
        return 1;
    }
}
