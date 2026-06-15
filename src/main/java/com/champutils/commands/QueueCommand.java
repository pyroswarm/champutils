package com.champutils.commands;

import com.champutils.matchmaking.MatchmakingManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import static net.minecraft.commands.Commands.literal;

public final class QueueCommand {
    private QueueCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("queue")
                        .then(literal("accept").executes(ctx -> MatchmakingManager.acceptMatch(ctx.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(literal("deny").executes(ctx -> MatchmakingManager.declineMatch(ctx.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(literal("decline").executes(ctx -> MatchmakingManager.declineMatch(ctx.getSource().getPlayerOrException()) ? 1 : 0))
        ));
    }
}
