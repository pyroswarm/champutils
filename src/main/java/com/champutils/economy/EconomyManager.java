package com.champutils.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.champutils.database.CreditsDatabaseRepository;

import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class EconomyManager {

    public static final String CURRENCY_NAME = "Credits";
    public static final String CURRENCY_NAME_SINGULAR = "Credit";

    private static final long MAX_BALANCE = 9_000_000_000_000_000L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final NumberFormat FORMAT = NumberFormat.getInstance(Locale.US);

    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "economy.json");
    private static final File TEMP_FILE = new File(DIR, "economy.json.tmp");
    private static final File LEDGER_FILE = new File(DIR, "economy_ledger.jsonl");

    private static EconomyRoot DATA = new EconomyRoot();
    private static boolean loaded = false;

    private EconomyManager() {
    }

    public static synchronized void load() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            if (!FILE.exists()) {
                DATA = new EconomyRoot();
                loaded = true;
                saveLocked();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                EconomyRoot loadedData = GSON.fromJson(reader, EconomyRoot.class);
                DATA = loadedData == null ? new EconomyRoot() : loadedData;
            }

            sanitizeRoot();
            loaded = true;
        } catch (Exception e) {
            e.printStackTrace();
            DATA = new EconomyRoot();
            loaded = true;
        }
    }

    public static synchronized void save() {
        ensureLoadedLocked();
        saveLocked();
    }

    public static synchronized void ensurePlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }

        ensureLoadedLocked();
        Account account = getOrCreateLocked(player.getUUID());
        account.username = player.getName().getString();
        account.updatedAt = Instant.now().toString();
        saveLocked();
        syncAccountLocked(player.getUUID(), account);
    }

    public static synchronized long getBalance(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }

        ensureLoadedLocked();
        return getOrCreateLocked(playerId).balance;
    }

    public static synchronized long getBalance(ServerPlayer player) {
        if (player == null) {
            return 0L;
        }

        ensureLoadedLocked();
        Account account = getOrCreateLocked(player.getUUID());
        account.username = player.getName().getString();
        return account.balance;
    }

    public static synchronized TransactionResult deposit(ServerPlayer player, long amount, String reason) {
        if (player == null) {
            return TransactionResult.fail("Player not found.");
        }

        return deposit(player.getUUID(), player.getName().getString(), amount, reason);
    }

    public static synchronized TransactionResult deposit(UUID playerId, String username, long amount, String reason) {
        if (playerId == null) {
            return TransactionResult.fail("Player not found.");
        }

        if (amount <= 0L) {
            return TransactionResult.fail("Amount must be positive.");
        }

        ensureLoadedLocked();
        Account account = getOrCreateLocked(playerId);
        updateUsername(account, username);

        if (MAX_BALANCE - account.balance < amount) {
            return TransactionResult.fail("That would exceed the maximum allowed balance.");
        }

        account.balance += amount;
        account.lifetimeEarned = safeAdd(account.lifetimeEarned, amount);
        account.updatedAt = Instant.now().toString();

        writeLedgerLocked("DEPOSIT", playerId, username, amount, account.balance, reason, null);
        saveLocked();
        syncAccountLocked(playerId, account);

        return TransactionResult.success(amount, account.balance);
    }

    public static synchronized TransactionResult withdraw(ServerPlayer player, long amount, String reason) {
        if (player == null) {
            return TransactionResult.fail("Player not found.");
        }

        return withdraw(player.getUUID(), player.getName().getString(), amount, reason);
    }

    public static synchronized TransactionResult withdraw(UUID playerId, String username, long amount, String reason) {
        if (playerId == null) {
            return TransactionResult.fail("Player not found.");
        }

        if (amount <= 0L) {
            return TransactionResult.success(0L, getBalance(playerId));
        }

        ensureLoadedLocked();
        Account account = getOrCreateLocked(playerId);
        updateUsername(account, username);

        if (account.balance < amount) {
            return TransactionResult.fail(
                    "You need " + format(amount) + " but only have " + format(account.balance) + "."
            );
        }

        account.balance -= amount;
        account.lifetimeSpent = safeAdd(account.lifetimeSpent, amount);
        account.updatedAt = Instant.now().toString();

        writeLedgerLocked("WITHDRAW", playerId, username, amount, account.balance, reason, null);
        saveLocked();
        syncAccountLocked(playerId, account);

        return TransactionResult.success(amount, account.balance);
    }

    public static synchronized TransactionResult setBalance(ServerPlayer player, long amount, String reason) {
        if (player == null) {
            return TransactionResult.fail("Player not found.");
        }

        if (amount < 0L || amount > MAX_BALANCE) {
            return TransactionResult.fail("Amount must be between 0 and " + MAX_BALANCE + ".");
        }

        ensureLoadedLocked();
        Account account = getOrCreateLocked(player.getUUID());
        account.username = player.getName().getString();
        account.balance = amount;
        account.updatedAt = Instant.now().toString();

        writeLedgerLocked("SET", player.getUUID(), account.username, amount, account.balance, reason, null);
        saveLocked();
        syncAccountLocked(player.getUUID(), account);

        return TransactionResult.success(amount, account.balance);
    }

    public static synchronized TransactionResult transfer(ServerPlayer from, ServerPlayer to, long amount, String reason) {
        if (from == null || to == null) {
            return TransactionResult.fail("Player not found.");
        }

        if (from.getUUID().equals(to.getUUID())) {
            return TransactionResult.fail("You cannot pay yourself.");
        }

        if (amount <= 0L) {
            return TransactionResult.fail("Amount must be positive.");
        }

        ensureLoadedLocked();

        Account sender = getOrCreateLocked(from.getUUID());
        Account receiver = getOrCreateLocked(to.getUUID());
        sender.username = from.getName().getString();
        receiver.username = to.getName().getString();

        if (sender.balance < amount) {
            return TransactionResult.fail(
                    "You need " + format(amount) + " but only have " + format(sender.balance) + "."
            );
        }

        if (MAX_BALANCE - receiver.balance < amount) {
            return TransactionResult.fail("The receiving player cannot hold that many Credits.");
        }

        String transferId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        sender.balance -= amount;
        sender.lifetimeSpent = safeAdd(sender.lifetimeSpent, amount);
        sender.updatedAt = now;

        receiver.balance += amount;
        receiver.lifetimeEarned = safeAdd(receiver.lifetimeEarned, amount);
        receiver.updatedAt = now;

        writeLedgerLocked("TRANSFER_OUT", from.getUUID(), sender.username, amount, sender.balance, reason, transferId);
        writeLedgerLocked("TRANSFER_IN", to.getUUID(), receiver.username, amount, receiver.balance, reason, transferId);
        saveLocked();
        syncAccountLocked(from.getUUID(), sender);
        syncAccountLocked(to.getUUID(), receiver);

        return TransactionResult.success(amount, sender.balance);
    }

    public static synchronized boolean canAfford(ServerPlayer player, long amount) {
        if (player == null) {
            return false;
        }

        if (amount <= 0L) {
            return true;
        }

        return getBalance(player) >= amount;
    }

    public static String format(long amount) {
        return FORMAT.format(Math.max(0L, amount)) + " " + (amount == 1L ? CURRENCY_NAME_SINGULAR : CURRENCY_NAME);
    }

    private static void ensureLoadedLocked() {
        if (!loaded) {
            load();
        }
    }

    private static Account getOrCreateLocked(UUID playerId) {
        sanitizeRoot();
        return DATA.players.computeIfAbsent(playerId.toString(), ignored -> new Account());
    }

    private static void updateUsername(Account account, String username) {
        if (account != null && username != null && !username.isBlank()) {
            account.username = username;
        }
    }

    private static void sanitizeRoot() {
        if (DATA == null) {
            DATA = new EconomyRoot();
        }
        if (DATA.players == null) {
            DATA.players = new HashMap<>();
        }
        DATA.players.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        for (Account account : DATA.players.values()) {
            if (account.balance < 0L) account.balance = 0L;
            if (account.balance > MAX_BALANCE) account.balance = MAX_BALANCE;
            if (account.lifetimeEarned < 0L) account.lifetimeEarned = 0L;
            if (account.lifetimeSpent < 0L) account.lifetimeSpent = 0L;
            if (account.createdAt == null || account.createdAt.isBlank()) account.createdAt = Instant.now().toString();
            if (account.updatedAt == null || account.updatedAt.isBlank()) account.updatedAt = account.createdAt;
        }
    }

    private static long safeAdd(long current, long amount) {
        if (amount <= 0L) {
            return current;
        }
        if (Long.MAX_VALUE - current < amount) {
            return Long.MAX_VALUE;
        }
        return current + amount;
    }

    private static void saveLocked() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            try (FileWriter writer = new FileWriter(TEMP_FILE)) {
                GSON.toJson(DATA, writer);
            }

            Files.move(
                    TEMP_FILE.toPath(),
                    FILE.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (Exception atomicMoveFailed) {
            try {
                Files.move(
                        TEMP_FILE.toPath(),
                        FILE.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void writeLedgerLocked(
            String type,
            UUID playerId,
            String username,
            long amount,
            long balanceAfter,
            String reason,
            String transferId
    ) {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            LedgerEntry entry = new LedgerEntry();
            entry.id = UUID.randomUUID().toString();
            entry.transferId = transferId;
            entry.type = type;
            entry.uuid = playerId == null ? null : playerId.toString();
            entry.username = username;
            entry.amount = amount;
            entry.balanceAfter = balanceAfter;
            entry.reason = reason == null ? "unspecified" : reason;
            entry.createdAt = Instant.now().toString();

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(LEDGER_FILE, true))) {
                writer.write(GSON.toJson(entry));
                writer.newLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final class EconomyRoot {
        private Map<String, Account> players = new HashMap<>();
    }

    private static final class Account {
        private String username = "";
        private long balance = 0L;
        private long lifetimeEarned = 0L;
        private long lifetimeSpent = 0L;
        private String createdAt = Instant.now().toString();
        private String updatedAt = Instant.now().toString();
    }

    private static final class LedgerEntry {
        private String id;
        private String transferId;
        private String type;
        private String uuid;
        private String username;
        private long amount;
        private long balanceAfter;
        private String reason;
        private String createdAt;
    }

    public static synchronized void syncAllToDatabase() {
        ensureLoadedLocked();
        sanitizeRoot();

        for (Map.Entry<String, Account> entry : DATA.players.entrySet()) {
            try {
                UUID playerId = UUID.fromString(entry.getKey());
                Account account = entry.getValue();
                CreditsDatabaseRepository.sync(
                        playerId,
                        account.username,
                        account.balance,
                        account.lifetimeEarned,
                        account.lifetimeSpent
                );
            } catch (Exception ignored) {
            }
        }
    }

    private static void syncAccountLocked(UUID playerId, Account account) {
        if (playerId == null || account == null) {
            return;
        }

        CreditsDatabaseRepository.sync(
                playerId,
                account.username,
                account.balance,
                account.lifetimeEarned,
                account.lifetimeSpent
        );
    }

    public static final class TransactionResult {
        public final boolean success;
        public final String error;
        public final long amount;
        public final long newBalance;

        private TransactionResult(boolean success, String error, long amount, long newBalance) {
            this.success = success;
            this.error = error;
            this.amount = amount;
            this.newBalance = newBalance;
        }

        public static TransactionResult success(long amount, long newBalance) {
            return new TransactionResult(true, null, amount, newBalance);
        }

        public static TransactionResult fail(String error) {
            return new TransactionResult(false, error, 0L, 0L);
        }
    }
}
