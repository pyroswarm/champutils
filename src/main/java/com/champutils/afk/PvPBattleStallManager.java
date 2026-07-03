package com.champutils.afk;

import com.champutils.battle.BattleStateManager;
import com.champutils.battle.BattleContextManager;
import com.champutils.matchmaking.MatchmakingManager;
import com.cobblemon.mod.common.battles.ForfeitActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PvPBattleStallManager {
    private static final Map<UUID, ChoiceClock> CLOCKS = new HashMap<>();
    private static int tickCounter = 0;

    private PvPBattleStallManager() {}

    public static void recordBattleStarted(Object battle) {
        if (battle == null) return;
        for (ServerPlayer player : playersInBattle(battle)) {
            if (isQueuedPvp(player)) {
                choiceMade(player, battleUuid(battle));
            }
        }
    }

    public static void recordBattleEnded(Object battle) {
        if (battle == null) return;
        UUID battleId = battleUuid(battle);
        CLOCKS.entrySet().removeIf(entry -> battleId != null && battleId.equals(entry.getValue().battleId));
        for (ServerPlayer player : playersInBattle(battle)) {
            CLOCKS.remove(player.getUUID());
        }
    }

    public static void choiceMade(ServerPlayer player, UUID battleId) {
        if (player == null) return;
        ChoiceClock clock = CLOCKS.computeIfAbsent(player.getUUID(), ignored -> new ChoiceClock());
        clock.battleId = battleId;
        clock.mustChooseSinceTick = -1;
        clock.warned = false;
        clock.finalWarned = false;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !AntiAfkConfig.get().pvpBattleStallEnabled) return;
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isQueuedPvp(player)) {
                CLOCKS.remove(player.getUUID());
                continue;
            }

            Object battle = BattleStateManager.getBattle(player);
            if (battle == null) {
                CLOCKS.remove(player.getUUID());
                continue;
            }

            Object actor = playerActorFor(battle, player);
            if (actor == null) continue;

            ChoiceClock clock = CLOCKS.computeIfAbsent(player.getUUID(), ignored -> new ChoiceClock());
            clock.battleId = battleUuid(battle);

            boolean mustChoose = mustChoose(actor);
            if (!mustChoose) {
                clock.mustChooseSinceTick = -1;
                clock.warned = false;
                clock.finalWarned = false;
                continue;
            }

            if (clock.mustChooseSinceTick < 0) {
                clock.mustChooseSinceTick = server.getTickCount();
                clock.warned = false;
                clock.finalWarned = false;
            }

            int waitedSeconds = Math.max(0, (server.getTickCount() - clock.mustChooseSinceTick) / 20);
            AntiAfkConfig config = AntiAfkConfig.get();

            if (!clock.warned && waitedSeconds >= config.pvpChoiceWarnSeconds) {
                clock.warned = true;
                player.sendSystemMessage(Component.literal("§ePick a move or switch. You will forfeit if you keep stalling."));
                ServerPlayer opponent = MatchmakingManager.getOpponent(player);
                if (opponent != null) {
                    opponent.sendSystemMessage(Component.literal("§eOpponent is taking a long time to choose."));
                }
            }

            if (!clock.finalWarned && waitedSeconds >= config.pvpChoiceFinalWarnSeconds) {
                clock.finalWarned = true;
                player.sendSystemMessage(Component.literal("§cFinal warning: choose now or you forfeit."));
            }

            if (waitedSeconds >= config.pvpChoiceTimeoutSeconds) {
                timeoutPlayer(player, battle, actor);
            }
        }
    }

    private static void timeoutPlayer(ServerPlayer player, Object battle, Object actor) {
        UUID playerId = player.getUUID();
        CLOCKS.remove(playerId);

        player.sendSystemMessage(Component.literal("§cYou forfeited because you did not choose a battle action in time."));
        ServerPlayer opponent = MatchmakingManager.getOpponent(player);
        if (opponent != null) {
            opponent.sendSystemMessage(Component.literal("§aOpponent forfeited by battle inactivity."));
        }

        boolean forfeited = false;
        if (AntiAfkConfig.get().pvpForfeitOnTimeout) {
            forfeited = submitForfeit(actor);
        }
        if (!forfeited) {
            stopBattle(battle, player);
        }
        if (AntiAfkConfig.get().pvpKickAfterForfeit) {
            player.connection.disconnect(Component.literal("Kicked for stalling a PvP battle."));
        }
    }

    private static boolean isQueuedPvp(ServerPlayer player) {
        if (player == null) return false;
        if (MatchmakingManager.isMatchmadeBattle(player)) return true;
        BattleContextManager.BattleType type = BattleContextManager.getContext(player.getUUID());
        return type == BattleContextManager.BattleType.RANKED || type == BattleContextManager.BattleType.CASUAL;
    }

    private static List<ServerPlayer> playersInBattle(Object battle) {
        java.util.ArrayList<ServerPlayer> players = new java.util.ArrayList<>();
        try {
            Object actors = battle.getClass().getMethod("getActors").invoke(battle);
            if (actors instanceof Iterable<?> iterable) {
                for (Object actor : iterable) {
                    if (actor instanceof PlayerBattleActor playerActor && playerActor.getEntity() instanceof ServerPlayer player) {
                        players.add(player);
                    }
                }
            }
        } catch (Exception ignored) {}
        return players;
    }

    private static Object playerActorFor(Object battle, ServerPlayer player) {
        try {
            Object actors = battle.getClass().getMethod("getActors").invoke(battle);
            if (actors instanceof Iterable<?> iterable) {
                for (Object actor : iterable) {
                    if (actor instanceof PlayerBattleActor playerActor && playerActor.getEntity() instanceof ServerPlayer actorPlayer) {
                        if (actorPlayer.getUUID().equals(player.getUUID())) return actor;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean mustChoose(Object actor) {
        try {
            Method method = actor.getClass().getMethod("getMustChoose");
            Object value = method.invoke(actor);
            return Boolean.TRUE.equals(value);
        } catch (Exception ignored) {}
        try {
            Method method = actor.getClass().getMethod("mustChoose");
            Object value = method.invoke(actor);
            return Boolean.TRUE.equals(value);
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean submitForfeit(Object actor) {
        try {
            Method method = actor.getClass().getMethod("setActionResponses", List.class);
            method.invoke(actor, List.<ShowdownActionResponse>of(new ForfeitActionResponse()));
            return true;
        } catch (Exception e) {
            System.err.println("[ChampUtils][PvPStall] Failed to submit forfeit: " + e.getMessage());
            return false;
        }
    }

    private static void stopBattle(Object battle, ServerPlayer player) {
        if (battle == null) return;
        String[] methods = {"forfeit", "flee", "stop", "end", "endBattle", "close"};
        for (String name : methods) {
            if (invoke(battle, name, player) || invoke(battle, name)) return;
        }
    }

    private static boolean invoke(Object target, String methodName, Object... args) {
        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) continue;
                method.setAccessible(true);
                method.invoke(target, args);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static UUID battleUuid(Object battle) {
        if (battle == null) return null;
        for (String methodName : new String[]{"getBattleId", "battleId", "getId", "id"}) {
            try {
                Object value = battle.getClass().getMethod(methodName).invoke(battle);
                if (value instanceof UUID uuid) return uuid;
                if (value != null) return UUID.fromString(String.valueOf(value));
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static final class ChoiceClock {
        UUID battleId;
        int mustChooseSinceTick = -1;
        boolean warned;
        boolean finalWarned;
    }
}
