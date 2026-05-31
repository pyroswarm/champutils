package com.champutils.commands;

import com.champutils.specialspawn.SpecialWildSpawnConfig;
import com.champutils.specialspawn.SpecialWildSpawnManager;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class SpecialWildSpawnCommand {
    private SpecialWildSpawnCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("specialspawns")
                .requires(source -> source.hasPermission(2))
                .then(literal("reload").executes(ctx -> {
                    SpecialWildSpawnConfig.load();
                    ctx.getSource().sendSuccess(() -> Component.literal("Reloaded special wild spawn config."), false);
                    return 1;
                }))
                .then(literal("save").executes(ctx -> {
                    SpecialWildSpawnConfig.save();
                    ctx.getSource().sendSuccess(() -> Component.literal("Saved special wild spawn config."), false);
                    return 1;
                }))
                .then(literal("force").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    SpecialWildSpawnManager.ForceSpawnResult result = SpecialWildSpawnManager.forceSpawnForResult(player);
                    ctx.getSource().sendSuccess(() -> Component.literal(result.message), false);
                    return result.success ? 1 : 0;
                }))
        ));
    }
}
