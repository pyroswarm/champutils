package com.champutils.profession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProfessionToolStatEffectListener {

    private static final ResourceLocation MINING_SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath("champutils", "profession_tool_mining_speed");

    private static final ResourceLocation VANILLA_TOOL_NERF_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath("champutils", "vanilla_tool_speed_nerf");

    private static final double VANILLA_TOOL_SPEED_NERF = -0.65D;

    private static final ResourceLocation BLOCK_BREAK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "player.block_break_speed");

    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static final Map<UUID, AppliedState> APPLIED = new ConcurrentHashMap<>();

    private record AppliedState(String key, boolean professionModifier, boolean vanillaModifier) {}

    private ProfessionToolStatEffectListener() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % UPDATE_INTERVAL_TICKS != 0) return;

            Set<UUID> online = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                online.add(player.getUUID());
                updateMiningSpeedModifier(player);
            }
            APPLIED.keySet().removeIf(uuid -> !online.contains(uuid));
        });
    }

    private static void updateMiningSpeedModifier(ServerPlayer player) {
        AttributeInstance attribute = getAttribute(player, BLOCK_BREAK_SPEED_ID);
        if (attribute == null) return;

        ItemStack stack = player.getMainHandItem();
        String key = stackKey(stack);
        UUID uuid = player.getUUID();
        AppliedState previous = APPLIED.get(uuid);
        if (previous != null && previous.key.equals(key)) return;

        if (previous != null) {
            if (previous.professionModifier) attribute.removeModifier(MINING_SPEED_MODIFIER_ID);
            if (previous.vanillaModifier) attribute.removeModifier(VANILLA_TOOL_NERF_MODIFIER_ID);
        } else {
            // Safety cleanup in case the server reloaded while a transient modifier existed.
            attribute.removeModifier(MINING_SPEED_MODIFIER_ID);
            attribute.removeModifier(VANILLA_TOOL_NERF_MODIFIER_ID);
        }

        boolean professionModifier = false;
        boolean vanillaModifier = false;

        if (!isUsableProfessionTool(player, stack)) {
            if (isVanillaProfessionTool(stack)) {
                attribute.addTransientModifier(new AttributeModifier(
                        VANILLA_TOOL_NERF_MODIFIER_ID,
                        VANILLA_TOOL_SPEED_NERF,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
                vanillaModifier = true;
            }
            APPLIED.put(uuid, new AppliedState(key, false, vanillaModifier));
            return;
        }

        double miningSpeed = ProfessionToolUtil.getStat(stack, "miningSpeed");
        if (miningSpeed > 0.0D) {
            double modifierAmount = Math.max(0.0D, ProfessionToolManager.getMiningSpeedMultiplier(miningSpeed) - 1.0D);
            if (modifierAmount != 0.0D) {
                attribute.addTransientModifier(new AttributeModifier(
                        MINING_SPEED_MODIFIER_ID,
                        modifierAmount,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
                professionModifier = true;
            }
        }

        APPLIED.put(uuid, new AppliedState(key, professionModifier, false));
    }

    private static String stackKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        // The component string is only built twice per second now, instead of every tick. It catches tool stat changes,
        // identified/broken changes, and normal item swaps without constantly removing/readding attributes.
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + stack.getComponents().toString();
    }

    private static boolean isUsableProfessionTool(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ProfessionToolMetadata.isProfessionTool(stack) ||
                !ProfessionToolMetadata.isIdentified(stack) || ProfessionToolMetadata.isBroken(stack)) {
            return false;
        }

        String toolId = ProfessionToolUtil.getToolId(stack);
        if (toolId == null) return false;

        ProfessionToolConfig.ToolData toolData = ProfessionToolConfig.TOOLS.get(toolId);
        if (toolData == null || toolData.profession == null) return false;

        try {
            ProfessionType.valueOf(toolData.profession.toUpperCase());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isVanillaProfessionTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (ProfessionToolMetadata.isProfessionTool(stack)) return false;
        return stack.getItem() instanceof PickaxeItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof HoeItem;
    }

    private static AttributeInstance getAttribute(ServerPlayer player, ResourceLocation attributeId) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).orElse(null);
        return holder == null ? null : player.getAttribute(holder);
    }
}
