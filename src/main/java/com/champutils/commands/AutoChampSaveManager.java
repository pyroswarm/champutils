package com.champutils.commands;

import com.champutils.time.DailyResetManager;
import net.minecraft.server.MinecraftServer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/** Runs the same force-save as /champsave once per day before the host auto reboot. */
public final class AutoChampSaveManager {
    private static LocalDate lastSavedDate = null;
    private static int tickGate = 0;

    private AutoChampSaveManager() {}

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (++tickGate < 20) return;
        tickGate = 0;
        ZonedDateTime now = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(DailyResetManager.resetZone());
        LocalTime time = now.toLocalTime();
        LocalDate date = now.toLocalDate();
        if (lastSavedDate != null && lastSavedDate.equals(date)) return;
        if (time.getHour() == 3 && time.getMinute() == 56) {
            lastSavedDate = date;
            System.out.println("[ChampUtils] Running scheduled /champsave before 3:57 AM auto reboot.");
            ForceSaveRestartCommand.forceSave(server);
        }
    }
}
