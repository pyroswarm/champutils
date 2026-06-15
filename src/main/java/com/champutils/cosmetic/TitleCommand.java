package com.champutils.cosmetic;

import com.champutils.buff.BuffType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

public final class TitleCommand {
    private TitleCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("titles")
                    .executes(ctx -> {
                        TitleMenu.open(ctx.getSource().getPlayerOrException());
                        return 1;
                    })
                    .then(Commands.literal("select")
                            .then(Commands.argument("id", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TitleConfig.titles().stream().map(t -> t.id), builder))
                                    .executes(ctx -> {
                                        TitleManager.select(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"));
                                        return 1;
                                    })))
                    .then(Commands.literal("unlock")
                            .requires(source -> source.hasPermission(4))
                            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TitleConfig.titles().stream().map(t -> t.id), builder))
                                            .executes(ctx -> unlock(
                                                    net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"),
                                                    StringArgumentType.getString(ctx, "id"),
                                                    null
                                            ))
                                            .then(Commands.argument("display", StringArgumentType.greedyString())
                                                    .executes(ctx -> unlock(
                                                            net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"),
                                                            StringArgumentType.getString(ctx, "id"),
                                                            StringArgumentType.getString(ctx, "display")
                                                    ))))))
                    .then(Commands.literal("admin")
                            .requires(source -> source.hasPermission(4))
                            .then(Commands.literal("create")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                                    .executes(ctx -> create(
                                                            ctx.getSource().getPlayerOrException(),
                                                            StringArgumentType.getString(ctx, "id"),
                                                            StringArgumentType.getString(ctx, "name")
                                                    )))))
                            .then(Commands.literal("give")
                                    .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                            .then(Commands.argument("id", StringArgumentType.word())
                                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TitleConfig.titles().stream().map(t -> t.id), builder))
                                                    .executes(ctx -> unlock(
                                                            net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"),
                                                            StringArgumentType.getString(ctx, "id"),
                                                            null
                                                    )))))
                            .then(Commands.literal("name")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TitleConfig.titles().stream().map(t -> t.id), builder))
                                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                                    .executes(ctx -> update(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "name"), null, null, null)))))
                            .then(Commands.literal("color")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TitleConfig.titles().stream().map(t -> t.id), builder))
                                            .then(Commands.argument("color", StringArgumentType.word())
                                                    .executes(ctx -> update(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"), null, StringArgumentType.getString(ctx, "color"), null, null)))))
                            .then(Commands.literal("icon")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TitleConfig.titles().stream().map(t -> t.id), builder))
                                            .then(Commands.argument("icon", StringArgumentType.word())
                                                    .executes(ctx -> update(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"), null, null, StringArgumentType.getString(ctx, "icon"), null)))))
                            .then(Commands.literal("description")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TitleConfig.titles().stream().map(t -> t.id), builder))
                                            .then(Commands.argument("description", StringArgumentType.greedyString())
                                                    .executes(ctx -> update(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"), null, null, null, StringArgumentType.getString(ctx, "description"))))))
                            .then(Commands.literal("buff")
                                    .then(Commands.literal("set")
                                            .then(Commands.argument("id", StringArgumentType.word())
                                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TitleConfig.titles().stream().map(t -> t.id), builder))
                                                    .then(Commands.argument("buff", StringArgumentType.word())
                                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(buffSuggestions(), builder))
                                                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D))
                                                                    .executes(ctx -> setBuff(
                                                                            ctx.getSource().getPlayerOrException(),
                                                                            StringArgumentType.getString(ctx, "id"),
                                                                            StringArgumentType.getString(ctx, "buff"),
                                                                            DoubleArgumentType.getDouble(ctx, "amount")
                                                                    ))))))
                                    .then(Commands.literal("remove")
                                            .then(Commands.argument("id", StringArgumentType.word())
                                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TitleConfig.titles().stream().map(t -> t.id), builder))
                                                    .then(Commands.argument("buff", StringArgumentType.word())
                                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(buffSuggestions(), builder))
                                                            .executes(ctx -> removeBuff(
                                                                    ctx.getSource().getPlayerOrException(),
                                                                    StringArgumentType.getString(ctx, "id"),
                                                                    StringArgumentType.getString(ctx, "buff")
                                                            ))))))));
        });
    }

    private static Iterable<String> buffSuggestions() {
        return Arrays.asList("SHINY_CHANCE", "BATTLING_XP", "CATCH_CHANCE");
    }

    private static int create(ServerPlayer admin, String id, String name) {
        TitleConfig.TitleDef def = TitleConfig.createManualTitle(id, name);
        if (def == null) {
            admin.sendSystemMessage(Component.literal("Could not create title.").withStyle(ChatFormatting.RED));
            return 0;
        }
        admin.sendSystemMessage(Component.literal("Created manual title " + def.id + ". Use /titles admin buff set " + def.id + " SHINY_CHANCE 0.001").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int update(ServerPlayer admin, String id, String name, String color, String icon, String description) {
        if (!TitleConfig.updateManualTitle(id, name, color, icon, description)) {
            admin.sendSystemMessage(Component.literal("Unknown title: " + id).withStyle(ChatFormatting.RED));
            return 0;
        }
        admin.sendSystemMessage(Component.literal("Updated title " + id + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setBuff(ServerPlayer admin, String id, String buff, double amount) {
        if (TitleConfig.parseBuffType(buff) == null) {
            admin.sendSystemMessage(Component.literal("Unknown buff. Use SHINY_CHANCE, BATTLING_XP, or CATCH_CHANCE.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!TitleConfig.setBuff(id, buff, amount)) {
            admin.sendSystemMessage(Component.literal("Unknown title: " + id).withStyle(ChatFormatting.RED));
            return 0;
        }
        admin.sendSystemMessage(Component.literal("Set " + buff + " on " + id + " to +" + String.format(java.util.Locale.US, "%.3f", amount * 100.0D).replaceAll("0+$", "").replaceAll("\\.$", "") + "%.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int removeBuff(ServerPlayer admin, String id, String buff) {
        if (!TitleConfig.removeBuff(id, buff)) {
            admin.sendSystemMessage(Component.literal("Could not remove buff. Check the title id and buff name.").withStyle(ChatFormatting.RED));
            return 0;
        }
        admin.sendSystemMessage(Component.literal("Removed " + buff + " from " + id + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int unlock(ServerPlayer player, String id, String display) {
        String resolved = display;
        if (resolved == null || resolved.isBlank()) resolved = TitleRegistry.defaultDisplay(id);
        if (resolved == null || resolved.isBlank()) resolved = TitleConfig.display(id);
        if (resolved == null || resolved.isBlank()) resolved = "&7[" + id + "]";
        boolean changed = TitleManager.unlock(player, id, resolved);
        if (!changed) {
            player.sendSystemMessage(Component.literal("That title is already unlocked.").withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }
}
