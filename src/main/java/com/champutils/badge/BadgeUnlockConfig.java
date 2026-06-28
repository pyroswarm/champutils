package com.champutils.badge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Configurable profile-based rewards unlocked by gym badges. */
public final class BadgeUnlockConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/badge_unlocks.json");
    private static volatile Config DATA = defaults();
    private static final Map<String, List<BadgeType>> PERMISSION_INDEX = new ConcurrentHashMap<>();

    private BadgeUnlockConfig() {}

    public static synchronized void load() {
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            if (!FILE.exists()) {
                DATA = defaults();
                save();
            } else {
                try (FileReader r = new FileReader(FILE)) {
                    Config loaded = GSON.fromJson(r, Config.class);
                    DATA = loaded == null ? defaults() : loaded;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            DATA = defaults();
        }
        normalizeRequiredUnlocks();
        rebuildIndex();
    }

    public static synchronized void save() {
        try (FileWriter w = new FileWriter(FILE)) { GSON.toJson(DATA, w); }
        catch (Exception e) { e.printStackTrace(); }
    }

    public static List<String> commands(BadgeType badge) {
        BadgeUnlock unlock = unlock(badge);
        return unlock == null || unlock.commands == null ? Collections.emptyList() : unlock.commands;
    }

    public static List<String> permissions(BadgeType badge) {
        BadgeUnlock unlock = unlock(badge);
        return unlock == null || unlock.permissions == null ? Collections.emptyList() : unlock.permissions;
    }

    public static List<String> titles(BadgeType badge) {
        BadgeUnlock unlock = unlock(badge);
        return unlock == null || unlock.titles == null ? Collections.emptyList() : unlock.titles;
    }

    public static boolean grantsPermission(ServerPlayer player, String permission) {
        if (player == null || permission == null || permission.isBlank()) return false;
        List<BadgeType> badges = PERMISSION_INDEX.get(permission.trim().toLowerCase(Locale.ROOT));
        if (badges == null || badges.isEmpty()) return false;
        for (BadgeType badge : badges) {
            if (BadgeManager.hasBadge(player, badge)) return true;
        }
        return false;
    }

    private static void normalizeRequiredUnlocks() {
        if (DATA == null || DATA.badges == null) DATA = defaults();
        for (BadgeType badge : BadgeType.values()) DATA.badges.computeIfAbsent(badge.name(), k -> new BadgeUnlock());
        addPermission(BadgeType.CASCADE, "champutils.command.pc");
        addPermission(BadgeType.CASCADE, "cobblemonextras.cobblemonextras.command.pc");
        addCommand(BadgeType.CASCADE, "/pc");
        addPermission(BadgeType.THUNDER, "champutils.command.pokeheal");
        addPermission(BadgeType.THUNDER, "command.healpokemon.self");
        addCommand(BadgeType.THUNDER, "/pokeheal");
    }

    private static void addPermission(BadgeType badge, String permission) {
        BadgeUnlock unlock = DATA.badges.computeIfAbsent(badge.name(), k -> new BadgeUnlock());
        if (unlock.permissions == null) unlock.permissions = new ArrayList<>();
        if (unlock.permissions.stream().noneMatch(p -> p.equalsIgnoreCase(permission))) unlock.permissions.add(permission);
    }

    private static void addCommand(BadgeType badge, String command) {
        BadgeUnlock unlock = DATA.badges.computeIfAbsent(badge.name(), k -> new BadgeUnlock());
        if (unlock.commands == null) unlock.commands = new ArrayList<>();
        if (unlock.commands.stream().noneMatch(c -> c.equalsIgnoreCase(command))) unlock.commands.add(command);
    }

    private static BadgeUnlock unlock(BadgeType badge) {
        if (badge == null || DATA == null || DATA.badges == null) return null;
        return DATA.badges.get(badge.name());
    }

    private static void rebuildIndex() {
        PERMISSION_INDEX.clear();
        if (DATA == null || DATA.badges == null) return;
        for (Map.Entry<String, BadgeUnlock> entry : DATA.badges.entrySet()) {
            BadgeType badge = BadgeType.fromString(entry.getKey());
            if (badge == null || entry.getValue() == null || entry.getValue().permissions == null) continue;
            for (String permission : entry.getValue().permissions) {
                if (permission == null || permission.isBlank()) continue;
                PERMISSION_INDEX.computeIfAbsent(permission.trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(badge);
            }
        }
    }

    private static Config defaults() {
        Config c = new Config();
        c.badges = new LinkedHashMap<>();
        for (BadgeType badge : BadgeType.values()) {
            BadgeUnlock u = new BadgeUnlock();
            u.permissions = new ArrayList<>();
            u.commands = new ArrayList<>();
            u.titles = new ArrayList<>();
            u.broadcastUnlocks = true;
            c.badges.put(badge.name(), u);
        }
        c.badges.get("BOULDER").titles.add("boulder_badge");
        c.badges.get("CASCADE").permissions.add("champutils.command.pc");
        c.badges.get("CASCADE").commands.add("/pc");
        c.badges.get("CASCADE").titles.add("cascade_badge");
        c.badges.get("THUNDER").permissions.add("champutils.command.pokeheal");
        c.badges.get("THUNDER").commands.add("/pokeheal");
        c.badges.get("THUNDER").titles.add("thunder_badge");
        c.badges.get("RAINBOW").titles.add("rainbow_badge");
        c.badges.get("SOUL").titles.add("soul_badge");
        c.badges.get("MARSH").titles.add("marsh_badge");
        c.badges.get("VOLCANO").titles.add("volcano_badge");
        c.badges.get("EARTH").titles.add("earth_badge");
        c.badges.get("LORELEI").titles.add("lorelei_badge");
        c.badges.get("BRUNO").titles.add("bruno_badge");
        c.badges.get("AGATHA").titles.add("agatha_badge");
        c.badges.get("LANCE").titles.add("lance_badge");
        c.badges.get("CHAMPION").titles.add("champion");
        return c;
    }

    public static final class Config { public Map<String, BadgeUnlock> badges = new LinkedHashMap<>(); }
    public static final class BadgeUnlock {
        public List<String> permissions = new ArrayList<>();
        public List<String> commands = new ArrayList<>();
        public List<String> titles = new ArrayList<>();
        public boolean broadcastUnlocks = true;
    }
}
