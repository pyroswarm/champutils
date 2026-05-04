package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class FishingProfessionListener {

    private static final Random RANDOM =
            new Random();

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

                    /*
                     First click = cast
                     */
                    if (!ACTIVE_CASTS.containsKey(uuid)) {
                        ACTIVE_CASTS.put(
                                uuid,
                                System.currentTimeMillis()
                        );
                    }

                    /*
                     Second click = reel
                     */
                    else {
                        long castTime =
                                ACTIVE_CASTS.get(uuid);

                        ACTIVE_CASTS.remove(uuid);

                        long duration =
                                System.currentTimeMillis() -
                                        castTime;

                        /*
                         Prevent spam abuse
                         */
                        if (duration < 3000) {
                            return InteractionResultHolder.pass(stack);
                        }

                        awardFishingXp(serverPlayer);
                    }

                    return InteractionResultHolder.pass(stack);
                }
        );
    }

    private static void awardFishingXp(
            ServerPlayer player
    ) {
        Integer xp =
                ProfessionConfig
                        .SETTINGS
                        .fishingXp
                        .get("default");

        if (xp == null || xp <= 0) {
            xp = 10;
        }

        ProfessionManager.addXp(
                player,
                ProfessionType.FISHING,
                xp
        );

        rollReward(player);
    }

    private static void rollReward(
            ServerPlayer player
    ) {
        var reward =
                ProfessionConfig
                        .SETTINGS
                        .rewards
                        .get("FISHING_RARE_DROP");

        if (reward == null) {
            return;
        }

        if (RANDOM.nextDouble() > reward.chance) {
            return;
        }

        int amount =
                reward.minAmount +
                        RANDOM.nextInt(
                                reward.maxAmount -
                                        reward.minAmount + 1
                        );

        ProfessionActionBarManager.sendRareDropMessage(
                player,
                reward.itemId,
                amount
        );
    }
}