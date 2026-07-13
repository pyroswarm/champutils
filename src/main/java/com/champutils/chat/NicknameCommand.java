package com.champutils.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class NicknameCommand {
    private NicknameCommand() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = Commands.literal("nick")
                    .executes(ctx -> status(ctx.getSource().getPlayerOrException()))
                    .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource().getPlayerOrException())))
                    .then(Commands.literal("reload").requires(source -> source.hasPermission(4)).executes(ctx -> reload(ctx.getSource().getPlayerOrException())))
                    .then(Commands.argument("nickname", StringArgumentType.greedyString()).executes(ctx -> set(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "nickname"))));
            dispatcher.register(root);
            dispatcher.register(Commands.literal("nickname").redirect(dispatcher.getRoot().getChild("nick")));
            dispatcher.register(Commands.literal("resetnick").executes(ctx -> clear(ctx.getSource().getPlayerOrException())));
            dispatcher.register(Commands.literal("realname")
                    .then(Commands.argument("nickname", StringArgumentType.greedyString()).executes(ctx -> realname(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "nickname")))));
        });
    }
    private static int status(ServerPlayer player) {
        if (!NicknameManager.canUse(player)) { player.sendSystemMessage(Component.literal("VIP or VIP+ is required to use /nick.").withStyle(ChatFormatting.RED)); return 0; }
        String current = NicknameManager.get(player);
        player.sendSystemMessage(Component.literal(current.isBlank() ? "You are not using a nickname. Use /nick <name>." : "Current nickname: " + current + ". Use /nick clear to remove it.").withStyle(ChatFormatting.YELLOW));
        return 1;
    }
    private static int set(ServerPlayer player, String value) {
        NicknameManager.set(player, value).whenComplete((message, error) -> player.server.execute(() -> player.sendSystemMessage(Component.literal(error == null ? message : "Nickname update failed.").withStyle(error == null && message.startsWith("Nickname set") ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return 1;
    }
    private static int clear(ServerPlayer player) {
        NicknameManager.clear(player).whenComplete((message, error) -> player.server.execute(() -> player.sendSystemMessage(Component.literal(error == null ? message : "Nickname update failed.").withStyle(error == null ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return 1;
    }
    private static int reload(ServerPlayer player) {
        NicknameConfig.load();
        NicknameManager.load(player);
        player.sendSystemMessage(Component.literal("Nickname configuration reloaded.").withStyle(ChatFormatting.GREEN));
        return 1;
    }
    private static int realname(ServerPlayer player, String nickname) {
        NicknameManager.realNameLookup(nickname).whenComplete((name, error) -> player.server.execute(() -> {
            if (error != null || name == null || name.isBlank()) player.sendSystemMessage(Component.literal("No player was found using that nickname.").withStyle(ChatFormatting.RED));
            else player.sendSystemMessage(Component.literal("True Name: " + name).withStyle(ChatFormatting.GRAY));
        }));
        return 1;
    }
}
