package com.champutils.commands;

import com.champutils.battle.AITestGymLeaderBuilder;
import com.champutils.battle.BattleAIDifficultyManager;
import com.champutils.battle.ChampBattleAIConfig;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.commands.Commands.literal;

public final class ChampAICommand {
    private ChampAICommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("champutils")
                        .then(literal("ai")
                                .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.staff"))
                                .then(literal("status").executes(context -> status(context.getSource())))
                                .then(literal("reload").executes(context -> reload(context.getSource())))
                                .then(literal("debug")
                                        .then(literal("on").executes(context -> debug(context.getSource(), true)))
                                        .then(literal("off").executes(context -> debug(context.getSource(), false))))
                                .then(literal("last").executes(context -> last(context.getSource())))
                                .then(literal("spawn-test-npc").executes(context -> spawnTestNpc(context.getSource()))))
        ));
    }

    private static int status(CommandSourceStack source) {
        ChampBattleAIConfig.Data data = ChampBattleAIConfig.DATA;
        source.sendSuccess(() -> Component.literal("ChampUtils AI Status").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        line(source, "enabled", String.valueOf(data.enabled));
        line(source, "debug", String.valueOf(data.debug));
        bucket(source, "wild", data.wildBattles);
        bucket(source, "trainer", data.trainerBattles);
        bucket(source, "gym", data.gymBattles);
        bucket(source, "guildBoss", data.guildBossBattles);
        bucket(source, "worldBoss", data.worldBossBattles);
        line(source, "antiSpam.protectRepeatPenaltyTurns", String.valueOf(data.antiSpam.protectRepeatPenaltyTurns));
        line(source, "antiSpam.sameMoveSoftLimit", String.valueOf(data.antiSpam.sameMoveSoftLimit));
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        BattleAIDifficultyManager.reloadConfig();
        source.sendSuccess(() -> Component.literal("Reloaded config/champutils/battle_ai.json").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int debug(CommandSourceStack source, boolean enabled) {
        BattleAIDifficultyManager.setDebug(enabled);
        source.sendSuccess(() -> Component.literal("ChampUtils AI debug " + (enabled ? "enabled" : "disabled") + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int last(CommandSourceStack source) {
        BattleAIDifficultyManager.LastAssignment last = BattleAIDifficultyManager.getLastAssignment();
        source.sendSuccess(() -> Component.literal("Last AI Assignment").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        line(source, "player", last.player());
        line(source, "battleId", last.battleId());
        line(source, "detectedType", last.detectedType());
        line(source, "actorType", last.actorType());
        line(source, "skill", String.valueOf(last.skill()));
        line(source, "wrapperUsed", String.valueOf(last.wrapperUsed()));
        line(source, "fallbackUsed", String.valueOf(last.fallbackUsed()));
        line(source, "applied", String.valueOf(last.applied()));
        return 1;
    }

    private static int spawnTestNpc(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            Vec3 pos = player.position().add(player.getLookAngle().normalize().scale(2.0D));
            NPCEntity npc = AITestGymLeaderBuilder.spawn(level, pos, player.getYRot() + 180.0F);
            if (npc == null) {
                source.sendFailure(Component.literal("Could not spawn AI Test Gym Leader."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Spawned AI Test Gym Leader. Fight it to verify hard AI, hazards, setup, recovery, and Protect anti-spam.").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only an in-game player can run /champutils ai spawn-test-npc."));
            return 0;
        }
    }

    private static void bucket(CommandSourceStack source, String name, ChampBattleAIConfig.BattleBucket bucket) {
        line(source, name, "enabled=" + bucket.enabled + ", skill=" + bucket.skill + ", competitiveLayer=" + bucket.competitiveLayer + ", antiSpamLayer=" + bucket.antiSpamLayer);
    }

    private static void line(CommandSourceStack source, String key, String value) {
        source.sendSuccess(() -> Component.literal("- " + key + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.AQUA)), false);
    }
}
