package com.champutils.commands;

import com.champutils.music.MusicConfig;
import com.champutils.music.MusicManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MusicCommand {
    private MusicCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("champmusic")
                        .then(Commands.literal("toggle")
                                .executes(ctx -> toggle(ctx.getSource())))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> reload(ctx.getSource())))
                        .then(Commands.literal("stop")
                                .executes(ctx -> stop(ctx.getSource())))
                        .then(Commands.literal("test")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("track", StringArgumentType.word())
                                        .executes(ctx -> test(ctx.getSource(), StringArgumentType.getString(ctx, "track")))))
        ));
    }

    private static int toggle(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); } catch (Exception e) { return 0; }
        boolean enabled = MusicManager.toggle(player);
        source.sendSuccess(() -> Component.literal(enabled ? "Champ music enabled." : "Champ music disabled."), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        MusicManager.reload(source.getServer());
        source.sendSuccess(() -> Component.literal("Reloaded Champ music config."), true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); } catch (Exception e) { return 0; }
        MusicManager.stop(player);
        source.sendSuccess(() -> Component.literal("Stopped current Champ music."), false);
        return 1;
    }

    private static int test(CommandSourceStack source, String track) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); } catch (Exception e) { return 0; }
        if (MusicConfig.getTrack(track) == null) {
            source.sendFailure(Component.literal("Unknown music track: " + track));
            return 0;
        }
        MusicManager.playTest(player, track);
        source.sendSuccess(() -> Component.literal("Playing test track: " + track), false);
        return 1;
    }
}
