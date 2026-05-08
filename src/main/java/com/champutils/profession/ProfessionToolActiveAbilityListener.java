package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfessionToolActiveAbilityListener {

    private static final Map<UUID, Map<String, Long>> COOLDOWNS =
            new HashMap<>();

    public static void register() {

        UseItemCallback.EVENT.register(
                (
                        player,
                        world,
                        hand
                ) -> {

                    ItemStack stack =
                            player.getItemInHand(
                                    hand
                            );

                    if (world.isClientSide()) {
                        return InteractionResultHolder.pass(
                                stack
                        );
                    }

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return InteractionResultHolder.pass(
                                stack
                        );
                    }

                    ProfessionToolConfig.ToolData toolData =
                            ProfessionToolUtil.getToolData(
                                    stack
                            );

                    if (
                            toolData == null ||
                                    toolData.activeAbility == null ||
                                    toolData.activeAbility.isBlank()
                    ) {
                        return InteractionResultHolder.pass(
                                stack
                        );
                    }

                    if (
                            !canUseTool(
                                    serverPlayer,
                                    toolData
                            )
                    ) {
                        return InteractionResultHolder.fail(
                                stack
                        );
                    }

                    String ability =
                            toolData.activeAbility
                                    .toLowerCase();

                    if (
                            isOnCooldown(
                                    serverPlayer,
                                    ability
                            )
                    ) {
                        sendCooldownMessage(
                                serverPlayer,
                                ability
                        );

                        return InteractionResultHolder.fail(
                                stack
                        );
                    }

                    boolean used =
                            switch (ability) {
                                case "prospect" ->
                                        useProspect(
                                                serverPlayer
                                        );

                                case "vein_burst" ->
                                        useVeinBurst(
                                                serverPlayer
                                        );

                                default ->
                                        false;
                            };

                    if (!used) {
                        return InteractionResultHolder.pass(
                                stack
                        );
                    }

                    setCooldown(
                            serverPlayer,
                            ability,
                            toolData.activeCooldownSeconds
                    );

                    return InteractionResultHolder.success(
                            stack
                    );
                }
        );
    }

    private static boolean canUseTool(
            ServerPlayer player,
            ProfessionToolConfig.ToolData toolData
    ) {

        try {
            ProfessionType professionType =
                    ProfessionType.valueOf(
                            toolData.profession
                                    .toUpperCase()
                    );

            int playerLevel =
                    ProfessionManager.getLevel(
                            player,
                            professionType
                    );

            if (
                    playerLevel <
                            toolData.requiredLevel
            ) {
                player.sendSystemMessage(
                        Component.literal(
                                "§cYou need " +
                                        formatWords(
                                                toolData.profession
                                        ) +
                                        " level " +
                                        toolData.requiredLevel +
                                        " to use this ability."
                        )
                );

                return false;
            }

        } catch (Exception ignored) {
        }

        return true;
    }

    private static boolean useProspect(
            ServerPlayer player
    ) {

        BlockPos origin =
                player.blockPosition();

        int radius =
                12;

        FoundOre nearest =
                null;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {

                    BlockPos pos =
                            origin.offset(
                                    x,
                                    y,
                                    z
                            );

                    BlockState state =
                            player.serverLevel()
                                    .getBlockState(
                                            pos
                                    );

                    String blockId =
                            getBlockId(
                                    state.getBlock()
                            );

                    if (
                            !ProfessionConfig
                                    .SETTINGS
                                    .miningXp
                                    .containsKey(
                                            blockId
                                    )
                    ) {
                        continue;
                    }

                    if (
                            ProfessionBlockTracker.isPlayerPlaced(
                                    player.serverLevel(),
                                    pos
                            )
                    ) {
                        continue;
                    }

                    double distance =
                            Math.sqrt(
                                    origin.distSqr(
                                            pos
                                    )
                            );

                    if (
                            nearest == null ||
                                    distance < nearest.distance
                    ) {
                        nearest =
                                new FoundOre(
                                        blockId,
                                        distance
                                );
                    }
                }
            }
        }

        if (nearest == null) {
            player.displayClientMessage(
                    Component.literal(
                            "§7Prospect found no natural ore nearby."
                    ),
                    true
            );

            player.playNotifySound(
                    SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS,
                    0.7f,
                    0.7f
            );

            return true;
        }

        player.sendSystemMessage(
                Component.literal(
                        "§bProspect: §f" +
                                formatBlockName(
                                        nearest.blockId
                                ) +
                                " §7is about §e" +
                                Math.round(
                                        nearest.distance
                                ) +
                                " blocks §7away."
                )
        );

        player.displayClientMessage(
                Component.literal(
                        "§bProspect found " +
                                formatBlockName(
                                        nearest.blockId
                                )
                ),
                true
        );

        player.playNotifySound(
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.9f,
                1.4f
        );

        return true;
    }

    private static boolean useVeinBurst(
            ServerPlayer player
    ) {

        ServerLevel level =
                player.serverLevel();

        BlockPos origin =
                player.blockPosition();

        int radius =
                5;

        int maxBlocks =
                20;

        int broken =
                0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {

                    if (broken >= maxBlocks) {
                        break;
                    }

                    BlockPos pos =
                            origin.offset(
                                    x,
                                    y,
                                    z
                            );

                    BlockState state =
                            level.getBlockState(
                                    pos
                            );

                    String blockId =
                            getBlockId(
                                    state.getBlock()
                            );

                    Integer xp =
                            ProfessionConfig
                                    .SETTINGS
                                    .miningXp
                                    .get(
                                            blockId
                                    );

                    if (xp == null || xp <= 0) {
                        continue;
                    }

                    if (
                            ProfessionBlockTracker.isPlayerPlaced(
                                    level,
                                    pos
                            )
                    ) {
                        continue;
                    }

                    boolean destroyed =
                            level.destroyBlock(
                                    pos,
                                    true,
                                    player
                            );

                    if (!destroyed) {
                        continue;
                    }

                    ProfessionManager.addXp(
                            player,
                            ProfessionType.MINING,
                            xp
                    );

                    broken++;
                }
            }
        }

        if (broken <= 0) {
            player.displayClientMessage(
                    Component.literal(
                            "§7Vein Burst found no natural ore nearby."
                    ),
                    true
            );

            player.playNotifySound(
                    SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS,
                    0.7f,
                    0.7f
            );

            return true;
        }

        player.displayClientMessage(
                Component.literal(
                        "§6Vein Burst broke §e" +
                                broken +
                                " §6ores!"
                ),
                true
        );

        player.playNotifySound(
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS,
                0.6f,
                1.4f
        );

        return true;
    }

    private static boolean isOnCooldown(
            ServerPlayer player,
            String ability
    ) {

        Map<String, Long> playerCooldowns =
                COOLDOWNS.get(
                        player.getUUID()
                );

        if (playerCooldowns == null) {
            return false;
        }

        long expiresAt =
                playerCooldowns.getOrDefault(
                        ability,
                        0L
                );

        return System.currentTimeMillis() < expiresAt;
    }

    private static void setCooldown(
            ServerPlayer player,
            String ability,
            int seconds
    ) {

        COOLDOWNS.computeIfAbsent(
                player.getUUID(),
                id -> new HashMap<>()
        ).put(
                ability,
                System.currentTimeMillis() +
                        seconds * 1000L
        );
    }

    private static void sendCooldownMessage(
            ServerPlayer player,
            String ability
    ) {

        Map<String, Long> playerCooldowns =
                COOLDOWNS.get(
                        player.getUUID()
                );

        if (playerCooldowns == null) {
            return;
        }

        long expiresAt =
                playerCooldowns.getOrDefault(
                        ability,
                        0L
                );

        long remaining =
                Math.max(
                        0,
                        expiresAt - System.currentTimeMillis()
                );

        long seconds =
                (long) Math.ceil(
                        remaining / 1000D
                );

        player.displayClientMessage(
                Component.literal(
                        "§cAbility on cooldown: " +
                                seconds +
                                "s"
                ),
                true
        );
    }

    private static String getBlockId(
            Block block
    ) {

        return block.builtInRegistryHolder()
                .key()
                .location()
                .toString();
    }

    private static String formatBlockName(
            String blockId
    ) {

        if (blockId == null || blockId.isBlank()) {
            return "Unknown";
        }

        String name =
                blockId.contains(":")
                        ? blockId.substring(
                        blockId.indexOf(":") + 1
                )
                        : blockId;

        return formatWords(
                name
        );
    }

    private static String formatWords(
            String value
    ) {

        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized =
                value.replace("_", " ")
                        .replace("-", " ")
                        .trim()
                        .toLowerCase();

        String[] parts =
                normalized.split("\\s+");

        StringBuilder builder =
                new StringBuilder();

        for (String part : parts) {

            if (part.isBlank()) {
                continue;
            }

            builder.append(
                    Character.toUpperCase(
                            part.charAt(0)
                    )
            );

            if (part.length() > 1) {
                builder.append(
                        part.substring(1)
                );
            }

            builder.append(" ");
        }

        return builder
                .toString()
                .trim();
    }

    private static class FoundOre {

        String blockId;
        double distance;

        FoundOre(
                String blockId,
                double distance
        ) {
            this.blockId =
                    blockId;

            this.distance =
                    distance;
        }
    }
}