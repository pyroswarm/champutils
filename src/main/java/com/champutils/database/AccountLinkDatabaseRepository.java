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
                ensureSchema(connection);

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

                ExistingLink uuidLink = findByMinecraftUuid(connection, playerUuid);
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

                upsertPlayerAccount(connection, request.websiteUserId, playerUuid, playerName);
                markRequestVerified(connection, request.id, playerUuid, playerName);
                touchPlayer(connection, playerUuid, playerName);

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

    public static void ensureSchema(Connection connection) throws Exception {
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "create table if not exists account_links (" +
                            "id uuid primary key default gen_random_uuid(), " +
                            "website_user_id uuid not null, " +
                            "minecraft_username text not null, " +
                            "verification_code text not null, " +
                            "verified boolean not null default false, " +
                            "minecraft_uuid uuid, " +
                            "verified_at timestamptz, " +
                            "created_at timestamptz not null default now(), " +
                            "expires_at timestamptz not null default (now() + interval '15 minutes')" +
                            ")"
            );
            statement.executeUpdate("alter table account_links add column if not exists website_user_id uuid");
            statement.executeUpdate("alter table account_links add column if not exists minecraft_username text not null default ''");
            statement.executeUpdate("alter table account_links add column if not exists verification_code text");
            statement.executeUpdate("alter table account_links add column if not exists verified boolean not null default false");
            statement.executeUpdate("alter table account_links add column if not exists minecraft_uuid uuid");
            statement.executeUpdate("alter table account_links add column if not exists verified_at timestamptz");
            statement.executeUpdate("alter table account_links add column if not exists created_at timestamptz not null default now()");
            statement.executeUpdate("alter table account_links add column if not exists expires_at timestamptz not null default (now() + interval '15 minutes')");
            statement.executeUpdate("update account_links set expires_at = created_at + interval '15 minutes' where expires_at is null");
            statement.executeUpdate("create index if not exists account_links_code_pending_index on account_links (upper(verification_code), verified, expires_at)");
            statement.executeUpdate("create index if not exists account_links_website_user_index on account_links (website_user_id)");

            statement.executeUpdate(
                    "create table if not exists player_accounts (" +
                            "website_user_id uuid not null, " +
                            "minecraft_uuid uuid primary key, " +
                            "minecraft_username text not null, " +
                            "linked_at timestamptz not null default now(), " +
                            "updated_at timestamptz not null default now()" +
                            ")"
            );
            statement.executeUpdate("alter table player_accounts add column if not exists website_user_id uuid");
            statement.executeUpdate("alter table player_accounts add column if not exists minecraft_uuid uuid");
            statement.executeUpdate("alter table player_accounts add column if not exists minecraft_username text not null default ''");
            statement.executeUpdate("alter table player_accounts add column if not exists linked_at timestamptz not null default now()");
            statement.executeUpdate("alter table player_accounts add column if not exists updated_at timestamptz not null default now()");
            statement.executeUpdate("delete from player_accounts where minecraft_uuid is null or website_user_id is null");
            statement.executeUpdate("create unique index if not exists player_accounts_minecraft_uuid_unique on player_accounts (minecraft_uuid)");
            statement.executeUpdate("create unique index if not exists player_accounts_website_user_unique on player_accounts (website_user_id)");
        }
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

    private static ExistingLink findByMinecraftUuid(Connection connection, UUID minecraftUuid) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select website_user_id, minecraft_uuid from player_accounts where minecraft_uuid = cast(? as uuid) limit 1"
        )) {
            statement.setString(1, minecraftUuid.toString());

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
                "select website_user_id, minecraft_uuid from player_accounts where website_user_id = cast(? as uuid) limit 1"
        )) {
            statement.setString(1, websiteUserId);

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

    private static void upsertPlayerAccount(Connection connection, String websiteUserId, UUID minecraftUuid, String minecraftUsername) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into player_accounts (website_user_id, minecraft_uuid, minecraft_username, linked_at, updated_at) " +
                        "values (cast(? as uuid), cast(? as uuid), ?, now(), now()) " +
                        "on conflict (minecraft_uuid) do update set " +
                        "minecraft_username = excluded.minecraft_username, " +
                        "updated_at = now()"
        )) {
            statement.setString(1, websiteUserId);
            statement.setString(2, minecraftUuid.toString());
            statement.setString(3, minecraftUsername);
            statement.executeUpdate();
        }
    }

    private static void markRequestVerified(Connection connection, String requestId, UUID minecraftUuid, String minecraftUsername) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "update account_links " +
                        "set verified = true, minecraft_uuid = cast(? as uuid), minecraft_username = ?, verified_at = now() " +
                        "where id = cast(? as uuid)"
        )) {
            statement.setString(1, minecraftUuid.toString());
            statement.setString(2, minecraftUsername);
            statement.setString(3, requestId);
            statement.executeUpdate();
        }
    }

    private static void touchPlayer(Connection connection, UUID minecraftUuid, String minecraftUsername) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into players (uuid, username, last_seen) values (cast(? as uuid), ?, now()) " +
                        "on conflict (uuid) do update set username = excluded.username, last_seen = now()"
        )) {
            statement.setString(1, minecraftUuid.toString());
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
