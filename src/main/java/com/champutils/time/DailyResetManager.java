package com.champutils.time;

import com.champutils.guild.BossConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Shared daily reset helper for every ChampUtils system that should roll over together.
 *
 * Default reset: 2:00 AM in the server JVM's local timezone.
 * Reuse this for guild bosses, daily login rewards, monthly login tracks, and future daily systems.
 */
public final class DailyResetManager {
    private DailyResetManager() {}

    public static ZoneId resetZone() {
        String configured = BossConfig.DATA.dailyReset.timeZone;
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("system")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configured.trim());
        } catch (Exception ignored) {
            return ZoneId.systemDefault();
        }
    }

    public static LocalTime resetTime() {
        int hour = Math.max(0, Math.min(23, BossConfig.DATA.dailyReset.hour));
        int minute = Math.max(0, Math.min(59, BossConfig.DATA.dailyReset.minute));
        return LocalTime.of(hour, minute);
    }

    public static long currentResetKeyMillis() {
        return resetKeyMillis(System.currentTimeMillis());
    }

    public static long resetKeyMillis(long epochMillis) {
        ZoneId zone = resetZone();
        LocalTime reset = resetTime();
        ZonedDateTime now = Instant.ofEpochMilli(epochMillis).atZone(zone);
        LocalDate resetDate = now.toLocalDate();
        if (now.toLocalTime().isBefore(reset)) {
            resetDate = resetDate.minusDays(1);
        }
        return ZonedDateTime.of(LocalDateTime.of(resetDate, reset), zone).toInstant().toEpochMilli();
    }

    public static long nextResetMillis(long epochMillis) {
        ZoneId zone = resetZone();
        LocalTime reset = resetTime();
        ZonedDateTime now = Instant.ofEpochMilli(epochMillis).atZone(zone);
        ZonedDateTime todayReset = ZonedDateTime.of(LocalDateTime.of(now.toLocalDate(), reset), zone);
        if (now.isBefore(todayReset)) return todayReset.toInstant().toEpochMilli();
        return todayReset.plusDays(1).toInstant().toEpochMilli();
    }

    public static String formatResetTime() {
        LocalTime time = resetTime();
        int hour = time.getHour();
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        String suffix = hour < 12 ? "AM" : "PM";
        return displayHour + ":" + String.format("%02d", time.getMinute()) + " " + suffix + " local server time";
    }
}
