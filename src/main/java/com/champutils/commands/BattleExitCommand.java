package com.champutils.commands;

import com.champutils.battle.BattleStateManager;
import com.champutils.battle.BattleStuckCleanupManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

public class BattleExitCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("battleexit")
                        .executes(ctx -> exitSelf(ctx.getSource(), false))
                        .then(Commands.literal("force")
                                .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.staff"))
                                .executes(ctx -> exitSelf(ctx.getSource(), true))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> exitOther(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                true
                                        ))))
                        .then(Commands.literal("check")
                                .executes(ctx -> checkSelf(ctx.getSource())))
        ));
    }

    private static int exitSelf(
            CommandSourceStack source,
            boolean force
    ) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can use /battleexit without a target."));
            return 0;
        }

        return clearPlayer(source, player, force);
    }

    private static int exitOther(
            CommandSourceStack source,
            ServerPlayer target,
            boolean force
    ) {
        return clearPlayer(source, target, force);
    }

    private static int checkSelf(
            CommandSourceStack source
    ) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only a player can use this command."));
            return 0;
        }

        boolean cleaned = BattleStuckCleanupManager.cleanupIfStale(player, true);
        if (!cleaned) {
            player.sendSystemMessage(
                    Component.literal("No stale battle state was found.")
                            .withStyle(ChatFormatting.YELLOW)
            );
        }
        return 1;
    }

    private static int clearPlayer(
            CommandSourceStack source,
            ServerPlayer player,
            boolean force
    ) {
        if (player == null) {
            source.sendFailure(Component.literal("Player not found."));
            return 0;
        }

        boolean hadTrackedState = BattleStateManager.hasTrackedState(player);
        boolean cleanedStale = BattleStuckCleanupManager.cleanupIfStale(player, false);

        if (!force && !cleanedStale) {
            if (!hadTrackedState) {
                player.sendSystemMessage(
                        Component.literal("No battle state was found.")
                                .withStyle(ChatFormatting.YELLOW)
                );
                return 0;
            }

            if (BattleStateManager.looksLikeActiveBattle(player)) {
                player.sendSystemMessage(
                        Component.literal("You still appear to be in an active battle. Use this only after the battle is actually stuck, or ask staff to run /battleexit force <player>.")
                                .withStyle(ChatFormatting.RED)
                );
                return 0;
            }

            BattleStateManager.clearAll(player);
            player.sendSystemMessage(
                    Component.literal("Your battle state was cleared.")
                            .withStyle(ChatFormatting.GREEN)
            );
            return 1;
        }

        if (force) {
            tryForfeitOrStopBattle(player);
            BattleStateManager.clearAll(player);
            player.sendSystemMessage(
                    Component.literal("Your battle state was force-cleared by staff.")
                            .withStyle(ChatFormatting.GREEN)
            );

            if (source.getEntity() != player) {
                source.sendSuccess(
                        () -> Component.literal("Force-cleared battle state for " + player.getName().getString() + "."),
                        false
                );
            }
            return 1;
        }

        player.sendSystemMessage(
                Component.literal("Your stale battle state was cleared.")
                        .withStyle(ChatFormatting.GREEN)
        );
        return 1;
    }

    private static void tryForfeitOrStopBattle(
            ServerPlayer player
    ) {
        Object battle = BattleStateManager.getBattle(player);
        if (battle == null) {
            return;
        }

        String[] methods = new String[]{
                "forfeit",
                "flee",
                "stop",
                "end",
                "endBattle",
                "close"
        };

        for (String methodName : methods) {
            if (invoke(battle, methodName, player)) {
                return;
            }
            if (invoke(battle, methodName)) {
                return;
            }
        }
    }

    private static boolean invoke(
            Object target,
            String methodName,
            Object... args
    ) {
        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }

                if (method.getParameterCount() != args.length) {
                    continue;
                }

                method.setAccessible(true);
                method.invoke(target, args);
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }
}
