package com.champutils.notifications;

import com.champutils.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class NotificationRepository {

    private NotificationRepository() {}

    public static List<PlayerNotification> fetchUndelivered(UUID playerUuid, int limit) throws Exception {
        List<PlayerNotification> notifications = new ArrayList<>();
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "select id, type, title, message, created_at from notifications " +
                        "where user_uuid = ? and delivered_in_game = false " +
                        "order by created_at asc limit ?"
        )) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, Math.max(1, Math.min(10, limit)));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    PlayerNotification notification = new PlayerNotification();
                    notification.id = UUID.fromString(rs.getString("id"));
                    notification.type = rs.getString("type");
                    notification.title = rs.getString("title");
                    notification.message = rs.getString("message");
                    notification.createdAt = String.valueOf(rs.getObject("created_at"));
                    notifications.add(notification);
                }
            }
        }
        return notifications;
    }

    public static List<PlayerNotification> fetchLatest(UUID playerUuid, int limit) throws Exception {
        List<PlayerNotification> notifications = new ArrayList<>();
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "select id, type, title, message, created_at from notifications " +
                        "where user_uuid = ? order by created_at desc limit ?"
        )) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, Math.max(1, Math.min(20, limit)));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    PlayerNotification notification = new PlayerNotification();
                    notification.id = UUID.fromString(rs.getString("id"));
                    notification.type = rs.getString("type");
                    notification.title = rs.getString("title");
                    notification.message = rs.getString("message");
                    notification.createdAt = String.valueOf(rs.getObject("created_at"));
                    notifications.add(notification);
                }
            }
        }
        return notifications;
    }

    public static void markDelivered(List<PlayerNotification> notifications) throws Exception {
        if (notifications == null || notifications.isEmpty()) return;
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "update notifications set delivered_in_game = true, delivered_in_game_at = now() where id = ?"
        )) {
            for (PlayerNotification notification : notifications) {
                statement.setObject(1, notification.id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public static void create(UUID userUuid, String username, String type, String title, String message) {
        if (userUuid == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("create notification", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into notifications (user_uuid, username, type, title, message, created_at) values (?, ?, ?, ?, ?, now())"
            )) {
                statement.setString(1, userUuid.toString());
                statement.setString(2, username == null ? "" : username);
                statement.setString(3, type == null ? "GENERAL" : type);
                statement.setString(4, title == null ? "Notification" : title);
                statement.setString(5, message == null ? "" : message);
                statement.executeUpdate();
            }
        });
    }

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure notifications schema", NotificationRepository::ensureSchema);
    }

    public static void ensureSchema(Connection connection) throws Exception {
        if (connection == null) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists notifications (" +
                    "id uuid primary key default gen_random_uuid(), " +
                    "user_uuid text, " +
                    "username text, " +
                    "type text not null default 'GENERAL', " +
                    "title text not null default '', " +
                    "message text not null default '', " +
                    "data jsonb not null default '{}'::jsonb, " +
                    "read_at timestamp with time zone, " +
                    "delivered_in_game boolean not null default false, " +
                    "delivered_in_game_at timestamp with time zone, " +
                    "created_at timestamp with time zone default now()" +
                    ")");
            statement.executeUpdate("alter table notifications add column if not exists user_uuid text");
            statement.executeUpdate("alter table notifications add column if not exists username text");
            statement.executeUpdate("alter table notifications add column if not exists type text not null default 'GENERAL'");
            statement.executeUpdate("alter table notifications add column if not exists title text not null default ''");
            statement.executeUpdate("alter table notifications add column if not exists message text not null default ''");
            statement.executeUpdate("alter table notifications add column if not exists data jsonb not null default '{}'::jsonb");
            statement.executeUpdate("alter table notifications add column if not exists read_at timestamp with time zone");
            statement.executeUpdate("alter table notifications add column if not exists delivered_in_game boolean not null default false");
            statement.executeUpdate("alter table notifications add column if not exists delivered_in_game_at timestamp with time zone");
            statement.executeUpdate("alter table notifications add column if not exists created_at timestamp with time zone default now()");
            statement.executeUpdate("create index if not exists idx_notifications_user_undelivered_created on notifications (user_uuid, created_at) where delivered_in_game = false");
            statement.executeUpdate("create index if not exists idx_notifications_user_created_desc on notifications (user_uuid, created_at desc)");
        }
    }

    public static final class PlayerNotification {
        public UUID id;
        public String type;
        public String title;
        public String message;
        public String createdAt;
    }
}
