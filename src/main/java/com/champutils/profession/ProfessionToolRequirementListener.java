package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public class ProfessionToolRequirementListener {

    public static void register() {

        /*
         Mining / Forestry / Farming
         */
        PlayerBlockBreakEvents.BEFORE.register(
                (
                        world,
                        player,
                        pos,
                        state,
                        blockEntity
                ) -> {

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return true;
                    }

                    return canUseTool(
                            serverPlayer,
                            player.getMainHandItem()
                    );
                }
        );

        /*
         Fishing rods
         */
        UseItemCallback.EVENT.register(
                (
                        player,
                        world,
                        hand
                ) -> {

                    ItemStack stack =
                            player.getItemInHand(hand);

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return InteractionResultHolder.pass(stack);
                    }

                    boolean allowed =
                            canUseTool(
                                    serverPlayer,
                                    stack
                            );

                    if (!allowed) {
                        return InteractionResultHolder.fail(stack);
                    }

                    return InteractionResultHolder.pass(stack);
                }
        );
    }

    private static boolean canUseTool(
            ServerPlayer player,
            ItemStack stack
    ) {

        if (stack.isEmpty()) {
            return true;
        }

        Item heldItem =
                stack.getItem();

        String heldId =
                BuiltInRegistries.ITEM
                        .getKey(heldItem)
                        .toString();

        for (
                Map.Entry<String, ProfessionToolConfig.ToolData> entry :
                ProfessionToolConfig.TOOLS.entrySet()
        ) {

            String toolId =
                    "champutils:" +
                            entry.getKey();

            if (!heldId.equals(toolId)) {
                continue;
            }

            ProfessionToolConfig.ToolData data =
                    entry.getValue();

            ProfessionType profession =
                    ProfessionType.valueOf(
                            data.profession
                    );

            int playerLevel =
                    ProfessionManager.getLevel(
                            player,
                            profession
                    );

            if (
                    playerLevel <
                            data.requiredLevel
            ) {
                player.displayClientMessage(
                        Component.literal(
                                "Requires " +
                                        data.profession +
                                        " Level " +
                                        data.requiredLevel
                        ),
                        true
                );

                return false;
            }

            return true;
        }

        return true;
    }
}