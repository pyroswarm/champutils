package com.champutils.database;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.UUID;

public final class AccountLinkDatabaseRepository {

    private AccountLinkDatabaseRepository() {
    }

    public static void linkAsync(ServerPlayer player, String rawCode) {
        if (player == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        UUID playerUuid = player.getUUID();
        String playerName = player.getGameProfile().getName();
        String code = normalizeCode(rawCode);

        if (code.isBlank()) {
            send(server, player, "§cUsage: /linkaccount CC-12345");
            return;
        }

        if (!DatabaseManager.isEnabled()) {
            send(server, player, "§cWebsite linking is currently unavailable. Database is not connected.");
            send(server, player, "§7Status: §f" + DatabaseManager.getLastStatus());
            return;
        }

        DatabaseManager.executeAsync("link website account for " + playerName, connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();

            try {
                connection.setAutoCommit(false);

                LinkRequest request = findPendingRequest(connection, code);

                if (request == null) {
                    connection.rollback();
                    send(server, player, "§cThat link code is invalid, expired, or already used.");
                    send(server, player, "§7Generate a new code on the Cobble Champs website, then run §f/linkaccount <code>§7.");
                    return;
                }

                if (!request.minecraftUsername.equalsIgnoreCase(playerName)) {
                    connection.rollback();
                    send(server, player, "§cThis code was created for §f" + request.minecraftUsername + "§c, but you are logged in as §f" + playerName + "§c.");
                    send(server, player, "§7Generate a new code using your exact Minecraft username.");
                    return;
                }

                ExistingLink uuidLink = findByMinecraftUuid(connection, playerUuid.toString());
                if (uuidLink != null && !uuidLink.websiteUserId.equals(request.websiteUserId)) {
                    connection.rollback();
                    send(server, player, "§cThis Minecraft account is already linked to another website account.");
                    send(server, player, "§7Ask an admin to unlink it if this is a mistake.");
                    return;
                }

                ExistingLink websiteLink = findByWebsiteUser(connection, request.websiteUserId);
                if (websiteLink != null && !websiteLink.minecraftUuid.equals(playerUuid.toString())) {
                    connection.rollback();
                    send(server, player, "§cThat website account is already linked to another Minecraft account.");
                    send(server, player, "§7Unlink it first before linking a different account.");
                    return;
                }

                upsertPlayerAccount(connection, request.websiteUserId, playerUuid.toString(), playerName);
                markRequestVerified(connection, request.id, playerUuid.toString(), playerName);
                touchPlayer(connection, playerUuid.toString(), playerName);

                connection.commit();

                send(server, player, "§aYour Minecraft account is now linked to your Cobble Champs website account!");
                send(server, player, "§7Linked as: §f" + playerName);
            }
            catch (Exception e) {
                try {
                    connection.rollback();
                }
                catch (Exception ignored) {
                }

                send(server, player, "§cAccount linking failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
            finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                }
                catch (Exception ignored) {
                }
            }
        });
    }

    private static LinkRequest findPendingRequest(Connection connection, String code) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id, website_user_id, minecraft_username " +
                        "from account_links " +
                        "where upper(verification_code) = ? " +
                        "and verified = false " +
                        "and (expires_at is null or expires_at > now()) " +
                        "order by created_at desc " +
                        "limit 1 " +
                        "for update"
        )) {
            statement.setString(1, code);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return new LinkRequest(
                        resultSet.getString("id"),
                        resultSet.getString("website_user_id"),
                        resultSet.getString("minecraft_username")
                );
            }
        }
    }

    private static ExistingLink findByMinecraftUuid(Connection connection, String minecraftUuid) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select website_user_id, minecraft_uuid from player_accounts where minecraft_uuid = ? limit 1"
        )) {
            statement.setString(1, minecraftUuid);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return new ExistingLink(
                        resultSet.getString("website_user_id"),
                        resultSet.getString("minecraft_uuid")
                );
            }
        }
    }

    private static ExistingLink findByWebsiteUser(Connection connection, String websiteUserId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select website_user_id, minecraft_uuid from player_accounts where website_user_id = ? limit 1"
        )) {
            statement.setObject(1, UUID.fromString(websiteUserId));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return new ExistingLink(
                        resultSet.getString("website_user_id"),
                        resultSet.getString("minecraft_uuid")
                );
            }
        }
    }

    private static void upsertPlayerAccount(Connection connection, String websiteUserId, String minecraftUuid, String minecraftUsername) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into player_accounts (website_user_id, minecraft_uuid, minecraft_username, linked_at, updated_at) " +
                        "values (?, ?, ?, now(), now()) " +
                        "on conflict (minecraft_uuid) do update set " +
                        "minecraft_username = excluded.minecraft_username, " +
                        "updated_at = now()"
        )) {
            statement.setObject(1, UUID.fromString(websiteUserId));
            statement.setString(2, minecraftUuid);
            statement.setString(3, minecraftUsername);
            statement.executeUpdate();
        }
    }

    private static void markRequestVerified(Connection connection, String requestId, String minecraftUuid, String minecraftUsername) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "update account_links " +
                        "set verified = true, minecraft_uuid = ?, minecraft_username = ?, verified_at = now() " +
                        "where id = ?"
        )) {
            statement.setString(1, minecraftUuid);
            statement.setString(2, minecraftUsername);
            statement.setObject(3, UUID.fromString(requestId));
            statement.executeUpdate();
        }
    }

    private static void touchPlayer(Connection connection, String minecraftUuid, String minecraftUsername) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into players (uuid, username, last_seen) values (?, ?, now()) " +
                        "on conflict (uuid) do update set username = excluded.username, last_seen = now()"
        )) {
            statement.setString(1, minecraftUuid);
            statement.setString(2, minecraftUsername);
            statement.executeUpdate();
        }
    }

    private static String normalizeCode(String rawCode) {
        if (rawCode == null) {
            return "";
        }

        return rawCode.trim().toUpperCase(Locale.ROOT);
    }

    private static void send(MinecraftServer server, ServerPlayer player, String message) {
        server.execute(() -> {
            if (player.connection != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        });
    }

    private record LinkRequest(String id, String websiteUserId, String minecraftUsername) {
    }

    private record ExistingLink(String websiteUserId, String minecraftUuid) {
    }
}
