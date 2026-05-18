package com.champutils.profession.actives;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NatureSenseAbility implements ProfessionActiveAbility {

    @Override
    public String id() {
        return "nature_sense";
    }

    @Override
    public boolean use(
            ServerPlayer player,
            ItemStack stack
    ) {

        ProfessionToolConfig.ToolData data =
                ProfessionToolUtil.getToolData(
                        stack
                );

        int radius =
                data == null || data.natureSenseRadius <= 0
                        ? 40
                        : data.natureSenseRadius;

        ServerLevel level =
                player.serverLevel();

        BlockPos origin =
                player.blockPosition();

        List<FoundTree> found =
                new ArrayList<>();

        int radiusSq =
                radius * radius;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -16; y <= 24; y++) {
                for (int z = -radius; z <= radius; z++) {

                    BlockPos pos =
                            origin.offset(
                                    x,
                                    y,
                                    z
                            );

                    if (pos.distSqr(origin) > radiusSq) {
                        continue;
                    }

                    BlockState state =
                            level.getBlockState(
                                    pos
                            );

                    if (!ForestryBlockUtil.isForestryLog(state)) {
                        continue;
                    }

                    found.add(
                            new FoundTree(
                                    ForestryBlockUtil.getBlockId(
                                            state.getBlock()
                                    ),
                                    pos.immutable(),
                                    pos.distSqr(origin)
                            )
                    );
                }
            }
        }

        if (found.isEmpty()) {
            player.sendSystemMessage(
                    Component.literal(
                            "§7Nature Sense found no valid logs nearby."
                    )
            );

            player.displayClientMessage(
                    Component.literal(
                            "§7No valid logs nearby."
                    ),
                    true
            );

            return true;
        }

        found.sort(
                Comparator.comparingDouble(
                        FoundTree::distanceSq
                )
        );

        player.sendSystemMessage(
                Component.literal(
                        "§2Nature Sense §7nearest trees:"
                )
        );

        int shown =
                Math.min(
                        3,
                        found.size()
                );

        for (int i = 0; i < shown; i++) {
            FoundTree tree =
                    found.get(i);

            int distance =
                    (int) Math.round(
                            Math.sqrt(
                                    tree.distanceSq()
                            )
                    );

            String direction =
                    directionFrom(
                            origin,
                            tree.pos()
                    );

            player.sendSystemMessage(
                    Component.literal(
                            "§a- §f" +
                                    formatBlockName(
                                            tree.blockId()
                                    ) +
                                    " §7" +
                                    distance +
                                    " blocks " +
                                    direction
                    )
            );
        }

        FoundTree nearest =
                found.get(0);

        player.displayClientMessage(
                Component.literal(
                        "§2Nature Sense: §f" +
                                formatBlockName(
                                        nearest.blockId()
                                ) +
                                " nearby"
                ),
                true
        );

        ProfessionNotificationSettings.playSound(player, 
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.6F,
                1.6F
        );

        return true;
    }

    private static String directionFrom(
            BlockPos origin,
            BlockPos target
    ) {

        int dx = target.getX() - origin.getX();
        int dz = target.getZ() - origin.getZ();

        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "east" : "west";
        }

        if (dz != 0) {
            return dz > 0 ? "south" : "north";
        }

        return "here";
    }

    private static String formatBlockName(
            String blockId
    ) {

        String name =
                blockId == null
                        ? "Tree"
                        : blockId.substring(
                                blockId.indexOf(':') + 1
                        );

        name =
                name.replace(
                        "_",
                        " "
                );

        StringBuilder builder =
                new StringBuilder();

        for (String part : name.split(" ")) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
            builder.append(" ");
        }

        return builder.toString().trim();
    }

    private record FoundTree(
            String blockId,
            BlockPos pos,
            double distanceSq
    ) {
    }
}
