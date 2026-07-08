package com.champutils.commerce;

import com.champutils.database.DatabaseManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AccountCommerceRepository {
    private AccountCommerceRepository() {}

    public record ResolvedAccount(UUID accountUuid, String username, boolean online) {}
    public record VoteBalance(long points, long lifetimePoints) {}
    public record BoosterCreditGrantResult(ResolvedAccount account, boolean inserted, int balance) {}
    public record CosmeticGrantResult(ResolvedAccount account, boolean unlocked) {}

    public static CompletableFuture<Void> ensureSchemaAsync() {
        return DatabaseManager.runAsync("account commerce schema", AccountCommerceRepository::ensureSchema)
                .exceptionally(error -> {
                    System.err.println("[ChampUtils] Account commerce schema setup failed: " + error.getMessage());
                    return null;
                });
    }

    public static void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS public.account_purchase_ledger (" +
                    "id uuid PRIMARY KEY DEFAULT gen_random_uuid()," +
                    "account_uuid uuid NOT NULL," +
                    "minecraft_username text NOT NULL DEFAULT ''," +
                    "source text NOT NULL DEFAULT 'TEBEX'," +
                    "package_key text NOT NULL," +
                    "quantity integer NOT NULL DEFAULT 1," +
                    "reference text NOT NULL DEFAULT ''," +
                    "note text NOT NULL DEFAULT ''," +
                    "created_at timestamptz NOT NULL DEFAULT now()" +
                    ")");
            statement.execute("ALTER TABLE public.account_purchase_ledger ADD COLUMN IF NOT EXISTS account_uuid uuid NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000'");
            statement.execute("ALTER TABLE public.account_purchase_ledger ADD COLUMN IF NOT EXISTS minecraft_username text NOT NULL DEFAULT ''");
            statement.execute("ALTER TABLE public.account_purchase_ledger ADD COLUMN IF NOT EXISTS source text NOT NULL DEFAULT 'TEBEX'");
            statement.execute("ALTER TABLE public.account_purchase_ledger ADD COLUMN IF NOT EXISTS package_key text NOT NULL DEFAULT ''");
            statement.execute("ALTER TABLE public.account_purchase_ledger ADD COLUMN IF NOT EXISTS quantity integer NOT NULL DEFAULT 1");
            statement.execute("ALTER TABLE public.account_purchase_ledger ADD COLUMN IF NOT EXISTS reference text NOT NULL DEFAULT ''");
            statement.execute("ALTER TABLE public.account_purchase_ledger ADD COLUMN IF NOT EXISTS note text NOT NULL DEFAULT ''");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS account_purchase_ledger_unique_ref ON public.account_purchase_ledger (source, reference, package_key, account_uuid) WHERE reference <> ''");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_account_purchase_ledger_account ON public.account_purchase_ledger (account_uuid, created_at DESC)");
            com.champutils.cashshop.BoosterCreditManager.ensureSchema(connection);
            statement.execute("CREATE TABLE IF NOT EXISTS public.account_booster_credit_grants (" +
                    "id uuid PRIMARY KEY DEFAULT gen_random_uuid()," +
                    "account_uuid uuid NOT NULL," +
                    "minecraft_username text NOT NULL DEFAULT ''," +
                    "source text NOT NULL DEFAULT 'TEBEX'," +
                    "reference text NOT NULL DEFAULT ''," +
                    "amount integer NOT NULL," +
                    "created_at timestamptz NOT NULL DEFAULT now()" +
                    ")");
            statement.execute("ALTER TABLE public.account_booster_credit_grants ADD COLUMN IF NOT EXISTS account_uuid uuid NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000'");
            statement.execute("ALTER TABLE public.account_booster_credit_grants ADD COLUMN IF NOT EXISTS minecraft_username text NOT NULL DEFAULT ''");
            statement.execute("ALTER TABLE public.account_booster_credit_grants ADD COLUMN IF NOT EXISTS source text NOT NULL DEFAULT 'TEBEX'");
            statement.execute("ALTER TABLE public.account_booster_credit_grants ADD COLUMN IF NOT EXISTS reference text NOT NULL DEFAULT ''");
            statement.execute("ALTER TABLE public.account_booster_credit_grants ADD COLUMN IF NOT EXISTS amount integer NOT NULL DEFAULT 0");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS account_booster_credit_grants_unique_ref ON public.account_booster_credit_grants (source, reference, account_uuid) WHERE reference <> ''");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_account_booster_credit_grants_account ON public.account_booster_credit_grants (account_uuid, created_at DESC)");

            statement.execute("CREATE TABLE IF NOT EXISTS public.account_cosmetic_unlocks (" +
                    "account_uuid uuid NOT NULL," +
                    "cosmetic_type text NOT NULL," +
                    "cosmetic_id text NOT NULL," +
                    "source text NOT NULL DEFAULT 'TEBEX'," +
                    "reference text NOT NULL DEFAULT ''," +
                    "unlocked_at timestamptz NOT NULL DEFAULT now()," +
                    "PRIMARY KEY (account_uuid, cosmetic_type, cosmetic_id)" +
                    ")");
            statement.execute("ALTER TABLE public.account_cosmetic_unlocks ADD COLUMN IF NOT EXISTS source text NOT NULL DEFAULT 'TEBEX'");
            statement.execute("ALTER TABLE public.account_cosmetic_unlocks ADD COLUMN IF NOT EXISTS reference text NOT NULL DEFAULT ''");
            statement.execute("ALTER TABLE public.account_cosmetic_unlocks ADD COLUMN IF NOT EXISTS unlocked_at timestamptz NOT NULL DEFAULT now()");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_account_cosmetic_unlocks_account ON public.account_cosmetic_unlocks (account_uuid, cosmetic_type)");

            statement.execute("CREATE TABLE IF NOT EXISTS public.account_cosmetic_settings (" +
                    "account_uuid uuid NOT NULL," +
                    "cosmetic_type text NOT NULL," +
                    "cosmetic_id text NOT NULL DEFAULT ''," +
                    "updated_at timestamptz NOT NULL DEFAULT now()," +
                    "PRIMARY KEY (account_uuid, cosmetic_type)" +
                    ")");
            statement.execute("ALTER TABLE public.account_cosmetic_settings ADD COLUMN IF NOT EXISTS cosmetic_id text NOT NULL DEFAULT ''");
            statement.execute("ALTER TABLE public.account_cosmetic_settings ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now()");

            statement.execute("CREATE TABLE IF NOT EXISTS public.account_vote_ledger (" +
                    "id uuid PRIMARY KEY DEFAULT gen_random_uuid()," +
                    "account_uuid uuid NOT NULL," +
                    "minecraft_username text NOT NULL DEFAULT ''," +
                    "service_name text NOT NULL DEFAULT ''," +
                    "vote_day date NOT NULL DEFAULT ((now() at time zone 'utc')::date)," +
                    "address_hash text NOT NULL DEFAULT ''," +
                    "points integer NOT NULL DEFAULT 1," +
                    "created_at timestamptz NOT NULL DEFAULT now()" +
                    ")");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS account_vote_ledger_account_service_day_unique ON public.account_vote_ledger (account_uuid, lower(service_name), vote_day)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_account_vote_ledger_account ON public.account_vote_ledger (account_uuid, created_at DESC)");

            statement.execute("CREATE TABLE IF NOT EXISTS public.account_vote_balances (" +
                    "account_uuid uuid PRIMARY KEY," +
                    "points bigint NOT NULL DEFAULT 0," +
                    "lifetime_points bigint NOT NULL DEFAULT 0," +
                    "updated_at timestamptz NOT NULL DEFAULT now()" +
                    ")");

            statement.execute("CREATE TABLE IF NOT EXISTS public.account_vote_unresolved (" +
                    "id uuid PRIMARY KEY DEFAULT gen_random_uuid()," +
                    "minecraft_username text NOT NULL," +
                    "service_name text NOT NULL DEFAULT ''," +
                    "address_hash text NOT NULL DEFAULT ''," +
                    "created_at timestamptz NOT NULL DEFAULT now()," +
                    "metadata jsonb NOT NULL DEFAULT '{}'::jsonb" +
                    ")");
        }
    }

    public static CompletableFuture<ResolvedAccount> resolveAccountAsync(MinecraftServer server, String rawPlayer) {
        String playerName = rawPlayer == null ? "" : rawPlayer.trim();
        ServerPlayer online = findOnline(server, playerName);
        if (online != null) {
            return CompletableFuture.completedFuture(new ResolvedAccount(online.getUUID(), online.getGameProfile().getName(), true));
        }
        return DatabaseManager.supplyAsync("resolve account commerce player", connection -> resolveAccount(connection, playerName));
    }

    private static ServerPlayer findOnline(MinecraftServer server, String rawPlayer) {
        if (server == null || rawPlayer == null || rawPlayer.isBlank()) return null;
        try {
            UUID uuid = UUID.fromString(rawPlayer.trim());
            ServerPlayer byUuid = server.getPlayerList().getPlayer(uuid);
            if (byUuid != null) return byUuid;
        } catch (Exception ignored) {
        }
        return server.getPlayerList().getPlayerByName(rawPlayer.trim());
    }

    public static ResolvedAccount resolveAccount(Connection connection, String rawPlayer) throws SQLException {
        if (rawPlayer == null || rawPlayer.isBlank()) return null;
        String input = rawPlayer.trim();
        UUID uuid = tryUuid(input);
        if (uuid != null) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, username FROM public.players WHERE uuid = ? LIMIT 1")) {
                statement.setObject(1, uuid);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) return new ResolvedAccount((UUID) rs.getObject(1), rs.getString(2), false);
                }
            }
            return new ResolvedAccount(uuid, input, false);
        }

        try (PreparedStatement statement = connection.prepareStatement("SELECT minecraft_uuid, minecraft_username FROM public.player_accounts WHERE lower(minecraft_username) = lower(?) ORDER BY updated_at DESC LIMIT 1")) {
            statement.setString(1, input);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return new ResolvedAccount((UUID) rs.getObject(1), rs.getString(2), false);
            }
        } catch (SQLException ignored) {
            // Some older database snapshots may not have player_accounts yet. Fall through to players.
        }

        try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, username FROM public.players WHERE lower(username) = lower(?) ORDER BY last_seen DESC LIMIT 1")) {
            statement.setString(1, input);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return new ResolvedAccount((UUID) rs.getObject(1), rs.getString(2), false);
            }
        }
        return null;
    }

    public static CompletableFuture<Boolean> recordPurchaseAsync(ResolvedAccount account, String source, String packageKey, int quantity, String reference, String note) {
        if (account == null || account.accountUuid() == null) return CompletableFuture.completedFuture(false);
        return DatabaseManager.supplyAsync("record account purchase", connection -> recordPurchase(connection, account, source, packageKey, quantity, reference, note));
    }

    public static boolean recordPurchase(Connection connection, ResolvedAccount account, String source, String packageKey, int quantity, String reference, String note) throws SQLException {
        String safeSource = safe(source, "TEBEX", 64).toUpperCase(Locale.ROOT);
        String safePackage = safe(packageKey, "unknown", 128);
        String safeReference = safe(reference, "", 128);
        String safeNote = safe(note, "", 256);
        int safeQuantity = Math.max(1, Math.min(quantity, 1_000_000));
        String sql = "INSERT INTO public.account_purchase_ledger (account_uuid, minecraft_username, source, package_key, quantity, reference, note) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, account.accountUuid());
            statement.setString(2, safe(account.username(), "", 64));
            statement.setString(3, safeSource);
            statement.setString(4, safePackage);
            statement.setInt(5, safeQuantity);
            statement.setString(6, safeReference);
            statement.setString(7, safeNote);
            return statement.executeUpdate() > 0;
        }
    }

    public static CompletableFuture<BoosterCreditGrantResult> grantBoosterCreditsAsync(ResolvedAccount account, int amount, String reference) {
        return grantBoosterCreditsAsync(account, amount, "TEBEX", reference, "Purchased booster credits");
    }

    public static CompletableFuture<BoosterCreditGrantResult> grantBoosterCreditsAsync(ResolvedAccount account, int amount, String source, String reference, String note) {
        if (account == null || account.accountUuid() == null || amount <= 0) {
            return CompletableFuture.completedFuture(new BoosterCreditGrantResult(account, false, 0));
        }
        int safeAmount = Math.max(1, Math.min(amount, 1_000_000));
        String safeSource = safe(source, "TEBEX", 64).toUpperCase(Locale.ROOT);
        String safeReference = safe(reference, "", 128);
        String safeNote = safe(note, "", 256);
        return DatabaseManager.supplyAsync("grant Tebex booster credits", connection -> {
            boolean oldAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                ensureSchema(connection);
                recordPurchase(connection, account, safeSource, "boostercredits", safeAmount, safeReference, safeNote);
                int balance = com.champutils.cashshop.BoosterCreditManager.currentPurchasedCredits(connection, account.accountUuid());
                boolean inserted = recordBoosterCreditGrant(connection, account, safeSource, safeReference, safeAmount);
                if (inserted) {
                    balance = com.champutils.cashshop.BoosterCreditManager.addPurchasedCredits(connection, account.accountUuid(), safeAmount);
                }
                connection.commit();
                return new BoosterCreditGrantResult(account, inserted, balance);
            } catch (Exception e) {
                try { connection.rollback(); } catch (Exception ignored) {}
                throw e;
            } finally {
                try { connection.setAutoCommit(oldAutoCommit); } catch (Exception ignored) {}
            }
        });
    }

    public static CompletableFuture<CosmeticGrantResult> unlockCosmeticAsync(ResolvedAccount account, String cosmeticType, String cosmeticId, String source, String reference, String note) {
        if (account == null || account.accountUuid() == null || cosmeticType == null || cosmeticType.isBlank() || cosmeticId == null || cosmeticId.isBlank()) {
            return CompletableFuture.completedFuture(new CosmeticGrantResult(account, false));
        }
        String type = safe(cosmeticType, "", 64).toLowerCase(Locale.ROOT);
        String id = safe(cosmeticId, "", 128).toLowerCase(Locale.ROOT);
        String safeSource = safe(source, "TEBEX", 64).toUpperCase(Locale.ROOT);
        String safeReference = safe(reference, "", 128);
        String safeNote = safe(note, "", 256);
        return DatabaseManager.supplyAsync("unlock account cosmetic", connection -> {
            boolean oldAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                ensureSchema(connection);
                recordPurchase(connection, account, safeSource, type + "_" + id, 1, safeReference, safeNote);
                boolean unlocked;
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO public.account_cosmetic_unlocks (account_uuid, cosmetic_type, cosmetic_id, source, reference) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING")) {
                    statement.setObject(1, account.accountUuid());
                    statement.setString(2, type);
                    statement.setString(3, id);
                    statement.setString(4, safeSource);
                    statement.setString(5, safeReference);
                    unlocked = statement.executeUpdate() > 0;
                }
                connection.commit();
                return new CosmeticGrantResult(account, unlocked);
            } catch (Exception e) {
                try { connection.rollback(); } catch (Exception ignored) {}
                throw e;
            } finally {
                try { connection.setAutoCommit(oldAutoCommit); } catch (Exception ignored) {}
            }
        });
    }

    public static CompletableFuture<java.util.Set<String>> loadCosmeticsAsync(UUID accountUuid, String cosmeticType) {
        if (accountUuid == null || cosmeticType == null || cosmeticType.isBlank()) return CompletableFuture.completedFuture(java.util.Set.of());
        String type = safe(cosmeticType, "", 64).toLowerCase(Locale.ROOT);
        return DatabaseManager.supplyAsync("load account cosmetics", connection -> {
            ensureSchema(connection);
            java.util.Set<String> out = new java.util.LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT cosmetic_id FROM public.account_cosmetic_unlocks WHERE account_uuid = ? AND cosmetic_type = ? ORDER BY unlocked_at ASC")) {
                statement.setObject(1, accountUuid);
                statement.setString(2, type);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) out.add(rs.getString(1));
                }
            }
            return out;
        }).exceptionally(error -> java.util.Set.of());
    }

    public static CompletableFuture<String> loadSelectedCosmeticAsync(UUID accountUuid, String cosmeticType) {
        if (accountUuid == null || cosmeticType == null || cosmeticType.isBlank()) return CompletableFuture.completedFuture("");
        String type = safe(cosmeticType, "", 64).toLowerCase(Locale.ROOT);
        return DatabaseManager.supplyAsync("load selected account cosmetic", connection -> {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("SELECT cosmetic_id FROM public.account_cosmetic_settings WHERE account_uuid = ? AND cosmetic_type = ?")) {
                statement.setObject(1, accountUuid);
                statement.setString(2, type);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? safe(rs.getString(1), "", 128).toLowerCase(Locale.ROOT) : "";
                }
            }
        }).exceptionally(error -> "");
    }

    public static void selectCosmeticAsync(UUID accountUuid, String cosmeticType, String cosmeticId) {
        if (accountUuid == null || cosmeticType == null || cosmeticType.isBlank()) return;
        String type = safe(cosmeticType, "", 64).toLowerCase(Locale.ROOT);
        String id = safe(cosmeticId, "", 128).toLowerCase(Locale.ROOT);
        DatabaseManager.executeCoalescedAsync("account-cosmetic-setting:" + accountUuid + ":" + type, "save account cosmetic setting", connection -> {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO public.account_cosmetic_settings (account_uuid, cosmetic_type, cosmetic_id, updated_at) VALUES (?, ?, ?, now()) ON CONFLICT (account_uuid, cosmetic_type) DO UPDATE SET cosmetic_id = EXCLUDED.cosmetic_id, updated_at = now()")) {
                statement.setObject(1, accountUuid);
                statement.setString(2, type);
                statement.setString(3, id);
                statement.executeUpdate();
            }
        });
    }

    private static boolean recordBoosterCreditGrant(Connection connection, ResolvedAccount account, String source, String reference, int amount) throws SQLException {
        String safeSource = safe(source, "TEBEX", 64).toUpperCase(Locale.ROOT);
        String safeReference = safe(reference, "", 128);
        int safeAmount = Math.max(1, Math.min(amount, 1_000_000));
        String sql = "INSERT INTO public.account_booster_credit_grants (account_uuid, minecraft_username, source, reference, amount) " +
                "VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, account.accountUuid());
            statement.setString(2, safe(account.username(), "", 64));
            statement.setString(3, safeSource);
            statement.setString(4, safeReference);
            statement.setInt(5, safeAmount);
            return statement.executeUpdate() > 0;
        }
    }

    public static CompletableFuture<Integer> recordVoteAsync(ResolvedAccount account, String serviceName, String addressHash, int points) {
        if (account == null || account.accountUuid() == null || points <= 0) return CompletableFuture.completedFuture(0);
        return DatabaseManager.supplyAsync("record account vote", connection -> recordVote(connection, account, serviceName, addressHash, points));
    }

    public static int recordVote(Connection connection, ResolvedAccount account, String serviceName, String addressHash, int points) throws SQLException {
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        int safePoints = Math.max(0, Math.min(points, 1000));
        String sql = "INSERT INTO public.account_vote_ledger (account_uuid, minecraft_username, service_name, vote_day, address_hash, points) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING";
        boolean inserted;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, account.accountUuid());
            statement.setString(2, safe(account.username(), "", 64));
            statement.setString(3, safe(serviceName, "", 128));
            statement.setDate(4, Date.valueOf(day));
            statement.setString(5, safe(addressHash, "", 128));
            statement.setInt(6, safePoints);
            inserted = statement.executeUpdate() > 0;
        }
        if (!inserted) return 0;
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO public.account_vote_balances (account_uuid, points, lifetime_points, updated_at) " +
                "VALUES (?, ?, ?, now()) ON CONFLICT (account_uuid) DO UPDATE SET points = account_vote_balances.points + EXCLUDED.points, lifetime_points = account_vote_balances.lifetime_points + EXCLUDED.lifetime_points, updated_at = now()")) {
            statement.setObject(1, account.accountUuid());
            statement.setInt(2, safePoints);
            statement.setInt(3, safePoints);
            statement.executeUpdate();
        }
        return safePoints;
    }

    public static CompletableFuture<Void> recordUnresolvedVoteAsync(String username, String serviceName, String addressHash) {
        return DatabaseManager.runAsync("record unresolved vote", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO public.account_vote_unresolved (minecraft_username, service_name, address_hash) VALUES (?, ?, ?)")) {
                statement.setString(1, safe(username, "", 64));
                statement.setString(2, safe(serviceName, "", 128));
                statement.setString(3, safe(addressHash, "", 128));
                statement.executeUpdate();
            }
        }).exceptionally(error -> null);
    }

    public static CompletableFuture<VoteBalance> voteBalanceAsync(UUID accountUuid) {
        if (accountUuid == null) return CompletableFuture.completedFuture(new VoteBalance(0, 0));
        return DatabaseManager.supplyAsync("read account vote balance", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT points, lifetime_points FROM public.account_vote_balances WHERE account_uuid = ?")) {
                statement.setObject(1, accountUuid);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) return new VoteBalance(rs.getLong(1), rs.getLong(2));
                }
            }
            return new VoteBalance(0, 0);
        }).exceptionally(error -> new VoteBalance(0, 0));
    }

    private static UUID tryUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safe(String value, String fallback, int maxLen) {
        String out = value == null || value.isBlank() ? fallback : value.trim();
        if (out == null) out = "";
        if (out.length() > maxLen) out = out.substring(0, maxLen);
        return out;
    }
}
