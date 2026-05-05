package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfessionToolRequirementListener {

    private static final Map<UUID, Long> LAST_DENY_MESSAGE =
            new HashMap<>();

    private static final long DENY_MESSAGE_COOLDOWN_MS =
            1500L;

    public static void register() {

        /*
         Left click block breaking
         */
        AttackBlockCallback.EVENT.register(
                (
                        player,
                        world,
                        hand,
                        pos,
                        direction
                ) -> {

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return InteractionResult.PASS;
                    }

                    ItemStack stack =
                            player.getItemInHand(
                                    hand
                            );

                    if (
                            !canUseTool(
                                    serverPlayer,
                                    stack
                            )
                    ) {
                        return InteractionResult.FAIL;
                    }

                    return InteractionResult.PASS;
                }
        );

        /*
         Right click block interactions only.
         No UseItemCallback here.
         This avoids interfering with vanilla/Cobblemon rod use.
        */
        UseBlockCallback.EVENT.register(
                (
                        player,
                        world,
                        hand,
                        hitResult
                ) -> {

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return InteractionResult.PASS;
                    }

                    ItemStack stack =
                            player.getItemInHand(
                                    hand
                            );

                    if (
                            !canUseTool(
                                    serverPlayer,
                                    stack
                            )
                    ) {
                        return InteractionResult.FAIL;
                    }

                    return InteractionResult.PASS;
                }
        );
    }

    private static boolean canUseTool(
            ServerPlayer player,
            ItemStack stack
    ) {

        String toolId =
                ProfessionToolUtil.getToolId(
                        stack
                );

        if (toolId == null) {
            return true;
        }

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(
                        toolId
                );

        if (toolData == null) {
            return true;
        }

        ProfessionType professionType;

        try {
            professionType =
                    ProfessionType.valueOf(
                            toolData.profession
                                    .toUpperCase()
                    );
        }
        catch (Exception e) {
            return true;
        }

        int playerLevel =
                ProfessionManager.getLevel(
                        player,
                        professionType
                );

        if (
                playerLevel <
                        toolData.requiredLevel
        ) {

            sendDeniedMessage(
                    player,
                    toolData
            );

            return false;
        }

        return true;
    }

    private static void sendDeniedMessage(
            ServerPlayer player,
            ProfessionToolConfig.ToolData toolData
    ) {

        long now =
                System.currentTimeMillis();

        long last =
                LAST_DENY_MESSAGE.getOrDefault(
                        player.getUUID(),
                        0L
                );

        if (
                now - last <
                        DENY_MESSAGE_COOLDOWN_MS
        ) {
            return;
        }

        LAST_DENY_MESSAGE.put(
                player.getUUID(),
                now
        );

        player.sendSystemMessage(
                Component.literal(
                        "You need " +
                                formatWords(
                                        toolData.profession
                                ) +
                                " level " +
                                toolData.requiredLevel +
                                " to use this item."
                ).withStyle(
                        ChatFormatting.RED
                )
        );
    }

    private static String formatWords(
            String value
    ) {

        if (
                value == null ||
                        value.isBlank()
        ) {
            return "";
        }

        String normalized =
                value.replace(
                                "_",
                                " "
                        )
                        .replace(
                                "-",
                                " "
                        )
                        .trim()
                        .toLowerCase();

        String[] parts =
                normalized.split(
                        "\\s+"
                );

        StringBuilder builder =
                new StringBuilder();

        for (
                String part :
                parts
        ) {

            if (
                    part.isBlank()
            ) {
                continue;
            }

            builder.append(
                    Character.toUpperCase(
                            part.charAt(
                                    0
                            )
                    )
            );

            if (
                    part.length() > 1
            ) {
                builder.append(
                        part.substring(
                                1
                        )
                );
            }

            builder.append(
                    " "
            );
        }

        return builder
                .toString()
                .trim();
    }
}