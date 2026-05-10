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
                        updateMiningSpeedModifier(player);
                    }
                }
        );
    }

    private static void updateMiningSpeedModifier(ServerPlayer player) {
        AttributeInstance attribute = getAttribute(player, BLOCK_BREAK_SPEED_ID);

        if (attribute == null) {
            return;
        }

        attribute.removeModifier(MINING_SPEED_MODIFIER_ID);

        ItemStack stack = player.getMainHandItem();

        if (!isUsableProfessionTool(player, stack)) {
            return;
        }

        double miningSpeed = ProfessionToolUtil.getStat(stack, "miningSpeed");

        if (miningSpeed <= 0.0D) {
            return;
        }

        double modifierAmount = Math.max(
                0.0D,
                ProfessionToolManager.getMiningSpeedMultiplier(miningSpeed) - 1.0D
        );

        AttributeModifier modifier = new AttributeModifier(
                MINING_SPEED_MODIFIER_ID,
                modifierAmount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );

        attribute.addTransientModifier(modifier);
    }

    private static boolean isUsableProfessionTool(ServerPlayer player, ItemStack stack) {
        if (
                stack == null ||
                        stack.isEmpty() ||
                        !ProfessionToolMetadata.isProfessionTool(stack) ||
                        !ProfessionToolMetadata.isIdentified(stack) ||
                        ProfessionToolMetadata.isBroken(stack)
        ) {
            return false;
        }

        String toolId = ProfessionToolUtil.getToolId(stack);

        if (toolId == null) {
            return false;
        }

        ProfessionToolConfig.ToolData toolData = ProfessionToolConfig.TOOLS.get(toolId);

        if (toolData == null || toolData.profession == null) {
            return false;
        }

        ProfessionType professionType;

        try {
            professionType = ProfessionType.valueOf(toolData.profession.toUpperCase());
        }
        catch (Exception ignored) {
            return false;
        }

        return ProfessionManager.getLevel(player, professionType) >= toolData.requiredLevel;
    }

    private static AttributeInstance getAttribute(ServerPlayer player, ResourceLocation attributeId) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE
                .getHolder(attributeId)
                .orElse(null);

        if (holder == null) {
            return null;
        }

        return player.getAttribute(holder);
    }
}
