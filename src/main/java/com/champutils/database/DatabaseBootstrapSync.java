package com.champutils.database;

import com.champutils.adventurer.AdventurerGuildDataManager;
import com.champutils.profession.ProfessionDataManager;
import com.champutils.profile.PlayerDataManager;

public final class DatabaseBootstrapSync {

    private DatabaseBootstrapSync() {
    }

    public static void syncExistingLocalData() {
        if (!DatabaseManager.isEnabled()) {
            return;
        }

        SeasonDatabaseRepository.syncCurrentSeason();

        for (PlayerDataManager.OfflinePlayerEntry entry : PlayerDataManager.getAllPlayers()) {
            if (entry == null || entry.data == null) {
                continue;
            }
            PlayerDatabaseRepository.sync(entry.data);
        }

        for (ProfessionDataManager.ProfessionData data : ProfessionDataManager.getAllPlayers()) {
            if (data == null) {
                continue;
            }
            ProfessionDatabaseRepository.sync(data);
        }

        for (AdventurerGuildDataManager.PlayerData data : AdventurerGuildDataManager.getAllLocalPlayers()) {
            if (data == null) {
                continue;
            }
            AdventurerGuildDataManager.syncToDatabase(data);
        }

        System.out.println("[ChampUtils] Queued local season/player/profession/adventurer guild data for database sync.");
    }
}
