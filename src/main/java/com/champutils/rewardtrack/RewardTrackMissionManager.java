package com.champutils.rewardtrack;

import com.champutils.time.DailyResetManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Ranked PvP daily/weekly mission layer for /rewardtrack. */
public final class RewardTrackMissionManager {
    private RewardTrackMissionManager() {}

    public static void ensure(ServerPlayer player) {
        if (player == null) return;
        RewardTrackData.Save save = RewardTrackData.get(player);
        long day = currentDayKey();
        long week = currentWeekKey();
        boolean changed = false;

        if (save.missions == null) save.missions = new ArrayList<>();
        if (save.dailyKey != day) {
            save.missions.removeIf(m -> m != null && m.daily);
            save.dailyKey = day;
            addDaily(save, day, player.getUUID().hashCode());
            changed = true;
        }
        if (save.lastWeeklyKey < week) {
            long start = save.lastWeeklyKey <= 0 ? week : save.lastWeeklyKey + 1;
            for (long w = start; w <= week; w++) addWeekly(save, w, player.getUUID().hashCode());
            save.lastWeeklyKey = week;
            changed = true;
        }
        if (changed) RewardTrackData.save(player);
    }

    public static void onRankedEvent(ServerPlayer player, String reason) {
        if (player == null || reason == null) return;
        ensure(player);
        RewardTrackData.Save save = RewardTrackData.get(player);
        boolean isWin = reason.equalsIgnoreCase("ranked_win");
        boolean isPlay = isWin || reason.equalsIgnoreCase("ranked_play");
        if (!isPlay) return;

        boolean changed = false;
        for (RewardTrackData.Mission mission : save.missions) {
            if (mission == null || mission.claimed) continue;
            if ("WIN".equalsIgnoreCase(mission.type) && !isWin) continue;
            if (!"WIN".equalsIgnoreCase(mission.type) && !"PLAY".equalsIgnoreCase(mission.type)) continue;
            int before = mission.progress;
            mission.progress = Math.min(Math.max(1, mission.target), mission.progress + 1);
            if (mission.progress != before) changed = true;
            if (mission.progress >= mission.target && !mission.claimed) {
                mission.claimed = true;
                changed = true;
                RewardTrackCommand.addRawXp(player, Math.max(1, mission.xp), mission.id);
                player.sendSystemMessage(Component.literal("Completed mission: " + mission.title + " (+" + mission.xp + " Battlepass XP)").withStyle(ChatFormatting.GREEN));
            }
        }
        if (changed) RewardTrackData.save(player);
    }

    public static void show(ServerPlayer player) {
        ensure(player);
        RewardTrackData.Save save = RewardTrackData.get(player);
        player.sendSystemMessage(Component.literal("Ranked Missions").withStyle(ChatFormatting.GOLD));
        save.missions.stream()
                .filter(m -> m != null)
                .sorted(Comparator.comparing((RewardTrackData.Mission m) -> !m.daily).thenComparing(m -> m.id))
                .forEach(m -> {
                    ChatFormatting color = m.claimed ? ChatFormatting.DARK_GRAY : (m.daily ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE);
                    String scope = m.daily ? "Daily" : "Week " + m.week;
                    player.sendSystemMessage(Component.literal(scope + " - " + m.title + " " + Math.min(m.progress, m.target) + "/" + m.target + (m.claimed ? " ✓" : "") + " (" + m.xp + " XP)").withStyle(color));
                });
    }

    private static void addDaily(RewardTrackData.Save save, long day, int seed) {
        Random random = new Random(day * 31L + seed);
        int playTarget = 2 + random.nextInt(2);
        int winTarget = 1 + random.nextInt(2);
        int practiceTarget = 1 + random.nextInt(3);
        save.missions.add(mission("daily_play_" + day, true, 0, "PLAY", "Play " + playTarget + " ranked PvP matches", playTarget, 250));
        save.missions.add(mission("daily_win_" + day, true, 0, "WIN", "Win " + winTarget + " ranked PvP matches", winTarget, 350));
        save.missions.add(mission("daily_practice_" + day, true, 0, "PLAY", dailyFlavor(random).replace("{n}", String.valueOf(practiceTarget)), practiceTarget, 300));
    }

