package com.champutils.commands;

import com.champutils.database.DatabaseManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DatabaseTestCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        dispatcher.register(
                                Commands.literal("dbtest")
                                        .requires(source -> source.hasPermission(4))
                                        .executes(context -> run(
                                                context.getSource().getPlayerOrException(),
                                                context.getSource().getServer()
                                        ))
                        )
        );
    }

    private static int run(ServerPlayer player, MinecraftServer server) {
        player.sendSystemMessage(Component.literal("§7Database status before reconnect: §f" + DatabaseManager.getLastStatus()));

        boolean connected = DatabaseManager.reloadAndConnect();

        player.sendSystemMessage(Component.literal("§7Database status after reconnect: §f" + DatabaseManager.getLastStatus()));

        if (!connected) {
            player.sendSystemMessage(Component.literal("§cDatabaseManager is still not enabled. Check server console for the exact connection error."));
            return 0;
        }

        try {
            Connection connection = DatabaseManager.getConnection();

            String uuid = player.getUUID().toString();
            String name = player.getGameProfile().getName();

            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into players (uuid, username, last_seen) " +
                            "values (?, ?, now()) " +
                            "on conflict (uuid) do update set " +
                            "username = excluded.username, " +
                            "last_seen = now()"
            )) {
                statement.setString(1, uuid);
                statement.setString(2, name);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into ranked_stats (uuid, season_id, rp, wins, losses, updated_at) " +
                            "values (?, 'season_1', 1000, 0, 0, now()) " +
                            "on conflict (uuid, season_id) do update set updated_at = now()"
            )) {
                statement.setString(1, uuid);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into server_status (id, online_players, max_players, motd, last_heartbeat) " +
                            "values ('main', ?, ?, ?, now()) " +
                            "on conflict (id) do update set " +
                            "online_players = excluded.online_players, " +
                            "max_players = excluded.max_players, " +
                            "motd = excluded.motd, " +
                            "last_heartbeat = now()"
            )) {
                statement.setInt(1, server.getPlayerList().getPlayerCount());
                statement.setInt(2, server.getPlayerList().getMaxPlayers());
                statement.setString(3, server.getMotd() == null ? "" : server.getMotd());
                statement.executeUpdate();
            }

            int playerCount = 0;

            try (
                    PreparedStatement statement = connection.prepareStatement("select count(*) from players");
                    ResultSet resultSet = statement.executeQuery()
            ) {
                if (resultSet.next()) {
                    playerCount = resultSet.getInt(1);
                }
            }

            player.sendSystemMessage(Component.literal(
                    "§aDatabase test succeeded. Inserted/touched " + name + ". players row count: " + playerCount
            ));

            return 1;
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal(
                    "§cDatabase test failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()
            ));

            e.printStackTrace();
            return 0;
        }
    }
}
