package com.champutils.commands;

import com.champutils.secret.SpawnSecretManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;

public final class SignBindCommand {
    private SignBindCommand() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("signbind").requires(source -> source.hasPermission(4))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> { builder.suggest("secretstart1"); builder.suggest("secretcomplete1"); return builder.buildFuture(); })
                                .executes(ctx -> SpawnSecretManager.beginBind(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "type"))))
        ));
    }
}
