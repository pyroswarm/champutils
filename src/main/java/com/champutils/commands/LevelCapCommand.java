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

public final class LevelCapCommand {
    private LevelCapCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("levelcap")
                        .then(literal("now")
                                .then(argument("level", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> setCap(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level")))))
                        .then(literal("off").executes(ctx -> clearCap(ctx.getSource())))
        ));
    }

    private static int setCap(CommandSourceStack source, int level) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) { source.sendFailure(Component.literal("Could not access your Cobblemon party.")); return 0; }
        int applied = 0;
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon == null) continue;
            XpLockManager.setLevelCap(pokemon, level);
            applied++;
        }
        int finalApplied = applied;
        source.sendSuccess(() -> Component.literal("Set level cap " + level + " on " + finalApplied + " party Pokémon. They will stop gaining XP once they reach/cross that level.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int clearCap(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) { source.sendFailure(Component.literal("Could not access your Cobblemon party.")); return 0; }
        int cleared = 0;
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon == null) continue;
            XpLockManager.clearLevelCap(pokemon);
            cleared++;
        }
        int finalCleared = cleared;
        source.sendSuccess(() -> Component.literal("Cleared level caps from " + finalCleared + " party Pokémon.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
