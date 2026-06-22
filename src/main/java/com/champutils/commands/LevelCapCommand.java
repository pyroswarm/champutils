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
                        .executes(ctx -> status(ctx.getSource()))
                        .then(literal("set")
                                .then(argument("level", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> setCap(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level")))))
                        .then(literal("on").executes(ctx -> enableCap(ctx.getSource())))
                        .then(literal("off").executes(ctx -> disableCap(ctx.getSource())))
        ));
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int cap = firstPartyCap(party);
        if (cap > 0) {
            source.sendSuccess(() -> Component.literal("Your party levelcap is ON at level " + cap + ". Use /levelcap set <level> to change it or /levelcap off to disable it.").withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("Your party levelcap is OFF. Use /levelcap set <level> or /levelcap on.").withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int setCap(CommandSourceStack source, int level) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) { source.sendFailure(Component.literal("Could not access your Cobblemon party.")); return 0; }
        int applied = applyCap(party, level);
        int finalApplied = applied;
        source.sendSuccess(() -> Component.literal("Your party levelcap is now ON at level " + level + " for " + finalApplied + " Pokémon.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int enableCap(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) { source.sendFailure(Component.literal("Could not access your Cobblemon party.")); return 0; }
        int existing = firstPartyCap(party);
        int level = existing > 0 ? existing : 100;
        int applied = applyCap(party, level);
        int finalApplied = applied;
        int finalLevel = level;
        source.sendSuccess(() -> Component.literal("Your party levelcap is ON at level " + finalLevel + " for " + finalApplied + " Pokémon. Use /levelcap set <level> to choose a different cap.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int disableCap(CommandSourceStack source) {
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
        source.sendSuccess(() -> Component.literal("Your party levelcap is OFF. Cleared level caps from " + finalCleared + " Pokémon.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int applyCap(PartyStore party, int level) {
        int applied = 0;
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon == null) continue;
            XpLockManager.setLevelCap(pokemon, level);
            applied++;
        }
        return applied;
    }

    private static int firstPartyCap(PartyStore party) {
        if (party == null) return 0;
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            int cap = XpLockManager.getLevelCap(pokemon);
            if (cap > 0) return cap;
        }
        return 0;
    }
}
