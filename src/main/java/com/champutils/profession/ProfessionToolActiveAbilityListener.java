package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import com.champutils.profession.actives.ActiveAbilityRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfessionToolActiveAbilityListener {

    private static final Map<UUID, Map<String, Long>> COOLDOWNS =
            new HashMap<>();

    public static void register() {

        UseBlockCallback.EVENT.register(
                (
                        player,
                        world,
                        hand,
                        hitResult
                ) -> {

                    if (world.isClientSide()) {
                        return InteractionResult.PASS;
                    }

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return InteractionResult.PASS;
                    }

                    ItemStack stack =
                            player.getItemInHand(
                                    hand
                            );

                    String toolId =
                            ProfessionToolUtil.getToolId(
                                    stack
                            );

                    if (toolId != null &&
                            !ProfessionToolMetadata.isIdentified(
                                    stack
                            )
                    ) {
                        serverPlayer.sendSystemMessage(
                                Component.literal(
                                        "§cYou must identify this item before using its active ability."
                                )
                        );

                        return InteractionResult.FAIL;
                    }

                    ProfessionToolConfig.ToolData toolData =
                            ProfessionToolUtil.getToolData(
                                    stack
                            );

                    if (
                            toolData != null &&
                                    ProfessionToolMetadata.isBroken(
                                            stack
                                    )
                    ) {
                        serverPlayer.sendSystemMessage(
                                Component.literal(
                                        "§cThis item is broken. Use /itemroll repair before using it again."
                                )
                        );

                        return InteractionResult.FAIL;
                    }

                    if (
                            toolData == null ||
                                    toolData.activeAbility == null ||
                                    toolData.activeAbility.isBlank()
                    ) {
                        return InteractionResult.PASS;
                    }

                    if (
                            !canUseTool(
                                    serverPlayer,
                                    toolData
                            )
                    ) {
                        return InteractionResult.FAIL;
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

                        return InteractionResult.FAIL;
                    }

                    boolean used =
                            ActiveAbilityRegistry.use(
                                    ability,
                                    serverPlayer,
                                    stack
                            );

                    if (!used) {
                        return InteractionResult.PASS;
                    }

                    setCooldown(
                            serverPlayer,
                            ability,
                            toolData.activeCooldownSeconds
                    );

                    return InteractionResult.SUCCESS;
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
                        Math.max(
                                0,
                                seconds
                        ) * 1000L
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

}