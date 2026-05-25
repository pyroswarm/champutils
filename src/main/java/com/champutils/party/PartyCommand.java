package com.champutils.party;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class PartyCommand {

    private PartyCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("party")
                    .executes(context -> info(context.getSource().getPlayerOrException()))
                    .then(Commands.literal("create")
                            .executes(context -> respond(context.getSource().getPlayerOrException(), PartyManager.create(context.getSource().getPlayerOrException()))))
                    .then(Commands.literal("invite")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> respond(
                                            context.getSource().getPlayerOrException(),
                                            PartyManager.invite(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"))
                                    ))))
                    .then(Commands.literal("accept")
                            .executes(context -> respond(context.getSource().getPlayerOrException(), PartyManager.accept(context.getSource().getPlayerOrException()))))
                    .then(Commands.literal("deny")
                            .executes(context -> respond(context.getSource().getPlayerOrException(), PartyManager.deny(context.getSource().getPlayerOrException()))))
                    .then(Commands.literal("leave")
                            .executes(context -> respond(context.getSource().getPlayerOrException(), PartyManager.leave(context.getSource().getPlayerOrException()))))
                    .then(Commands.literal("disband")
                            .executes(context -> respond(context.getSource().getPlayerOrException(), PartyManager.disband(context.getSource().getPlayerOrException()))))
                    .then(Commands.literal("kick")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> respond(
                                            context.getSource().getPlayerOrException(),
                                            PartyManager.kick(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"))
                                    ))))
                    .then(Commands.literal("promote")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> respond(
                                            context.getSource().getPlayerOrException(),
                                            PartyManager.promote(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"))
                                    ))))
                    .then(Commands.literal("info")
                            .executes(context -> info(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("list")
                            .executes(context -> info(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("chat")
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(context -> chat(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "message")
                                    )))));

            dispatcher.register(Commands.literal("p")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(context -> chat(
                                    context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "message")
                            ))));
        });
    }

    private static int respond(ServerPlayer player, PartyManager.Result result) {
        if (result == null) return 0;
        if (!result.silent() && result.message() != null && !result.message().isBlank()) {
            player.sendSystemMessage(Component.literal(result.message()).withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        return result.success() ? 1 : 0;
    }

    private static int info(ServerPlayer player) {
        PartyManager.PartySnapshot party = PartyManager.snapshot(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(Component.literal("You are not in a party. Use /party create to start one.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Party members (" + party.memberIds().size() + "/" + party.maxSize() + "):").withStyle(ChatFormatting.LIGHT_PURPLE));
        for (UUID memberId : party.memberIds()) {
            boolean leader = party.ownerId().equals(memberId);
            player.sendSystemMessage(Component.literal("- " + party.nameOf(memberId) + (leader ? " (Leader)" : "")).withStyle(leader ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int chat(ServerPlayer player, String message) {
        return com.champutils.chat.ServerChatManager.send(player, com.champutils.chat.ChatMode.PARTY, message, true) ? 1 : 0;
    }
}
