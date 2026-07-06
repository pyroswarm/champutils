package com.champutils.commands;

import com.champutils.network.NetworkServerConfig;
import com.champutils.permissions.LuckPermsHook;
import com.champutils.profile.PreferredSurvivalServerManager;
import com.champutils.profile.ProxyTransferBridge;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Staff-only direct backend switching. Does not issue or require a profile transfer token. */
public final class StaffServerCommand {
    private StaffServerCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var node = literal("server")
                    .requires(source -> hasAccess(source.getPlayer()))
                    .then(argument("target", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(suggestions(), builder))
                            .executes(context -> switchServer(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "target"))));
            dispatcher.register(node);
            dispatcher.register(literal("staffserver")
                    .requires(source -> hasAccess(source.getPlayer()))
                    .then(argument("target", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(suggestions(), builder))
                            .executes(context -> switchServer(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "target")))));
        });
    }

    private static boolean hasAccess(ServerPlayer player) {
        return player != null && (player.hasPermissions(4) || LuckPermsHook.hasPermissionCached(player, "champutils.server.admin"));
    }

    private static int switchServer(ServerPlayer player, String rawTarget) {
        String target = normalize(rawTarget);
        if (target.isBlank()) {
            player.sendSystemMessage(Component.literal("Unknown server. Use Alpha, Omega, profile_lobby, main_survival1, or survival2.").withStyle(ChatFormatting.RED));
            return 0;
        }
        boolean sent = ProxyTransferBridge.connect(player, target);
        if (!sent) {
            player.sendSystemMessage(Component.literal("Could not ask the proxy to move you to " + target + ".").withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Sending you to " + PreferredSurvivalServerManager.displayName(target) + "...").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("alpha")) return PreferredSurvivalServerManager.ALPHA_SERVER_ID;
        if (lower.equals("omega")) return PreferredSurvivalServerManager.OMEGA_SERVER_ID;
        if (lower.equals("lobby")) return NetworkServerConfig.get().profileLobbyServerId;
        if (lower.equals("profile_lobby")) return NetworkServerConfig.get().profileLobbyServerId;
        if (lower.equals("main_survival1") || lower.equals("survival2")) return lower;
        for (String id : NetworkServerConfig.get().normalizedSurvivalBackends()) {
            if (lower.equals(id.toLowerCase(Locale.ROOT))) return id;
        }
        return "";
    }

    private static List<String> suggestions() {
        List<String> values = new ArrayList<>();
        values.add("Alpha");
        values.add("Omega");
        values.add("profile_lobby");
        values.addAll(NetworkServerConfig.get().normalizedSurvivalBackends());
        return values;
    }
}
