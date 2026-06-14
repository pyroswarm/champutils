package com.champutils.cosmetic;

import com.champutils.battle.BattleContextManager;
import com.champutils.profession.ProfessionType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Config-backed title definitions. SQL stores only ownership/selection.
 */
public final class TitleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/titles.json");
    public static Config CONFIG = new Config();
    private static final Map<String, TitleDef> BY_ID = new ConcurrentHashMap<>();

    private TitleConfig() {}

    public static synchronized void load() {
        try {
            FILE.getParentFile().mkdirs();
            if (!FILE.exists()) {
                CONFIG = defaults();
                save();
            } else {
                try (FileReader reader = new FileReader(FILE)) {
                    Config loaded = GSON.fromJson(reader, Config.class);
                    CONFIG = loaded == null ? defaults() : loaded;
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load titles config. Using defaults.");
            e.printStackTrace();
            CONFIG = defaults();
        }
        rebuildIndex();
    }

    public static synchronized void save() {
        try {
            FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(CONFIG, writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save titles config.");
            e.printStackTrace();
        }
    }

    private static void rebuildIndex() {
        BY_ID.clear();
        if (CONFIG.titles == null) CONFIG.titles = new ArrayList<>();
        for (TitleDef def : CONFIG.titles) {
            if (def == null || def.id == null || def.id.isBlank()) continue;
            if (def.display == null || def.display.isBlank()) def.display = formatDisplay(def);
            BY_ID.put(def.id, def);
        }
    }

    public static Collection<TitleDef> titles() { return BY_ID.values(); }
    public static TitleDef get(String id) { return id == null ? null : BY_ID.get(id); }
    public static String display(String id) {
        TitleDef def = get(id);
        return def == null ? null : def.display;
    }

    public static void handleBattleWin(ServerPlayer player, BattleContextManager.BattleType type) {
        if (player == null || type == null) return;
        for (TitleDef def : BY_ID.values()) {
            UnlockCondition c = def.unlock;
            if (c == null || !"battle_win".equalsIgnoreCase(c.type)) continue;
            if (c.battleType != null && !c.battleType.isBlank() && !c.battleType.equalsIgnoreCase(type.name())) continue;
            TitleManager.unlock(player, def.id);
        }
    }

    public static void handleCatch(ServerPlayer player) {
        if (player == null) return;
        for (TitleDef def : BY_ID.values()) {
            UnlockCondition c = def.unlock;
            if (c == null || !"catch".equalsIgnoreCase(c.type)) continue;
            TitleManager.unlock(player, def.id);
        }
    }

    public static void handleBoss(ServerPlayer player) {
        if (player == null) return;
        for (TitleDef def : BY_ID.values()) {
            UnlockCondition c = def.unlock;
            if (c == null || !"boss_win".equalsIgnoreCase(c.type)) continue;
            TitleManager.unlock(player, def.id);
        }
    }

    public static void handleProfessionLevel(ServerPlayer player, ProfessionType profession, int level) {
        if (player == null || profession == null) return;
        for (TitleDef def : BY_ID.values()) {
            UnlockCondition c = def.unlock;
            if (c == null || !"profession_level".equalsIgnoreCase(c.type)) continue;
            if (c.profession != null && !c.profession.isBlank() && !c.profession.equalsIgnoreCase(profession.name())) continue;
            if (level < Math.max(1, c.level)) continue;
            TitleManager.unlock(player, def.id);
        }
    }

    private static String formatDisplay(TitleDef def) {
        String color = (def.color == null || def.color.isBlank()) ? "&7" : def.color;
        String icon = def.icon == null ? "" : def.icon.trim();
        String name = def.name == null || def.name.isBlank() ? def.id : def.name;
        return color + "[" + (icon.isBlank() ? "" : icon + " ") + name + "]";
    }

    private static Config defaults() {
        Config c = new Config();
        c.titles = new ArrayList<>();
        add(c, "first_win", "First Win", "&a", "⚔", "battle_win", null, null, 0, "Win any battle.");
        add(c, "ranked_winner", "Ranked Winner", "&6", "🏆", "battle_win", "RANKED", null, 0, "Win a ranked battle.");
        add(c, "boss_slayer", "Boss Slayer", "&c", "★", "boss_win", null, null, 0, "Defeat a boss.");
        add(c, "collector", "Collector", "&b", "◇", "catch", null, null, 0, "Catch a Pokémon.");
        for (ProfessionType type : ProfessionType.values()) {
            String p = pretty(type.name());
            add(c, type.name().toLowerCase(Locale.ROOT) + "_apprentice", p + " Apprentice", "&b", "✦", "profession_level", null, type.name(), 10, "Reach " + p + " level 10.");
            add(c, type.name().toLowerCase(Locale.ROOT) + "_expert", p + " Expert", "&6", "✦", "profession_level", null, type.name(), 50, "Reach " + p + " level 50.");
        }
        return c;
    }

    private static void add(Config c, String id, String name, String color, String icon, String type, String battleType, String profession, int level, String desc) {
        TitleDef d = new TitleDef();
        d.id = id;
        d.name = name;
        d.color = color;
        d.icon = icon;
        d.display = formatDisplay(d);
        d.description = desc;
        d.unlock = new UnlockCondition();
        d.unlock.type = type;
        d.unlock.battleType = battleType;
        d.unlock.profession = profession;
        d.unlock.level = level;
        c.titles.add(d);
    }

    private static String pretty(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String part : lower.split(" ")) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return out.toString();
    }

    public static final class Config { public List<TitleDef> titles = new ArrayList<>(); }
    public static final class TitleDef {
        public String id;
        public String name;
        public String color;
        public String icon;
        public String display;
        public String description;
        public UnlockCondition unlock;
    }
    public static final class UnlockCondition {
        /** battle_win, catch, boss_win, profession_level, manual */
        public String type = "manual";
        public String battleType;
        public String profession;
        public int level;
    }
}
