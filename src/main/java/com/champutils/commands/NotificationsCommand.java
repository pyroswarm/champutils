package com.champutils.commands;

import com.champutils.notifications.NotificationManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import static net.minecraft.commands.Commands.literal;

public final class NotificationsCommand {

    private NotificationsCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("notifications")
                        .executes(context -> {
                            NotificationManager.showLatest(context.getSource().getPlayerOrException());
                            return 1;
                        })
        ));
    }
}
