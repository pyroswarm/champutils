package com.champutils.teleport;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ChunkPregenerationTeleportCommand {

    private ChunkPregenerationTeleportCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("pregentp")
                        .requires(source -> source.hasPermission(4))
                        .then(literal("start")
                                .then(argument("radiusChunks", IntegerArgumentType.integer(1, 5000))
                                        .executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radiusChunks"), 20))
                                        .then(argument("delayTicks", IntegerArgumentType.integer(1, 200))
                                                .executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radiusChunks"), IntegerArgumentType.getInteger(ctx, "delayTicks"))))))
                        .then(literal("stop")
                                .executes(ctx -> stop(ctx.getSource())))
                        .then(literal("status")
                                .executes(ctx -> status(ctx.getSource())))
        ));
    }

    private static int start(CommandSourceStack source, int radiusChunks, int delayTicks) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos center = player.chunkPosition();
        List<ChunkPos> chunks = buildSquare(center, radiusChunks);

        ChunkPregenerationTeleportManager.start(player, level, chunks, delayTicks, radiusChunks);

        player.sendSystemMessage(Component.literal("Started pregeneration teleport pass in " + level.dimension().location() + ".").withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal("Radius: " + radiusChunks + " chunks. Chunks queued: " + chunks.size() + ". Delay: " + delayTicks + " ticks.").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Use /pregentp stop to cancel. Keep this small; huge radii can lag the server.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static List<ChunkPos> buildSquare(ChunkPos center, int radiusChunks) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = center.x - radiusChunks; x <= center.x + radiusChunks; x++) {
            for (int z = center.z - radiusChunks; z <= center.z + radiusChunks; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }

        chunks.sort(Comparator.comparingInt((ChunkPos pos) -> Math.max(Math.abs(pos.x - center.x), Math.abs(pos.z - center.z)))
                .thenComparingInt(pos -> pos.x)
                .thenComparingInt(pos -> pos.z));
        return chunks;
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean stopped = ChunkPregenerationTeleportManager.stop(player.getUUID());
        if (stopped) {
            player.sendSystemMessage(Component.literal("Stopped your pregeneration teleport pass.").withStyle(ChatFormatting.GREEN));
        }
        else {
            player.sendSystemMessage(Component.literal("You do not have an active pregeneration teleport pass.").withStyle(ChatFormatting.YELLOW));
        }
        return stopped ? 1 : 0;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ChunkPregenerationTeleportManager.Task task = ChunkPregenerationTeleportManager.get(player.getUUID());
        if (task == null) {
            player.sendSystemMessage(Component.literal("You do not have an active pregeneration teleport pass.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Pregeneration: " + task.getDone() + "/" + task.getTotal() + " chunks. Radius: " + task.getRadiusChunks() + ".").withStyle(ChatFormatting.GOLD));
        return 1;
    }
}
