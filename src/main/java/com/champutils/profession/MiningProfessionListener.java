package com.champutils.profession;

import com.champutils.profession.actives.ActiveEffectManager;
import com.champutils.profession.actives.MiningBlockUtil;
import com.champutils.profession.passives.DurabilitySavePassive;
import com.champutils.profession.passives.PassiveRegistry;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class MiningProfessionListener {

    private static final Set<UUID> BREAKING_EXTRA_BLOCKS =
            new HashSet<>();

    private static final Set<String> MANUALLY_PROCESSED_EXTRA_BLOCKS =
            new HashSet<>();

    public static void register() {

        PlayerBlockBreakEvents.BEFORE.register(
                (
                        world,
                        player,
                        pos,
                        state,
                        blockEntity
                ) -> {

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return true;
                    }

                    if (
                            isBreakingExtraBlock(
                                    serverPlayer
                            ) &&
                                    consumeManuallyProcessedExtraBlock(
                                            serverPlayer,
                                            pos
                                    )
                    ) {
                        return true;
                    }

                    Block block =
                            state.getBlock();

                    String blockId =
                            block.builtInRegistryHolder()
                                    .key()
                                    .location()
                                    .toString();

                    if (
                            ProfessionBlockTracker.isPlayerPlaced(
                                    serverPlayer.serverLevel(),
                                    pos
                            )
                    ) {
                        DurabilitySavePassive.markRecentBreak(
                                serverPlayer,
                                serverPlayer.serverLevel(),
                                pos,
                                false
                        );

                        ProfessionBlockTracker.remove(
                                serverPlayer.serverLevel(),
                                pos
                        );

                        if (!isBreakingExtraBlock(
                                serverPlayer
                        )) {
                            handleVeinMinerActive(
                                    serverPlayer,
                                    pos,
                                    state,
                                    blockId
                            );

                            handleExcavationActive(
                                    serverPlayer,
                                    pos,
                                    state
                            );
                        }

                        return true;
                    }

                    if (
                            MiningBlockUtil.isPickaxeBlock(
                                    serverPlayer.serverLevel(),
                                    pos,
                                    state
                            )
                    ) {
                        DurabilitySavePassive.markRecentBreak(
                                serverPlayer,
                                serverPlayer.serverLevel(),
                                pos,
                                true
                        );
                    }

                    if (
                            !isBreakingExtraBlock(
                                    serverPlayer
                            ) &&
                                    handleAutoSmeltActive(
                                            serverPlayer,
                                            pos,
                                            state,
                                            blockId
                                    )
                    ) {
                        return false;
                    }

                    handleAscendedToolTracker(
                            serverPlayer,
                            blockId
                    );

                    if (
                            MiningBlockUtil.isPickaxeBlock(
                                    serverPlayer.serverLevel(),
                                    pos,
                                    state
                            )
                    ) {
                        PassiveRegistry.applyMiningPassives(
                                serverPlayer,
                                serverPlayer.serverLevel(),
                                pos,
                                blockId
                        );
                    }

                    Integer xp =
                            ProfessionConfig
                                    .SETTINGS
                                    .miningXp
                                    .get(blockId);

                    if (xp == null || xp <= 0) {
                        if (!isBreakingExtraBlock(
                                serverPlayer
                        )) {
                            handleExcavationActive(
                                    serverPlayer,
                                    pos,
                                    state
                            );
                        }

                        return true;
                    }

                    ProfessionManager.addXp(
                            serverPlayer,
                            ProfessionType.MINING,
                            xp
                    );

                    ProfessionLootManager.rollReward(
                            serverPlayer,
                            ProfessionType.MINING
                    );

                    if (!isBreakingExtraBlock(
                            serverPlayer
                    )) {
                        handleVeinMinerActive(
                                serverPlayer,
                                pos,
                                state,
                                blockId
                        );

                        handleExcavationActive(
                                serverPlayer,
                                pos,
                                state
                        );
                    }

                    return true;
                }
        );
    }

    private static boolean handleAutoSmeltActive(
            ServerPlayer player,
            BlockPos pos,
            BlockState state,
            String blockId
    ) {

        if (!ActiveEffectManager.hasAutoSmelt(
                player,
                player.getMainHandItem()
        )) {
            return false;
        }

        SmeltDrop smeltDrop =
                getSmeltDrop(
                        blockId
                );

        if (smeltDrop == null) {
            return false;
        }

        if (!MiningBlockUtil.isPickaxeBlock(
                player.serverLevel(),
                pos,
                state
        )) {
            return false;
        }

        handleAscendedToolTracker(
                player,
                blockId
        );

        PassiveRegistry.applyMiningPassives(
                player,
                player.serverLevel(),
                pos,
                blockId
        );

        Integer xp =
                ProfessionConfig
                        .SETTINGS
                        .miningXp
                        .get(blockId);

        if (xp != null && xp > 0) {
            ProfessionManager.addXp(
                    player,
                    ProfessionType.MINING,
                    xp
            );

            ProfessionLootManager.rollReward(
                    player,
                    ProfessionType.MINING
            );
        }

        removeBlockWithoutDrops(
                player.serverLevel(),
                pos,
                state
        );

        giveOrDrop(
                player,
                new ItemStack(
                        smeltDrop.item,
                        smeltDrop.amount
                )
        );

        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "§6Auto-Smelt: §fOre smelted."
                ),
                true
        );

        handleVeinMinerActive(
                player,
                pos,
                state,
                blockId
        );

        handleExcavationActive(
                player,
                pos,
                state
        );

        return true;
    }

    private static void giveOrDrop(
            ServerPlayer player,
            ItemStack stack
    ) {

        if (!player.getInventory().add(
                stack
        )) {
            player.drop(
                    stack,
                    false
            );
        }
    }

    private static SmeltDrop getSmeltDrop(
            String blockId
    ) {

        return switch (blockId) {

            /*
             * Stone-style blocks.
             */
            case "minecraft:stone",
                 "minecraft:cobblestone" ->
                    new SmeltDrop(
                            Items.STONE,
                            1
                    );

            case "minecraft:deepslate",
                 "minecraft:cobbled_deepslate" ->
                    new SmeltDrop(
                            Items.DEEPSLATE,
                            1
                    );

            case "minecraft:netherrack" ->
                    new SmeltDrop(
                            Items.NETHER_BRICK,
                            1
                    );

            case "minecraft:clay" ->
                    new SmeltDrop(
                            Items.TERRACOTTA,
                            1
                    );

            /*
             * Ore blocks.
             */
            case "minecraft:iron_ore",
                 "minecraft:deepslate_iron_ore" ->
                    new SmeltDrop(
                            Items.IRON_INGOT,
                            1
                    );

            case "minecraft:gold_ore",
                 "minecraft:deepslate_gold_ore",
                 "minecraft:nether_gold_ore" ->
                    new SmeltDrop(
                            Items.GOLD_INGOT,
                            1
                    );

            case "minecraft:copper_ore",
                 "minecraft:deepslate_copper_ore" ->
                    new SmeltDrop(
                            Items.COPPER_INGOT,
                            3
                    );

            case "minecraft:ancient_debris" ->
                    new SmeltDrop(
                            Items.NETHERITE_SCRAP,
                            1
                    );

            default ->
                    null;
        };
    }

    private static void removeBlockWithoutDrops(
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {

        /*
         * Do not call destroyBlock(...) here.
         *
         * This is being called from inside the player's block-break callback.
         * Calling destroyBlock(...) from inside that callback can cause the
         * block to be processed twice in some cases, which is why auto-smelt
         * sometimes gave the normal cobblestone/raw drop instead of the
         * smelted result.
         *
         * We manually play the break effect, replace the block with air,
         * and then give the smelted item ourselves.
         */
        level.levelEvent(
                2001,
                pos,
                Block.getId(
                        state
                )
        );

        level.setBlock(
                pos,
                Blocks.AIR.defaultBlockState(),
                3
        );
    }

    private record SmeltDrop(
            Item item,
            int amount
    ) {
    }


    private static void handleVeinMinerActive(
            ServerPlayer player,
            BlockPos center,
            BlockState centerState,
            String centerBlockId
    ) {

        if (!ActiveEffectManager.hasTimedEffect(
                player,
                "vein_miner_burst",
                player.getMainHandItem()
        )) {
            return;
        }

        if (!isOreBlock(
                centerBlockId
        )) {
            return;
        }

        ServerLevel level =
                player.serverLevel();

        if (!MiningBlockUtil.isPickaxeBlock(
                level,
                center,
                centerState
        )) {
            return;
        }

        ItemStack tool =
                player.getMainHandItem();

        if (
                tool == null ||
                        tool.isEmpty() ||
                        !ProfessionToolMetadata.isProfessionTool(
                                tool
                        ) ||
                        !ProfessionToolMetadata.isIdentified(
                                tool
                        ) ||
                        ProfessionToolMetadata.isBroken(
                                tool
                        )
        ) {
            return;
        }

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolUtil.getToolData(
                        tool
                );

        int maxBlocks =
                toolData == null
                        ? 30
                        : Math.max(
                                1,
                                toolData.maxVeinBlocks
                        );

        Set<BlockPos> visited =
                new HashSet<>();

        Queue<BlockPos> queue =
                new ArrayDeque<>();

        visited.add(
                center
        );
        queue.add(
                center
        );

        int broken =
                0;

        BREAKING_EXTRA_BLOCKS.add(
                player.getUUID()
        );

        try {
            while (
                    !queue.isEmpty() &&
                            broken < maxBlocks
            ) {
                BlockPos current =
                        queue.poll();

                for (Direction direction : Direction.values()) {
                    if (broken >= maxBlocks) {
                        break;
                    }

                    BlockPos target =
                            current.relative(
                                    direction
                            );

                    if (!visited.add(
                            target
                    )) {
                        continue;
                    }

                    BlockState targetState =
                            level.getBlockState(
                                    target
                            );

                    if (targetState.isAir()) {
                        continue;
                    }

                    String targetBlockId =
                            targetState.getBlock()
                                    .builtInRegistryHolder()
                                    .key()
                                    .location()
                                    .toString();

                    if (!targetBlockId.equals(
                            centerBlockId
                    )) {
                        continue;
                    }

                    if (!MiningBlockUtil.isPickaxeBlock(
                            level,
                            target,
                            targetState
                    )) {
                        continue;
                    }

                    if (ProfessionBlockTracker.isPlayerPlaced(
                            level,
                            target
                    )) {
                        continue;
                    }

                    queue.add(
                            target
                    );

                    processExtraMiningBlock(
                            player,
                            level,
                            target,
                            targetState,
                            targetBlockId
                    );

                    broken++;
                }
            }
        }
        finally {
            BREAKING_EXTRA_BLOCKS.remove(
                    player.getUUID()
            );
        }

        if (broken > 0) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§bVein Miner Burst: §fMined §e" +
                                    broken +
                                    " §fextra ore blocks."
                    ),
                    true
            );
        }
    }

    private static void processExtraMiningBlock(
            ServerPlayer player,
            ServerLevel level,
            BlockPos target,
            BlockState targetState,
            String targetBlockId
    ) {

        handleAscendedToolTracker(
                player,
                targetBlockId
        );

        PassiveRegistry.applyMiningPassives(
                player,
                level,
                target,
                targetBlockId
        );

        Integer xp =
                ProfessionConfig
                        .SETTINGS
                        .miningXp
                        .get(targetBlockId);

        if (xp != null && xp > 0) {
            ProfessionManager.addXp(
                    player,
                    ProfessionType.MINING,
                    xp
            );

            ProfessionLootManager.rollReward(
                    player,
                    ProfessionType.MINING
            );
        }

        if (ActiveEffectManager.hasAutoSmelt(
                player,
                player.getMainHandItem()
        )) {
            SmeltDrop smeltDrop =
                    getSmeltDrop(
                            targetBlockId
                    );

            if (smeltDrop != null) {
                removeBlockWithoutDrops(
                        level,
                        target,
                        targetState
                );

                giveOrDrop(
                        player,
                        new ItemStack(
                                smeltDrop.item,
                                smeltDrop.amount
                        )
                );

                return;
            }
        }

        MANUALLY_PROCESSED_EXTRA_BLOCKS.add(
                extraBlockKey(
                        player,
                        target
                )
        );

        level.destroyBlock(
                target,
                true,
                player
        );
    }

    private static boolean isOreBlock(
            String blockId
    ) {

        return blockId != null &&
                (
                        blockId.endsWith(
                                "_ore"
                        ) ||
                                blockId.equals(
                                        "minecraft:ancient_debris"
                                )
                );
    }

    private static void handleExcavationActive(
            ServerPlayer player,
            BlockPos center,
            BlockState centerState
    ) {

        if (!ActiveEffectManager.hasExcavation(
                player,
                player.getMainHandItem()
        )) {
            return;
        }

        ServerLevel level =
                player.serverLevel();

        if (!MiningBlockUtil.isPickaxeBlock(
                level,
                center,
                centerState
        )) {
            return;
        }

        ItemStack tool =
                player.getMainHandItem();

        if (
                tool == null ||
                        tool.isEmpty() ||
                        !ProfessionToolMetadata.isProfessionTool(
                                tool
                        ) ||
                        !ProfessionToolMetadata.isIdentified(
                                tool
                        ) ||
                        ProfessionToolMetadata.isBroken(
                                tool
                        )
        ) {
            return;
        }

        BREAKING_EXTRA_BLOCKS.add(
                player.getUUID()
        );

        try {
            breakExcavationArea(
                    player,
                    center
            );
        }
        finally {
            BREAKING_EXTRA_BLOCKS.remove(
                    player.getUUID()
            );
        }
    }

    private static void breakExcavationArea(
            ServerPlayer player,
            BlockPos center
    ) {

        ServerLevel level =
                player.serverLevel();

        Direction direction =
                getMiningPlaneDirection(
                        player
                );

        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {

                if (a == 0 && b == 0) {
                    continue;
                }

                BlockPos target =
                        offsetForPlane(
                                center,
                                direction,
                                a,
                                b
                        );

                BlockState targetState =
                        level.getBlockState(
                                target
                        );

                if (!MiningBlockUtil.isPickaxeBlock(
                        level,
                        target,
                        targetState
                )) {
                    continue;
                }


                String targetBlockId =
                        targetState.getBlock()
                                .builtInRegistryHolder()
                                .key()
                                .location()
                                .toString();

                /*
                 * ServerLevel.destroyBlock does not reliably flow back through
                 * the player's normal block-break callback in every mapping/version.
                 * Apply mining passives explicitly here so Excavation's extra
                 * 3x3 blocks can trigger Double/Triple/Quadruple/Quintuple Drop.
                 * PassiveRegistry still enforces natural-only blocks.
                 */
                PassiveRegistry.applyMiningPassives(
                        player,
                        level,
                        target,
                        targetBlockId
                );

                if (ActiveEffectManager.hasAutoSmelt(
                        player,
                        player.getMainHandItem()
                )) {
                    SmeltDrop smeltDrop =
                            getSmeltDrop(
                                    targetBlockId
                            );

                    if (smeltDrop != null) {
                        removeBlockWithoutDrops(
                                level,
                                target,
                                targetState
                        );

                        giveOrDrop(
                                player,
                                new ItemStack(
                                        smeltDrop.item,
                                        smeltDrop.amount
                                )
                        );

                        continue;
                    }
                }

                level.destroyBlock(
                        target,
                        true,
                        player
                );
            }
        }
    }

    private static Direction getMiningPlaneDirection(
            ServerPlayer player
    ) {

        float pitch =
                player.getXRot();

        if (pitch > 55.0F || pitch < -55.0F) {
            return Direction.UP;
        }

        return player.getDirection();
    }

    private static BlockPos offsetForPlane(
            BlockPos center,
            Direction direction,
            int a,
            int b
    ) {

        return switch (direction) {
            case EAST, WEST ->
                    center.offset(
                            0,
                            a,
                            b
                    );

            case NORTH, SOUTH ->
                    center.offset(
                            a,
                            b,
                            0
                    );

            case UP, DOWN ->
                    center.offset(
                            a,
                            0,
                            b
                    );
        };
    }


    private static boolean consumeManuallyProcessedExtraBlock(
            ServerPlayer player,
            BlockPos pos
    ) {

        return MANUALLY_PROCESSED_EXTRA_BLOCKS.remove(
                extraBlockKey(
                        player,
                        pos
                )
        );
    }

    private static String extraBlockKey(
            ServerPlayer player,
            BlockPos pos
    ) {

        return player.getUUID() + ":" + pos.asLong();
    }

    private static boolean isBreakingExtraBlock(
            ServerPlayer player
    ) {

        return BREAKING_EXTRA_BLOCKS.contains(
                player.getUUID()
        );
    }

    private static void handleAscendedToolTracker(
            ServerPlayer player,
            String blockId
    ) {

        ItemStack stack =
                player.getMainHandItem();

        if (
                stack == null ||
                        stack.isEmpty()
        ) {
            return;
        }

        if (
                !ProfessionToolMetadata.isProfessionTool(
                        stack
                ) ||
                        !ProfessionToolMetadata.isIdentified(
                                stack
                        ) ||
                        !ProfessionToolMetadata.isAscended(
                                stack
                        )
        ) {
            return;
        }

        String selectedTracker =
                ProfessionToolMetadata.getSelectedTracker(
                        stack
                );

        if (
                selectedTracker == null ||
                        selectedTracker.isBlank()
        ) {
            return;
        }

        if (
                !doesBlockMatchTracker(
                        selectedTracker,
                        blockId
                )
        ) {
            return;
        }

        ProfessionToolMetadata.incrementTracker(
                stack,
                selectedTracker,
                1L
        );

        ProfessionToolManager.refreshToolStack(
                stack
        );
    }

    private static boolean doesBlockMatchTracker(
            String trackerId,
            String blockId
    ) {

        return switch (trackerId) {

            case "stone_mined" ->
                    isStoneTrackedBlock(
                            blockId
                    );

            case "coal_mined" ->
                    blockId.equals("minecraft:coal_ore") ||
                            blockId.equals("minecraft:deepslate_coal_ore");

            case "copper_mined" ->
                    blockId.equals("minecraft:copper_ore") ||
                            blockId.equals("minecraft:deepslate_copper_ore");

            case "iron_mined" ->
                    blockId.equals("minecraft:iron_ore") ||
                            blockId.equals("minecraft:deepslate_iron_ore");

            case "gold_mined" ->
                    blockId.equals("minecraft:gold_ore") ||
                            blockId.equals("minecraft:deepslate_gold_ore") ||
                            blockId.equals("minecraft:nether_gold_ore");

            case "redstone_mined" ->
                    blockId.equals("minecraft:redstone_ore") ||
                            blockId.equals("minecraft:deepslate_redstone_ore");

            case "lapis_mined" ->
                    blockId.equals("minecraft:lapis_ore") ||
                            blockId.equals("minecraft:deepslate_lapis_ore");

            case "emerald_mined" ->
                    blockId.equals("minecraft:emerald_ore") ||
                            blockId.equals("minecraft:deepslate_emerald_ore");

            case "diamonds_mined" ->
                    blockId.equals("minecraft:diamond_ore") ||
                            blockId.equals("minecraft:deepslate_diamond_ore");

            case "ancient_debris_mined" ->
                    blockId.equals("minecraft:ancient_debris");

            default ->
                    false;
        };
    }

    private static boolean isStoneTrackedBlock(
            String blockId
    ) {

        return switch (blockId) {
            case "minecraft:stone",
                 "minecraft:deepslate",
                 "minecraft:granite",
                 "minecraft:diorite",
                 "minecraft:andesite",
                 "minecraft:tuff",
                 "minecraft:calcite",
                 "minecraft:dripstone_block",
                 "minecraft:blackstone",
                 "minecraft:basalt",
                 "minecraft:smooth_basalt" ->
                    true;

            default ->
                    false;
        };
    }
}
