package com.champutils.shop;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * Optional Flan integration for player chest shops.
 *
 * Chest shops are player-owned economy blocks, so they should only be created
 * inside a real claim where the creator is allowed to build. This avoids players
 * turning random public chests/barrels into protected shop blocks.
 */
public final class ChestShopClaimCompat {

    private static boolean warnedReflectionFailure = false;

    private ChestShopClaimCompat() {
    }

    public static ClaimCheckResult canCreateShop(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) {
            return ClaimCheckResult.DENIED;
        }

        if (player.hasPermissions(4)) {
            return ClaimCheckResult.ALLOWED;
        }

        if (!FabricLoader.getInstance().isModLoaded("flan")) {
            return ClaimCheckResult.NO_CLAIM_MOD;
        }

        try {
            Object claim = getClaimContainer(level, pos);
            if (claim == null) {
                return ClaimCheckResult.UNCLAIMED;
            }

            // PLACE is the correct intent for creating a shop on an existing chest/barrel.
            // Fall back to BREAK because older Flan builds exposed different names, and both
            // indicate the player has build-level trust in that claim.
            if (canInteract(player, level, pos, "PLACE", "place")) {
                return ClaimCheckResult.ALLOWED;
            }
            if (canInteract(player, level, pos, "BREAK", "break")) {
                return ClaimCheckResult.ALLOWED;
            }

            return ClaimCheckResult.NOT_TRUSTED;
        } catch (Throwable t) {
            if (!warnedReflectionFailure) {
                warnedReflectionFailure = true;
                System.out.println("[ChampUtils] Chest shop Flan claim check failed (" + t.getClass().getSimpleName() + "). Blocking non-admin chest shop creation until the claim check can be verified.");
            }
            return ClaimCheckResult.CHECK_FAILED;
        }
    }

    private static Object getClaimContainer(ServerLevel level, BlockPos pos) throws Exception {
        Class<?> handler = Class.forName("io.github.flemmli97.flan.api.ClaimHandler");
        Method getPermissionStorage = handler.getMethod("getPermissionStorage", ServerLevel.class);
        Object storage = getPermissionStorage.invoke(null, level);
        if (storage == null) {
            return null;
        }

        Method getForPermissionCheck = storage.getClass().getMethod("getForPermissionCheck", BlockPos.class);
        return getForPermissionCheck.invoke(storage, pos);
    }

    private static boolean canInteract(ServerPlayer player, ServerLevel level, BlockPos pos, String fieldName, String fallbackPath) {
        Object permission = getBuiltinPermission(fieldName, fallbackPath);
        if (permission == null) {
            return false;
        }

        // Current Flan API: ClaimHandler#canInteract(ServerPlayer player, BlockPos pos, ResourceLocation permission)
        try {
            Class<?> handler = Class.forName("io.github.flemmli97.flan.api.ClaimHandler");
            for (Method method : handler.getMethods()) {
                if (!method.getName().equals("canInteract")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 3 && ServerPlayer.class.isAssignableFrom(params[0]) && params[1] == BlockPos.class) {
                    Object result = method.invoke(null, player, pos, permission);
                    return result instanceof Boolean allowed && allowed;
                }
            }
        } catch (Throwable ignored) {
            // Try the older container method below.
        }

        // Older/internal shape used elsewhere in this project:
        // claimContainer.canInteract(ServerPlayer player, permission, BlockPos pos, boolean message)
        try {
            Object claim = getClaimContainer(level, pos);
            if (claim == null) {
                return false;
            }
            for (Method method : claim.getClass().getMethods()) {
                if (!method.getName().equals("canInteract")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 4 && params[2] == BlockPos.class) {
                    Object result = method.invoke(claim, player, permission, pos, false);
                    return result instanceof Boolean allowed && allowed;
                }
            }
        } catch (Throwable ignored) {
            // Handled by returning false.
        }

        return false;
    }

    private static Object getBuiltinPermission(String fieldName, String fallbackPath) {
        try {
            Class<?> clazz = Class.forName("io.github.flemmli97.flan.api.permission.BuiltinPermission");
            Object value = clazz.getField(fieldName).get(null);
            if (value != null) {
                return value;
            }
        } catch (Throwable ignored) {
            // Fall back to a raw ResourceLocation for Flan 1.10+.
        }

        try {
            return ResourceLocation.fromNamespaceAndPath("flan", fallbackPath);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public enum ClaimCheckResult {
        ALLOWED,
        NO_CLAIM_MOD,
        UNCLAIMED,
        NOT_TRUSTED,
        CHECK_FAILED,
        DENIED
    }
}
