package com.champutils.profession.passives;

import com.champutils.profession.ProfessionBlockTracker;
import com.champutils.profession.ProfessionToolMetadata;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PassiveRegistry {

    private static final List<ProfessionPassive> MINING_PASSIVES =
            new ArrayList<>();

    private static final List<ProfessionPassive> FORESTRY_PASSIVES =
            new ArrayList<>();

    private PassiveRegistry() {
    }

    public static void registerDefaults() {

        MINING_PASSIVES.clear();
        MINING_PASSIVES.add(
                new DropMultiplierPassive()
        );

        MINING_PASSIVES.add(
                new ShardFinderPassive()
        );

        MINING_PASSIVES.add(
                new GemFinderPassive()
        );

        MINING_PASSIVES.add(
                new XpSurgePassive()
        );

        MINING_PASSIVES.add(
                new TreasurePingPassive()
        );

        FORESTRY_PASSIVES.clear();
        FORESTRY_PASSIVES.add(
                new ForestryDropMultiplierPassive()
        );
        FORESTRY_PASSIVES.add(
                new ForestryRewardFinderPassive()
        );
        FORESTRY_PASSIVES.add(
                new ForestryXpSurgePassive()
        );
    }

    public static void applyMiningPassives(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            String blockId
    ) {

        if (
                player == null ||
                        level == null ||
                        pos == null ||
                        blockId == null ||
                        blockId.isBlank()
        ) {
            return;
        }

        /*
         * Economy passives must only apply to natural blocks.
         * In this mod, natural means "not tracked as player-placed".
         * This prevents silk-touch/place/break loops from farming
         * double/triple/quadruple/quintuple bonus drops.
         */
        if (
                ProfessionBlockTracker.isPlayerPlaced(
                        level,
                        pos
                )
        ) {
            return;
        }

        ItemStack stack =
                player.getMainHandItem();

        if (
                stack == null ||
                        stack.isEmpty() ||
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
            return;
        }

        for (
                ProfessionPassive passive :
                MINING_PASSIVES
        ) {
            passive.apply(
                    player,
                    stack,
                    level,
                    pos,
                    blockId
            );
        }
    }
    public static void applyForestryPassives(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            String blockId
    ) {

        if (
                player == null ||
                        level == null ||
                        pos == null ||
                        blockId == null ||
                        blockId.isBlank()
        ) {
            return;
        }

        if (
                ProfessionBlockTracker.isPlayerPlaced(
                        level,
                        pos
                )
        ) {
            return;
        }

        ItemStack stack =
                player.getMainHandItem();

        if (
                stack == null ||
                        stack.isEmpty() ||
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
            return;
        }

        for (
                ProfessionPassive passive :
                FORESTRY_PASSIVES
        ) {
            passive.apply(
                    player,
                    stack,
                    level,
                    pos,
                    blockId
            );
        }
    }

}
