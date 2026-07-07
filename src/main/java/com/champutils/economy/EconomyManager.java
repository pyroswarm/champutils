package com.champutils.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.champutils.database.CreditsDatabaseRepository;
import com.champutils.database.DatabaseManager;
import com.champutils.network.NetworkEventManager;
import com.champutils.network.NetworkServerConfig;
import com.champutils.profile.PlayerProfileManager;

import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EconomyManager {

    public static final String CURRENCY_NAME = "Credits";
    public static final String CURRENCY_NAME_SINGULAR = "Credit";

    private static final long MAX_BALANCE = 9_000_000_000_000_000L;
    // Stored as cents so the economy can support values like 1.32 Credits.
    private static final long CURRENCY_SCALE = 100L;
    private static final long STARTING_BALANCE = 25_000L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final NumberFormat FORMAT = NumberFormat.getInstance(Locale.US);

    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "economy.json");
    private static final File TEMP_FILE = new File(DIR, "economy.json.tmp");
    private static final File LEDGER_FILE = new File(DIR, "economy_ledger.jsonl");
    private static final long LEDGER_ROTATE_BYTES = 8L * 1024L * 1024L;
    private static final int LEDGER_ARCHIVES_TO_KEEP = 5;
    private static final DateTimeFormatter LEDGER_ARCHIVE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private static EconomyRoot DATA = new EconomyRoot();
    private static final Set<UUID> SQL_LOADED = new HashSet<>();
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
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (useSqlSourceOfTruth()) {
            ensureSqlLoadedLocked(profileId);
        }
        Account account = getOrCreateLocked(profileId);
        account.username = player.getName().getString();
        account.updatedAt = Instant.now().toString();
        saveLocked();
        if (!useSqlSourceOfTruth()) {
            syncAccountLocked(profileId, account);
        }
    }

    public static synchronized void ensureProfile(UUID profileId, String username) {
        if (profileId == null) {
            return;
        }

        ensureLoadedLocked();
        if (useSqlSourceOfTruth()) {
            ensureSqlLoadedLocked(profileId);
        }
        Account account = getOrCreateLocked(profileId);
        updateUsername(account, username);
        account.updatedAt = Instant.now().toString();
        saveLocked();
        if (!useSqlSourceOfTruth()) {
            syncAccountLocked(profileId, account);
        }
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
        Account account = getOrCreateLocked(PlayerProfileManager.activeProfileId(player));
        account.username = player.getName().getString();
        return account.balance;
    }

    public static synchronized TransactionResult deposit(ServerPlayer player, long amount, String reason) {
        if (player == null) {
            return TransactionResult.fail("Player not found.");
        }

        return deposit(PlayerProfileManager.activeProfileId(player), player.getName().getString(), amount, reason);
    }

    public static synchronized TransactionResult deposit(UUID playerId, String username, long amount, String reason) {
        if (playerId == null) {
            return TransactionResult.fail("Player not found.");
        }

        if (amount <= 0L) {
            return TransactionResult.fail("Amount must be positive.");
        }

        if (useSqlSourceOfTruth()) {
            CreditsDatabaseRepository.MutationResult sql = CreditsDatabaseRepository.deposit(
                    playerId,
                    username,
                    amount,
                    reason,
                    NetworkServerConfig.serverId()
            );
            return applySqlMutation(playerId, username, sql);
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

        return withdraw(PlayerProfileManager.activeProfileId(player), player.getName().getString(), amount, reason);
    }

    public static synchronized TransactionResult withdraw(UUID playerId, String username, long amount, String reason) {
        if (playerId == null) {
            return TransactionResult.fail("Player not found.");
        }

        if (amount <= 0L) {
            return TransactionResult.success(0L, getBalance(playerId));
        }

        if (useSqlSourceOfTruth()) {
            CreditsDatabaseRepository.MutationResult sql = CreditsDatabaseRepository.withdraw(
                    playerId,
                    username,
                    amount,
                    reason,
                    NetworkServerConfig.serverId()
            );
            if (!sql.success && sql.error != null && sql.error.contains("cents")) {
                return TransactionResult.fail("You do not have enough Credits.");
            }
            return applySqlMutation(playerId, username, sql);
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

        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (useSqlSourceOfTruth()) {
            CreditsDatabaseRepository.MutationResult sql = CreditsDatabaseRepository.setBalance(
                    profileId,
                    player.getName().getString(),
                    amount,
                    reason,
                    NetworkServerConfig.serverId()
            );
            return applySqlMutation(profileId, player.getName().getString(), sql);
        }

        ensureLoadedLocked();
        Account account = getOrCreateLocked(profileId);
        account.username = player.getName().getString();
        account.balance = amount;
        account.updatedAt = Instant.now().toString();

        writeLedgerLocked("SET", profileId, account.username, amount, account.balance, reason, null);
        saveLocked();
        syncAccountLocked(profileId, account);

        return TransactionResult.success(amount, account.balance);
    }

    public static synchronized TransactionResult transfer(ServerPlayer from, ServerPlayer to, long amount, String reason) {
        if (from == null || to == null) {
            return TransactionResult.fail("Player not found.");
        }

        UUID fromProfileId = PlayerProfileManager.activeProfileId(from);
        UUID toProfileId = PlayerProfileManager.activeProfileId(to);

        if (fromProfileId.equals(toProfileId)) {
            return TransactionResult.fail("You cannot pay yourself.");
        }

        if (amount <= 0L) {
            return TransactionResult.fail("Amount must be positive.");
        }

        if (useSqlSourceOfTruth()) {
            CreditsDatabaseRepository.MutationResult sql = CreditsDatabaseRepository.transfer(
                    fromProfileId,
                    from.getName().getString(),
                    toProfileId,
                    to.getName().getString(),
                    amount,
                    reason,
                    NetworkServerConfig.serverId()
            );
            if (!sql.success && sql.error != null && sql.error.contains("cents")) {
                return TransactionResult.fail("You do not have enough Credits.");
            }
            if (sql.success) {
                invalidateSharedCache(toProfileId);
                NetworkEventManager.publishCacheInvalidation("ECONOMY", toProfileId);
            }
            return applySqlMutation(fromProfileId, from.getName().getString(), sql);
        }

        ensureLoadedLocked();

        Account sender = getOrCreateLocked(fromProfileId);
        Account receiver = getOrCreateLocked(toProfileId);
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

        writeLedgerLocked("TRANSFER_OUT", fromProfileId, sender.username, amount, sender.balance, reason, transferId);
        writeLedgerLocked("TRANSFER_IN", toProfileId, receiver.username, amount, receiver.balance, reason, transferId);
        saveLocked();
        syncAccountLocked(fromProfileId, sender);
        syncAccountLocked(toProfileId, receiver);

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

    public static long wholeCreditsToCents(long credits) {
        if (credits <= 0L) return 0L;
        if (credits >= MAX_BALANCE / CURRENCY_SCALE) return MAX_BALANCE;
        return credits * CURRENCY_SCALE;
    }

    public static String formatWholeCredits(long credits) {
        long safe = Math.max(0L, credits);
        return FORMAT.format(safe) + " " + (safe == 1L ? CURRENCY_NAME_SINGULAR : CURRENCY_NAME);
    }

    public static long creditsToCents(double credits) {
        if (Double.isNaN(credits) || Double.isInfinite(credits) || credits <= 0.0D) {
            return 0L;
        }
        double cents = credits * CURRENCY_SCALE;
        if (cents >= MAX_BALANCE) {
            return MAX_BALANCE;
        }
        return Math.max(0L, Math.round(cents));
    }

    public static String format(long amount) {
        long safe = Math.max(0L, amount);
        long whole = safe / CURRENCY_SCALE;
        long cents = safe % CURRENCY_SCALE;
        String value = FORMAT.format(whole) + "." + (cents < 10L ? "0" : "") + cents;
        return value + " " + (safe == CURRENCY_SCALE ? CURRENCY_NAME_SINGULAR : CURRENCY_NAME);
    }

    private static void ensureLoadedLocked() {
        if (!loaded) {
            load();
        }
    }

    private static Account getOrCreateLocked(UUID playerId) {
        sanitizeRoot();
        ensureSqlLoadedLocked(playerId);
        return DATA.players.computeIfAbsent(playerId.toString(), ignored -> new Account());
    }

    private static void ensureSqlLoadedLocked(UUID playerId) {
        if (playerId == null || !SQL_LOADED.add(playerId)) {
            return;
        }
        CreditsDatabaseRepository.AccountSnapshot snapshot = CreditsDatabaseRepository.load(playerId);
        if (snapshot == null) {
            return;
        }
        Account account = DATA.players.computeIfAbsent(playerId.toString(), ignored -> new Account());
        account.username = snapshot.username;
        account.balance = Math.max(0L, Math.min(MAX_BALANCE, snapshot.credits));
        account.lifetimeEarned = Math.max(account.balance, snapshot.lifetimeEarned);
        account.lifetimeSpent = Math.max(0L, snapshot.lifetimeSpent);
        account.updatedAt = Instant.now().toString();
        saveLocked();
    }

    private static boolean useSqlSourceOfTruth() {
        try {
            NetworkServerConfig config = NetworkServerConfig.get();
            return DatabaseManager.isEnabled() && config.databaseIsSourceOfTruth;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static TransactionResult applySqlMutation(UUID playerId, String username, CreditsDatabaseRepository.MutationResult sql) {
        if (sql == null || !sql.success) {
            String error = sql == null ? "Economy database is unavailable. Please try again." : sql.error;
            return TransactionResult.fail(error == null ? "Economy database is unavailable. Please try again." : error);
        }
        ensureLoadedLocked();
        Account account = DATA.players.computeIfAbsent(playerId.toString(), ignored -> new Account());
        updateUsername(account, username);
        account.balance = Math.max(0L, Math.min(MAX_BALANCE, sql.newBalance));
        account.lifetimeEarned = Math.max(0L, sql.lifetimeEarned);
        account.lifetimeSpent = Math.max(0L, sql.lifetimeSpent);
        account.updatedAt = Instant.now().toString();
        SQL_LOADED.add(playerId);
        writeLedgerLocked("SQL_COMMIT", playerId, username, sql.amount, account.balance, "database_source_of_truth", null);
        saveLocked();
        NetworkEventManager.publishCacheInvalidation("ECONOMY", playerId);
        return TransactionResult.success(sql.amount, account.balance);
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

            rotateLedgerIfNeededLocked();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(LEDGER_FILE, true))) {
                writer.write(GSON.toJson(entry));
                writer.newLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void rotateLedgerIfNeededLocked() {
        try {
            if (!LEDGER_FILE.exists() || LEDGER_FILE.length() < LEDGER_ROTATE_BYTES) return;

            String stamp = LEDGER_ARCHIVE_FORMAT.format(Instant.now());
            File archive = new File(DIR, "economy_ledger-" + stamp + ".jsonl");
            int suffix = 1;
            while (archive.exists()) {
                archive = new File(DIR, "economy_ledger-" + stamp + "-" + suffix++ + ".jsonl");
            }

            Files.move(LEDGER_FILE.toPath(), archive.toPath(), StandardCopyOption.ATOMIC_MOVE);
            pruneLedgerArchivesLocked();
        } catch (Exception atomicMoveFailed) {
            try {
                String stamp = LEDGER_ARCHIVE_FORMAT.format(Instant.now());
                File archive = new File(DIR, "economy_ledger-" + stamp + ".jsonl");
                Files.move(LEDGER_FILE.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
                pruneLedgerArchivesLocked();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void pruneLedgerArchivesLocked() {
        File[] archives = DIR.listFiles((dir, name) -> name.startsWith("economy_ledger-") && name.endsWith(".jsonl"));
        if (archives == null || archives.length <= LEDGER_ARCHIVES_TO_KEEP) return;
        Arrays.sort(archives, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = LEDGER_ARCHIVES_TO_KEEP; i < archives.length; i++) {
            try {
                Files.deleteIfExists(archives[i].toPath());
            } catch (Exception ignored) {
            }
        }
    }

    private static final class EconomyRoot {
        private Map<String, Account> players = new HashMap<>();
    }

    private static final class Account {
        private String username = "";
        private long balance = STARTING_BALANCE;
        private long lifetimeEarned = STARTING_BALANCE;
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
        if (useSqlSourceOfTruth()) {
            return;
        }
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
        NetworkEventManager.publishCacheInvalidation("ECONOMY", playerId);
    }

    public static synchronized void invalidateSharedCache(UUID playerId) {
        if (playerId == null) {
            return;
        }
        SQL_LOADED.remove(playerId);
        if (DATA != null && DATA.players != null) {
            DATA.players.remove(playerId.toString());
        }
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
