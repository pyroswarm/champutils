package com.champutils.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatPreferenceManager {
    private static final Map<UUID, ChatMode> MODES = new ConcurrentHashMap<>();
    private ChatPreferenceManager() {}

    public static ChatMode get(UUID uuid) {
        return MODES.getOrDefault(uuid, ChatMode.LOCAL);
    }

    public static void set(UUID uuid, ChatMode mode) {
        if (uuid != null && mode != null) MODES.put(uuid, mode);
    }

    public static void clear(UUID uuid) {
        if (uuid != null) MODES.remove(uuid);
    }
}
