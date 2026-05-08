package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
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
         Left click block breaking.
         This prevents unidentified profession gear from breaking blocks.
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
         Right click block interactions.
         This prevents unidentified profession gear from using block interactions.
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

        /*
         Right click air / item use.
         This prevents unidentified profession gear from triggering any use behavior.
        */
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

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return InteractionResultHolder.pass(
                                stack
                        );
                    }

                    if (
                            !canUseTool(
                                    serverPlayer,
                                    stack
                            )
                    ) {
                        return InteractionResultHolder.fail(
                                stack
                        );
                    }

                    return InteractionResultHolder.pass(
                            stack
                    );
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

        if (
                !ProfessionToolMetadata.isIdentified(
                        stack
                )
        ) {
            sendUnidentifiedMessage(
                    player,
                    toolData
            );

            return false;
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

            sendLevelDeniedMessage(
                    player,
                    toolData
            );

            return false;
        }

        return true;
    }

    private static void sendUnidentifiedMessage(
            ServerPlayer player,
            ProfessionToolConfig.ToolData toolData
    ) {

        if (!canSendMessage(player)) {
            return;
        }

        player.sendSystemMessage(
                Component.literal(
                        "You must identify " +
                                getDisplayName(
                                        toolData
                                ) +
                                " before you can use it."
                ).withStyle(
                        ChatFormatting.RED
                )
        );
    }

    private static void sendLevelDeniedMessage(
            ServerPlayer player,
            ProfessionToolConfig.ToolData toolData
    ) {

        if (!canSendMessage(player)) {
            return;
        }

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

    private static boolean canSendMessage(
            ServerPlayer player
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
            return false;
        }

        LAST_DENY_MESSAGE.put(
                player.getUUID(),
                now
        );

        return true;
    }

    private static String getDisplayName(
            ProfessionToolConfig.ToolData toolData
    ) {

        if (
                toolData != null &&
                        toolData.displayName != null &&
                        !toolData.displayName.isBlank()
        ) {
            return toolData.displayName;
        }

        return "this item";
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
