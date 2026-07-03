package com.champutils.dailylogin;

import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.economy.EconomyManager;
import com.champutils.time.DailyResetManager;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

public final class DailyLoginManager {
    private DailyLoginManager() {}

    public static void load() { DailyLoginConfig.load(); DailyLoginData.load(); }
    public static void save() { DailyLoginConfig.save(); DailyLoginData.save(); }

    public static void handleJoin(ServerPlayer player) { ensureCurrent(player); }
    public static void handleDisconnect(ServerPlayer player) { DailyLoginData.save(); }

    public static void tick(MinecraftServer server) {
        if (!DailyLoginConfig.DATA.settings.enabled) return;
        if (server.getTickCount() <= 0 || server.getTickCount() % 1200 != 0) return;
        boolean changed = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) changed |= addOnlineMinute(player);
        if (changed) DailyLoginData.save();
    }

    public static DailyLoginData.PlayerState getState(ServerPlayer player) { return ensureCurrent(player); }

    private static DailyLoginData.PlayerState ensureCurrent(ServerPlayer player) {
        long resetKey = DailyResetManager.currentResetKeyMillis();
        String monthKey = currentMonthKey(resetKey);
        DailyLoginData.PlayerState state = DailyLoginData.state(player.getUUID(), player.getGameProfile().getName());

        if (!monthKey.equals(state.monthKey)) {
            state.monthKey = monthKey;
            state.trackProgress = 0;
            state.claimedDays.clear();
            state.lastQualifiedResetKey = -1L;
            state.activeResetKey = resetKey;
            state.minutesThisReset = 0;
        }

        if (state.activeResetKey != resetKey) {
            state.activeResetKey = resetKey;
            state.minutesThisReset = 0;
        }

        return state;
    }

    private static boolean addOnlineMinute(ServerPlayer player) {
        DailyLoginData.PlayerState state = ensureCurrent(player);
        if (state.lastQualifiedResetKey == state.activeResetKey) return false;

        state.minutesThisReset++;
        int required = Math.max(1, DailyLoginConfig.DATA.settings.requiredOnlineMinutes);

        if (DailyLoginConfig.DATA.settings.announceProgressEveryFiveMinutes && state.minutesThisReset < required && state.minutesThisReset % 5 == 0) {
            player.sendSystemMessage(Component.literal("Daily login progress: " + state.minutesThisReset + "/" + required + " minutes online.").withStyle(ChatFormatting.AQUA));
        }

        if (state.minutesThisReset >= required) {
            int max = Math.min(DailyLoginConfig.DATA.track.size(), 20);
            if (state.trackProgress < max) {
                state.trackProgress++;
                state.lastQualifiedResetKey = state.activeResetKey;
                ProfessionNotificationSettings.playSound(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.15F);
                player.sendSystemMessage(Component.literal("Daily login complete! Day " + state.trackProgress + " reward is ready in /dailylogin.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                if (DailyLoginConfig.DATA.settings.autoOpenMenuOnEarn) DailyLoginMenu.open(player);
            } else {
                state.lastQualifiedResetKey = state.activeResetKey;
                player.sendSystemMessage(Component.literal("Daily login complete! You already finished this month's reward track.").withStyle(ChatFormatting.GOLD));
            }
        }
        return true;
    }

    public static boolean claim(ServerPlayer player, int day) {
        DailyLoginData.PlayerState state = ensureCurrent(player);
        if (day <= 0 || day > DailyLoginConfig.DATA.track.size()) return false;
        if (state.trackProgress < day) {
            player.sendSystemMessage(Component.literal("That daily login reward is still locked.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (state.claimedDays.contains(day)) {
            player.sendSystemMessage(Component.literal("You already claimed day " + day + ".").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        DailyLoginConfig.RewardDay reward = DailyLoginConfig.DATA.track.get(day - 1);
        for (String command : reward.commands) runRewardCommand(player, command, day);
        state.claimedDays.add(day);
        DailyLoginData.save();
        ProfessionNotificationSettings.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.25F);
        player.sendSystemMessage(Component.literal("Claimed daily login day " + day + " reward!").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static int requiredMinutes() { return Math.max(1, DailyLoginConfig.DATA.settings.requiredOnlineMinutes); }

    public static String currentMonthKey() { return currentMonthKey(DailyResetManager.currentResetKeyMillis()); }

    private static String currentMonthKey(long resetKeyMillis) {
        return YearMonth.from(Instant.ofEpochMilli(resetKeyMillis).atZone(DailyResetManager.resetZone())).toString();
    }

    private static void runRewardCommand(ServerPlayer player, String rawCommand, int day) {
        if (rawCommand == null || rawCommand.isBlank()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        String command = rawCommand
                .replace("%player%", player.getGameProfile().getName())
                .replace("%uuid%", player.getUUID().toString())
                .replace("%day%", String.valueOf(day))
                .replace("%month%", currentMonthKey());
        if (command.startsWith("/")) command = command.substring(1);
        if (handleInternalCommand(player, command)) return;
        CommandSourceStack source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static boolean handleInternalCommand(ServerPlayer player, String command) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length >= 3 && parts[0].equalsIgnoreCase("dailycredits")) {
            try {
                long amount = Math.max(0L, Long.parseLong(parts[2]));
                if (amount > 0L) {
                    EconomyManager.deposit(player, amount, "Daily login reward");
                    player.sendSystemMessage(Component.literal("+" + EconomyManager.format(amount) + " Credits").withStyle(ChatFormatting.GOLD));
                }
            } catch (Exception ignored) {}
            return true;
        }
        return false;
    }
}
