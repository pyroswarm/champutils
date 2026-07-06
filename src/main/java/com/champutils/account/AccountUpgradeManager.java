package com.champutils.account;

import com.champutils.economy.EconomyManager;
import com.champutils.commerce.AccountCommerceRepository;
import com.champutils.permissions.LuckPermsHook;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AccountUpgradeManager {
    private AccountUpgradeManager() {}

    public enum Tier { VIP, VIP_PLUS }

    public static boolean hasVip(ServerPlayer player) {
        return owns(player, AccountUpgradeConfig.CONFIG.vip) || hasVipPlus(player);
    }

    public static boolean hasVipPlus(ServerPlayer player) {
        return owns(player, AccountUpgradeConfig.CONFIG.vipPlus);
    }

    public static PurchaseResult purchase(ServerPlayer player, Tier tier) {
        if (player == null || tier == null) return PurchaseResult.fail("Player not found.");
        AccountUpgradeConfig.Upgrade upgrade = tier == Tier.VIP ? AccountUpgradeConfig.CONFIG.vip : AccountUpgradeConfig.CONFIG.vipPlus;
        if (upgrade == null || !upgrade.enabled) return PurchaseResult.fail("That upgrade is disabled.");

        if (tier == Tier.VIP && hasVip(player)) return PurchaseResult.fail("You already have VIP or better.");
        if (tier == Tier.VIP_PLUS && hasVipPlus(player)) return PurchaseResult.fail("You already have VIP+.");

        long price = AccountUpgradeConfig.priceCents(upgrade);
        if (price <= 0L) return PurchaseResult.fail("This upgrade has an invalid price. Ask staff to check account_upgrades.json.");

        EconomyManager.TransactionResult withdrawn = EconomyManager.withdraw(player, price, "Account upgrade: " + upgrade.displayName);
        if (!withdrawn.success) return PurchaseResult.fail(withdrawn.error == null ? "You cannot afford that upgrade." : withdrawn.error);

        boolean applied = LuckPermsHook.addGroup(player, upgrade.luckPermsGroup);
        if (!applied) {
            EconomyManager.deposit(player, price, "Refund failed account upgrade: " + upgrade.displayName);
            return PurchaseResult.fail("The account rank could not be applied. Credits were refunded.");
        }

        if (tier == Tier.VIP_PLUS) {
            if (AccountUpgradeConfig.CONFIG.vip != null && AccountUpgradeConfig.CONFIG.vip.enabled) {
                LuckPermsHook.addGroup(player, AccountUpgradeConfig.CONFIG.vip.luckPermsGroup);
            }
            com.champutils.cashshop.BoosterCreditManager.updateVipPlusProgress(player, true);
        }

        AccountCommerceRepository.recordPurchaseAsync(
                new AccountCommerceRepository.ResolvedAccount(player.getUUID(), player.getName().getString(), true),
                "IN_GAME",
                tier == Tier.VIP_PLUS ? "rank_vipplus" : "rank_vip",
                1,
                "accountupgrade:" + player.getUUID(),
                "Purchased with in-game Credits"
        );

        player.sendSystemMessage(Component.literal("Account upgrade purchased: " + upgrade.displayName + "!").withStyle(ChatFormatting.LIGHT_PURPLE));
        return PurchaseResult.success(upgrade.displayName, price);
    }

    private static boolean owns(ServerPlayer player, AccountUpgradeConfig.Upgrade upgrade) {
        if (player == null || upgrade == null) return false;
        if (upgrade.ownedPermission != null && !upgrade.ownedPermission.isBlank() && LuckPermsHook.hasPermission(player, upgrade.ownedPermission)) return true;
        return LuckPermsHook.hasGroup(player, upgrade.luckPermsGroup);
    }

    public record PurchaseResult(boolean success, String message, String displayName, long priceCents) {
        public static PurchaseResult success(String displayName, long priceCents) {
            return new PurchaseResult(true, "Purchased " + displayName + ".", displayName, priceCents);
        }
        public static PurchaseResult fail(String message) {
            return new PurchaseResult(false, message == null ? "Purchase failed." : message, "", 0L);
        }
    }
}
