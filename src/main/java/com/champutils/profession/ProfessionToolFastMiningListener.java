package com.champutils.profession;

/**
 * Disabled. Mining speed is now handled by vanilla tool tiers plus
 * ProfessionToolStatEffectListener. This class remains only so older
 * installs that still have it referenced do not fail compilation.
 */
public final class ProfessionToolFastMiningListener {

    private ProfessionToolFastMiningListener() {
    }

    public static void register() {
        // No-op.
    }
}
