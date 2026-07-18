package com.champutils.account;

import com.champutils.database.DatabaseManager;
import com.champutils.economy.EconomyManager;
import com.champutils.permissions.LuckPermsHook;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class AccountUpgradeManager {
    private static final Set<UUID> LOCAL_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private AccountUpgradeManager() {}

    public enum Tier { VIP, VIP_PLUS }
    public record PurchaseResult(boolean success, boolean pending, String message, String displayName, long priceCents) {
        public static PurchaseResult ok(String name, long price) { return new PurchaseResult(true, false, "Purchased " + name + ".", name, price); }
        public static PurchaseResult pending(String name, long price) { return new PurchaseResult(true, true, "Purchase submitted to Tebex.", name, price); }
        public static PurchaseResult fail(String message) { return new PurchaseResult(false, false, message == null ? "Purchase failed." : message, "", 0L); }
    }

    public static void initialize() { RankPurchaseRepository.ensureSchemaAsync(); }

    public static boolean hasVip(ServerPlayer player) { return owns(player, AccountUpgradeConfig.CONFIG.vip) || hasVipPlus(player); }
    public static boolean hasVipPlus(ServerPlayer player) { return owns(player, AccountUpgradeConfig.CONFIG.vipPlus); }

    public static long priceFor(ServerPlayer player, Tier tier) {
        if (tier == Tier.VIP_PLUS && hasVip(player) && !hasVipPlus(player)) {
            long credits = AccountUpgradeConfig.CONFIG.vipPlus.upgradeFromVipPriceCredits;
            if (credits > 0L) return EconomyManager.wholeCreditsToCents(credits);
        }
        return AccountUpgradeConfig.priceCents(tier == Tier.VIP ? AccountUpgradeConfig.CONFIG.vip : AccountUpgradeConfig.CONFIG.vipPlus);
    }

    public static CompletableFuture<PurchaseResult> purchaseAsync(ServerPlayer player, Tier tier) {
        if (player == null || tier == null) return CompletableFuture.completedFuture(PurchaseResult.fail("Player not found."));
        UUID uuid = player.getUUID();
        if (!LOCAL_IN_FLIGHT.add(uuid)) return CompletableFuture.completedFuture(PurchaseResult.fail("A rank purchase is already processing."));

        AccountUpgradeConfig.Upgrade upgrade = tier == Tier.VIP ? AccountUpgradeConfig.CONFIG.vip : AccountUpgradeConfig.CONFIG.vipPlus;
        if (upgrade == null || !upgrade.enabled) { LOCAL_IN_FLIGHT.remove(uuid); return CompletableFuture.completedFuture(PurchaseResult.fail("That upgrade is disabled.")); }
        if (tier == Tier.VIP && hasVip(player)) { LOCAL_IN_FLIGHT.remove(uuid); return CompletableFuture.completedFuture(PurchaseResult.fail("You already have VIP or better.")); }
        if (tier == Tier.VIP_PLUS && hasVipPlus(player)) { LOCAL_IN_FLIGHT.remove(uuid); return CompletableFuture.completedFuture(PurchaseResult.fail("You already have VIP+.")); }

        long price = priceFor(player, tier);
        if (price <= 0L || upgrade.tebexPackageId <= 0L) { LOCAL_IN_FLIGHT.remove(uuid); return CompletableFuture.completedFuture(PurchaseResult.fail("This rank is not configured correctly.")); }
        String tierKey = tier == Tier.VIP_PLUS ? "vipplus" : "vip";

        return RankPurchaseRepository.hasActive(uuid).thenCompose(active -> {
            if (active) return CompletableFuture.completedFuture(PurchaseResult.fail("A rank purchase is already pending for your account."));
            return RankPurchaseRepository.create(uuid, player.getGameProfile().getName(), tierKey, upgrade.tebexPackageId, price)
                    .thenCompose(request -> EconomyManager.withdrawAsync(player, price, "Champs Shop rank: " + upgrade.displayName)
                            .thenCompose(withdrawn -> {
                                if (withdrawn == null || !withdrawn.success) {
                                    return RankPurchaseRepository.update(request.id(), "FAILED", "", withdrawn == null ? "Credit withdrawal failed" : withdrawn.error)
                                            .thenApply(v -> PurchaseResult.fail(withdrawn == null || withdrawn.error == null ? "You cannot afford that rank." : withdrawn.error));
                                }
                                return TebexRankService.submit(player.getGameProfile().getName(), upgrade.tebexPackageId, request.id().toString())
                                        .thenCompose(submission -> {
                                            if (submission.accepted()) {
                                                return RankPurchaseRepository.update(request.id(), "SUBMITTED", submission.reference(), "")
                                                        .thenApply(v -> PurchaseResult.pending(upgrade.displayName, price));
                                            }
                                            if (submission.uncertain()) {
                                                return RankPurchaseRepository.update(request.id(), "RECONCILIATION_REQUIRED", submission.reference(), submission.error())
                                                        .thenApply(v -> PurchaseResult.pending(upgrade.displayName, price));
                                            }
                                            return EconomyManager.depositAsync(player, price, "Refund rejected Tebex rank purchase")
                                                    .thenCompose(v -> RankPurchaseRepository.update(request.id(), "REFUNDED", "", submission.error()))
                                                    .thenApply(v -> PurchaseResult.fail("Tebex rejected the purchase. Your Credits were refunded."));
                                        });
                            }));
        }).whenComplete((result, error) -> LOCAL_IN_FLIGHT.remove(uuid));
    }

    public static void markDelivered(UUID accountUuid, String tier, String tebexReference) {
        if (accountUuid == null || tier == null) return;
        RankPurchaseRepository.completeLatest(accountUuid, tier.replace("+", "plus"), tebexReference)
                .exceptionally(error -> { error.printStackTrace(); return null; });
    }

    private static boolean owns(ServerPlayer player, AccountUpgradeConfig.Upgrade upgrade) {
        if (player == null || upgrade == null) return false;
        if (upgrade.ownedPermission != null && !upgrade.ownedPermission.isBlank() && LuckPermsHook.hasPermission(player, upgrade.ownedPermission)) return true;
        if ("vipplus".equalsIgnoreCase(upgrade.luckPermsGroup) && LuckPermsHook.hasAnyGroup(player, "vipplus", "vip+")) return true;
        return LuckPermsHook.hasGroup(player, upgrade.luckPermsGroup);
    }
}
