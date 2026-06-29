package com.champutils.commands;

import com.champutils.profession.ProfessionTrinketManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

public final class MagnetCommand {
    private MagnetCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("magnet")
                .then(literal("toggle").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!ProfessionTrinketManager.toggleBestMagnet(player)) {
                        player.sendSystemMessage(Component.literal("§cYou do not have a Magnet trinket in your inventory or trinket pouch."));
                    }
                    return 1;
                }))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal("§eUse §f/magnet toggle §eto enable or disable your best Magnet trinket."));
                    return 1;
                })));
    }
}