    private static void addWeekly(RewardTrackData.Save save, long week, int seed) {
        Random random = new Random(week * 53L + seed);
        String[] flavors = weeklyFlavors();
        int weeklyPlay = 10 + random.nextInt(6);
        int weeklyWin = 5 + random.nextInt(4);
        save.missions.add(mission("weekly_play_" + week, false, week, "PLAY", "Play " + weeklyPlay + " ranked PvP matches", weeklyPlay, 900));
        save.missions.add(mission("weekly_win_" + week, false, week, "WIN", "Win " + weeklyWin + " ranked PvP matches", weeklyWin, 1100));
        java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>();
        int guard = 0;
        while (selected.size() < 5 && guard++ < 80) {
            selected.add(flavors[Math.floorMod(random.nextInt(), flavors.length)]);
        }
        int i = 0;
        for (String title : selected) {
            String type = title.toLowerCase(Locale.ROOT).startsWith("win") ? "WIN" : "PLAY";
            int target = "WIN".equals(type) ? 2 + random.nextInt(4) : 3 + random.nextInt(5);
            save.missions.add(mission("weekly_" + week + "_var_" + i, false, week, type, title.replace("{n}", String.valueOf(target)), target, "WIN".equals(type) ? 1100 : 850));
            i++;
        }
    }

    private static RewardTrackData.Mission mission(String id, boolean daily, long week, String type, String title, int target, int xp) {
        RewardTrackData.Mission m = new RewardTrackData.Mission();
        m.id = id;
        m.daily = daily;
        m.week = week;
        m.type = type;
        m.title = title;
        m.target = Math.max(1, target);
        m.xp = Math.max(1, xp);
        return m;
    }

    private static String dailyFlavor(Random random) {
        String[] values = {
                "Play {n} ranked PvP matches",
                "Play {n} ranked PvP matches without leaving early",
                "Play {n} ranked PvP matches using your chosen lead Pokémon",
                "Play {n} ranked PvP matches using the same team",
                "Play {n} ranked PvP matches and finish each battle"
        };
        return values[Math.floorMod(random.nextInt(), values.length)];
    }

    private static String[] weeklyFlavors() {
        return new String[] {
                "Win {n} ranked PvP matches using a team that all shares one type",
                "Play {n} ranked PvP matches with a mono-type team",
                "Win {n} ranked PvP matches with no duplicate held items",
                "Play {n} ranked PvP matches with a starter Pokémon on your team",
                "Win {n} ranked PvP matches using the same lead slot all match",
                "Play {n} ranked PvP matches with at least one support Pokémon",
                "Win {n} ranked PvP matches",
                "Play {n} ranked PvP matches using the same team",
                "Play {n} ranked PvP matches and finish each battle",
                "Win {n} ranked PvP matches after changing at least one team member"
        };
    }

    private static long currentDayKey() {
        return java.time.Instant.ofEpochMilli(DailyResetManager.currentResetKeyMillis())
                .atZone(DailyResetManager.resetZone())
                .toLocalDate()
                .toEpochDay();
    }

    private static long currentWeekKey() {
        LocalDate now = java.time.Instant.ofEpochMilli(DailyResetManager.currentResetKeyMillis())
                .atZone(DailyResetManager.resetZone())
                .toLocalDate();
        WeekFields wf = WeekFields.ISO;
        return now.getYear() * 100L + now.get(wf.weekOfWeekBasedYear());
    }

    public static int seasonWeekIndex(long weekKey) {
        if (weekKey <= 0) return 1;
        return (int) (Math.floorMod(weekKey, 4) + 1);
    }
}
