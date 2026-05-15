package com.champutils.worldevent;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;

public final class FlanClaimCompat {

    private FlanClaimCompat() {}

    public static boolean isAreaUnclaimed(ServerLevel level, BlockPos center, int radius) {
        if (level == null || center == null) return false;
        if (!FabricLoader.getInstance().isModLoaded("flan")) return true;

        int r = Math.max(0, radius);
        BlockPos[] samples = new BlockPos[] {
                center,
                center.offset(r, 0, 0), center.offset(-r, 0, 0),
                center.offset(0, 0, r), center.offset(0, 0, -r),
                center.offset(r, 0, r), center.offset(r, 0, -r),
                center.offset(-r, 0, r), center.offset(-r, 0, -r)
        };

        for (BlockPos pos : samples) {
            if (!isPositionUnclaimed(level, pos)) return false;
        }
        return true;
    }

    private static boolean isPositionUnclaimed(ServerLevel level, BlockPos pos) {
        try {
            Class<?> handler = Class.forName("io.github.flemmli97.flan.api.ClaimHandler");
            Method getPermissionStorage = handler.getMethod("getPermissionStorage", ServerLevel.class);
            Object storage = getPermissionStorage.invoke(null, level);
            if (storage == null) return true;

            Method getForPermissionCheck = storage.getClass().getMethod("getForPermissionCheck", BlockPos.class);
            Object container = getForPermissionCheck.invoke(storage, pos);
            if (container == null) return true;

            Object breakPermission = getBuiltinPermission("BREAK");
            if (breakPermission == null) {
                breakPermission = ResourceLocation.fromNamespaceAndPath("flan", "break");
            }

            for (Method method : container.getClass().getMethods()) {
                if (!method.getName().equals("canInteract")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 4 && params[2] == BlockPos.class) {
                    Object result = method.invoke(container, null, breakPermission, pos, false);
                    if (result instanceof Boolean allowed) return allowed;
                }
            }

            return true;
        } catch (Throwable t) {
            System.out.println("[ChampUtils] Flan claim check failed; rejecting world event location to be safe: " + t.getClass().getSimpleName());
            return false;
        }
    }

    private static Object getBuiltinPermission(String fieldName) {
        try {
            Class<?> clazz = Class.forName("io.github.flemmli97.flan.api.permission.BuiltinPermission");
            return clazz.getField(fieldName).get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
