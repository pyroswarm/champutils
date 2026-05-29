package com.champutils.commands;

import com.champutils.menu.GraveyardMenu;
import com.champutils.profile.NuzlockeManager;
import com.champutils.profile.PlayerProfileManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GraveyardCommand {
    private GraveyardCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("graveyard")
                .executes(ctx -> open(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("claim")
                        .executes(ctx -> claim(ctx.getSource().getPlayerOrException()))));
    }

    private static int open(ServerPlayer player) {
        if (!PlayerProfileManager.hasActiveProfile(player)) {
            player.sendSystemMessage(Component.literal("Select a profile first.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!PlayerProfileManager.isNuzlocke(player) && NuzlockeManager.graveyardEntries(player, true).isEmpty()) {
            player.sendSystemMessage(Component.literal("This profile has no Nuzlocke Graveyard entries.").withStyle(ChatFormatting.RED));
            return 0;
        }
        GraveyardMenu.open(player);
        return 1;
    }

    private static int claim(ServerPlayer player) {
        String result = NuzlockeManager.claimGraveyard(player);
        boolean ok = result.startsWith("Graveyard claim complete");
        player.sendSystemMessage(Component.literal(result).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
        return ok ? 1 : 0;
    }
}
