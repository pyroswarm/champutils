package com.champutils.adventurer;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class AdventurerGuildCommand {
    private AdventurerGuildCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("adventurer")
                    .executes(ctx -> { AdventurerGuildMenu.open(ctx.getSource().getPlayerOrException()); return 1; })
                    .then(Commands.literal("admin")
                            .requires(source -> source.hasPermission(4))
                            .then(Commands.literal("open")
                                    .executes(ctx -> { AdventurerGuildMenu.open(ctx.getSource().getPlayerOrException()); return 1; }))
                            .then(Commands.literal("settowerfloor")
                                    .then(Commands.argument("floor", IntegerArgumentType.integer(1, 25))
                                            .executes(ctx -> AdventurerGuildManager.setBattleTowerFloorLocation(
                                                    ctx.getSource().getPlayerOrException(),
                                                    IntegerArgumentType.getInteger(ctx, "floor")
                                            ) ? 1 : 0)))
                            .then(Commands.literal("reload")
                                    .executes(ctx -> {
                                        AdventurerGuildManager.reload();
                                        ctx.getSource().sendSuccess(() -> Component.literal("§aReloaded adventurers_guild.json."), true);
                                        return 1;
                                    }))));

            dispatcher.register(Commands.literal("adventurersguild")
                    .executes(ctx -> { AdventurerGuildMenu.open(ctx.getSource().getPlayerOrException()); return 1; }));
            dispatcher.register(Commands.literal("aguild")
                    .executes(ctx -> { AdventurerGuildMenu.open(ctx.getSource().getPlayerOrException()); return 1; }));
        });
    }

    
}
