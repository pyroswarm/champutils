package com.champutils.profession.actives;

import com.champutils.profession.ProfessionBlockTracker;
import com.champutils.profession.ProfessionConfig;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class ProspectAbility implements ProfessionActiveAbility {

    private static final int DEFAULT_RADIUS =
            28;

    @Override
    public String id() {
        return "prospect";
    }

    @Override
    public boolean use(
            ServerPlayer player,
            ItemStack stack
    ) {

        BlockPos origin =
                player.blockPosition();

        int radius =
                getRadius(
                        stack
                );

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

                    Block block =
                            player.serverLevel()
                                    .getBlockState(
                                            pos
                                    )
                                    .getBlock();

                    String blockId =
                            block.builtInRegistryHolder()
                                    .key()
                                    .location()
                                    .toString();

                    if (
                            ProfessionConfig.SETTINGS == null ||
                                    ProfessionConfig.SETTINGS.miningXp == null ||
                                    !ProfessionConfig.SETTINGS.miningXp.containsKey(
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
                                        pos,
                                        distance
                                );
                    }
                }
            }
        }

        if (nearest == null) {
            player.sendSystemMessage(
                    Component.literal(
                            "§7Prospect found no natural ore within §e" +
                                    radius +
                                    " blocks§7."
                    )
            );

            player.displayClientMessage(
                    Component.literal(
                            "§7No natural ore detected nearby."
                    ),
                    true
            );

            player.playNotifySound(
                    SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS,
                    0.7F,
                    0.7F
            );

            return true;
        }

        String oreName =
                formatBlockName(
                        nearest.blockId
                );

        int blocksAway =
                Math.max(
                        1,
                        (int) Math.round(
                                nearest.distance
                        )
                );

        String direction =
                getDirectionText(
                        origin,
                        nearest.pos
                );

        player.sendSystemMessage(
                Component.literal(
                        "§bProspect: §f" +
                                oreName +
                                " §7detected §e" +
                                blocksAway +
                                " blocks §7to the §e" +
                                direction +
                                "§7."
                )
        );

        player.displayClientMessage(
                Component.literal(
                        "§b" +
                                oreName +
                                " detected " +
                                blocksAway +
                                " blocks " +
                                direction
                ),
                true
        );

        player.playNotifySound(
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.9F,
                1.4F
        );

        return true;
    }

    private int getRadius(
            ItemStack stack
    ) {

        double rolledRadius =
                ProfessionToolUtil.getStat(
                        stack,
                        "prospectRadius"
                );

        if (rolledRadius <= 0.0D) {
            return DEFAULT_RADIUS;
        }

        return Math.max(
                8,
                (int) Math.round(
                        rolledRadius
                )
        );
    }

    private String getDirectionText(
            BlockPos origin,
            BlockPos target
    ) {

        int dx =
                target.getX() - origin.getX();
        int dy =
                target.getY() - origin.getY();
        int dz =
                target.getZ() - origin.getZ();

        String vertical =
                "";

        if (dy > 2) {
            vertical =
                    "upward ";
        }
        else if (dy < -2) {
            vertical =
                    "downward ";
        }

        String horizontal;

        if (Math.abs(dx) >= Math.abs(dz) * 2) {
            horizontal =
                    dx > 0
                            ? "east"
                            : "west";
        }
        else if (Math.abs(dz) >= Math.abs(dx) * 2) {
            horizontal =
                    dz > 0
                            ? "south"
                            : "north";
        }
        else {
            String northSouth =
                    dz > 0
                            ? "south"
                            : "north";
            String eastWest =
                    dx > 0
                            ? "east"
                            : "west";

            horizontal =
                    northSouth +
                            "-" +
                            eastWest;
        }

        return vertical +
                horizontal;
    }

    private String formatBlockName(
            String blockId
    ) {

        if (blockId == null || blockId.isBlank()) {
            return "Unknown Ore";
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

    private String formatWords(
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

        return builder.toString()
                .trim();
    }

    private static class FoundOre {

        String blockId;
        BlockPos pos;
        double distance;

        FoundOre(
                String blockId,
                BlockPos pos,
                double distance
        ) {
            this.blockId =
                    blockId;
            this.pos =
                    pos;
            this.distance =
                    distance;
        }
    }
}
