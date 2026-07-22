package com.champutils.commands;

import com.champutils.profession.ProfessionChunkConfig;
import com.champutils.profession.ProfessionChunkManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChunksCommand {
    private ChunksCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("chunks")
                        .then(Commands.literal("list").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            list(player);
                            return 1;
                        }))
        ));
    }

    private static void list(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6§lYour Chunks"));
        List<String> chunks = new ArrayList<>(ProfessionChunkConfig.CONFIG.chunks.keySet());
        chunks.sort(Comparator.comparingInt(ChunksCommand::rank));
        boolean any = false;
        for (String chunk : chunks) {
            int amount = ProfessionChunkManager.count(player, chunk);
            if (amount <= 0) continue;
            any = true;
            ProfessionChunkConfig.ChunkData data = ProfessionChunkConfig.CONFIG.chunks.get(chunk);
            String name = data == null ? ProfessionChunkManager.formatChunk(chunk) : data.displayName;
            player.sendSystemMessage(Component.literal("§7- ").append(Component.literal(name).withStyle(ProfessionChunkManager.color(chunk))).append(Component.literal("§7: §f" + amount)));
        }
        if (!any) player.sendSystemMessage(Component.literal("§7You do not have any chunks yet."));
    }

    private static int rank(String chunk) {
        return switch (ProfessionChunkManager.normalizeChunk(chunk)) {
            case "COBBLESTONE" -> 0; case "COPPER" -> 1; case "IRON" -> 2; case "GOLD" -> 3; case "EMERALD" -> 4; case "DIAMOND" -> 5; case "NETHERITE" -> 6; default -> 99;
        };
    }
}
