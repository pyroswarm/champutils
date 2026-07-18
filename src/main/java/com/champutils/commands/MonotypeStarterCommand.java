package com.champutils.commands;

import com.champutils.profile.MonotypeStarterManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import static net.minecraft.commands.Commands.literal;

/** Reopens the custom monotype starter menu only while the active profile still needs a starter. */
public final class MonotypeStarterCommand {
    private MonotypeStarterCommand() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("monotypestarter").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (!MonotypeStarterManager.needsStarter(player)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cThis profile has already claimed its starter."));
                        return 0;
                    }
                    MonotypeStarterManager.open(player);
                    return 1;
                }))
        );
    }
}
