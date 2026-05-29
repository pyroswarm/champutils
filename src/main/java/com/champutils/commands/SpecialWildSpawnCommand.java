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
                    boolean spawned = SpecialWildSpawnManager.forceSpawnFor(player);
                    ctx.getSource().sendSuccess(() -> Component.literal(spawned ? "Forced a special wild spawn near you." : "Could not force a special wild spawn here. Check world/profile/config."), false);
                    return spawned ? 1 : 0;
                }))
        ));
    }
}
