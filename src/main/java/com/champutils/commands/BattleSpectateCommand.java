package com.champutils.commands;

import com.champutils.battle.BattleStateManager;
import com.champutils.matchmaking.MatchmakingManager;
import com.champutils.menu.BattleSpectateMenu;
import com.champutils.profile.ProfileManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Random;

public class BattleSpectateCommand {

    private static final Random RANDOM = new Random();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("spectatebattle")
                        .executes(ctx -> openMenu(ctx.getSource()))
                        .then(Commands.literal("menu")
                                .executes(ctx -> openMenu(ctx.getSource())))
                        .then(Commands.literal("random")
                                .executes(ctx -> spectateRandom(ctx.getSource())))
                        .then(Commands.literal("highmmr")
                                .executes(ctx -> spectateHighMmr(ctx.getSource())))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    MinecraftServer server = ctx.getSource().getServer();
                                    for (ServerPlayer player : getSpectatablePlayers(server, getViewerOrNull(ctx.getSource()))) {
                                        builder.suggest(player.getName().getString());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> spectatePlayer(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")
                                )))
        ));
    }

    private static int openMenu(CommandSourceStack source) {
        ServerPlayer viewer = getPlayer(source);
        if (viewer == null) return 0;
        BattleSpectateMenu.open(viewer);
        return 1;
    }

    public static int spectateRandom(CommandSourceStack source) {
        ServerPlayer viewer = getPlayer(source);
        if (viewer == null) return 0;

        List<ServerPlayer> candidates = getSpectatablePlayers(viewer.getServer(), viewer);
        if (candidates.isEmpty()) {
            viewer.sendSystemMessage(Component.literal("No PvP battles are available to spectate right now.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        return runBuiltInSpectate(viewer, candidates.get(RANDOM.nextInt(candidates.size())));
    }

    public static int spectateHighMmr(CommandSourceStack source) {
        ServerPlayer viewer = getPlayer(source);
        if (viewer == null) return 0;

        ServerPlayer target = getHighestMmrTarget(viewer.getServer(), viewer);
        if (target == null) {
            viewer.sendSystemMessage(Component.literal("No ranked/high-MMR PvP battles are available to spectate right now.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        return runBuiltInSpectate(viewer, target);
    }

    public static int spectatePlayer(CommandSourceStack source, String username) {
        ServerPlayer viewer = getPlayer(source);
        if (viewer == null) return 0;

        ServerPlayer target = viewer.getServer().getPlayerList().getPlayerByName(username);
        if (target == null) {
            viewer.sendSystemMessage(Component.literal("Player not found: " + username).withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!isSpectatableTarget(target, viewer)) {
            viewer.sendSystemMessage(Component.literal(target.getName().getString() + " is not in a spectatable PvP battle.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        return runBuiltInSpectate(viewer, target);
    }

    public static int spectateTarget(ServerPlayer viewer, ServerPlayer target) {
        if (viewer == null || target == null) return 0;
        if (!isSpectatableTarget(target, viewer)) {
            viewer.sendSystemMessage(Component.literal("That player is not in a spectatable PvP battle.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        return runBuiltInSpectate(viewer, target);
    }

    public static ServerPlayer getHighestMmrTarget(MinecraftServer server, ServerPlayer viewer) {
        return getSpectatablePlayers(server, viewer).stream()
                .max(Comparator.comparingInt(BattleSpectateCommand::safeRp))
                .orElse(null);
    }

    public static List<ServerPlayer> getSpectatablePlayers(MinecraftServer server, ServerPlayer viewer) {
        List<ServerPlayer> result = new ArrayList<>();
        if (server == null) return result;

        Set<String> seenBattles = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isSpectatableTarget(player, viewer)) {
                continue;
            }

            ServerPlayer opponent = MatchmakingManager.getOpponent(player);
            String key;
            if (opponent == null) {
                key = player.getUUID().toString();
            } else {
                String a = player.getUUID().toString();
                String b = opponent.getUUID().toString();
                key = a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
            }

            if (seenBattles.add(key)) {
                if (opponent != null && safeRp(opponent) > safeRp(player) && isSpectatableTarget(opponent, viewer)) {
                    result.add(opponent);
                } else {
                    result.add(player);
                }
            }
        }

        result.sort(Comparator
                .comparingInt(BattleSpectateCommand::safeRp)
                .reversed()
                .thenComparing(p -> p.getName().getString().toLowerCase(Locale.ROOT)));

        return result;
    }

    public static boolean isSpectatableTarget(ServerPlayer target, ServerPlayer viewer) {
        if (target == null) return false;
        if (viewer != null && target.getUUID().equals(viewer.getUUID())) return false;
        return BattleStateManager.isInBattle(target) && MatchmakingManager.isMatchmadeBattle(target);
    }

    public static int getRp(ServerPlayer player) {
        return safeRp(player);
    }

    public static String getBattleLabel(ServerPlayer player) {
        ServerPlayer opponent = MatchmakingManager.getOpponent(player);
        String type = MatchmakingManager.isRankedMatch(player) ? "Ranked" : "Casual";
        if (opponent == null) {
            return type + " PvP Battle";
        }
        return type + ": " + player.getName().getString() + " vs " + opponent.getName().getString();
    }

    private static int runBuiltInSpectate(ServerPlayer viewer, ServerPlayer target) {
        try {
            viewer.closeContainer();
            String command = "battlespectate " + target.getName().getString();
            viewer.getServer().getCommands().performPrefixedCommand(viewer.createCommandSourceStack(), command);
            viewer.sendSystemMessage(Component.literal("Spectating " + getBattleLabel(target) + ".").withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (Exception e) {
            viewer.sendSystemMessage(Component.literal("Could not start spectating. Make sure Cobblemon's /battlespectate command is enabled on this server.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int safeRp(ServerPlayer player) {
        try {
            return ProfileManager.getCurrentRp(player);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return null;
        }
    }

    private static ServerPlayer getViewerOrNull(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception ignored) {
            return null;
        }
    }
}
