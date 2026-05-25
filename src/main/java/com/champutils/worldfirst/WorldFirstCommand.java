package com.champutils.worldfirst;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
public final class WorldFirstCommand { private WorldFirstCommand() {} public static void register() { CommandRegistrationCallback.EVENT.register((d,r,e) -> d.register(Commands.literal("worldfirsts").executes(ctx -> { WorldFirstMenu.open(ctx.getSource().getPlayerOrException()); return 1; }))); } }
