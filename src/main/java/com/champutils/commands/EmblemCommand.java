package com.champutils.commands;

import com.champutils.emblem.EmblemConfig;
import com.champutils.emblem.EmblemManager;
import com.champutils.menu.EmblemMenu;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class EmblemCommand {

    private EmblemCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("emblems")
                        .executes(context -> { EmblemMenu.open(context.getSource().getPlayerOrException()); return 1; })
                        .then(Commands.literal("menu").executes(context -> { EmblemMenu.open(context.getSource().getPlayerOrException()); return 1; }))
                        .then(Commands.literal("craft")
                                .then(Commands.argument("emblem", StringArgumentType.word())
                                        .suggests((context, builder) -> { for (String id : EmblemConfig.CONFIG.emblems.keySet()) builder.suggest(id); return builder.buildFuture(); })
                                        .executes(context -> craft(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "emblem")))))
                        .then(Commands.literal("give")
                                .requires(source -> source.hasPermission(4))
                                .then(Commands.argument("emblem", StringArgumentType.word())
                                        .suggests((context, builder) -> { for (String id : EmblemConfig.CONFIG.emblems.keySet()) builder.suggest(id); return builder.buildFuture(); })
                                        .executes(context -> give(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "emblem"), 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> give(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "emblem"), IntegerArgumentType.getInteger(context, "amount"))))))
        ));
    }

    private static int craft(ServerPlayer player, String emblemId) {
        EmblemManager.CraftResult result = EmblemManager.craft(player, emblemId);
        if (!result.success()) {
            player.sendSystemMessage(Component.literal(result.error()).withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Crafted " + result.displayName() + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int give(ServerPlayer player, String emblemId, int amount) {
        ItemStack stack = EmblemManager.createEmblemStack(emblemId, amount);
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("Unknown emblem: " + emblemId).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.sendSystemMessage(Component.literal("Gave emblem.").withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
