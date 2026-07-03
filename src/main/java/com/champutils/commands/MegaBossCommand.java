package com.champutils.commands;

import com.champutils.megaboss.MegaBossManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MegaBossCommand {
    private MegaBossCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("megaboss")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.literal("force")
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(MegaBossManager.validRarities(), builder))
                                        .executes(context -> force(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "rarity")
                                        ))))
        ));
    }

    private static int force(ServerPlayer player, String rarity) {
        MegaBossManager.ForceSpawnResult result = MegaBossManager.forceSpawn(player, rarity);
        if (result.success) {
            player.sendSystemMessage(Component.literal("§a" + result.message));
            if (result.pos != null) {
                player.sendSystemMessage(Component.literal("§7Location: §f" + result.pos.getX() + " " + result.pos.getY() + " " + result.pos.getZ()));
            }
            return 1;
        }
        player.sendSystemMessage(Component.literal("§c" + result.message));
        return 0;
    }
}
