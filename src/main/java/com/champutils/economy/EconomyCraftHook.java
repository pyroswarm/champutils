package com.champutils.economy;

import net.minecraft.server.level.ServerPlayer;

public final class EconomyCraftHook {

    private EconomyCraftHook() {
    }

    public static ChargeResult withdraw(ServerPlayer player, long amount) {
        EconomyManager.TransactionResult result =
                EconomyManager.withdraw(player, amount, "itemroll_charge");

        if (!result.success) {
            return ChargeResult.fail(result.error);
        }

        return ChargeResult.success(result.amount, result.newBalance);
    }

    public static AffordResult canAfford(ServerPlayer player, long amount) {
        if (player == null) {
            return AffordResult.fail("Player not found.");
        }

        long balance = EconomyManager.getBalance(player);

        if (amount > 0L && balance < amount) {
            return AffordResult.fail(
                    "You need " +
                            formatMoney(amount) +
                            " but only have " +
                            formatMoney(balance) +
                            "."
            );
        }

        return AffordResult.success(balance);
    }

    public static String formatMoney(long amount) {
        return EconomyManager.format(amount);
    }

    public static final class AffordResult {

        public final boolean success;
        public final String error;
        public final long balance;

        private AffordResult(boolean success, String error, long balance) {
            this.success = success;
            this.error = error;
            this.balance = balance;
        }

        public static AffordResult success(long balance) {
            return new AffordResult(true, null, balance);
        }

        public static AffordResult fail(String error) {
            return new AffordResult(false, error, 0L);
        }
    }

    public static final class ChargeResult {

        public final boolean success;
        public final String error;
        public final long amountCharged;
        public final long newBalance;

        private ChargeResult(boolean success, String error, long amountCharged, long newBalance) {
            this.success = success;
            this.error = error;
            this.amountCharged = amountCharged;
            this.newBalance = newBalance;
        }

        public static ChargeResult success(long amountCharged, long newBalance) {
            return new ChargeResult(true, null, amountCharged, newBalance);
        }

        public static ChargeResult fail(String error) {
            return new ChargeResult(false, error, 0L, 0L);
        }
    }
}
