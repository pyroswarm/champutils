package com.champutils.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class DatabaseManager {

    @FunctionalInterface
    public interface SqlTask {
        void run(Connection connection) throws Exception;
    }

    @FunctionalInterface
    public interface SqlSupplier<T> {
        T get(Connection connection) throws Exception;
    }

    private static final long CONNECTION_VALIDATION_INTERVAL_MILLIS = 30_000L;
    private static final ThreadLocal<Connection> ASYNC_CONNECTION = new ThreadLocal<>();
    private static final ThreadLocal<Long> ASYNC_CONNECTION_LAST_VALIDATION = ThreadLocal.withInitial(() -> 0L);
    private static final Set<Connection> TRACKED_ASYNC_CONNECTIONS = ConcurrentHashMap.newKeySet();
    private static final Map<String, AtomicLong> COALESCED_TASK_GENERATIONS = new ConcurrentHashMap<>();
    private static final AtomicLong SUBMITTED_TASKS = new AtomicLong();
    private static final AtomicLong COMPLETED_TASKS = new AtomicLong();
    private static final AtomicInteger WORKER_SEQUENCE = new AtomicInteger();

    private static Connection connection;
    private static long lastConnectionValidationAtMillis;
    private static DatabaseConfig config;
    private static boolean enabled;
    private static ThreadPoolExecutor executor;
    private static volatile long lastServerThreadWarningAtMillis;
    private static volatile long lastQueueFullWarningAtMillis;
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
            int threads = Math.max(2, Math.min(8, config.asyncDatabaseThreads));
            int queueLimit = Math.max(512, Math.min(32768, config.asyncDatabaseQueueLimit));
            executor = new ThreadPoolExecutor(
                    threads,
                    threads,
                    30L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(queueLimit),
                    runnable -> {
                        Thread thread = new Thread(runnable, "ChampUtils-Database-" + WORKER_SEQUENCE.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy()
            );
            executor.prestartAllCoreThreads();
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

            closeQuietly(connection);
            connection = openConnection();
            lastConnectionValidationAtMillis = System.currentTimeMillis();
            enabled = true;
            lastStatus = "Connected to Supabase/Postgres successfully.";
            System.out.println("[ChampUtils] " + lastStatus);
        }
        catch (Exception e) {
            enabled = false;
            lastStatus = "Failed database connection: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            System.err.println("[ChampUtils] " + lastStatus);
            e.printStackTrace();
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.username, config.password);
    }

    public static synchronized Connection getConnection() throws SQLException {
        warnIfServerThreadConnection();

        // Legacy repositories called from the bounded database executor reuse that worker's
        // connection. This avoids contention on the lifecycle connection and prevents a nested
        // repository call from opening an extra connection.
        if (isDatabaseWorkerThread()) {
            return getAsyncConnection();
        }

        if (config == null) init();
        if (config == null || !config.enabled || !config.isConfigured()) throw new SQLException(lastStatus);

        if (connection == null || !isConnectionUsable(connection, false)) connect();
        if (!enabled || connection == null) throw new SQLException(lastStatus);
        return connection;
    }

    private static boolean isDatabaseWorkerThread() {
        String name = Thread.currentThread().getName();
        return name != null && name.startsWith("ChampUtils-Database-");
    }

    private static Connection getAsyncConnection() throws SQLException {
        if (config == null) init();
        if (config == null || !config.enabled || !config.isConfigured()) throw new SQLException(lastStatus);

        Connection existing = ASYNC_CONNECTION.get();
        if (existing == null || !isConnectionUsable(existing, true)) {
            invalidateCurrentAsyncConnection();
            existing = openConnection();
            ASYNC_CONNECTION.set(existing);
            ASYNC_CONNECTION_LAST_VALIDATION.set(System.currentTimeMillis());
            TRACKED_ASYNC_CONNECTIONS.add(existing);
        }
        return existing;
    }

    private static boolean isConnectionUsable(Connection candidate, boolean async) throws SQLException {
        if (candidate == null || candidate.isClosed()) return false;

        long now = System.currentTimeMillis();
        long lastValidation = async ? ASYNC_CONNECTION_LAST_VALIDATION.get() : lastConnectionValidationAtMillis;
        if (now - lastValidation < CONNECTION_VALIDATION_INTERVAL_MILLIS) return true;

        boolean valid = candidate.isValid(2);
        if (valid) {
            if (async) ASYNC_CONNECTION_LAST_VALIDATION.set(now);
            else lastConnectionValidationAtMillis = now;
        }
        return valid;
    }

    public static void executeAsync(String description, SqlTask task) {
        if (task == null) return;
        if (!prepareAsyncSubmission(description)) return;

        submit(description, () -> {
            try {
                task.run(getAsyncConnection());
            }
            catch (Throwable error) {
                handleTaskFailure(description, error, true);
            }
            finally {
                resetCurrentAsyncConnection();
                COMPLETED_TASKS.incrementAndGet();
            }
        }, null);
    }

    /**
     * Queues a database task where only the newest value for a key matters. During a burst,
     * stale snapshots remain cheap no-ops and the generation entry is removed after the newest
     * task finishes so the coalescing map cannot grow forever.
     */
    public static void executeCoalescedAsync(String coalesceKey, String description, SqlTask task) {
        if (coalesceKey == null || coalesceKey.isBlank()) {
            executeAsync(description, task);
            return;
        }

        AtomicLong generation = COALESCED_TASK_GENERATIONS.computeIfAbsent(coalesceKey, ignored -> new AtomicLong());
        long myGeneration = generation.incrementAndGet();
        runAsync(description, workerConnection -> {
            if (generation.get() == myGeneration) task.run(workerConnection);
        }).whenComplete((ignored, error) -> {
            if (generation.get() == myGeneration) COALESCED_TASK_GENERATIONS.remove(coalesceKey, generation);
        });
    }

    public static CompletableFuture<Void> runAsync(String description, SqlTask task) {
        if (task == null) return CompletableFuture.completedFuture(null);
        if (isDatabaseWorkerThread()) {
            try {
                task.run(getAsyncConnection());
                return CompletableFuture.completedFuture(null);
            } catch (Throwable error) {
                handleTaskFailure(description, error, false);
                return failedFuture(error);
            }
        }
        if (!prepareAsyncSubmission(description)) return failedFuture(new SQLException(lastStatus));

        CompletableFuture<Void> future = new CompletableFuture<>();
        submit(description, () -> {
            try {
                task.run(getAsyncConnection());
                future.complete(null);
            }
            catch (Throwable error) {
                handleTaskFailure(description, error, false);
                future.completeExceptionally(error);
            }
            finally {
                resetCurrentAsyncConnection();
                COMPLETED_TASKS.incrementAndGet();
            }
        }, future);
        return future;
    }

    public static <T> CompletableFuture<T> supplyAsync(String description, SqlSupplier<T> task) {
        if (task == null) return CompletableFuture.completedFuture(null);
        if (isDatabaseWorkerThread()) {
            try {
                return CompletableFuture.completedFuture(task.get(getAsyncConnection()));
            } catch (Throwable error) {
                handleTaskFailure(description, error, false);
                return failedFuture(error);
            }
        }
        if (!prepareAsyncSubmission(description)) return failedFuture(new SQLException(lastStatus));

        CompletableFuture<T> future = new CompletableFuture<>();
        submit(description, () -> {
            try {
                future.complete(task.get(getAsyncConnection()));
            }
            catch (Throwable error) {
                handleTaskFailure(description, error, false);
                future.completeExceptionally(error);
            }
            finally {
                resetCurrentAsyncConnection();
                COMPLETED_TASKS.incrementAndGet();
            }
        }, future);
        return future;
    }

    private static boolean prepareAsyncSubmission(String description) {
        if (config == null) init();
        if (!isEnabled() || executor == null || executor.isShutdown()) {
            System.out.println("[ChampUtils] Skipped database task '" + description + "': " + lastStatus);
            return false;
        }
        SUBMITTED_TASKS.incrementAndGet();
        return true;
    }

    private static void submit(String description, Runnable runnable, CompletableFuture<?> future) {
        try {
            executor.execute(runnable);
        }
        catch (RejectedExecutionException rejected) {
            COMPLETED_TASKS.incrementAndGet();
            long now = System.currentTimeMillis();
            if (now - lastQueueFullWarningAtMillis >= 5_000L) {
                lastQueueFullWarningAtMillis = now;
                int queueSize = executor == null ? -1 : executor.getQueue().size();
                System.err.println("[ChampUtils][PERF] Database queue rejected task '" + description + "' (queued=" + queueSize + "). " +
                        "The task was not moved to an unbounded overflow thread. Increase asyncDatabaseQueueLimit only after checking slow queries.");
            }
            if (future != null) future.completeExceptionally(rejected);
        }
    }

    private static void handleTaskFailure(String description, Throwable error, boolean printStackTrace) {
        if (isConnectionFailure(error)) invalidateCurrentAsyncConnection();
        System.err.println("[ChampUtils] Database task failed: " + description + " - " + rootMessage(error));
        if (printStackTrace) error.printStackTrace();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current == null || current.getMessage() == null ? String.valueOf(error) : current.getMessage();
    }

    private static boolean isConnectionFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && state.startsWith("08")) return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void resetCurrentAsyncConnection() {
        Connection current = ASYNC_CONNECTION.get();
        if (current == null) return;
        try {
            if (current.isClosed()) {
                invalidateCurrentAsyncConnection();
                return;
            }
            if (!current.getAutoCommit()) {
                try { current.rollback(); } finally { current.setAutoCommit(true); }
            }
            current.clearWarnings();
        }
        catch (SQLException error) {
            invalidateCurrentAsyncConnection();
        }
    }

    private static void invalidateCurrentAsyncConnection() {
        Connection current = ASYNC_CONNECTION.get();
        ASYNC_CONNECTION.remove();
        ASYNC_CONNECTION_LAST_VALIDATION.remove();
        if (current != null) {
            TRACKED_ASYNC_CONNECTIONS.remove(current);
            closeQuietly(current);
        }
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> failed = new CompletableFuture<>();
        failed.completeExceptionally(error);
        return failed;
    }

    /**
     * Blocks until tasks submitted before this call finish. This is for server shutdown or an
     * explicit hard-save lifecycle operation only; gameplay and disconnect paths must queue an
     * immutable snapshot instead of invoking this on the Minecraft server thread.
     */
    public static boolean flushSubmittedTasks(long timeout, TimeUnit unit) {
        boolean serverThread = isMinecraftServerThread();
        if (serverThread) {
            System.err.println("[ChampUtils][PERF] Blocking database flush requested on the Minecraft server thread. This should only happen during shutdown/hard-save lifecycle handling.");
        }
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        long target = SUBMITTED_TASKS.get();
        while (COMPLETED_TASKS.get() < target) {
            if (System.nanoTime() >= deadline) {
                System.err.println("[ChampUtils] Database flush timed out. completed=" + COMPLETED_TASKS.get() + " target=" + target + " serverThread=" + serverThread);
                return false;
            }
            try {
                Thread.sleep(serverThread ? 1L : 10L);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private static void warnIfServerThreadConnection() {
        if (!isMinecraftServerThread()) return;
        long now = System.currentTimeMillis();
        if (now - lastServerThreadWarningAtMillis < 5_000L) return;
        lastServerThreadWarningAtMillis = now;
        System.err.println("[ChampUtils][PERF] DatabaseManager.getConnection() was called on the Minecraft server thread. Move this call to DatabaseManager.runAsync/executeAsync.");
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String line = element.toString();
            if (line.contains("com.champutils")) System.err.println("[ChampUtils][PERF]   at " + line);
        }
    }

    private static boolean isMinecraftServerThread() {
        String name = Thread.currentThread().getName();
        return name != null && name.equalsIgnoreCase("Server thread");
    }

    public static boolean isEnabled() {
        return enabled && config != null && config.enabled && config.isConfigured();
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
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
            }
            catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }

        COALESCED_TASK_GENERATIONS.clear();
        invalidateCurrentAsyncConnection();
        for (Connection workerConnection : TRACKED_ASYNC_CONNECTIONS.toArray(Connection[]::new)) {
            closeQuietly(workerConnection);
        }
        TRACKED_ASYNC_CONNECTIONS.clear();

        closeQuietly(connection);
        connection = null;
        enabled = false;
        SUBMITTED_TASKS.set(0L);
        COMPLETED_TASKS.set(0L);
        lastStatus = "Database connections closed.";
        System.out.println("[ChampUtils] " + lastStatus);
    }

    private static void closeQuietly(Connection value) {
        if (value == null) return;
        try {
            value.close();
        }
        catch (SQLException ignored) {
        }
    }
}
