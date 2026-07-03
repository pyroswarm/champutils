package com.champutils.commands;

import com.champutils.debug.ChampDebugManager;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ChampDebugCommand {
    private ChampDebugCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("champdebug")
                        .requires(source -> source.hasPermission(4))
                        .executes(context -> status(context.getSource()))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String suggestion : ChampDebugManager.suggestions()) builder.suggest(suggestion);
                                    return builder.buildFuture();
                                })
                                .executes(context -> apply(context.getSource(), StringArgumentType.getString(context, "mode"))))
        ));
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6ChampDebug§7: §f" + ChampDebugManager.status()), false);
        source.sendSuccess(() -> Component.literal("§7Usage: §f/champdebug off§7, §f/champdebug all§7, or §f/champdebug ai/profiles/spawns/bosses/gyms/islander/guilds/performance"), false);
        return 1;
    }

    private static int apply(net.minecraft.commands.CommandSourceStack source, String mode) {
        String raw = mode == null ? "" : mode.trim();
        if (raw.equalsIgnoreCase("off") || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("false")) {
            ChampDebugManager.off();
            source.sendSuccess(() -> Component.literal("§6ChampDebug§7: §coff"), true);
            return 1;
        }
        if (raw.equalsIgnoreCase("all") || raw.equalsIgnoreCase("true")) {
            ChampDebugManager.all();
            source.sendSuccess(() -> Component.literal("§6ChampDebug§7: §aall debugging enabled"), true);
            return 1;
        }
        if (!ChampDebugManager.setOnly(raw)) {
            source.sendFailure(Component.literal("§cUnknown debug category: " + raw + ". Try /champdebug for the category list."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§6ChampDebug§7: §aenabled §f" + ChampDebugManager.status() + " §7only"), true);
        return 1;
    }
}
