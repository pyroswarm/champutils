package com.champutils.economy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

public final class EconomyCraftHook {

    private static final NumberFormat FALLBACK_FORMAT =
            NumberFormat.getInstance(Locale.US);

    private EconomyCraftHook() {
    }

    public static ChargeResult withdraw(
            ServerPlayer player,
            long amount
    ) {

        if (amount <= 0L) {
            return ChargeResult.success(
                    0L,
                    getBalanceOrZero(player)
            );
        }

        if (player == null) {
            return ChargeResult.fail(
                    "Player not found."
            );
        }

        try {
            Object manager =
                    getManager(
                            player.getServer()
                    );

            if (manager == null) {
                return ChargeResult.fail(
                        "EconomyCraft is not available."
                );
            }

            UUID playerId =
                    player.getUUID();

            long balance =
                    getBalance(
                            manager,
                            playerId,
                            true
                    );

            if (balance < amount) {
                return ChargeResult.fail(
                        "You need " +
                                formatMoney(amount) +
                                " but only have " +
                                formatMoney(balance) +
                                "."
                );
            }

            Method removeMoney =
                    manager.getClass()
                            .getMethod(
                                    "removeMoney",
                                    UUID.class,
                                    long.class
                            );

            Object removed =
                    removeMoney.invoke(
                            manager,
                            playerId,
                            amount
                    );

            if (!(removed instanceof Boolean) || !((Boolean) removed)) {
                return ChargeResult.fail(
                        "Could not remove money from your balance."
                );
            }

            long newBalance =
                    getBalance(
                            manager,
                            playerId,
                            true
                    );

            return ChargeResult.success(
                    amount,
                    newBalance
            );

        } catch (Exception e) {
            return ChargeResult.fail(
                    "EconomyCraft payment failed: " +
                            e.getClass().getSimpleName()
            );
        }
    }

    public static AffordResult canAfford(
            ServerPlayer player,
            long amount
    ) {

        if (amount <= 0L) {
            return AffordResult.success(
                    getBalanceOrZero(player)
            );
        }

        if (player == null) {
            return AffordResult.fail(
                    "Player not found."
            );
        }

        try {
            Object manager =
                    getManager(
                            player.getServer()
                    );

            if (manager == null) {
                return AffordResult.fail(
                        "EconomyCraft is not available."
                );
            }

            long balance =
                    getBalance(
                            manager,
                            player.getUUID(),
                            true
                    );

            if (balance < amount) {
                return AffordResult.fail(
                        "You need " +
                                formatMoney(amount) +
                                " but only have " +
                                formatMoney(balance) +
                                "."
                );
            }

            return AffordResult.success(
                    balance
            );

        } catch (Exception e) {
            return AffordResult.fail(
                    "EconomyCraft balance check failed: " +
                            e.getClass().getSimpleName()
            );
        }
    }

    public static String formatMoney(
            long amount
    ) {

        try {
            Class<?> economyCraft =
                    Class.forName(
                            "com.reazip.economycraft.EconomyCraft"
                    );

            Method formatMoney =
                    economyCraft.getMethod(
                            "formatMoney",
                            long.class
                    );

            Object formatted =
                    formatMoney.invoke(
                            null,
                            amount
                    );

            if (formatted != null) {
                return formatted.toString();
            }

        } catch (Exception ignored) {
        }

        return "$" +
                FALLBACK_FORMAT.format(
                        amount
                );
    }

    private static Object getManager(
            MinecraftServer server
    ) throws Exception {

        if (server == null) {
            return null;
        }

        Class<?> economyCraft =
                Class.forName(
                        "com.reazip.economycraft.EconomyCraft"
                );

        Method getManager =
                economyCraft.getMethod(
                        "getManager",
                        MinecraftServer.class
                );

        return getManager.invoke(
                null,
                server
        );
    }

    private static long getBalance(
            Object manager,
            UUID playerId,
            boolean createIfMissing
    ) throws Exception {

        Method getBalance =
                manager.getClass()
                        .getMethod(
                                "getBalance",
                                UUID.class,
                                boolean.class
                        );

        Object balance =
                getBalance.invoke(
                        manager,
                        playerId,
                        createIfMissing
                );

        if (balance instanceof Number) {
            return ((Number) balance).longValue();
        }

        return 0L;
    }

    private static long getBalanceOrZero(
            ServerPlayer player
    ) {

        if (player == null) {
            return 0L;
        }

        try {
            Object manager =
                    getManager(
                            player.getServer()
                    );

            if (manager == null) {
                return 0L;
            }

            return getBalance(
                    manager,
                    player.getUUID(),
                    true
            );

        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static final class AffordResult {

        public final boolean success;
        public final String error;
        public final long balance;

        private AffordResult(
                boolean success,
                String error,
                long balance
        ) {
            this.success = success;
            this.error = error;
            this.balance = balance;
        }

        public static AffordResult success(
                long balance
        ) {
            return new AffordResult(
                    true,
                    null,
                    balance
            );
        }

        public static AffordResult fail(
                String error
        ) {
            return new AffordResult(
                    false,
                    error,
                    0L
            );
        }
    }

    public static final class ChargeResult {

        public final boolean success;
        public final String error;
        public final long amountCharged;
        public final long newBalance;

        private ChargeResult(
                boolean success,
                String error,
                long amountCharged,
                long newBalance
        ) {
            this.success = success;
            this.error = error;
            this.amountCharged = amountCharged;
            this.newBalance = newBalance;
        }

        public static ChargeResult success(
                long amountCharged,
                long newBalance
        ) {
            return new ChargeResult(
                    true,
                    null,
                    amountCharged,
                    newBalance
            );
        }

        public static ChargeResult fail(
                String error
        ) {
            return new ChargeResult(
                    false,
                    error,
                    0L,
                    0L
            );
        }
    }
}
