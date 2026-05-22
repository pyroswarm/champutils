package com.champutils.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.UUID;

public class QuestDataManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class QuestData {
        public String uuid;
        public String name;
        public QuestSet daily;
        public QuestSet weekly;
        public ArrayList<Contract> contracts = new ArrayList<>();
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
    }

    public static class Contract extends Objective {
        public long purchasedAtMillis;
        public long expiresAtMillis;
        public int creditCost;
        public String difficulty;
        public boolean completed;
        public ArrayList<String> rewardCommands = new ArrayList<>();
    }

    private static File dir() {
        File dir = new File("config/champutils/quests/players");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File file(UUID uuid) {
        return new File(dir(), uuid.toString() + ".json");
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
