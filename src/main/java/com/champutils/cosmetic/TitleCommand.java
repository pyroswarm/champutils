package com.champutils.cosmetic;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;

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
                                    .executes(ctx -> {
                                        TitleManager.select(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"));
                                        return 1;
                                    }))));
        });
    }
}
