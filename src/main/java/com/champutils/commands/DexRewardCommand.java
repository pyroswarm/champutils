package com.champutils.commands;

import com.champutils.dex.DexProgressManager;
import com.champutils.dex.DexRewardsMenu;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DexRewardCommand {

    private DexRewardCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    Commands.literal("dexcheck")
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                int caught = DexProgressManager.getCaughtCount(player);
                                int total = DexProgressManager.getTotalPokemon();
                                double percent = DexProgressManager.getCompletionPercent(player);
                                int unlocked = DexProgressManager.getUnlockedPercent(player);

                                player.sendSystemMessage(Component.literal("Pokédex Progress").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                                player.sendSystemMessage(Component.literal("Caught: ").withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(String.valueOf(caught)).withStyle(ChatFormatting.WHITE))
                                        .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                                        .append(Component.literal(String.valueOf(total)).withStyle(ChatFormatting.WHITE)));
                                player.sendSystemMessage(Component.literal("Completion: ").withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(String.format("%.2f%%", percent)).withStyle(ChatFormatting.YELLOW)));
                                player.sendSystemMessage(Component.literal("Highest unlocked reward: ").withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(unlocked + "%").withStyle(ChatFormatting.GOLD)));
                                return 1;
                            })
            );

            dispatcher.register(
                    Commands.literal("dexrewards")
                            .executes(context -> {
                                DexRewardsMenu.open(context.getSource().getPlayerOrException());
                                return 1;
                            })
            );
        });
    }
}
