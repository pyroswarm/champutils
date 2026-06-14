package com.champutils.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChatTagConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/chat_tags.json");

    public static ChatTagConfig INSTANCE = defaults();

    public boolean showLuckPermsPrefix = true;
    public boolean showLuckPermsSuffix = false;
    public List<TagDefinition> tags = new ArrayList<>();

    public static final class TagDefinition {
        public String id = "";
        public String display = "";
        public String permission = "";
        public int priority = 0;
    }

    private ChatTagConfig() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) {
                INSTANCE = defaults();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                ChatTagConfig loaded = GSON.fromJson(reader, ChatTagConfig.class);
                INSTANCE = loaded == null ? defaults() : loaded;
                if (INSTANCE.tags == null) INSTANCE.tags = new ArrayList<>();
                INSTANCE.tags.removeIf(tag -> tag != null && tag.id != null && (tag.id.equalsIgnoreCase("helper") || tag.id.equalsIgnoreCase("champion") || tag.id.equalsIgnoreCase("vip")));
            }
        } catch (Exception e) {
            INSTANCE = defaults();
            System.err.println("[ChampUtils] Failed to load chat_tags.json; using defaults.");
            e.printStackTrace();
        }
        INSTANCE.tags.sort(Comparator.comparingInt((TagDefinition tag) -> tag.priority).reversed());
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save chat_tags.json.");
            e.printStackTrace();
        }
    }

    private static ChatTagConfig defaults() {
        ChatTagConfig config = new ChatTagConfig();
        // Staff/VIP group tags are handled by LuckPerms prefixes only. No default cosmetic tags.
        return config;
    }

    private static TagDefinition tag(String id, String display, String permission, int priority) {
        TagDefinition tag = new TagDefinition();
        tag.id = id;
        tag.display = display;
        tag.permission = permission;
        tag.priority = priority;
        return tag;
    }
}
