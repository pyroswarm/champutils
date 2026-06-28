package com.champutils.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

public final class DatabaseManager {

    @FunctionalInterface
    public interface SqlTask {
        void run(Connection connection) throws Exception;
    }

    private static final int ASYNC_DATABASE_THREADS = 4;
    private static final int ASYNC_DATABASE_QUEUE_LIMIT = 4096;

    private static final long CONNECTION_VALIDATION_INTERVAL_MILLIS = 30_000L;
    private static Connection connection;
    private static long lastConnectionValidationAtMillis = 0L;
    private static final ThreadLocal<Connection> asyncConnection = new ThreadLocal<>();
    private static final ThreadLocal<Long> asyncConnectionLastValidationAtMillis = ThreadLocal.withInitial(() -> 0L);
    private static DatabaseConfig config;
    private static boolean enabled = false;
    private static ExecutorService executor;
    private static final Map<String, AtomicLong> COALESCED_TASK_GENERATIONS = new ConcurrentHashMap<>();
    private static final AtomicLong SUBMITTED_TASKS = new AtomicLong();
    private static final AtomicLong COMPLETED_TASKS = new AtomicLong();
    private static volatile long lastServerThreadWarningAtMillis = 0L;
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
            executor = new ThreadPoolExecutor(
                    ASYNC_DATABASE_THREADS,
                    ASYNC_DATABASE_THREADS,
                    30L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(ASYNC_DATABASE_QUEUE_LIMIT),
                    runnable -> {
                        Thread thread = new Thread(runnable, "ChampUtils-Database");
                        thread.setDaemon(true);
                        return thread;
                    },
                    (runnable, pool) -> {
                        System.err.println("[ChampUtils] Database queue is full; spilling one database task to an overflow thread to protect the server tick.");
                        Thread overflow = new Thread(runnable, "ChampUtils-Database-Overflow");
                        overflow.setDaemon(true);
                        overflow.start();
                    }
            );
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

            if (connection != null && isConnectionUsable(connection, false)) {
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
        warnIfServerThreadConnection();
        if (config == null) {
            init();
        }

        if (config == null || !config.enabled || !config.isConfigured()) {
            throw new SQLException(lastStatus);
        }

        if (connection == null || !isConnectionUsable(connection, false)) {
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
        if (existing == null || !isConnectionUsable(existing, true)) {
            existing = openConnection();
            asyncConnection.set(existing);
            asyncConnectionLastValidationAtMillis.set(System.currentTimeMillis());
        }
        return existing;
    }

    private static boolean isConnectionUsable(Connection candidate, boolean async) throws SQLException {
        if (candidate == null || candidate.isClosed()) return false;

        long now = System.currentTimeMillis();
        long lastValidation = async ? asyncConnectionLastValidationAtMillis.get() : lastConnectionValidationAtMillis;
        if (now - lastValidation < CONNECTION_VALIDATION_INTERVAL_MILLIS) return true;

        boolean valid = candidate.isValid(2);
        if (valid) {
            if (async) asyncConnectionLastValidationAtMillis.set(now);
            else lastConnectionValidationAtMillis = now;
        }
        return valid;
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

        SUBMITTED_TASKS.incrementAndGet();
        executor.submit(() -> {
            try {
                task.run(getAsyncConnection());
            }
            catch (Exception e) {
                System.err.println("[ChampUtils] Database task failed: " + description);
                e.printStackTrace();
            }
            finally {
                COMPLETED_TASKS.incrementAndGet();
            }
        });
    }

    /**
     * Queues a database task that only needs the newest value for a key.
     *
     * This is intended for hot sync paths such as playtime, money, profession XP, ranked stats,
     * and server heartbeats. If 20 updates for the same profile are queued during a lag spike,
     * the executor will skip the 19 stale copies and only write the newest snapshot. This keeps
     * the main server thread smooth with high player counts while still persisting final state.
     */
    public static void executeCoalescedAsync(String coalesceKey, String description, SqlTask task) {
        if (coalesceKey == null || coalesceKey.isBlank()) {
            executeAsync(description, task);
            return;
        }

        AtomicLong generation = COALESCED_TASK_GENERATIONS.computeIfAbsent(coalesceKey, ignored -> new AtomicLong());
        long myGeneration = generation.incrementAndGet();

        executeAsync(description, connection -> {
            if (generation.get() != myGeneration) {
                return;
            }
            task.run(connection);
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

        SUBMITTED_TASKS.incrementAndGet();
        return CompletableFuture.runAsync(() -> {
            try {
                task.run(getAsyncConnection());
            } catch (Exception e) {
                System.err.println("[ChampUtils] Database task failed: " + description);
                throw new CompletionException(e);
            } finally {
                COMPLETED_TASKS.incrementAndGet();
            }
        }, executor);
    }


    @FunctionalInterface
    public interface SqlSupplier<T> {
        T get(Connection connection) throws Exception;
    }

    public static <T> CompletableFuture<T> supplyAsync(String description, SqlSupplier<T> task) {
        if (task == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (config == null) {
            init();
        }

        if (!isEnabled() || executor == null) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(new SQLException(lastStatus));
            return failed;
        }

        SUBMITTED_TASKS.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.get(getAsyncConnection());
            } catch (Exception e) {
                System.err.println("[ChampUtils] Database task failed: " + description);
                throw new CompletionException(e);
            } finally {
                COMPLETED_TASKS.incrementAndGet();
            }
        }, executor);
    }

    /**
     * Blocks the caller until all database tasks submitted before this call have completed, or the timeout expires.
     * Use this only on lifecycle safety points such as /forcesaverestart, player disconnect cleanup, and server stop.
     */
    public static boolean flushSubmittedTasks(long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        long target = SUBMITTED_TASKS.get();
        while (COMPLETED_TASKS.get() < target) {
            if (System.nanoTime() >= deadline) {
                System.err.println("[ChampUtils] Database flush timed out. completed=" + COMPLETED_TASKS.get() + " target=" + target);
                return false;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private static void warnIfServerThreadConnection() {
        String threadName = Thread.currentThread().getName();
        if (threadName == null || !threadName.equalsIgnoreCase("Server thread")) return;
        long now = System.currentTimeMillis();
        if (now - lastServerThreadWarningAtMillis < 5_000L) return;
        lastServerThreadWarningAtMillis = now;
        System.err.println("[ChampUtils][PERF] DatabaseManager.getConnection() was called on the Minecraft server thread. Move this call to DatabaseManager.runAsync/executeAsync.");
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String line = element.toString();
            if (line.contains("com.champutils")) {
                System.err.println("[ChampUtils][PERF]   at " + line);
            }
        }
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

        COALESCED_TASK_GENERATIONS.clear();

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
