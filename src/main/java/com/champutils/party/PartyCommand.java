package com.champutils.party;

import com.champutils.network.NetworkPlayerDirectory;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class PartyCommand {

    private PartyCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("party")
                    .executes(context -> info(context.getSource().getPlayerOrException()))
                    .then(Commands.literal("create")
                            .executes(context -> run(context.getSource().getPlayerOrException(), PartyManager::create)))
                    .then(Commands.literal("invite")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(NetworkPlayerDirectory::suggestNames)
                                    .executes(context -> run(context.getSource().getPlayerOrException(), (player, callback) ->
                                            PartyManager.invite(player, StringArgumentType.getString(context, "player"), callback)))))
                    .then(Commands.literal("accept")
                            .executes(context -> run(context.getSource().getPlayerOrException(), PartyManager::accept)))
                    .then(Commands.literal("deny")
                            .executes(context -> run(context.getSource().getPlayerOrException(), PartyManager::deny)))
                    .then(Commands.literal("leave")
                            .executes(context -> run(context.getSource().getPlayerOrException(), PartyManager::leave)))
                    .then(Commands.literal("disband")
                            .executes(context -> run(context.getSource().getPlayerOrException(), PartyManager::disband)))
                    .then(Commands.literal("kick")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(NetworkPlayerDirectory::suggestNames)
                                    .executes(context -> run(context.getSource().getPlayerOrException(), (player, callback) ->
                                            PartyManager.kick(player, StringArgumentType.getString(context, "player"), callback)))))
                    .then(Commands.literal("promote")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(NetworkPlayerDirectory::suggestNames)
                                    .executes(context -> run(context.getSource().getPlayerOrException(), (player, callback) ->
                                            PartyManager.promote(player, StringArgumentType.getString(context, "player"), callback)))))
                    .then(Commands.literal("info").executes(context -> info(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("list").executes(context -> info(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("chat")
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(context -> chat(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "message"))))));

            dispatcher.register(Commands.literal("p")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(context -> chat(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "message")))));
        });
    }

    private static int run(ServerPlayer player, AsyncPartyAction action) {
        action.run(player, result -> respond(player, result));
        return 1;
    }

    private static void respond(ServerPlayer player, PartyManager.Result result) {
        if (result == null || result.silent()) return;
        if (result.message() != null && !result.message().isBlank()) {
            player.sendSystemMessage(Component.literal(result.message()).withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
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

    @FunctionalInterface
    private interface AsyncPartyAction {
        void run(ServerPlayer player, java.util.function.Consumer<PartyManager.Result> callback);
    }
}
