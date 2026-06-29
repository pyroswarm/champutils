package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import com.champutils.profession.actives.ActiveAbilityRegistry;
import com.champutils.profession.actives.ActiveEffectManager;

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

                    ProfessionToolConfig.ToolData toolData =
                            ProfessionToolUtil.getToolData(
                                    stack
                            );

                    // Active profession tool abilities are always shift + right-click.
                    // Plain right-click stays vanilla so tools do not block normal interactions.
                    if (toolData != null && !serverPlayer.isShiftKeyDown()) {
                        return InteractionResult.PASS;
                    }

                    if (toolId != null &&
                            !ProfessionToolMetadata.isIdentified(
                                    stack
                            )
                    ) {
                        serverPlayer.sendSystemMessage(
                                Component.literal(
                                        "§cYou must identify this equipment before you can use it."
                                )
                        );

                        return InteractionResult.FAIL;
                    }

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

                    if (!ActiveEffectManager.canActivateAbility(
                            serverPlayer,
                            ability,
                            stack
                    )) {
                        String activeName = ActiveEffectManager.getCurrentActiveDisplayName(serverPlayer);
                        ActiveEffectManager.clearAllActiveEffects(serverPlayer);
                        serverPlayer.displayClientMessage(
                                Component.literal(
                                        "§eTurned off " + (activeName == null ? "your previous active ability" : activeName) + " before starting the new ability."
                                ),
                                true
                        );
                    }

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

                    com.champutils.quest.QuestManager.recordProfessionAbility(serverPlayer, ability);

                    setCooldown(
                            serverPlayer,
                            ability,
                            getCooldownAfterDurationSeconds(serverPlayer, toolData)
                    );

                    return InteractionResult.SUCCESS;
                }
        );
    }

    private static boolean isOnCooldown(
            ServerPlayer player,
            String ability
    ) {
        Map<String, Long> playerCooldowns =
                COOLDOWNS.get(player.getUUID());

        if (playerCooldowns == null) {
            return false;
        }

        Long expiresAt = playerCooldowns.get(ability);

        if (expiresAt == null) {
            return false;
        }

        if (System.currentTimeMillis() >= expiresAt) {
            playerCooldowns.remove(ability);
            if (playerCooldowns.isEmpty()) {
                COOLDOWNS.remove(player.getUUID());
            }
            return false;
        }

        return true;
    }

    private static int getCooldownAfterDurationSeconds(ServerPlayer player, ProfessionToolConfig.ToolData toolData) {
        int cooldown = Math.max(0, toolData.activeCooldownSeconds);
        int duration = Math.max(0, toolData.activeDurationSeconds);
        int perLevel = Math.max(0, toolData.activeDurationSecondsPerLevel);
        if (perLevel > 0 && toolData.profession != null && !toolData.profession.isBlank()) {
            try {
                ProfessionType profession = ProfessionType.valueOf(toolData.profession.trim().toUpperCase(java.util.Locale.ROOT));
                duration += Math.max(0, ProfessionManager.getLevel(player, profession) - 1) * perLevel;
            } catch (Exception ignored) {
            }
        }
        return duration + cooldown;
    }

    private static void setCooldown(
            ServerPlayer player,
            String ability,
            int seconds
    ) {
        if (seconds <= 0) {
            return;
        }

        COOLDOWNS
                .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .put(ability, System.currentTimeMillis() + seconds * 1000L);
    }

    private static void sendCooldownMessage(
            ServerPlayer player,
            String ability
    ) {
        Map<String, Long> playerCooldowns =
                COOLDOWNS.get(player.getUUID());

        long remainingSeconds = 1L;

        if (playerCooldowns != null) {
            Long expiresAt = playerCooldowns.get(ability);
            if (expiresAt != null) {
                remainingSeconds = Math.max(
                        1L,
                        (expiresAt - System.currentTimeMillis() + 999L) / 1000L
                );
            }
        }

        player.displayClientMessage(
                Component.literal(
                        "§c" + formatWords(ability) + " is on cooldown for " + remainingSeconds + "s."
                ),
                true
        );
    }


    private static boolean isHoeTool(
            ProfessionToolConfig.ToolData toolData
    ) {

        if (toolData == null || toolData.baseItem == null) {
            return false;
        }

        return toolData.baseItem
                .toLowerCase()
                .contains("hoe");
    }

    private static boolean canUseTool(
            ServerPlayer player,
            ProfessionToolConfig.ToolData toolData
    ) {

        // Profession tool active abilities are no longer level-gated.
        // Profession level now improves rewards/drop rates instead of locking tool usage.
        return true;
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
                .trim()
                .replaceAll("\\bXp\\b", "XP");
    }

}