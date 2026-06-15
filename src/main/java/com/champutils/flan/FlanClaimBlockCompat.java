package com.champutils.flan;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

public final class FlanClaimBlockCompat {

    private FlanClaimBlockCompat() {
    }

    public static boolean isFlanLoaded() {
        return FabricLoader.getInstance().isModLoaded("flan");
    }

    public static Result addClaimBlocks(ServerPlayer player, int amount) {
        if (player == null) return Result.failed("Player is missing.");
        if (amount <= 0) return Result.success(0, "No claim blocks to add.");
        if (!isFlanLoaded()) return Result.failed("Flan is not loaded.");

        try {
            Class<?> playerClaimDataClass = Class.forName("io.github.flemmli97.flan.player.PlayerClaimData");
            Method get = playerClaimDataClass.getMethod("get", ServerPlayer.class);
            Object data = get.invoke(null, player);
            if (data == null) return Result.failed("Flan player claim data was not available.");

            Method addDirect = findMethod(playerClaimDataClass, "addClaimBlocksDirect", int.class);
            if (addDirect != null) {
                addDirect.invoke(data, amount);
                invokeNoArg(data, "updateScoreboard");
                return Result.success(amount, null);
            }

            Method add = findMethod(playerClaimDataClass, "addClaimBlocks", int.class);
            if (add != null) {
                Object added = add.invoke(data, amount);
                if (added instanceof Boolean && !((Boolean) added)) {
                    Method getClaimBlocks = findMethod(playerClaimDataClass, "getClaimBlocks");
                    Method setClaimBlocks = findMethod(playerClaimDataClass, "setClaimBlocks", int.class);
                    if (getClaimBlocks != null && setClaimBlocks != null) {
                        int current = ((Number) getClaimBlocks.invoke(data)).intValue();
                        setClaimBlocks.invoke(data, current + amount);
                        invokeNoArg(data, "updateScoreboard");
                        return Result.success(amount, null);
                    }
                    return Result.failed("Flan rejected the claim block increase, likely due to its max claim block cap.");
                }
                invokeNoArg(data, "updateScoreboard");
                return Result.success(amount, null);
            }

            Method getClaimBlocks = findMethod(playerClaimDataClass, "getClaimBlocks");
            Method setClaimBlocks = findMethod(playerClaimDataClass, "setClaimBlocks", int.class);
            if (getClaimBlocks != null && setClaimBlocks != null) {
                int current = ((Number) getClaimBlocks.invoke(data)).intValue();
                setClaimBlocks.invoke(data, current + amount);
                invokeNoArg(data, "updateScoreboard");
                return Result.success(amount, null);
            }

            return Result.failed("Could not find a compatible Flan claim block API.");
        } catch (Throwable t) {
            t.printStackTrace();
            return Result.failed("Could not add Flan claim blocks: " + t.getClass().getSimpleName());
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.invoke(target);
        } catch (Throwable ignored) {
        }
    }

    public static final class Result {
        public final boolean success;
        public final int amount;
        public final String message;

        private Result(boolean success, int amount, String message) {
            this.success = success;
            this.amount = amount;
            this.message = message;
        }

        public static Result success(int amount, String message) {
            return new Result(true, amount, message);
        }

        public static Result failed(String message) {
            return new Result(false, 0, message);
        }
    }
}
