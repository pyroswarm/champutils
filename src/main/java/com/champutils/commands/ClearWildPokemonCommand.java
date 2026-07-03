package com.champutils.commands;

import com.champutils.antilag.AntiLagConfig;
import com.champutils.antilag.WildPokemonCleanupManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.HashSet;

public final class ClearWildPokemonCommand {
    private ClearWildPokemonCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("clearwildpokemon")
                        .requires(source -> source.hasPermission(4))
                        .executes(ctx -> clear(ctx.getSource(), 0, Math.max(5000, AntiLagConfig.DATA.maxRemovalsPerScan)))
                        .then(Commands.argument("minAgeSeconds", IntegerArgumentType.integer(0, 3600))
                                .executes(ctx -> clear(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "minAgeSeconds"),
                                        Math.max(5000, AntiLagConfig.DATA.maxRemovalsPerScan)
                                ))
                                .then(Commands.argument("max", IntegerArgumentType.integer(1, 50000))
                                        .executes(ctx -> clear(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "minAgeSeconds"),
                                                IntegerArgumentType.getInteger(ctx, "max")
                                        ))))
        ));
    }

    private static int clear(CommandSourceStack source, int minAgeSeconds, int max) {
        WildPokemonCleanupManager.Options options = new WildPokemonCleanupManager.Options();
        options.clearDroppedItems = false;
        options.clearWildPokemon = true;
        options.protectCustomNames = AntiLagConfig.DATA.protectPokemonWithCustomName;
        options.minWildPokemonAgeTicks = Math.max(0, minAgeSeconds) * 20;
        options.maxRemovals = Math.max(1, max);
        options.disabledDimensions = AntiLagConfig.DATA.disabledDimensions == null ? new HashSet<>() : new HashSet<>(AntiLagConfig.DATA.disabledDimensions);

        WildPokemonCleanupManager.CleanupResult result = WildPokemonCleanupManager.cleanup(source.getServer(), options);
        source.sendSuccess(() -> Component.literal(
                "§aCleared §f" + result.wildPokemon + "§a ordinary wild Pokémon. " +
                        "§7Checked " + result.checkedWildPokemon + ", protected " + result.protectedWildPokemon + ". " +
                        "§8Reasons: " + result.protectedReasonSummary()
        ), true);
        return result.wildPokemon;
    }
}
