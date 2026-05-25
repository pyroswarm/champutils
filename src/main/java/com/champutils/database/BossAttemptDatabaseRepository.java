package com.champutils.database;

import com.champutils.time.DailyResetManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class BossAttemptDatabaseRepository {
    private static final AtomicLong LAST_PRUNED_RESET_KEY = new AtomicLong(Long.MIN_VALUE);

    private BossAttemptDatabaseRepository() {
    }

    public static boolean hasAttempt(String bossType, UUID bossId, UUID playerUuid) {
        if (bossType == null || bossType.isBlank() || bossId == null || playerUuid == null) {
            return false;
        }
        if (!DatabaseManager.isEnabled()) {
            return false;
        }

        long resetKeyMillis = DailyResetManager.currentResetKeyMillis();

        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "select 1 from boss_attempts where boss_type = ? and boss_id = ? and player_uuid = ? and reset_key_millis = ? limit 1"
        )) {
            statement.setString(1, bossType);
            statement.setObject(2, bossId);
            statement.setObject(3, playerUuid);
            statement.setLong(4, resetKeyMillis);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to check boss attempt for " + playerUuid + " / " + bossType + " / " + bossId);
            e.printStackTrace();
            return false;
        }
    }

    public static void recordAttempt(String bossType, UUID bossId, UUID playerUuid, String playerName) {
        if (bossType == null || bossType.isBlank() || bossId == null || playerUuid == null) {
            return;
        }
        if (!DatabaseManager.isEnabled()) {
            return;
        }

        String safeName = playerName == null || playerName.isBlank() ? playerUuid.toString() : playerName;
        long resetKeyMillis = DailyResetManager.currentResetKeyMillis();

        DatabaseManager.executeAsync(
                "record boss attempt " + bossType + " " + bossId + " " + playerUuid,
                connection -> {
                    try (PreparedStatement playerStatement = connection.prepareStatement(
                            "insert into players (uuid, username, last_seen) " +
                                    "values (?, ?, now()) " +
                                    "on conflict (uuid) do update set username = excluded.username, last_seen = now()"
                    )) {
                        playerStatement.setObject(1, playerUuid);
                        playerStatement.setString(2, safeName);
                        playerStatement.executeUpdate();
                    }

                    try (PreparedStatement statement = connection.prepareStatement(
                            "insert into boss_attempts (boss_type, boss_id, player_uuid, reset_key_millis, player_name, attempted_at) " +
                                    "values (?, ?, ?, ?, ?, now()) " +
                                    "on conflict (boss_type, boss_id, player_uuid, reset_key_millis) do nothing"
                    )) {
                        statement.setString(1, bossType);
                        statement.setObject(2, bossId);
                        statement.setObject(3, playerUuid);
                        statement.setLong(4, resetKeyMillis);
                        statement.setString(5, safeName);
                        statement.executeUpdate();
                    }
                }
        );
    }

    public static void pruneBeforeResetAsync(long currentResetKeyMillis) {
        if (!DatabaseManager.isEnabled()) {
            return;
        }
        long lastPruned = LAST_PRUNED_RESET_KEY.get();
        if (lastPruned == currentResetKeyMillis) {
            return;
        }
        if (!LAST_PRUNED_RESET_KEY.compareAndSet(lastPruned, currentResetKeyMillis)) {
            return;
        }

        DatabaseManager.executeAsync(
                "prune old boss attempts before reset " + currentResetKeyMillis,
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "delete from boss_attempts where reset_key_millis < ?"
                    )) {
                        statement.setLong(1, currentResetKeyMillis);
                        statement.executeUpdate();
                    }
                }
        );
    }
}

