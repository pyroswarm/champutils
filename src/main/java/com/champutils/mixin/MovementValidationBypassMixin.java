package com.champutils.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Removes vanilla's distance-based "moved too quickly" corrections.
 *
 * Cobblemon mounts can legitimately move a rider farther per packet than vanilla expects.
 * Vanilla then rubber-bands the player/vehicle and repeatedly reprocesses movement, which is
 * both disruptive and needlessly expensive. This disables both vanilla distance and moved-wrongly rollback thresholds;
 * packet finiteness checks, collision handling, world border checks, teleport acknowledgement,
 * chunk tracking, and all normal position application remain in vanilla code.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MovementValidationBypassMixin {
    private static final double CHAMPUTILS_DISABLED_MOVEMENT_THRESHOLD = Double.MAX_VALUE;

    @ModifyConstant(
            method = "handleMovePlayer",
            constant = @Constant(doubleValue = 100.0D),
            require = 0
    )
    private double champutils$disablePlayerMovedTooQuickly100(double original) {
        return CHAMPUTILS_DISABLED_MOVEMENT_THRESHOLD;
    }

    @ModifyConstant(
            method = "handleMovePlayer",
            constant = @Constant(doubleValue = 300.0D),
            require = 0
    )
    private double champutils$disablePlayerMovedTooQuickly300(double original) {
        return CHAMPUTILS_DISABLED_MOVEMENT_THRESHOLD;
    }

    @ModifyConstant(
            method = "handleMoveVehicle",
            constant = @Constant(doubleValue = 100.0D),
            require = 0
    )
    private double champutils$disableVehicleMovedTooQuickly(double original) {
        return CHAMPUTILS_DISABLED_MOVEMENT_THRESHOLD;
    }
    @ModifyConstant(
            method = "handleMovePlayer",
            constant = @Constant(doubleValue = 0.0625D),
            require = 0
    )
    private double champutils$disablePlayerMovedWrongly(double original) {
        return CHAMPUTILS_DISABLED_MOVEMENT_THRESHOLD;
    }

    @ModifyConstant(
            method = "handleMoveVehicle",
            constant = @Constant(doubleValue = 0.0625D),
            require = 0
    )
    private double champutils$disableVehicleMovedWrongly(double original) {
        return CHAMPUTILS_DISABLED_MOVEMENT_THRESHOLD;
    }

}
