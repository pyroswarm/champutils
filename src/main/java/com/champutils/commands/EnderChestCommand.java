package com.champutils.commands;

import com.champutils.permissions.PermissionUtil;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;

import static net.minecraft.commands.Commands.literal;

public final class EnderChestCommand {
    private static final String PERMISSION = "champutils.command.ec";

    private EnderChestCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("ec").executes(ctx -> open(ctx.getSource().getPlayerOrException())));
            dispatcher.register(literal("enderchest").executes(ctx -> open(ctx.getSource().getPlayerOrException())));
        });
    }

    private static int open(ServerPlayer player) {
        if (!PermissionUtil.has(player.createCommandSourceStack(), PERMISSION)) {
            player.sendSystemMessage(Component.literal("§c/ec is a VIP feature. Unlock it with /accountupgrade."));
            return 0;
        }
        player.openMenu(new SimpleMenuProvider(
                (syncId, inventory, ignored) -> ChestMenu.threeRows(syncId, inventory, player.getEnderChestInventory()),
                Component.literal("Ender Chest")
        ));
        return 1;
    }
}
