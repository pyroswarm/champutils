package com.champutils.commands;

import com.champutils.gamerule.GlobalGameruleConfig;
import com.champutils.gamerule.GlobalGameruleManager;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class GlobalGameruleCommand {

    private GlobalGameruleCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("globalgamerules")
                        .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                        .then(Commands.literal("reload").executes(context -> {
                            GlobalGameruleConfig.load();
                            GlobalGameruleManager.applyAll(context.getSource().getServer());
                            context.getSource().sendSuccess(() -> Component.literal("§aGlobal gamerules reloaded and applied to all loaded worlds."), true);
                            return 1;
                        }))
                        .then(Commands.literal("apply").executes(context -> {
                            GlobalGameruleManager.applyAll(context.getSource().getServer());
                            context.getSource().sendSuccess(() -> Component.literal("§aGlobal gamerules applied to all loaded worlds."), true);
                            return 1;
                        }))
                        .then(Commands.literal("save").executes(context -> {
                            GlobalGameruleConfig.save();
                            context.getSource().sendSuccess(() -> Component.literal("§aGlobal gamerules config saved."), true);
                            return 1;
                        }))
        ));
    }
}
