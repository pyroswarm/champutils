package com.champutils.commands;

import com.champutils.profile.IslanderMineConfig;
import com.champutils.profile.IslanderMineManager;
import com.champutils.database.SharedJsonStateRepository;
import com.champutils.network.NetworkServerConfig;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileNetworkTransferFlow;
import com.champutils.teleport.SafeTeleportManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class IslanderMineCommand {
    private static final String PENDING_KEY = "pending_islander_mine_transfer";
    private static final long PENDING_TTL_MS = 120_000L;

    private IslanderMineCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("island")
                    .then(literal("mine")
                            .executes(ctx -> openMine(ctx.getSource().getPlayerOrException()))));

            dispatcher.register(literal("islandermine")
                    .requires(source -> source.hasPermission(4))
                    .then(literal("reload")
                            .executes(ctx -> {
                                IslanderMineConfig.load();
                                ctx.getSource().sendSuccess(() -> Component.literal("Reloaded islander_mines.json.").withStyle(ChatFormatting.GREEN), false);
                                return Command.SINGLE_SUCCESS;
                            }))
                    .then(literal("reset")
                            .executes(ctx -> {
                                IslanderMineManager.queueManualReset(ctx.getSource().getServer(), ctx.getSource().getPlayer());
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(argument("playerOrProfile", StringArgumentType.word())
                                    .executes(ctx -> {
                                        IslanderMineManager.queueManualReset(
                                                ctx.getSource().getServer(),
                                                ctx.getSource().getPlayer(),
                                                StringArgumentType.getString(ctx, "playerOrProfile")
                                        );
                                        return Command.SINGLE_SUCCESS;
                                    }))));
        });
    }

    private static int openMine(ServerPlayer player) {
        if (player == null) return 0;
        String targetServer = NetworkServerConfig.get().islanderMineServerId;
        if (targetServer == null || targetServer.isBlank()) targetServer = NetworkServerConfig.get().survivalServerId;
        if (targetServer == null || targetServer.isBlank() || targetServer.equalsIgnoreCase(NetworkServerConfig.serverId())) {
            return IslanderMineManager.teleportToMine(player) ? Command.SINGLE_SUCCESS : 0;
        }
        if (!PlayerProfileManager.isIslander(player) && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("Only Islander profiles can use Islander mines.").withStyle(ChatFormatting.RED));
            return 0;
        }
        PlayerProfileManager.ProfileRecord active = PlayerProfileManager.active(player);
        if (active == null) return 0;
        PendingMineTransfer pending = new PendingMineTransfer();
        pending.targetServerId = targetServer;
        pending.expiresAtMillis = System.currentTimeMillis() + PENDING_TTL_MS;
        String finalTargetServer = targetServer;
        player.sendSystemMessage(Component.literal("Sending you to the Islander mine server.").withStyle(ChatFormatting.YELLOW));
        SharedJsonStateRepository.savePlayerAsync(player.getUUID(), PENDING_KEY, pending).whenComplete((ignored, error) -> player.server.execute(() -> {
            if (!SafeTeleportManager.isLive(player)) return;
            if (error != null) {
                player.sendSystemMessage(Component.literal("Could not prepare the Islander mine transfer.").withStyle(ChatFormatting.RED));
                return;
            }
            ProfileNetworkTransferFlow.issueTransferFromLobby(player, active, finalTargetServer, message -> {
                if (message != null && message.startsWith("Could not")) {
                    clearPending(player);
                    player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
                }
            });
        }));
        return Command.SINGLE_SUCCESS;
    }

    public static void handleProfileReady(ServerPlayer player) {
        if (player == null) return;
        SharedJsonStateRepository.loadPlayerAsync(player.getUUID(), PENDING_KEY, PendingMineTransfer.class, null)
                .thenAccept(pending -> player.server.execute(() -> {
                    if (!SafeTeleportManager.isLive(player) || pending == null) return;
                    if (pending.expiresAtMillis < System.currentTimeMillis()) { clearPending(player); return; }
                    if (pending.targetServerId == null || !pending.targetServerId.equalsIgnoreCase(NetworkServerConfig.serverId())) return;
                    clearPending(player);
                    if (!IslanderMineManager.teleportToMine(player)) {
                        player.sendSystemMessage(Component.literal("The Islander mine could not be opened on this server.").withStyle(ChatFormatting.RED));
                    }
                }));
    }

    private static void clearPending(ServerPlayer player) {
        PendingMineTransfer cleared = new PendingMineTransfer();
        SharedJsonStateRepository.savePlayerAsync(player.getUUID(), PENDING_KEY, cleared);
    }

    public static final class PendingMineTransfer {
        public String targetServerId = "";
        public long expiresAtMillis = 0L;
    }
}
