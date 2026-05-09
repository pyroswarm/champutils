package com.champutils.profession.passives;

import com.champutils.profession.ProfessionBlockTracker;
import com.champutils.profession.ProfessionToolMetadata;
import com.champutils.profession.ProfessionToolUtil;
import com.champutils.profession.actives.MiningBlockUtil;
import com.champutils.profession.actives.ActiveEffectManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class DurabilitySavePassive {

    public static final String STAT_ID = "durabilitySaveChance";

    private static final Random RANDOM =
            new Random();

    private static final Map<UUID, RecentBreak> RECENT_BREAKS =
            new HashMap<>();

    private DurabilitySavePassive() {
    }

    public static void markRecentBreak(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            boolean natural
    ) {

        if (
                player == null ||
                        level == null ||
                        pos == null
        ) {
            return;
        }

        RECENT_BREAKS.put(
                player.getUUID(),
                new RecentBreak(
                        level.dimension().location().toString(),
                        pos.immutable(),
                        level.getGameTime(),
                        natural
                )
        );
    }

    public static boolean shouldPreserveDurability(
            ServerPlayer player,
            ItemStack stack,
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {

        if (
                player == null ||
                        stack == null ||
                        stack.isEmpty() ||
                        level == null ||
                        pos == null ||
                        state == null
        ) {
            return false;
        }

        if (
                !ProfessionToolMetadata.isProfessionTool(
                        stack
                ) ||
                        !ProfessionToolMetadata.isIdentified(
                                stack
                        ) ||
                        ProfessionToolMetadata.isBroken(
                                stack
                        )
        ) {
            return false;
        }

        if (
                !MiningBlockUtil.isPickaxeBlock(
                        level,
                        pos,
                        state
                )
        ) {
            return false;
        }

        if (
                !isNaturalRecentBreak(
                        player,
                        level,
                        pos
                )
        ) {
            return false;
        }

        double chancePercent =
                ProfessionToolUtil.getStat(
                        stack,
                        STAT_ID
                );

        if (chancePercent <= 0.0D) {
            return false;
        }

        double focusMultiplier =
                ActiveEffectManager.getMiningPassiveChanceMultiplier(
                        player,
                        stack
                );

        boolean preserved =
                RANDOM.nextDouble() <
                        Math.min(
                                100.0D,
                                chancePercent * focusMultiplier
                        ) / 100.0D;

        if (preserved) {
            celebrate(
                    player
            );
        }

        return preserved;
    }

    private static boolean isNaturalRecentBreak(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos
    ) {

        RecentBreak recent =
                RECENT_BREAKS.get(
                        player.getUUID()
                );

        if (recent != null) {
            boolean sameBreak =
                    recent.dimension.equals(
                            level.dimension().location().toString()
                    ) &&
                            recent.pos.equals(
                                    pos
                            ) &&
                            Math.abs(
                                    level.getGameTime() - recent.gameTime
                            ) <= 1L;

            if (sameBreak) {
                return recent.natural;
            }
        }

        /*
         * Fallback for any block-break path that does not pass through the
         * normal Fabric BEFORE callback. The recent-break marker above is what
         * prevents player-placed blocks from becoming "natural" after the
         * placement tracker removes them during the normal break flow.
         */
        return !ProfessionBlockTracker.isPlayerPlaced(
                level,
                pos
        );
    }

    private static void celebrate(
            ServerPlayer player
    ) {

        player.displayClientMessage(
                Component.literal(
                        "§aDurability Preserved!"
                ),
                true
        );

        player.serverLevel().playSound(
                null,
                player.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.35F,
                1.7F
        );
    }

    private record RecentBreak(
            String dimension,
            BlockPos pos,
            long gameTime,
            boolean natural
    ) {
    }
}
