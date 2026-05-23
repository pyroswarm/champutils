package com.champutils.commands;

import com.champutils.hunt.PokemonHuntConfig;
import com.champutils.hunt.PokemonHuntManager;
import com.champutils.menu.PokemonHuntMenu;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class PokemonHuntCommand {

    private PokemonHuntCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("hunts")
                    .executes(ctx -> {
                        PokemonHuntMenu.open(ctx.getSource().getPlayerOrException());
                        return 1;
                    })
                    .then(literal("refresh")
                            .requires(source -> source.hasPermission(2))
                            .executes(ctx -> {
                                PokemonHuntManager.forceRefresh(ctx.getSource().getServer());
                                ctx.getSource().sendSuccess(() -> Component.literal("Refreshed Pokémon hunts."), false);
                                return 1;
                            })
                    )
                    .then(literal("reload")
                            .requires(source -> source.hasPermission(2))
                            .executes(ctx -> {
                                PokemonHuntConfig.load();
                                PokemonHuntManager.load();
                                PokemonHuntManager.ensureStarted(ctx.getSource().getServer());
                                ctx.getSource().sendSuccess(() -> Component.literal("Reloaded Pokémon hunt config/state."), false);
                                return 1;
                            })
                    )
                    .then(literal("interval")
                            .requires(source -> source.hasPermission(2))
                            .then(argument("hours", DoubleArgumentType.doubleArg(0.05, 168.0))
                                    .executes(ctx -> {
                                        double hours = DoubleArgumentType.getDouble(ctx, "hours");
                                        PokemonHuntManager.setRefreshHours(hours, ctx.getSource().getServer());
                                        ctx.getSource().sendSuccess(() -> Component.literal("Set Pokémon hunt interval to " + hours + " hour(s)."), false);
                                        return 1;
                                    })
                            )
                    )
            );
        });
    }
}
