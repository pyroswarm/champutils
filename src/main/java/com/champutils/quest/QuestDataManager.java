package com.champutils.quest;

import com.champutils.database.SharedJsonStateRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class QuestDataManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PLAYER_STATE_KEY = "quests";
    private static final String GUILD_STATE_KEY = "guild_quests";

    public static class QuestData {
        public String uuid;
        public String name;
        public QuestSet daily;
        public QuestSet weekly;
        public ArrayList<Contract> contracts = new ArrayList<>();
        public String trackedKind;
        public String trackedObjectiveId;
        public long trackedContractPurchasedAt;
    }

    public static class QuestSet {
        public String periodKey;
        public boolean completed;
        public ArrayList<Objective> objectives = new ArrayList<>();
    }

    public static class Objective {
        public String id;
        public String description;
        public String objectiveType;
        public String profession;
        public String target;
        public int required;
        public int progress;
        public int requiredPlayers;
        public HashMap<String, Integer> playerProgress = new HashMap<>();
        public HashSet<String> completedPlayers = new HashSet<>();
    }

    public static class GuildQuestData {
        public String guildId;
        public String guildName;
        public QuestSet weekly;
        public HashSet<String> claimedWeekly = new HashSet<>();
    }

    public static class Contract extends Objective {
        public long purchasedAtMillis;
        public long expiresAtMillis;
        public int creditCost;
        public int rewardCredits;
        public String difficulty;
        public boolean completed;
        public ArrayList<String> rewardCommands = new ArrayList<>();
    }

    private static File dir() {
        File dir = new File("config/champutils/quests/players");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File guildDir() {
        File dir = new File("config/champutils/quests/guilds");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File file(UUID uuid) {
        return new File(dir(), uuid.toString() + ".json");
    }

    private static File guildFile(UUID guildId) {
        return new File(guildDir(), guildId.toString() + ".json");
    }

    public static QuestData load(UUID uuid, String name) {
        try {
            File file = file(uuid);
            QuestData data = null;
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    data = GSON.fromJson(reader, QuestData.class);
                }
            }
            if (data == null) data = new QuestData();
            data = SharedJsonStateRepository.loadProfile(uuid, PLAYER_STATE_KEY, QuestData.class, data);
            data.uuid = uuid.toString();
            data.name = name;
            if (data.daily == null) data.daily = new QuestSet();
            if (data.weekly == null) data.weekly = new QuestSet();
            if (data.daily.objectives == null) data.daily.objectives = new ArrayList<>();
            if (data.weekly.objectives == null) data.weekly.objectives = new ArrayList<>();
            if (data.contracts == null) data.contracts = new ArrayList<>();
            for (Contract c : data.contracts) if (c.rewardCommands == null) c.rewardCommands = new ArrayList<>();
            return data;
        } catch (Exception e) {
            e.printStackTrace();
            QuestData data = new QuestData();
            data.uuid = uuid.toString();
            data.name = name;
            data.daily = new QuestSet();
            data.weekly = new QuestSet();
            data.contracts = new ArrayList<>();
            return data;
        }
    }

    public static void save(UUID uuid, QuestData data) {
        try (FileWriter writer = new FileWriter(file(uuid))) {
            GSON.toJson(data, writer);
            SharedJsonStateRepository.saveProfile(uuid, PLAYER_STATE_KEY, data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static GuildQuestData loadGuild(UUID guildId, String guildName) {
        try {
            File file = guildFile(guildId);
            GuildQuestData data = null;
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    data = GSON.fromJson(reader, GuildQuestData.class);
                }
            }
            if (data == null) data = new GuildQuestData();
            data = SharedJsonStateRepository.loadProfile(guildId, GUILD_STATE_KEY, GuildQuestData.class, data);
            data.guildId = guildId.toString();
            data.guildName = guildName;
            if (data.weekly == null) data.weekly = new QuestSet();
            if (data.weekly.objectives == null) data.weekly.objectives = new ArrayList<>();
            if (data.claimedWeekly == null) data.claimedWeekly = new HashSet<>();
            for (Objective o : data.weekly.objectives) {
                if (o.playerProgress == null) o.playerProgress = new HashMap<>();
                if (o.completedPlayers == null) o.completedPlayers = new HashSet<>();
            }
            return data;
        } catch (Exception e) {
            e.printStackTrace();
            GuildQuestData data = new GuildQuestData();
            data.guildId = guildId.toString();
            data.guildName = guildName;
            data.weekly = new QuestSet();
            data.claimedWeekly = new HashSet<>();
            return data;
        }
    }

    public static void saveGuild(UUID guildId, GuildQuestData data) {
        try (FileWriter writer = new FileWriter(guildFile(guildId))) {
            GSON.toJson(data, writer);
            SharedJsonStateRepository.saveProfile(guildId, GUILD_STATE_KEY, data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static <R> java.util.concurrent.CompletableFuture<R> mutateGuildAsync(
            UUID guildId,
            String guildName,
            java.util.function.Function<GuildQuestData, R> mutator
    ) {
        GuildQuestData fallback = new GuildQuestData();
        fallback.guildId = guildId == null ? "" : guildId.toString();
        fallback.guildName = guildName == null ? "" : guildName;
        fallback.weekly = new QuestSet();
        fallback.claimedWeekly = new HashSet<>();
        return SharedJsonStateRepository.mutateProfileAsync(guildId, GUILD_STATE_KEY, GuildQuestData.class, fallback, data -> {
            normalizeGuild(data, guildId, guildName);
            R result = mutator.apply(data);
            try (FileWriter writer = new FileWriter(guildFile(guildId))) {
                GSON.toJson(data, writer);
            } catch (Exception ignored) {
                // SQL is authoritative; the local JSON file is only a recovery/debug mirror.
            }
            return result;
        });
    }

    private static void normalizeGuild(GuildQuestData data, UUID guildId, String guildName) {
        if (data == null) return;
        data.guildId = guildId == null ? "" : guildId.toString();
        data.guildName = guildName == null ? "" : guildName;
        if (data.weekly == null) data.weekly = new QuestSet();
        if (data.weekly.objectives == null) data.weekly.objectives = new ArrayList<>();
        if (data.claimedWeekly == null) data.claimedWeekly = new HashSet<>();
        for (Objective o : data.weekly.objectives) {
            if (o == null) continue;
            if (o.playerProgress == null) o.playerProgress = new HashMap<>();
            if (o.completedPlayers == null) o.completedPlayers = new HashSet<>();
        }
    }
}
