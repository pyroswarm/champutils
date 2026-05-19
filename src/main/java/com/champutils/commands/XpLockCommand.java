package com.champutils.commands;

import com.champutils.xplock.XpLockManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class XpLockCommand {

    private XpLockCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("xplock")
                        .then(argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(context -> toggle(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "slot")
                                )))
        ));
    }

    private static int toggle(CommandSourceStack source, int slot) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) {
            source.sendFailure(Component.literal("Could not access your Cobblemon party."));
            return 0;
        }

        Pokemon pokemon = party.get(slot - 1);
        if (pokemon == null) {
            source.sendFailure(Component.literal("There is no Pokémon in party slot " + slot + "."));
            return 0;
        }

        boolean locked = XpLockManager.toggle(pokemon);
        String pokemonName = pokemon.getDisplayName(true).getString();

        if (locked) {
            source.sendSuccess(
                    () -> Component.literal("XP locked slot " + slot + ": ")
                            .withStyle(ChatFormatting.RED)
                            .append(Component.literal(pokemonName).withStyle(ChatFormatting.YELLOW)),
                    false
            );
        } else {
            source.sendSuccess(
                    () -> Component.literal("XP unlocked slot " + slot + ": ")
                            .withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(pokemonName).withStyle(ChatFormatting.YELLOW)),
                    false
            );
        }

        return 1;
    }
}
