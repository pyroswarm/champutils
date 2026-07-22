package com.champutils.claims;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LandClaimSelectionItemListener {
    public static final String CLAIMING_STICK_NAME = "Claiming Stick";
    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();

    private LandClaimSelectionItemListener() {}

    public static ItemStack createClaimingStick() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(CLAIMING_STICK_NAME).withStyle(ChatFormatting.GOLD));
        return stack;
    }

    public static boolean isClaimingStick(ItemStack stack) {
        if (stack == null || !stack.is(Items.STICK)) return false;
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return name != null && CLAIMING_STICK_NAME.equals(name.getString());
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!isClaimingStick(serverPlayer.getMainHandItem())) return InteractionResult.PASS;
            if (LandClaimCommand.denyIslanderClaiming(serverPlayer)) return InteractionResult.FAIL;
            long now = System.currentTimeMillis();
            long last = LAST_CLICK.getOrDefault(serverPlayer.getUUID(), 0L);
            if (now - last < 250L) return InteractionResult.FAIL;
            LAST_CLICK.put(serverPlayer.getUUID(), now);
            boolean sneak = serverPlayer.isShiftKeyDown();
            LandClaimCommand.setPosAt(serverPlayer, !sneak, hitResult.getBlockPos());
            serverPlayer.sendSystemMessage(Component.literal(sneak
                    ? "Set claim position 2 with your Claiming Stick."
                    : "Set claim position 1 with your Claiming Stick. Sneak-right-click to set position 2.").withStyle(ChatFormatting.GOLD));
            return InteractionResult.FAIL;
        });
    }
}
