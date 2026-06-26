package com.champutils.mixin;

import com.champutils.profile.ProfileLobbyLockManager;
import com.champutils.profile.ProfileLoadingStateManager;
import com.champutils.commands.CommandBlocker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks every non-profile command while the player is in the locked profile lobby.
 * Add this mixin to your ChampUtils mixin json if it is not already auto-included.
 */
@Mixin(Commands.class)
public abstract class CommandsLockedLobbyMixin {
    @Inject(method = "performPrefixedCommand", at = @At("HEAD"), cancellable = true)
    private void champutils$blockCommandsInProfileLobby(CommandSourceStack source, String command, CallbackInfo ci) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception ignored) {
            return;
        }

        if (CommandBlocker.isBlockedRoot(command) && !source.hasPermission(4)) {
            player.sendSystemMessage(CommandBlocker.denyMessage());
            ci.cancel();
            return;
        }

        if (!ProfileLobbyLockManager.isLocked(player)) return;
        // Staff/console-level commands must always be able to rescue a player from a bad profile lock.
        if (source.hasPermission(4)) return;
        if (ProfileLobbyLockManager.hasBypass(player)) return;

        // Survival profile hydration is stricter than the profile-lobby menu lock.
        // Only login/auth commands can pass while the profile is not fully attached.
        if (ProfileLoadingStateManager.isLoading(player)) {
            if (ProfileLoadingStateManager.isAllowedCommand(command)) return;
            ProfileLoadingStateManager.deny(player);
            ci.cancel();
            return;
        }

        if (ProfileLobbyLockManager.isAllowedCommand(command)) return;

        ProfileLobbyLockManager.deny(player);
        ci.cancel();
    }
}
