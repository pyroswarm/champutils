package com.champutils.commands;

import com.champutils.database.DatabaseManager;
import com.champutils.database.NetworkReadySchemaManager;
import com.champutils.network.NetworkServerConfig;
import com.champutils.territory.TerritoryRepository;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkDatabaseCommand {

    private NetworkDatabaseCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("networkdb")
                        .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                        .executes(context -> run(context.getSource().getPlayerOrException()))));
    }

    private static int run(ServerPlayer player) {
        NetworkServerConfig config = NetworkServerConfig.get();

        player.sendSystemMessage(Component.literal("ChampUtils network/database status").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Server: " + config.safeSummary()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Database: " + DatabaseManager.getLastStatus()).withStyle(DatabaseManager.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED));

        if (DatabaseManager.isEnabled()) {
            NetworkReadySchemaManager.ensureAsync();
            TerritoryRepository.refreshAll();
            player.sendSystemMessage(Component.literal("Queued schema check + territory cache reload.").withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.literal("Database is offline. Check config/champutils/database.json.").withStyle(ChatFormatting.RED));
        }

        return 1;
    }
}
