package com.champutils.account;

import com.champutils.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RankPurchaseRepository {
    private RankPurchaseRepository() {}

    public record Request(UUID id, UUID accountUuid, String username, String tier, long creditsCharged, String status, String tebexReference) {}

    public static CompletableFuture<Void> ensureSchemaAsync() {
        return DatabaseManager.runAsync("rank purchase schema", connection -> {
            try (Statement s = connection.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS public.account_rank_purchase_requests (" +
                        "id uuid PRIMARY KEY DEFAULT gen_random_uuid()," +
                        "account_uuid uuid NOT NULL," +
                        "minecraft_username text NOT NULL DEFAULT ''," +
                        "tier text NOT NULL," +
                        "tebex_package_id bigint NOT NULL," +
                        "credits_charged bigint NOT NULL," +
                        "status text NOT NULL DEFAULT 'PENDING'," +
                        "tebex_reference text NOT NULL DEFAULT ''," +
                        "error_message text NOT NULL DEFAULT ''," +
                        "created_at timestamptz NOT NULL DEFAULT now()," +
                        "updated_at timestamptz NOT NULL DEFAULT now())");
                s.execute("CREATE UNIQUE INDEX IF NOT EXISTS account_rank_purchase_active_unique ON public.account_rank_purchase_requests (account_uuid) WHERE status IN ('PENDING','SUBMITTED','RECONCILIATION_REQUIRED')");
                s.execute("CREATE INDEX IF NOT EXISTS idx_account_rank_purchase_account ON public.account_rank_purchase_requests (account_uuid, created_at DESC)");
            }
        });
    }

    public static CompletableFuture<Request> create(UUID account, String username, String tier, long packageId, long credits) {
        return DatabaseManager.supplyAsync("create rank purchase request", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO public.account_rank_purchase_requests(account_uuid,minecraft_username,tier,tebex_package_id,credits_charged,status) VALUES(?,?,?,?,?,'PENDING') RETURNING id")) {
                ps.setObject(1, account); ps.setString(2, username); ps.setString(3, tier); ps.setLong(4, packageId); ps.setLong(5, credits);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("Could not create rank purchase request");
                    return new Request((UUID) rs.getObject(1), account, username, tier, credits, "PENDING", "");
                }
            }
        });
    }

    public static CompletableFuture<Void> update(UUID id, String status, String reference, String error) {
        return DatabaseManager.runAsync("update rank purchase request", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE public.account_rank_purchase_requests SET status=?, tebex_reference=?, error_message=?, updated_at=now() WHERE id=?")) {
                ps.setString(1, status); ps.setString(2, reference == null ? "" : reference); ps.setString(3, error == null ? "" : error); ps.setObject(4, id); ps.executeUpdate();
            }
        });
    }

    public static CompletableFuture<Boolean> hasActive(UUID account) {
        return DatabaseManager.supplyAsync("check active rank purchase", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM public.account_rank_purchase_requests WHERE account_uuid=? AND status IN ('PENDING','SUBMITTED','RECONCILIATION_REQUIRED') LIMIT 1")) {
                ps.setObject(1, account); try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        }).exceptionally(error -> true);
    }

    public static CompletableFuture<Void> completeLatest(UUID account, String tier, String tebexReference) {
        return DatabaseManager.runAsync("complete rank purchase", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE public.account_rank_purchase_requests SET status='COMPLETED', tebex_reference=CASE WHEN ?='' THEN tebex_reference ELSE ? END, updated_at=now() WHERE id=(SELECT id FROM public.account_rank_purchase_requests WHERE account_uuid=? AND tier=? AND status IN ('PENDING','SUBMITTED','RECONCILIATION_REQUIRED') ORDER BY created_at DESC LIMIT 1)")) {
                String ref = tebexReference == null ? "" : tebexReference;
                ps.setString(1, ref); ps.setString(2, ref); ps.setObject(3, account); ps.setString(4, tier); ps.executeUpdate();
            }
        });
    }
}
