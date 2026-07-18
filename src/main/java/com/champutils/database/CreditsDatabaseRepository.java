package com.champutils.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class CreditsDatabaseRepository {

    private static boolean schemaEnsured = false;
    private static final long STARTING_BALANCE = 25_000L;
    private static final long MAX_BALANCE = 9_000_000_000_000_000L;

    private CreditsDatabaseRepository() {
    }

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure credits schema", CreditsDatabaseRepository::ensureSchema);
    }

    private static void ensureSchema(java.sql.Connection connection) throws Exception {
        if (schemaEnsured) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists player_economy (" +
                        "uuid text primary key, " +
                        "username text not null, " +
                        "credits bigint not null default 0, " +
                        "lifetime_earned bigint not null default 0, " +
                        "lifetime_spent bigint not null default 0, " +
                        "updated_at timestamp with time zone default now()" +
                        ")"
        )) {
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists economy_ledger (" +
                        "id uuid primary key, " +
                        "transfer_id uuid, " +
                        "operation_id uuid, " +
                        "uuid text, " +
                        "username text not null default '', " +
                        "type text not null, " +
                        "amount bigint not null default 0, " +
                        "balance_after bigint not null default 0, " +
                        "reason text not null default 'unspecified', " +
                        "server_id text not null default '', " +
                        "created_at timestamp with time zone default now()" +
                        ")"
        )) {
            statement.executeUpdate();
        }

        try (PreparedStatement alter = connection.prepareStatement(
                "alter table economy_ledger add column if not exists operation_id uuid"
        )) {
            alter.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "create unique index if not exists economy_ledger_operation_idx on economy_ledger (operation_id, type) where operation_id is not null"
        )) {
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "create index if not exists economy_ledger_uuid_created_idx on economy_ledger (uuid, created_at desc)"
        )) {
            statement.executeUpdate();
        }

        schemaEnsured = true;
    }

    public static void sync(
            UUID playerId,
            String username,
            long credits,
            long lifetimeEarned,
            long lifetimeSpent
    ) {
        if (playerId == null) {
            return;
        }

        long safeCredits = Math.max(0L, credits);
        long safeEarned = Math.max(0L, lifetimeEarned);
        long safeSpent = Math.max(0L, lifetimeSpent);
        String safeUsername = username == null || username.isBlank()
                ? playerId.toString()
                : username;

        DatabaseManager.executeAsync("sync credits " + playerId, connection -> {
            ensureSchema(connection);

            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into player_economy (uuid, username, credits, lifetime_earned, lifetime_spent, updated_at) " +
                            "values (?, ?, ?, ?, ?, now()) " +
                            "on conflict (uuid) do update set " +
                            "username = excluded.username, " +
                            "credits = excluded.credits, " +
                            "lifetime_earned = excluded.lifetime_earned, " +
                            "lifetime_spent = excluded.lifetime_spent, " +
                            "updated_at = now()"
            )) {
                statement.setString(1, playerId.toString());
                statement.setString(2, safeUsername);
                statement.setLong(3, safeCredits);
                statement.setLong(4, safeEarned);
                statement.setLong(5, safeSpent);
                statement.executeUpdate();
            }

            try (PreparedStatement stats = connection.prepareStatement(
                    "insert into profile_player_stats (profile_id, money, updated_at) " +
                            "select ?::uuid, ?, now() where exists (select 1 from player_profiles where id = ?::uuid) " +
                            "on conflict (profile_id) do update set money = excluded.money, updated_at = now() " +
                        "where profile_player_stats.money is distinct from excluded.money"
            )) {
                stats.setString(1, playerId.toString());
                stats.setLong(2, safeCredits);
                stats.setString(3, playerId.toString());
                stats.executeUpdate();
            }
        });
    }

    public static CompletableFuture<AccountSnapshot> loadAsync(UUID playerId) {
        if (playerId == null || !DatabaseManager.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        return DatabaseManager.supplyAsync("load credits " + playerId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select username, credits, lifetime_earned, lifetime_spent from player_economy where uuid = ?"
            )) {
                statement.setString(1, playerId.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new AccountSnapshot(
                            rs.getString(1),
                            Math.max(0L, rs.getLong(2)),
                            Math.max(0L, rs.getLong(3)),
                            Math.max(0L, rs.getLong(4))
                    );
                }
            }
        });
    }

    public static AccountSnapshot load(UUID playerId) {
        if (playerId == null || !DatabaseManager.isEnabled()) {
            return null;
        }
        if (Thread.currentThread().getName() != null && Thread.currentThread().getName().equalsIgnoreCase("Server thread")) {
            System.err.println("[ChampUtils][PERF] Synchronous credits load requested on the server thread for " + playerId + "; returning cached/default economy data instead.");
            return null;
        }
        try {
            return loadAsync(playerId).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load credits from SQL for " + playerId + ".");
            e.printStackTrace();
            return null;
        }
    }

    public static CompletableFuture<MutationResult> depositAsync(UUID operationId, UUID playerId, String username, long amount, String reason, String serverId) {
        return mutateAsync("deposit credits " + playerId, connection -> deposit(connection, operationId, playerId, username, amount, reason, serverId));
    }

    public static CompletableFuture<MutationResult> withdrawAsync(UUID operationId, UUID playerId, String username, long amount, String reason, String serverId) {
        return mutateAsync("withdraw credits " + playerId, connection -> withdraw(connection, operationId, playerId, username, amount, reason, serverId));
    }

    public static CompletableFuture<MutationResult> setBalanceAsync(UUID operationId, UUID playerId, String username, long amount, String reason, String serverId) {
        return mutateAsync("set credits " + playerId, connection -> setBalance(connection, operationId, playerId, username, amount, reason, serverId));
    }

    public static CompletableFuture<MutationResult> transferAsync(UUID operationId, UUID fromId, String fromName, UUID toId, String toName, long amount, String reason, String serverId) {
        return mutateAsync("transfer credits " + fromId + " to " + toId, connection -> transfer(connection, operationId, fromId, fromName, toId, toName, amount, reason, serverId));
    }

    private static CompletableFuture<MutationResult> mutateAsync(String description, SqlMutation mutation) {
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(MutationResult.fail("Database is not available."));
        return DatabaseManager.supplyAsync(description, connection -> {
            try {
                return mutation.run(connection);
            } catch (Exception e) {
                System.err.println("[ChampUtils] Atomic economy operation failed: " + description);
                e.printStackTrace();
                return MutationResult.fail("Economy database is busy. Please try again.");
            }
        });
    }

    public static MutationResult deposit(UUID playerId, String username, long amount, String reason, String serverId) {
        return runOnlyOffServerThread("deposit credits " + playerId, c -> deposit(c, UUID.randomUUID(), playerId, username, amount, reason, serverId));
    }

    public static MutationResult withdraw(UUID playerId, String username, long amount, String reason, String serverId) {
        return runOnlyOffServerThread("withdraw credits " + playerId, c -> withdraw(c, UUID.randomUUID(), playerId, username, amount, reason, serverId));
    }

    public static MutationResult setBalance(UUID playerId, String username, long amount, String reason, String serverId) {
        return runOnlyOffServerThread("set credits " + playerId, c -> setBalance(c, UUID.randomUUID(), playerId, username, amount, reason, serverId));
    }

    public static MutationResult transfer(UUID fromId, String fromName, UUID toId, String toName, long amount, String reason, String serverId) {
        return runOnlyOffServerThread("transfer credits " + fromId + " to " + toId, c -> transfer(c, UUID.randomUUID(), fromId, fromName, toId, toName, amount, reason, serverId));
    }

    private static MutationResult runOnlyOffServerThread(String description, SqlMutation mutation) {
        if (!DatabaseManager.isEnabled()) return MutationResult.fail("Database is not available.");
        String threadName = Thread.currentThread().getName();
        if (threadName != null && threadName.equalsIgnoreCase("Server thread")) {
            System.err.println("[ChampUtils][PERF] Rejected synchronous economy operation on server thread: " + description);
            return MutationResult.fail("Economy request must be processed asynchronously.");
        }
        try {
            if (threadName != null && threadName.startsWith("ChampUtils-Database")) return mutation.run(DatabaseManager.getConnection());
            return DatabaseManager.supplyAsync(description, mutation::run).join();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Atomic economy operation failed: " + description);
            e.printStackTrace();
            return MutationResult.fail("Economy database is busy. Please try again.");
        }
    }

    private static MutationResult deposit(Connection connection, UUID operationId, UUID playerId, String username, long amount, String reason, String serverId) throws Exception {
        if (playerId == null) return MutationResult.fail("Player not found.");
        if (amount <= 0L) return MutationResult.fail("Amount must be positive.");
        return inTransaction(connection, operationId, "DEPOSIT", playerId, () -> {
            AccountSnapshot before = ensureAccountForUpdate(connection, playerId, username);
            if (MAX_BALANCE - before.credits < amount) return MutationResult.fail("That would exceed the maximum allowed balance.");
            long balance = before.credits + amount;
            long earned = safeAdd(before.lifetimeEarned, amount);
            updateAccount(connection, playerId, username, balance, earned, before.lifetimeSpent);
            insertLedger(connection, null, operationId, playerId, username, "DEPOSIT", amount, balance, reason, serverId);
            syncProfileStats(connection, playerId, balance);
            return MutationResult.success(amount, balance, earned, before.lifetimeSpent);
        });
    }

    private static MutationResult withdraw(Connection connection, UUID operationId, UUID playerId, String username, long amount, String reason, String serverId) throws Exception {
        if (playerId == null) return MutationResult.fail("Player not found.");
        if (amount <= 0L) {
            AccountSnapshot before = ensureAccountForUpdate(connection, playerId, username);
            return MutationResult.success(0L, before.credits, before.lifetimeEarned, before.lifetimeSpent);
        }
        return inTransaction(connection, operationId, "WITHDRAW", playerId, () -> {
            AccountSnapshot before = ensureAccountForUpdate(connection, playerId, username);
            if (before.credits < amount) return MutationResult.fail("You need " + amount + " cents but only have " + before.credits + " cents.");
            long balance = before.credits - amount;
            long spent = safeAdd(before.lifetimeSpent, amount);
            updateAccount(connection, playerId, username, balance, before.lifetimeEarned, spent);
            insertLedger(connection, null, operationId, playerId, username, "WITHDRAW", amount, balance, reason, serverId);
            syncProfileStats(connection, playerId, balance);
            return MutationResult.success(amount, balance, before.lifetimeEarned, spent);
        });
    }

    private static MutationResult setBalance(Connection connection, UUID operationId, UUID playerId, String username, long amount, String reason, String serverId) throws Exception {
        if (playerId == null) return MutationResult.fail("Player not found.");
        if (amount < 0L || amount > MAX_BALANCE) return MutationResult.fail("Amount is outside the allowed range.");
        return inTransaction(connection, operationId, "SET", playerId, () -> {
            AccountSnapshot before = ensureAccountForUpdate(connection, playerId, username);
            long earned = Math.max(before.lifetimeEarned, amount);
            updateAccount(connection, playerId, username, amount, earned, before.lifetimeSpent);
            insertLedger(connection, null, operationId, playerId, username, "SET", amount, amount, reason, serverId);
            syncProfileStats(connection, playerId, amount);
            return MutationResult.success(amount, amount, earned, before.lifetimeSpent);
        });
    }

    private static MutationResult transfer(Connection connection, UUID operationId, UUID fromId, String fromName, UUID toId, String toName, long amount, String reason, String serverId) throws Exception {
        if (fromId == null || toId == null) return MutationResult.fail("Player not found.");
        if (fromId.equals(toId)) return MutationResult.fail("You cannot pay yourself.");
        if (amount <= 0L) return MutationResult.fail("Amount must be positive.");
        boolean restoreAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            ensureSchema(connection);
            lockOperation(connection, operationId);
            MutationResult prior = priorOperation(connection, operationId, "TRANSFER_OUT", fromId);
            if (prior != null) { connection.commit(); return prior; }
            UUID first = fromId.toString().compareTo(toId.toString()) <= 0 ? fromId : toId;
            UUID second = first.equals(fromId) ? toId : fromId;
            ensureAccountForUpdate(connection, first, first.equals(fromId) ? fromName : toName);
            ensureAccountForUpdate(connection, second, second.equals(fromId) ? fromName : toName);
            AccountSnapshot sender = loadForUpdate(connection, fromId);
            AccountSnapshot receiver = loadForUpdate(connection, toId);
            if (sender.credits < amount) { connection.rollback(); return MutationResult.fail("You do not have enough Credits."); }
            if (MAX_BALANCE - receiver.credits < amount) { connection.rollback(); return MutationResult.fail("The receiving player cannot hold that many Credits."); }
            long senderBalance = sender.credits - amount;
            long receiverBalance = receiver.credits + amount;
            long senderSpent = safeAdd(sender.lifetimeSpent, amount);
            long receiverEarned = safeAdd(receiver.lifetimeEarned, amount);
            UUID transferId = UUID.randomUUID();
            updateAccount(connection, fromId, fromName, senderBalance, sender.lifetimeEarned, senderSpent);
            updateAccount(connection, toId, toName, receiverBalance, receiverEarned, receiver.lifetimeSpent);
            insertLedger(connection, transferId, operationId, fromId, fromName, "TRANSFER_OUT", amount, senderBalance, reason, serverId);
            insertLedger(connection, transferId, operationId, toId, toName, "TRANSFER_IN", amount, receiverBalance, reason, serverId);
            syncProfileStats(connection, fromId, senderBalance);
            syncProfileStats(connection, toId, receiverBalance);
            connection.commit();
            return MutationResult.success(amount, senderBalance, sender.lifetimeEarned, senderSpent);
        } catch (Exception e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(restoreAutoCommit); }
    }

    private static MutationResult inTransaction(Connection connection, UUID operationId, String type, UUID playerId, TxBody body) throws Exception {
        boolean restoreAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            ensureSchema(connection);
            lockOperation(connection, operationId);
            MutationResult prior = priorOperation(connection, operationId, type, playerId);
            if (prior != null) { connection.commit(); return prior; }
            MutationResult result = body.run();
            if (!result.success) { connection.rollback(); return result; }
            connection.commit();
            return result;
        } catch (Exception e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(restoreAutoCommit); }
    }

    private static void lockOperation(Connection connection, UUID operationId) throws Exception {
        if (operationId == null) return;
        long key = operationId.getMostSignificantBits() ^ operationId.getLeastSignificantBits();
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_xact_lock(?)")) {
            statement.setLong(1, key);
            statement.executeQuery();
        }
    }

    private static MutationResult priorOperation(Connection connection, UUID operationId, String type, UUID playerId) throws Exception {
        if (operationId == null) return null;
        try (PreparedStatement statement = connection.prepareStatement(
                "select l.amount, l.balance_after, coalesce(e.lifetime_earned, 0), coalesce(e.lifetime_spent, 0) " +
                        "from economy_ledger l left join player_economy e on e.uuid = l.uuid " +
                        "where l.operation_id = ? and l.type = ? and l.uuid = ? limit 1")) {
            statement.setObject(1, operationId);
            statement.setString(2, type);
            statement.setString(3, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? MutationResult.success(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)) : null;
            }
        }
    }

    @FunctionalInterface private interface TxBody { MutationResult run() throws Exception; }

    private static AccountSnapshot ensureAccountForUpdate(Connection connection, UUID playerId, String username) throws Exception {
        String safeUsername = safeUsername(playerId, username);
        try (PreparedStatement insert = connection.prepareStatement(
                "insert into player_economy (uuid, username, credits, lifetime_earned, lifetime_spent, updated_at) " +
                        "values (?, ?, ?, ?, 0, now()) on conflict (uuid) do nothing"
        )) {
            insert.setString(1, playerId.toString());
            insert.setString(2, safeUsername);
            insert.setLong(3, STARTING_BALANCE);
            insert.setLong(4, STARTING_BALANCE);
            insert.executeUpdate();
        }
        return loadForUpdate(connection, playerId);
    }

    private static AccountSnapshot loadForUpdate(Connection connection, UUID playerId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select username, credits, lifetime_earned, lifetime_spent from player_economy where uuid = ? for update"
        )) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return new AccountSnapshot("", STARTING_BALANCE, STARTING_BALANCE, 0L);
                }
                return new AccountSnapshot(
                        rs.getString(1),
                        Math.max(0L, rs.getLong(2)),
                        Math.max(0L, rs.getLong(3)),
                        Math.max(0L, rs.getLong(4))
                );
            }
        }
    }

    private static void updateAccount(Connection connection, UUID playerId, String username, long credits, long lifetimeEarned, long lifetimeSpent) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "update player_economy set username = ?, credits = ?, lifetime_earned = ?, lifetime_spent = ?, updated_at = now() " +
                        "where uuid = ? and (username is distinct from ? or credits is distinct from ? or lifetime_earned is distinct from ? or lifetime_spent is distinct from ?)"
        )) {
            statement.setString(1, safeUsername(playerId, username));
            statement.setLong(2, Math.max(0L, Math.min(MAX_BALANCE, credits)));
            statement.setLong(3, Math.max(0L, lifetimeEarned));
            statement.setLong(4, Math.max(0L, lifetimeSpent));
            statement.setString(5, playerId.toString());
            statement.setString(6, safeUsername(playerId, username));
            statement.setLong(7, Math.max(0L, Math.min(MAX_BALANCE, credits)));
            statement.setLong(8, Math.max(0L, lifetimeEarned));
            statement.setLong(9, Math.max(0L, lifetimeSpent));
            statement.executeUpdate();
        }
    }

    private static void insertLedger(Connection connection, UUID transferId, UUID operationId, UUID playerId, String username, String type, long amount, long balanceAfter, String reason, String serverId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into economy_ledger (id, transfer_id, operation_id, uuid, username, type, amount, balance_after, reason, server_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, transferId);
            statement.setObject(3, operationId);
            statement.setString(4, playerId == null ? null : playerId.toString());
            statement.setString(5, safeUsername(playerId, username));
            statement.setString(6, type == null ? "UNKNOWN" : type);
            statement.setLong(7, Math.max(0L, amount));
            statement.setLong(8, Math.max(0L, balanceAfter));
            statement.setString(9, reason == null || reason.isBlank() ? "unspecified" : reason);
            statement.setString(10, serverId == null ? "" : serverId);
            statement.executeUpdate();
        }
    }

    private static void syncProfileStats(Connection connection, UUID profileId, long credits) {
        try (PreparedStatement stats = connection.prepareStatement(
                "insert into profile_player_stats (profile_id, money, updated_at) " +
                        "select ?::uuid, ?, now() where exists (select 1 from player_profiles where id = ?::uuid) " +
                        "on conflict (profile_id) do update set money = excluded.money, updated_at = now() " +
                        "where profile_player_stats.money is distinct from excluded.money"
        )) {
            stats.setString(1, profileId.toString());
            stats.setLong(2, Math.max(0L, credits));
            stats.setString(3, profileId.toString());
            stats.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private static String safeUsername(UUID playerId, String username) {
        return username == null || username.isBlank()
                ? (playerId == null ? "" : playerId.toString())
                : username;
    }

    private static long safeAdd(long current, long amount) {
        if (amount <= 0L) return current;
        if (Long.MAX_VALUE - current < amount) return Long.MAX_VALUE;
        return current + amount;
    }

    @FunctionalInterface
    private interface SqlMutation {
        MutationResult run(Connection connection) throws Exception;
    }

    public static final class MutationResult {
        public final boolean success;
        public final String error;
        public final long amount;
        public final long newBalance;
        public final long lifetimeEarned;
        public final long lifetimeSpent;

        private MutationResult(boolean success, String error, long amount, long newBalance, long lifetimeEarned, long lifetimeSpent) {
            this.success = success;
            this.error = error;
            this.amount = amount;
            this.newBalance = newBalance;
            this.lifetimeEarned = lifetimeEarned;
            this.lifetimeSpent = lifetimeSpent;
        }

        public static MutationResult success(long amount, long newBalance, long lifetimeEarned, long lifetimeSpent) {
            return new MutationResult(true, null, amount, newBalance, lifetimeEarned, lifetimeSpent);
        }

        public static MutationResult fail(String error) {
            return new MutationResult(false, error == null ? "Economy operation failed." : error, 0L, 0L, 0L, 0L);
        }
    }

    public static final class AccountSnapshot {
        public final String username;
        public final long credits;
        public final long lifetimeEarned;
        public final long lifetimeSpent;

        private AccountSnapshot(String username, long credits, long lifetimeEarned, long lifetimeSpent) {
            this.username = username == null ? "" : username;
            this.credits = credits;
            this.lifetimeEarned = lifetimeEarned;
            this.lifetimeSpent = lifetimeSpent;
        }
    }
}
