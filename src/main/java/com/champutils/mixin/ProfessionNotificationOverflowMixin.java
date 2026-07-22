package com.champutils.mixin;

import com.champutils.profession.ProfessionNotificationSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ProfessionNotificationOverflowMixin {
    private static final ThreadLocal<Boolean> CHAMPUTILS_GUARD = ThreadLocal.withInitial(() -> false);

    @Inject(method = "displayClientMessage", at = @At("HEAD"), require = 0)
    private void champutils$captureProfessionPopup(Component message, boolean overlay, CallbackInfo ci) {
        if (!overlay || Boolean.TRUE.equals(CHAMPUTILS_GUARD.get())) return;
        boolean professionCaller = false;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().startsWith("com.champutils.profession.")) {
                professionCaller = true;
                break;
            }
        }
        if (!professionCaller) return;
        CHAMPUTILS_GUARD.set(true);
        try {
            ProfessionNotificationSettings.handleProfessionActionBar((ServerPlayer) (Object) this, message);
        } finally {
            CHAMPUTILS_GUARD.set(false);
        }
    }
}
