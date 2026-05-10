package com.champutils.profession.actives;

import com.champutils.profession.ProfessionConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class ForestryBlockUtil {

    private ForestryBlockUtil() {
    }

    public static boolean isForestryLog(
            BlockState state
    ) {

        if (state == null || state.isAir()) {
            return false;
        }

        String blockId =
                getBlockId(
                        state.getBlock()
                );

        Integer xp =
                ProfessionConfig
                        .SETTINGS
                        .forestryXp
                        .get(
                                blockId
                        );

        return xp != null && xp > 0;
    }

    public static boolean isLeaf(
            BlockState state
    ) {

        return state != null &&
                !state.isAir() &&
                state.getBlock() instanceof LeavesBlock;
    }

    public static boolean isSameTreeBlock(
            BlockState original,
            BlockState candidate
    ) {

        if (
                original == null ||
                        candidate == null ||
                        original.isAir() ||
                        candidate.isAir()
        ) {
            return false;
        }

        return original.getBlock() == candidate.getBlock();
    }

    public static String getBlockId(
            Block block
    ) {

        return block.builtInRegistryHolder()
                .key()
                .location()
                .toString();
    }

    public static Block getSaplingForLog(
            String blockId
    ) {

        if (blockId == null) {
            return Blocks.AIR;
        }

        return switch (blockId) {
            case "minecraft:oak_log",
                 "minecraft:oak_wood",
                 "minecraft:stripped_oak_log",
                 "minecraft:stripped_oak_wood" -> Blocks.OAK_SAPLING;

            case "minecraft:spruce_log",
                 "minecraft:spruce_wood",
                 "minecraft:stripped_spruce_log",
                 "minecraft:stripped_spruce_wood" -> Blocks.SPRUCE_SAPLING;

            case "minecraft:birch_log",
                 "minecraft:birch_wood",
                 "minecraft:stripped_birch_log",
                 "minecraft:stripped_birch_wood" -> Blocks.BIRCH_SAPLING;

            case "minecraft:jungle_log",
                 "minecraft:jungle_wood",
                 "minecraft:stripped_jungle_log",
                 "minecraft:stripped_jungle_wood" -> Blocks.JUNGLE_SAPLING;

            case "minecraft:acacia_log",
                 "minecraft:acacia_wood",
                 "minecraft:stripped_acacia_log",
                 "minecraft:stripped_acacia_wood" -> Blocks.ACACIA_SAPLING;

            case "minecraft:dark_oak_log",
                 "minecraft:dark_oak_wood",
                 "minecraft:stripped_dark_oak_log",
                 "minecraft:stripped_dark_oak_wood" -> Blocks.DARK_OAK_SAPLING;

            case "minecraft:mangrove_log",
                 "minecraft:mangrove_wood",
                 "minecraft:stripped_mangrove_log",
                 "minecraft:stripped_mangrove_wood" -> Blocks.MANGROVE_PROPAGULE;

            case "minecraft:cherry_log",
                 "minecraft:cherry_wood",
                 "minecraft:stripped_cherry_log",
                 "minecraft:stripped_cherry_wood" -> Blocks.CHERRY_SAPLING;

            case "cobblemon:apricorn_log",
                 "cobblemon:apricorn_wood",
                 "cobblemon:stripped_apricorn_log",
                 "cobblemon:stripped_apricorn_wood" -> getOptionalBlock(
                    "cobblemon:apricorn_seed"
            );

            default -> Blocks.AIR;
        };
    }

    private static Block getOptionalBlock(
            String id
    ) {

        try {
            Block block =
                    BuiltInRegistries.BLOCK.get(
                            ResourceLocation.parse(
                                    id
                            )
                    );

            return block == null ? Blocks.AIR : block;
        } catch (Exception ignored) {
            return Blocks.AIR;
        }
    }

    public static boolean canReplantAt(
            ServerLevel level,
            BlockPos pos
    ) {

        if (level == null || pos == null) {
            return false;
        }

        if (!level.getBlockState(pos).isAir()) {
            return false;
        }

        BlockState below =
                level.getBlockState(
                        pos.below()
                );

        return below.isFaceSturdy(
                level,
                pos.below(),
                Direction.UP
        );
    }
}
