package com.champutils.commands;

import com.champutils.menu.QuestMenu;
import com.champutils.quest.QuestConfig;
import com.champutils.quest.QuestManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

public class QuestCommand {

    private static final SuggestionProvider<CommandSourceStack> CONTRACT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(QuestManager.eligibleContracts(context.getSource().getPlayerOrException()).stream().map(t -> t.id), builder);

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("quest")
                    .executes(context -> open(context.getSource().getPlayerOrException()))
                    .then(Commands.literal("menu")
                            .executes(context -> open(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("daily")
                            .then(Commands.literal("complete")
                                    .executes(context -> complete(context.getSource().getPlayerOrException(), true))))
                    .then(Commands.literal("weekly")
                            .then(Commands.literal("complete")
                                    .executes(context -> complete(context.getSource().getPlayerOrException(), false))))
                    .then(Commands.literal("contract")
                            .executes(context -> open(context.getSource().getPlayerOrException()))
                            .then(Commands.literal("list")
                                    .executes(context -> open(context.getSource().getPlayerOrException())))
                            .then(Commands.literal("buy")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests(CONTRACT_SUGGESTIONS)
                                            .executes(context -> buyContract(
                                                    context.getSource().getPlayerOrException(),
                                                    StringArgumentType.getString(context, "id")
                                            ))))
                            .then(Commands.literal("complete")
                                    .executes(context -> completeContract(context.getSource().getPlayerOrException())))
                            .then(Commands.literal("abandon")
                                    .executes(context -> abandonContract(context.getSource().getPlayerOrException()))))
            );
        });
    }

    private static int open(ServerPlayer player) {
        QuestMenu.open(player);
        return 1;
    }

    private static int complete(ServerPlayer player, boolean daily) {
        QuestManager.complete(player, daily);
        return 1;
    }

    private static int buyContract(ServerPlayer player, String id) {
        QuestManager.buyContract(player, id);
        return 1;
    }

    private static int completeContract(ServerPlayer player) {
        QuestManager.completeContract(player);
        return 1;
    }

    private static int abandonContract(ServerPlayer player) {
        QuestManager.abandonContract(player);
        return 1;
    }
}
