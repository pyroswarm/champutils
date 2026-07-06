package com.champutils.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class CreditsDatabaseRepository {

    private static boolean schemaEnsured = false;
    private static final long STARTING_BALANCE = 25_000L;
    private static final long MAX_BALANCE = 9_000_000_000_000_000L;
    private static final long ATOMIC_OPERATION_TIMEOUT_MILLIS = 1500L;

    private CreditsDatabaseRepository() {
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
                            "on conflict (profile_id) do update set money = excluded.money, updated_at = now()"
            )) {
                stats.setString(1, playerId.toString());
                stats.setLong(2, safeCredits);
                stats.setString(3, playerId.toString());
                stats.executeUpdate();
            }
        });
    }

    public static AccountSnapshot load(UUID playerId) {
        if (playerId == null || !DatabaseManager.isEnabled()) {
            return null;
        }
        try {
            return DatabaseManager.supplyAsync("load credits " + playerId, connection -> {
                ensureSchema(connection);
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
            }).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load credits from SQL for " + playerId + ".");
            e.printStackTrace();
            return null;
        }
    }

    public static MutationResult deposit(UUID playerId, String username, long amount, String reason, String serverId) {
        if (playerId == null) return MutationResult.fail("Player not found.");
        if (amount <= 0L) return MutationResult.fail("Amount must be positive.");
        return atomic("deposit credits " + playerId, connection -> {
            boolean restoreAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureSchema(connection);
                AccountSnapshot before = ensureAccountForUpdate(connection, playerId, username);
                if (MAX_BALANCE - before.credits < amount) {
                    connection.rollback();
                    return MutationResult.fail("That would exceed the maximum allowed balance.");
                }
                long balance = before.credits + amount;
                long earned = safeAdd(before.lifetimeEarned, amount);
                updateAccount(connection, playerId, username, balance, earned, before.lifetimeSpent);
                insertLedger(connection, null, playerId, username, "DEPOSIT", amount, balance, reason, serverId);
                syncProfileStats(connection, playerId, balance);
                connection.commit();
                return MutationResult.success(amount, balance, earned, before.lifetimeSpent);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(restoreAutoCommit);
            }
        });
    }

    public static MutationResult withdraw(UUID playerId, String username, long amount, String reason, String serverId) {
        if (playerId == null) return MutationResult.fail("Player not found.");
        if (amount <= 0L) {
            AccountSnapshot snapshot = load(playerId);
            return MutationResult.success(0L, snapshot == null ? 0L : snapshot.credits, snapshot == null ? 0L : snapshot.lifetimeEarned, snapshot == null ? 0L : snapshot.lifetimeSpent);
        }
        return atomic("withdraw credits " + playerId, connection -> {
            boolean restoreAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureSchema(connection);
                AccountSnapshot before = ensureAccountForUpdate(connection, playerId, username);
                if (before.credits < amount) {
                    connection.rollback();
                    return MutationResult.fail("You need " + amount + " cents but only have " + before.credits + " cents.");
                }
                long balance = before.credits - amount;
                long spent = safeAdd(before.lifetimeSpent, amount);
                updateAccount(connection, playerId, username, balance, before.lifetimeEarned, spent);
                insertLedger(connection, null, playerId, username, "WITHDRAW", amount, balance, reason, serverId);
                syncProfileStats(connection, playerId, balance);
                connection.commit();
                return MutationResult.success(amount, balance, before.lifetimeEarned, spent);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(restoreAutoCommit);
            }
        });
    }

    public static MutationResult setBalance(UUID playerId, String username, long amount, String reason, String serverId) {
        if (playerId == null) return MutationResult.fail("Player not found.");
        if (amount < 0L || amount > MAX_BALANCE) return MutationResult.fail("Amount is outside the allowed range.");
        return atomic("set credits " + playerId, connection -> {
            boolean restoreAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureSchema(connection);
                AccountSnapshot before = ensureAccountForUpdate(connection, playerId, username);
                long earned = Math.max(before.lifetimeEarned, amount);
                updateAccount(connection, playerId, username, amount, earned, before.lifetimeSpent);
                insertLedger(connection, null, playerId, username, "SET", amount, amount, reason, serverId);
                syncProfileStats(connection, playerId, amount);
                connection.commit();
                return MutationResult.success(amount, amount, earned, before.lifetimeSpent);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(restoreAutoCommit);
            }
        });
    }

    public static MutationResult transfer(UUID fromId, String fromName, UUID toId, String toName, long amount, String reason, String serverId) {
        if (fromId == null || toId == null) return MutationResult.fail("Player not found.");
        if (fromId.equals(toId)) return MutationResult.fail("You cannot pay yourself.");
        if (amount <= 0L) return MutationResult.fail("Amount must be positive.");
        return atomic("transfer credits " + fromId + " to " + toId, connection -> {
            boolean restoreAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureSchema(connection);
                UUID first = fromId.toString().compareTo(toId.toString()) <= 0 ? fromId : toId;
                UUID second = first.equals(fromId) ? toId : fromId;
                ensureAccountForUpdate(connection, first, first.equals(fromId) ? fromName : toName);
                ensureAccountForUpdate(connection, second, second.equals(fromId) ? fromName : toName);

                AccountSnapshot sender = loadForUpdate(connection, fromId);
                AccountSnapshot receiver = loadForUpdate(connection, toId);
                if (sender.credits < amount) {
                    connection.rollback();
                    return MutationResult.fail("You need " + amount + " cents but only have " + sender.credits + " cents.");
                }
                if (MAX_BALANCE - receiver.credits < amount) {
                    connection.rollback();
                    return MutationResult.fail("The receiving player cannot hold that many Credits.");
                }

                long senderBalance = sender.credits - amount;
                long receiverBalance = receiver.credits + amount;
                long senderSpent = safeAdd(sender.lifetimeSpent, amount);
                long receiverEarned = safeAdd(receiver.lifetimeEarned, amount);
                UUID transferId = UUID.randomUUID();

                updateAccount(connection, fromId, fromName, senderBalance, sender.lifetimeEarned, senderSpent);
                updateAccount(connection, toId, toName, receiverBalance, receiverEarned, receiver.lifetimeSpent);
                insertLedger(connection, transferId, fromId, fromName, "TRANSFER_OUT", amount, senderBalance, reason, serverId);
                insertLedger(connection, transferId, toId, toName, "TRANSFER_IN", amount, receiverBalance, reason, serverId);
                syncProfileStats(connection, fromId, senderBalance);
                syncProfileStats(connection, toId, receiverBalance);
                connection.commit();
                return MutationResult.success(amount, senderBalance, sender.lifetimeEarned, senderSpent);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(restoreAutoCommit);
            }
        });
    }

    private static MutationResult atomic(String description, SqlMutation mutation) {
        if (!DatabaseManager.isEnabled()) return MutationResult.fail("Database is not available.");
        try {
            return DatabaseManager.supplyAsync(description, mutation::run).get(ATOMIC_OPERATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Atomic economy operation failed: " + description);
            e.printStackTrace();
            return MutationResult.fail("Economy database is busy. Please try again.");
        }
    }

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
                "update player_economy set username = ?, credits = ?, lifetime_earned = ?, lifetime_spent = ?, updated_at = now() where uuid = ?"
        )) {
            statement.setString(1, safeUsername(playerId, username));
            statement.setLong(2, Math.max(0L, Math.min(MAX_BALANCE, credits)));
            statement.setLong(3, Math.max(0L, lifetimeEarned));
            statement.setLong(4, Math.max(0L, lifetimeSpent));
            statement.setString(5, playerId.toString());
            statement.executeUpdate();
        }
    }

    private static void insertLedger(Connection connection, UUID transferId, UUID playerId, String username, String type, long amount, long balanceAfter, String reason, String serverId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into economy_ledger (id, transfer_id, uuid, username, type, amount, balance_after, reason, server_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, transferId);
            statement.setString(3, playerId == null ? null : playerId.toString());
            statement.setString(4, safeUsername(playerId, username));
            statement.setString(5, type == null ? "UNKNOWN" : type);
            statement.setLong(6, Math.max(0L, amount));
            statement.setLong(7, Math.max(0L, balanceAfter));
            statement.setString(8, reason == null || reason.isBlank() ? "unspecified" : reason);
            statement.setString(9, serverId == null ? "" : serverId);
            statement.executeUpdate();
        }
    }

    private static void syncProfileStats(Connection connection, UUID profileId, long credits) {
        try (PreparedStatement stats = connection.prepareStatement(
                "insert into profile_player_stats (profile_id, money, updated_at) " +
                        "select ?::uuid, ?, now() where exists (select 1 from player_profiles where id = ?::uuid) " +
                        "on conflict (profile_id) do update set money = excluded.money, updated_at = now()"
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
