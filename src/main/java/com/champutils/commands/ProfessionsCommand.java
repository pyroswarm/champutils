package com.champutils.commands;

import com.champutils.menu.ProfessionsMenu;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import static net.minecraft.commands.Commands.literal;

public final class ProfessionsCommand {
    private ProfessionsCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(literal("professions")
                .executes(ctx -> {
                    ProfessionsMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
