package com.champutils.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseManager {
    private static Connection connection;
    private static DatabaseConfig config;
    private static boolean enabled = false;

    private DatabaseManager() {}

    public static void init() {
        config = DatabaseConfig.loadOrCreate();

        if (!config.enabled) {
            System.out.println("[ChampUtils] Database disabled.");
            return;
        }

        if (!config.isConfigured()) {
            System.out.println("[ChampUtils] Set your database password in config/champutils/database.json");
            return;
        }

        connect();
    }

    private static void connect() {
        try {
            connection = DriverManager.getConnection(
                    config.jdbcUrl(),
                    config.username,
                    config.password
            );

            enabled = true;
            System.out.println("[ChampUtils] Connected to Supabase successfully.");
        } catch (SQLException e) {
            enabled = false;
            System.err.println("[ChampUtils] Failed database connection.");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        if (!enabled) {
            throw new SQLException("Database not connected.");
        }

        if (connection == null || connection.isClosed()) {
            connect();
        }

        return connection;
    }

    public static void shutdown() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
