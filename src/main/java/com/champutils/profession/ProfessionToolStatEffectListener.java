package com.champutils.profession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

public class ProfessionToolStatEffectListener {

    private static final ResourceLocation MINING_SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "champutils",
                    "profession_tool_mining_speed"
            );

    private static final ResourceLocation BLOCK_BREAK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "player.block_break_speed"
            );

    private ProfessionToolStatEffectListener() {
    }

    public static void register() {

        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        updateMiningSpeedModifier(
                                player
                        );
                    }
                }
        );
    }

    private static void updateMiningSpeedModifier(
            ServerPlayer player
    ) {

        AttributeInstance attribute =
                getBlockBreakSpeedAttribute(
                        player
                );

        if (attribute == null) {
            return;
        }

        attribute.removeModifier(
                MINING_SPEED_MODIFIER_ID
        );

        ItemStack stack =
                player.getMainHandItem();

        if (
                stack == null ||
                        stack.isEmpty() ||
                        !ProfessionToolMetadata.isProfessionTool(stack) ||
                        !ProfessionToolMetadata.isIdentified(stack) ||
                        ProfessionToolMetadata.isBroken(stack)
        ) {
            return;
        }

        String toolId =
                ProfessionToolUtil.getToolId(
                        stack
                );

        if (toolId == null) {
            return;
        }

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(
                        toolId
                );

        if (toolData == null) {
            return;
        }

        ProfessionType professionType;

        try {
            professionType =
                    ProfessionType.valueOf(
                            toolData.profession.toUpperCase()
                    );
        }
        catch (Exception ignored) {
            return;
        }

        int playerLevel =
                ProfessionManager.getLevel(
                        player,
                        professionType
                );

        if (playerLevel < toolData.requiredLevel) {
            return;
        }

        double miningSpeed =
                ProfessionToolUtil.getStat(
                        stack,
                        "miningSpeed"
                );

        if (miningSpeed <= 0.0D) {
            return;
        }

        /*
         * Keep the visible Polymer/client mining animation close to the
         * server-side percentage multiplier.
         *
         * IMPORTANT: for the smoothest feel, the configured baseItem should
         * match toolTier so the client starts from the same vanilla baseline
         * as the server. Example: toolTier WOOD + minecraft:wooden_pickaxe.
         */
        double modifierAmount =
                Math.max(
                        0.0D,
                        ProfessionToolManager.getMiningSpeedMultiplier(
                                miningSpeed
                        ) - 1.0D
                );

        AttributeModifier modifier =
                new AttributeModifier(
                        MINING_SPEED_MODIFIER_ID,
                        modifierAmount,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                );

        attribute.addTransientModifier(
                modifier
        );
    }

    private static AttributeInstance getBlockBreakSpeedAttribute(
            ServerPlayer player
    ) {

        Holder<Attribute> holder =
                BuiltInRegistries.ATTRIBUTE
                        .getHolder(
                                BLOCK_BREAK_SPEED_ID
                        )
                        .orElse(
                                null
                        );

        if (holder == null) {
            return null;
        }

        return player.getAttribute(
                holder
        );
    }
}
