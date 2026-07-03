package com.champutils.commands;

import com.champutils.exploration.ItemBindInteractionListener;
import com.champutils.exploration.ItemBindRegistry;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class ItemBindCommand {

    private static final String[] BASIC_REWARDS = new String[] {
            "none", "potion", "super_potion", "pokeballs", "great_ball", "food", "revive",
            "full_heal", "antidote", "paralyze_heal", "exp_candy", "apricorns"
    };

    private ItemBindCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("itembind")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.literal("cancel")
                                .executes(ItemBindCommand::cancel))
                        .then(Commands.literal("list")
                                .executes(ItemBindCommand::list))
                        .then(Commands.literal("clear")
                                .executes(ItemBindCommand::beginClear))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("bindName", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ItemBindRegistry.allBindings().stream().map(binding -> binding.bindName), builder))
                                                .executes(ItemBindCommand::reset))))
                        .then(Commands.literal("dialogue")
                                .then(Commands.argument("bindName", StringArgumentType.word())
                                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(context -> beginBind(context, "none", true)))))
                        .then(Commands.argument("rewardName", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(BASIC_REWARDS, builder))
                                .executes(context -> beginBind(context, StringArgumentType.getString(context, "rewardName"), false))
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(context -> beginBind(context, StringArgumentType.getString(context, "rewardName"), false))))
        ));
    }

    private static int beginBind(CommandContext<CommandSourceStack> context, String rewardName, boolean dialogueOnly) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String bindName;
            String dialogue = null;

            if (dialogueOnly) {
                bindName = StringArgumentType.getString(context, "bindName");
                dialogue = StringArgumentType.getString(context, "message");
            } else {
                bindName = rewardName;
                try { dialogue = StringArgumentType.getString(context, "message"); }
                catch (Exception ignored) {}
            }

            ItemBindInteractionListener.beginBind(player, bindName, rewardName, dialogue);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Only players can start an item bind."));
            return 0;
        }
    }

    private static int cancel(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean cancelled = ItemBindInteractionListener.cancelBind(player);
            player.sendSystemMessage(Component.literal(cancelled ? "Cancelled item bind." : "You do not have an active item bind.").withStyle(cancelled ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            return cancelled ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Only players can cancel an item bind."));
            return 0;
        }
    }

    private static int beginClear(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ItemBindInteractionListener.beginUnbind(player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Only players can clear/bind from in game."));
            return 0;
        }
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Item-bound NPCs/Pokemon:").withStyle(ChatFormatting.YELLOW), false);
        if (ItemBindRegistry.allBindings().isEmpty()) {
            source.sendSuccess(() -> Component.literal("None yet."), false);
            return 1;
        }

        for (ItemBindRegistry.Binding binding : ItemBindRegistry.allBindings()) {
            source.sendSuccess(() -> Component.literal("- " + binding.bindName + " -> " + binding.rewardName + " @ " + binding.world + " / " + binding.entityUuid).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            String bindName = StringArgumentType.getString(context, "bindName");
            UUID playerUuid = target.getUUID();
            boolean reset = ItemBindRegistry.resetClaim(playerUuid, bindName);
            context.getSource().sendSuccess(() -> Component.literal(reset ? "Reset claim for " + target.getName().getString() + " on " + bindName + "." : "That player had not claimed " + bindName + ".").withStyle(reset ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
            return reset ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Could not reset that claim."));
            return 0;
        }
    }
}
