package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FishingProfessionListener {

    private static final Map<UUID, Long> ACTIVE_CASTS =
            new HashMap<>();

    public static void register() {

        UseItemCallback.EVENT.register(
                (player, world, hand) -> {

                    if (world.isClientSide()) {
                        return InteractionResultHolder.pass(
                                player.getItemInHand(hand)
                        );
                    }

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return InteractionResultHolder.pass(
                                player.getItemInHand(hand)
                        );
                    }

                    ItemStack stack =
                            player.getItemInHand(hand);

                    if (!(stack.getItem() instanceof FishingRodItem)) {
                        return InteractionResultHolder.pass(stack);
                    }

                    UUID uuid =
                            player.getUUID();

                    if (!ACTIVE_CASTS.containsKey(uuid)) {
                        ACTIVE_CASTS.put(
                                uuid,
                                System.currentTimeMillis()
                        );
                    } else {
                        long castTime =
                                ACTIVE_CASTS.get(uuid);

                        ACTIVE_CASTS.remove(uuid);

                        long duration =
                                System.currentTimeMillis() - castTime;

                        if (duration < 3000) {
                            return InteractionResultHolder.pass(stack);
                        }

                        Integer xp =
                                ProfessionConfig
                                        .SETTINGS
                                        .fishingXp
                                        .getOrDefault(
                                                "default",
                                                10
                                        );

                        ProfessionManager.addXp(
                                serverPlayer,
                                ProfessionType.FISHING,
                                xp
                        );

                        ProfessionLootManager.rollReward(
                                serverPlayer,
                                ProfessionType.FISHING
                        );
                    }

                    return InteractionResultHolder.pass(stack);
                }
        );
    }
}