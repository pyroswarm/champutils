package com.champutils.breeding;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
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
            dispatcher.register(literal("eggsteps").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
                boolean found = false;
                player.sendSystemMessage(Component.literal("§d§lEgg Steps"));
                for (int slot = 0; slot < 6; slot++) {
                    Pokemon pokemon = party == null ? null : party.get(slot);
                    if (!BreedingEggData.isEgg(pokemon)) continue;
                    found = true;
                    player.sendSystemMessage(Component.literal("§7Slot §f" + (slot + 1) + "§7: §e" + BreedingEggData.remainingSteps(pokemon) + " steps remaining §8(" + BreedingEggData.progressPercent(pokemon) + "%)"));
                }
                if (!found) player.sendSystemMessage(Component.literal("§7There are no Eggs in your party."));
                return 1;
            }));
        });
    }
}
