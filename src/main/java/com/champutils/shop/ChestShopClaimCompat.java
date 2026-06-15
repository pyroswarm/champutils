package com.champutils.shop;

import com.champutils.claims.LandClaimRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Chest shops must be created inside a ChampUtils land claim where the active
 * profile can build. This keeps shop ownership compatible with profile-based
 * ironman/nuzlocke/islander rules.
 */
public final class ChestShopClaimCompat {
    private ChestShopClaimCompat() {}

    public static ClaimCheckResult canCreateShop(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return ClaimCheckResult.DENIED;
        if (player.hasPermissions(4)) return ClaimCheckResult.ALLOWED;

        LandClaimRepository.Claim claim = LandClaimRepository.findAt(level, pos);
        if (claim == null) return ClaimCheckResult.UNCLAIMED;
        return LandClaimRepository.canBuild(player, claim) ? ClaimCheckResult.ALLOWED : ClaimCheckResult.NOT_TRUSTED;
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
