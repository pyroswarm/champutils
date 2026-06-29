package com.champutils.commands;

import com.champutils.time.DailyResetManager;
import com.champutils.profile.ProfileNetworkTransferFlow;
import net.minecraft.server.MinecraftServer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/** Runs the pre-reboot drain once per day before the host auto reboot. */
public final class AutoChampSaveManager {
    private static LocalDate lastSavedDate = null;
    private static LocalDate lastWarn10Date = null;
    private static LocalDate lastWarn5Date = null;
    private static LocalDate lastWarn1Date = null;
    private static int tickGate = 0;

    private AutoChampSaveManager() {}

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (++tickGate < 20) return;
        tickGate = 0;
        // Only the survival server has the host auto-reboot. The profile lobby must remain online
        // so players can safely land there while survival drains and saves.
        if (!ProfileNetworkTransferFlow.isSurvivalServer()) return;

        ZonedDateTime now = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(DailyResetManager.resetZone());
        LocalTime time = now.toLocalTime();
        LocalDate date = now.toLocalDate();
        if (time.getHour() == 3 && time.getMinute() == 47 && !date.equals(lastWarn10Date)) {
            lastWarn10Date = date;
            ForceSaveRestartCommand.broadcastPreRebootWarning(server, 10);
        }
        if (time.getHour() == 3 && time.getMinute() == 52 && !date.equals(lastWarn5Date)) {
            lastWarn5Date = date;
            ForceSaveRestartCommand.broadcastPreRebootWarning(server, 5);
        }
        if (time.getHour() == 3 && time.getMinute() == 53 && !date.equals(lastWarn1Date)) {
            lastWarn1Date = date;
            ForceSaveRestartCommand.broadcastPreRebootWarning(server, 1);
        }
        if (lastSavedDate != null && lastSavedDate.equals(date)) return;
        // Host reboot is 3:57 AM. At 3:54, drain players back to the profile lobby, then
        // run the same save/SQL barrier used by /champsave without stopping this JVM.
        // This gives the save pipeline roughly 3 minutes before the host process restarts.
        if (time.getHour() == 3 && time.getMinute() == 54) {
            lastSavedDate = date;
            System.out.println("[ChampUtils] Running scheduled survival pre-reboot drain before 3:57 AM auto reboot.");
            ForceSaveRestartCommand.preRebootDrain(server, null);
        }
    }
}
