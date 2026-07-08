package com.champutils.cosmetic;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TrailCommand {
    private TrailCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("trails")
                    .executes(ctx -> {
                        com.champutils.account.AccountUpgradeMenu.openTrails(ctx.getSource().getPlayerOrException(), 0);
                        return 1;
                    })
                    .then(Commands.literal("off")
                            .executes(ctx -> {
                                TrailCosmeticManager.select(ctx.getSource().getPlayerOrException(), "off");
                                return 1;
                            }))
                    .then(Commands.literal("select")
                            .then(Commands.argument("trail", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TrailCosmeticManager.trails().stream().map(TrailCosmeticManager.TrailDef::id), builder))
                                    .executes(ctx -> select(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "trail"))))));
            dispatcher.register(Commands.literal("trail")
                    .executes(ctx -> {
                        com.champutils.account.AccountUpgradeMenu.openTrails(ctx.getSource().getPlayerOrException(), 0);
                        return 1;
                    }));
        });
    }

    private static int select(ServerPlayer player, String trail) {
        TrailCosmeticManager.select(player, trail);
        String selected = TrailCosmeticManager.selected(player);
        if (!selected.isBlank()) {
            player.sendSystemMessage(Component.literal("Use /trails to change trails anytime.").withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }
}
