package com.champutils.commands;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.ProfessionToolManager;
import com.champutils.profession.ProfessionToolMetadata;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public final class ItemLockCommand {

    private ItemLockCommand() {
    }

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(
                            Commands.literal("itemlock")
                                    .executes(context -> {

                                        ServerPlayer player =
                                                context.getSource()
                                                        .getPlayerOrException();

                                        ItemStack stack =
                                                player.getMainHandItem();

                                        if (
                                                stack == null ||
                                                        stack.isEmpty() ||
                                                        !ProfessionToolMetadata.isProfessionTool(stack)
                                        ) {
                                            player.sendSystemMessage(
                                                    Component.literal(
                                                            "§cHold a Champ profession item to lock or unlock it."
                                                    )
                                            );

                                            return 0;
                                        }

                                        boolean locked =
                                                !ProfessionToolMetadata.isLocked(stack);

                                        ProfessionToolMetadata.setLocked(
                                                stack,
                                                locked
                                        );

                                        ProfessionToolManager.refreshToolStack(
                                                stack
                                        );

                                        ProfessionNotificationSettings.playSound(player, 
                                                locked
                                                        ? SoundEvents.EXPERIENCE_ORB_PICKUP
                                                        : SoundEvents.UI_BUTTON_CLICK.value(),
                                                SoundSource.PLAYERS,
                                                1.0F,
                                                locked ? 1.35F : 0.8F
                                        );

                                        player.sendSystemMessage(
                                                Component.literal(
                                                        locked
                                                                ? "§6§lItem Locked §7- protected from rerolls, salvage, and item UI actions."
                                                                : "§7Item unlocked."
                                                )
                                        );

                                        return 1;
                                    })
                    );
                }
        );
    }
}
