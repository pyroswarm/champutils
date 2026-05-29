package com.champutils.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class DatabaseManager {

    @FunctionalInterface
    public interface SqlTask {
        void run(Connection connection) throws Exception;
    }

    private static final int ASYNC_DATABASE_THREADS = 4;

    private static Connection connection;
    private static final ThreadLocal<Connection> asyncConnection = new ThreadLocal<>();
    private static DatabaseConfig config;
    private static boolean enabled = false;
    private static ExecutorService executor;
    private static String lastStatus = "Database has not initialized yet.";

    private DatabaseManager() {
    }

    public static synchronized void init() {
        System.out.println("[ChampUtils] Database init starting.");

        config = DatabaseConfig.loadOrCreate();

        System.out.println("[ChampUtils] Database config path: " + DatabaseConfig.getConfigPath().toAbsolutePath());
        System.out.println("[ChampUtils] Database config loaded: " + config.safeSummary());

        if (!config.enabled) {
            enabled = false;
            lastStatus = "Database disabled in config.";
            System.out.println("[ChampUtils] " + lastStatus);
            return;
        }

        if (!config.isConfigured()) {
            enabled = false;
            lastStatus = "Database config is not complete. Check password/host/username.";
            System.out.println("[ChampUtils] " + lastStatus);
            return;
        }

        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(ASYNC_DATABASE_THREADS, runnable -> {
                Thread thread = new Thread(runnable, "ChampUtils-Database");
                thread.setDaemon(true);
                return thread;
            });
        }

        connect();
    }

    public static synchronized boolean reloadAndConnect() {
        shutdown();
        init();
        return enabled;
    }

    private static synchronized void connect() {
        try {
            Class.forName("org.postgresql.Driver");

            if (connection != null && !connection.isClosed() && connection.isValid(2)) {
                enabled = true;
                lastStatus = "Connected to Supabase/Postgres successfully.";
                return;
            }

            connection = openConnection();

            enabled = true;
            lastStatus = "Connected to Supabase/Postgres successfully.";

            System.out.println("[ChampUtils] " + lastStatus);
        }
        catch (Exception e) {
            enabled = false;
            lastStatus =
                    "Failed database connection: " +
                            e.getClass().getSimpleName() +
                            ": " +
                            e.getMessage();

            System.err.println("[ChampUtils] " + lastStatus);
            e.printStackTrace();
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                config.jdbcUrl(),
                config.username,
                config.password
        );
    }

    public static synchronized Connection getConnection() throws SQLException {
        if (config == null) {
            init();
        }

        if (config == null || !config.enabled || !config.isConfigured()) {
            throw new SQLException(lastStatus);
        }

        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            connect();
        }

        if (!enabled || connection == null) {
            throw new SQLException(lastStatus);
        }

        return connection;
    }

    private static Connection getAsyncConnection() throws SQLException {
        if (config == null) {
            init();
        }

        if (config == null || !config.enabled || !config.isConfigured()) {
            throw new SQLException(lastStatus);
        }

        Connection existing = asyncConnection.get();
        if (existing == null || existing.isClosed() || !existing.isValid(2)) {
            existing = openConnection();
            asyncConnection.set(existing);
        }
        return existing;
    }

    public static void executeAsync(String description, SqlTask task) {
        if (task == null) {
            return;
        }

        if (config == null) {
            init();
        }

        if (!isEnabled() || executor == null) {
            System.out.println("[ChampUtils] Skipped database task '" + description + "': " + lastStatus);
            return;
        }

        executor.submit(() -> {
            try {
                task.run(getAsyncConnection());
            }
            catch (Exception e) {
                System.err.println("[ChampUtils] Database task failed: " + description);
                e.printStackTrace();
            }
        });
    }


    public static CompletableFuture<Void> runAsync(String description, SqlTask task) {
        if (task == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (config == null) {
            init();
        }

        if (!isEnabled() || executor == null) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new SQLException(lastStatus));
            return failed;
        }

        return CompletableFuture.runAsync(() -> {
            try {
                task.run(getAsyncConnection());
            } catch (Exception e) {
                System.err.println("[ChampUtils] Database task failed: " + description);
                throw new CompletionException(e);
            }
        }, executor);
    }

    public static boolean isEnabled() {
        return enabled
                && config != null
                && config.enabled
                && config.isConfigured();
    }

    public static String getLastStatus() {
        return lastStatus;
    }

    public static DatabaseConfig getConfig() {
        return config;
    }

    public static synchronized void shutdown() {
        if (executor != null) {
            executor.shutdown();

            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }
            catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            executor = null;
        }

        closeQuietly(asyncConnection.get());
        asyncConnection.remove();

        try {
            if (connection != null) {
                connection.close();
                connection = null;
                System.out.println("[ChampUtils] Database connection closed.");
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }

        enabled = false;
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
