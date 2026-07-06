package com.champutils.profile;

import com.champutils.chat.ChatPreferenceManager;
import com.champutils.database.DatabaseManager;
import com.champutils.economy.EconomyManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.quest.QuestManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.TimeUnit;

public final class ProfileStateFlushService {
    private ProfileStateFlushService() {
    }

    public static boolean flushBeforeTransfer(ServerPlayer player, String reason, long timeout, TimeUnit unit) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) {
            return true;
        }

        try {
            PlayerProfileManager.forceSaveActiveProfileStateAsync(player, reason == null ? "profile_transfer" : reason);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to queue active profile checkpoint before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
            return false;
        }

        try {
            PlayerProfileManager.saveActiveLocationAsync(player);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to queue location checkpoint before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
        }

        try {
            ProfessionManager.savePlayerNow(player);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save professions before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
            return false;
        }

        try {
            QuestManager.savePlayer(player);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save quest state before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
        }

        try {
            com.champutils.adventureguide.AdventureGuideManager.saveAll();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save adventure guide state before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
        }

        try {
            com.champutils.adventurer.AdventurerGuildManager.savePlayer(player);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save adventurer guild state before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
        }

        try {
            ChatPreferenceManager.saveAsync(player.getUUID(), ChatPreferenceManager.get(player.getUUID()));
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to queue chat preferences before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
        }

        try {
            EconomyManager.save();
        } catch (Exception ignored) {
        }

        // Do not block the Minecraft server thread waiting for the entire global database queue.
        // The global queue can contain unrelated sidebar/economy/profession tasks, which made
        // /profiles fail with "Could not safely save your profile yet" even though the profile
        // checkpoint had already been queued. Profile saves are atomic/coalesced in SQL, so the
        // safe transfer behavior is to snapshot on the server thread, queue the writes, and let
        // the profile lobby/survival handoff continue instead of freezing or falsely failing.
        if (Thread.currentThread().getName() != null && Thread.currentThread().getName().equalsIgnoreCase("Server thread")) {
            return true;
        }
        return DatabaseManager.flushSubmittedTasks(timeout, unit);
    }
}
