package com.champutils.breeding;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class BreedingCommand {
    private BreedingCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var breedingNode = dispatcher.register(literal("breeding")
                    .executes(context -> {
                        BreedingMenu.open(context.getSource().getPlayerOrException());
                        return 1;
                    })
                    .then(literal("cooldown")
                            .requires(source -> source.hasPermission(4))
                            .then(argument("minutes", IntegerArgumentType.integer(0, 35791394))
                                    .executes(context -> {
                                        int minutes = IntegerArgumentType.getInteger(context, "minutes");
                                        if (!BreedingConfig.setCooldownMinutes(minutes)) {
                                            context.getSource().sendFailure(Component.literal("Could not save the breeding cooldown."));
                                            return 0;
                                        }
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                "Breeding cooldown set to " + minutes + " minute" + (minutes == 1 ? "" : "s") + "."
                                        ).withStyle(ChatFormatting.GREEN), true);
                                        return 1;
                                    }))));
            dispatcher.register(literal("breed").redirect(breedingNode));
            dispatcher.register(literal("eggs").redirect(breedingNode));
        });
    }
}
