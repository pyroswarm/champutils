package com.champutils.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Lightweight Minecraft-side state for Discord linking. A Discord bot/bridge should call the verify/chat commands. */
public final class DiscordLinkManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/discord_links.json");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static Root DATA = new Root();
    private static boolean loaded = false;

    private DiscordLinkManager() {}

    public static synchronized String createCode(UUID minecraftUuid, String minecraftName) {
        load();
        PendingLink pending = new PendingLink();
        pending.minecraftUuid = minecraftUuid.toString();
        pending.minecraftName = minecraftName;
        pending.code = randomCode();
        pending.createdAt = Instant.now().toString();
        DATA.pendingLinks.put(minecraftUuid.toString(), pending);
        save();
        return pending.code;
    }

    public static synchronized boolean isLinked(UUID minecraftUuid) {
        load();
        return DATA.linkedAccounts.containsKey(minecraftUuid.toString());
    }

    public static synchronized LinkedAccount linked(UUID minecraftUuid) {
        load();
        return DATA.linkedAccounts.get(minecraftUuid.toString());
    }

    public static synchronized LinkedAccount linkedByDiscordId(String discordId) {
        load();
        if (discordId == null) return null;
        for (LinkedAccount linked : DATA.linkedAccounts.values()) {
            if (linked != null && discordId.equals(linked.discordId)) return linked;
        }
        return null;
    }

    public static synchronized VerifyResult verify(UUID minecraftUuid, String code, String discordId, String discordName) {
        load();
        if (minecraftUuid == null || code == null || discordId == null || discordId.isBlank()) return VerifyResult.INVALID;
        PendingLink pending = DATA.pendingLinks.get(minecraftUuid.toString());
        if (pending == null) return VerifyResult.NO_PENDING_CODE;
        if (!pending.code.equalsIgnoreCase(code.trim())) return VerifyResult.BAD_CODE;
        LinkedAccount linked = new LinkedAccount();
        linked.minecraftUuid = minecraftUuid.toString();
        linked.minecraftName = pending.minecraftName;
        linked.discordId = discordId.trim();
        linked.discordName = discordName == null || discordName.isBlank() ? discordId.trim() : discordName.trim();
        linked.linkedAt = Instant.now().toString();
        DATA.pendingLinks.remove(minecraftUuid.toString());
        DATA.linkedAccounts.put(minecraftUuid.toString(), linked);
        save();
        return VerifyResult.SUCCESS;
    }

    public static synchronized boolean unlink(UUID minecraftUuid) {
        load();
        boolean removed = DATA.linkedAccounts.remove(minecraftUuid.toString()) != null;
        DATA.pendingLinks.remove(minecraftUuid.toString());
        if (removed) save();
        return removed;
    }

    private static String randomCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(6);
        for (int i = 0; i < 6; i++) builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return builder.toString().toUpperCase(Locale.ROOT);
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) {
                DATA = new Root();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                Root loadedRoot = GSON.fromJson(reader, Root.class);
                DATA = loadedRoot == null ? new Root() : loadedRoot;
            }
            if (DATA.pendingLinks == null) DATA.pendingLinks = new LinkedHashMap<>();
            if (DATA.linkedAccounts == null) DATA.linkedAccounts = new LinkedHashMap<>();
        } catch (Exception e) {
            e.printStackTrace();
            DATA = new Root();
        }
    }

    private static synchronized void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(DATA, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public enum VerifyResult { SUCCESS, INVALID, NO_PENDING_CODE, BAD_CODE }

    public static final class Root {
        public Map<String, PendingLink> pendingLinks = new LinkedHashMap<>();
        public Map<String, LinkedAccount> linkedAccounts = new LinkedHashMap<>();
    }

    public static final class PendingLink {
        public String minecraftUuid;
        public String minecraftName;
        public String code;
        public String createdAt;
    }

    public static final class LinkedAccount {
        public String minecraftUuid;
        public String minecraftName;
        public String discordId;
        public String discordName;
        public String linkedAt;
    }
}
