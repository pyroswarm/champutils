package com.champutils.database;

import com.champutils.profession.ProfessionDataManager;
import com.champutils.profile.PlayerDataManager;

public final class DatabaseBootstrapSync {

    private DatabaseBootstrapSync() {
    }

    public static void syncExistingLocalData() {
        if (!DatabaseManager.isEnabled()) {
            return;
        }

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

        System.out.println("[ChampUtils] Queued local player/profession data for database sync.");
    }
}
