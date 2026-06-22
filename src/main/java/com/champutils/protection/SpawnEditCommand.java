package com.champutils.protection;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnEditCommand {
    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();
    private SpawnEditCommand() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("spawnedit")
                .requires(source -> source.hasPermission(4) || (source.getEntity() instanceof ServerPlayer sp && com.champutils.permissions.LuckPermsHook.hasPermission(sp, "champutils.admin")))
                .executes(ctx -> toggle(ctx.getSource().getPlayerOrException()))));
    }
    private static int toggle(ServerPlayer player) {
        if (!ENABLED.remove(player.getUUID())) {
            ENABLED.add(player.getUUID());
            player.sendSystemMessage(Component.literal("Spawn edit enabled. You can build/break in spawn until you run /spawnedit again or log out.").withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.literal("Spawn edit disabled.").withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }
    public static boolean canEdit(ServerPlayer player) {
        return player != null && ENABLED.contains(player.getUUID()) && (player.hasPermissions(4) || com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin"));
    }
    public static void clear(ServerPlayer player) { if (player != null) ENABLED.remove(player.getUUID()); }
}
