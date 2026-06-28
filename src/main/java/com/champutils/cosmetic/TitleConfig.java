package com.champutils.cosmetic;

import com.champutils.battle.BattleContextManager;
import com.champutils.profession.ProfessionType;
import com.champutils.buff.BuffType;
import com.champutils.buff.BuffManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

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
            if (def.unlock == null) def.unlock = new UnlockCondition();
            if (def.buffs == null) def.buffs = new ArrayList<>();
            def.scope = normalizeScope(def.scope);
            def.accountBound = def.accountBound || "ACCOUNT".equalsIgnoreCase(def.scope);
            if (def.accountBound) def.scope = "ACCOUNT";
            BY_ID.put(def.id, def);
        }
    }

    public static Collection<TitleDef> titles() { return BY_ID.values(); }
    public static TitleDef get(String id) { return id == null ? null : BY_ID.get(id); }
    public static String display(String id) {
        TitleDef def = get(id);
        return def == null ? null : def.display;
    }

    public static boolean isAccountBound(String id) {
        TitleDef def = get(normalizeId(id));
        if (def == null) return false;
        return def.accountBound || "ACCOUNT".equalsIgnoreCase(def.scope);
    }

    public static synchronized boolean setScope(String id, String scope) {
        TitleDef def = get(normalizeId(id));
        if (def == null) return false;
        String normalized = normalizeScope(scope);
        def.scope = normalized;
        def.accountBound = "ACCOUNT".equals(normalized);
        rebuildIndex();
        save();
        return true;
    }


    public static synchronized TitleDef createManualTitle(String id, String name) {
        if (id == null || id.isBlank()) return null;
        String normalizedId = normalizeId(id);
        TitleDef existing = get(normalizedId);
        if (existing != null) return existing;
        if (CONFIG.titles == null) CONFIG.titles = new ArrayList<>();
        TitleDef def = new TitleDef();
        def.id = normalizedId;
        def.name = (name == null || name.isBlank()) ? pretty(normalizedId) : name.trim();
        def.color = "&7";
        def.icon = "";
        def.description = "Manual admin title.";
        def.unlock = new UnlockCondition();
        def.unlock.type = "manual";
        def.scope = "PROFILE";
        def.accountBound = false;
        def.buffs = new ArrayList<>();
        def.display = formatDisplay(def);
        def.passiveDescription = buffText(def);
        CONFIG.titles.add(def);
        rebuildIndex();
        save();
        return def;
    }

    public static synchronized boolean updateManualTitle(String id, String name, String color, String icon, String description) {
        TitleDef def = get(normalizeId(id));
        if (def == null) return false;
        if (name != null) def.name = name.trim();
        if (color != null) def.color = color.trim();
        if (icon != null) def.icon = icon.trim();
        if (description != null) def.description = description.trim();
        def.display = formatDisplay(def);
        def.passiveDescription = buffText(def);
        rebuildIndex();
        save();
        return true;
    }

    public static synchronized boolean setBuff(String id, String buff, double amount) {
        TitleDef def = get(normalizeId(id));
        BuffType type = parseBuffType(buff);
        if (def == null || type == null) return false;
        if (def.buffs == null) def.buffs = new ArrayList<>();
        double safeAmount = Math.max(0.0D, amount);
        boolean found = false;
        for (TitleBuff titleBuff : def.buffs) {
            if (titleBuff != null && titleBuff.type != null && titleBuff.type.equalsIgnoreCase(type.name())) {
                titleBuff.amount = safeAmount;
                found = true;
            }
        }
        if (!found) {
            TitleBuff titleBuff = new TitleBuff();
            titleBuff.type = type.name();
            titleBuff.amount = safeAmount;
            def.buffs.add(titleBuff);
        }
        def.passiveDescription = buffText(def);
        rebuildIndex();
        save();
        return true;
    }

    public static synchronized boolean removeBuff(String id, String buff) {
        TitleDef def = get(normalizeId(id));
        BuffType type = parseBuffType(buff);
        if (def == null || type == null || def.buffs == null) return false;
        boolean changed = def.buffs.removeIf(titleBuff -> titleBuff != null && titleBuff.type != null && titleBuff.type.equalsIgnoreCase(type.name()));
        if (changed) {
            def.passiveDescription = buffText(def);
            rebuildIndex();
            save();
        }
        return changed;
    }

    public static double activeBuff(ServerPlayer player, BuffType type) {
        if (player == null || type == null) return 0.0D;
        String selected = TitleManager.selected(player.getUUID());
        TitleDef def = get(selected);
        if (def == null && selected != null && selected.startsWith("wf_")) {
            if (type == BuffType.SHINY_CHANCE && selected.contains("shiny")) return 0.001D;
            if (type == BuffType.CATCH_CHANCE && (selected.contains("catch") || selected.contains("legendary") || selected.contains("ultra_beast"))) return 0.01D;
            if (type == BuffType.BATTLING_XP && selected.contains("battle")) return 0.03D;
            if (type.isProfessionXp() && selected.contains(type.professionType.name().toLowerCase(Locale.ROOT))) return 0.03D;
            return type == BuffType.CATCH_CHANCE ? 0.0025D : 0.0D;
        }
        if (def == null || def.buffs == null) return 0.0D;
        double total = 0.0D;
        for (TitleBuff titleBuff : def.buffs) {
            BuffType configured = parseBuffType(titleBuff == null ? null : titleBuff.type);
            if (configured == type) total += Math.max(0.0D, titleBuff.amount);
        }
        return total;
    }


    public static Component hoverText(String id) {
        TitleDef def = get(id);
        if (def == null) {
            String wf = com.champutils.worldfirst.WorldFirstManager.titleDisplay(id);
            if (wf != null) {
                return com.champutils.chat.ChatTagResolver.legacy("&6World First Title\n&7Obtained by completing a server world first.");
            }
            return Component.literal("Title: " + (id == null ? "Unknown" : id));
        }
        StringBuilder text = new StringBuilder();
        text.append("&6").append(def.name == null ? id : def.name).append("\n");
        text.append("&7Obtained: &f").append(def.description == null || def.description.isBlank() ? describeUnlock(def.unlock) : def.description).append("\n");
        text.append("&7Scope: &f").append(isAccountBound(id) ? "Account" : "Profile").append("\n");
        String passive = def.passiveDescription == null || def.passiveDescription.isBlank() ? buffText(def) : def.passiveDescription;
        text.append("&7Passive: &a").append(passive);
        return com.champutils.chat.ChatTagResolver.legacy(text.toString());
    }

    private static String describeUnlock(UnlockCondition unlock) {
        if (unlock == null || unlock.type == null || unlock.type.isBlank()) return "Special unlock.";
        String type = unlock.type.trim().toLowerCase(Locale.ROOT);
        if ("battle_win".equals(type)) return unlock.battleType == null || unlock.battleType.isBlank() ? "Win battles." : "Win a " + pretty(unlock.battleType) + " battle.";
        if ("catch".equals(type)) return "Catch Pokémon.";
        if ("boss_win".equals(type)) return "Defeat a boss.";
        if ("profession_level".equals(type)) return "Reach " + pretty(unlock.profession == null ? "profession" : unlock.profession) + " level " + Math.max(1, unlock.level) + ".";
        if ("manual".equals(type)) return "Granted manually or from a special server achievement.";
        return pretty(type) + " unlock.";
    }

    public static String buffText(TitleDef def) {
        List<String> parts = new ArrayList<>();
        if (def != null && def.passive != null && def.passive.professionXpBonus > 0.0D) {
            String prof = def.passive.profession == null || def.passive.profession.isBlank() ? "Profession" : pretty(def.passive.profession);
            parts.add("+" + formatPercent(def.passive.professionXpBonus) + " " + prof + " XP");
        }
        if (def != null && def.buffs != null) {
            for (TitleBuff titleBuff : def.buffs) {
                BuffType type = parseBuffType(titleBuff == null ? null : titleBuff.type);
                if (type == null || titleBuff.amount <= 0.0D) continue;
                parts.add("+" + formatPercent(titleBuff.amount) + " " + type.displayName);
            }
        }
        return parts.isEmpty() ? "No passive bonus." : String.join(", ", parts) + " while equipped.";
    }

    public static BuffType parseBuffType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("BATTLE_XP") || normalized.equals("BATTLEXP") || normalized.equals("BATTLING")) normalized = "BATTLING_XP";
        if (normalized.equals("POKEMONXP") || normalized.equals("POKEMON_EXP") || normalized.equals("MON_XP") || normalized.equals("POKEMON_EXPERIENCE")) normalized = "POKEMON_XP";
        if (normalized.equals("SHINY") || normalized.equals("SHINY_RATE")) normalized = "SHINY_CHANCE";
        if (normalized.equals("CATCH") || normalized.equals("CATCHING") || normalized.equals("CAPTURE_CHANCE")) normalized = "CATCH_CHANCE";
        if (normalized.equals("PERFECT_IV") || normalized.equals("IV_CHANCE") || normalized.equals("PERFECTIV")) normalized = "PERFECT_IV_CHANCE";
        if (normalized.equals("MONEY") || normalized.equals("NPC_CREDITS") || normalized.equals("TRAINER_MONEY")) normalized = "NPC_MONEY";
        if (normalized.equals("WORLD_REWARDS") || normalized.equals("EVENT_REWARDS")) normalized = "WORLD_EVENT_REWARDS";
        try { return BuffType.valueOf(normalized); } catch (Exception ignored) { return null; }
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) return "PROFILE";
        String normalized = scope.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("ACCOUNT") || normalized.equals("ACCOUNT_BOUND") || normalized.equals("GLOBAL")) return "ACCOUNT";
        return "PROFILE";
    }

    private static String formatPercent(double value) {
        return String.format(Locale.US, "%.3f", Math.max(0.0D, value) * 100.0D).replaceAll("0+$", "").replaceAll("\\.$", "") + "%";
    }

    public static void handleBattleWin(ServerPlayer player, BattleContextManager.BattleType type) {
        if (player == null || type == null) return;
        for (TitleDef def : BY_ID.values()) {
            UnlockCondition c = def.unlock;
            if (c == null || !"battle_win".equalsIgnoreCase(c.type)) continue;
            if (looksLikeBossTitle(def)) continue;
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


    private static boolean looksLikeBossTitle(TitleDef def) {
        if (def == null) return false;
        String id = def.id == null ? "" : def.id.toLowerCase(Locale.ROOT);
        String name = def.name == null ? "" : def.name.toLowerCase(Locale.ROOT);
        String desc = def.description == null ? "" : def.description.toLowerCase(Locale.ROOT);
        return id.contains("boss") || name.contains("boss") || desc.contains("boss");
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

    public static double activeProfessionXpBonus(ServerPlayer player, ProfessionType profession) {
        if (player == null || profession == null) return 0.0D;
        String selected = TitleManager.selected(player.getUUID());
        TitleDef def = get(selected);
        if (def == null || def.passive == null) return 0.0D;
        if (def.passive.profession != null && !def.passive.profession.isBlank() && !def.passive.profession.equalsIgnoreCase(profession.name())) return 0.0D;
        return Math.max(0.0D, def.passive.professionXpBonus);
    }

    private static String passiveText(TitleDef def) {
        if (def == null || def.passive == null || def.passive.professionXpBonus <= 0.0D) return "No passive bonus.";
        String prof = def.passive.profession == null || def.passive.profession.isBlank() ? "Profession" : pretty(def.passive.profession);
        return "+" + String.format(Locale.US, "%.2f", def.passive.professionXpBonus * 100.0D).replaceAll("0+$", "").replaceAll("\\.$", "") + "% " + prof + " XP while equipped.";
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
        add(c, "casual_scrapper", "Casual Scrapper", "&a", "⚔", "battle_win", "CASUAL", null, 0, "Win a casual PvP battle.");
        add(c, "ranked_contender", "Ranked Contender", "&e", "⚔", "battle_win", "RANKED", null, 0, "Win your first ranked PvP battle.");
        add(c, "arena_regular", "Arena Regular", "&b", "✧", "manual", null, null, 0, "Win 10 PvP battles.");
        add(c, "champion_spark", "Champion Spark", "&6", "✦", "manual", null, null, 0, "Earn your first gym badge.");
        add(c, "hunt_helper", "Hunt Helper", "&a", "◎", "manual", null, null, 0, "Complete a Pokémon hunt.");
        add(c, "questing_soul", "Questing Soul", "&d", "◆", "manual", null, null, 0, "Complete a daily quest set.");
        add(c, "contractor", "Contractor", "&6", "$", "manual", null, null, 0, "Complete a paid contract.");
        add(c, "cascade_badge", "Cascade Badge", "&b", "💧", "manual", null, null, 0, "Defeat the Cascade Gym.");
        add(c, "marsh_badge", "Marsh Badge", "&d", "✦", "manual", null, null, 0, "Defeat the Marsh Gym.");
        add(c, "thunder_badge", "Thunder Badge", "&e", "⚡", "manual", null, null, 0, "Defeat the Thunder Gym.");
        add(c, "rainbow_badge", "Rainbow Badge", "&a", "✿", "manual", null, null, 0, "Defeat the Rainbow Gym.");
        add(c, "soul_badge", "Soul Badge", "&5", "☠", "manual", null, null, 0, "Defeat the Soul Gym.");
        add(c, "volcano_badge", "Volcano Badge", "&c", "🔥", "manual", null, null, 0, "Defeat the Volcano Gym.");
        add(c, "earth_badge", "Earth Badge", "&2", "◆", "manual", null, null, 0, "Defeat the Earth Gym.");
        add(c, "elite_four_clear", "Elite Four Victor", "&6", "♛", "manual", null, null, 0, "Defeat the Elite Four.");
        add(c, "gym_champion", "Gym Champion", "&6", "🏅", "manual", null, null, 0, "Complete all gyms.");
        add(c, "mega_hunter", "Mega Hunter", "&5", "✹", "boss_win", null, null, 0, "Defeat a Mega Boss.");
        add(c, "tm_collector", "TM Collector", "&b", "▣", "manual", null, null, 0, "Craft or earn TMs.");
        add(c, "fragment_forger", "Fragment Forger", "&d", "◇", "manual", null, null, 0, "Upgrade or use profession fragments.");
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
        d.scope = "PROFILE";
        d.accountBound = false;
        if ("profession_level".equalsIgnoreCase(type) && profession != null) {
            d.passive = new PassiveBonus();
            d.passive.profession = profession;
            d.passive.professionXpBonus = level >= 50 ? 0.02D : 0.01D;
        }
        d.buffs = new ArrayList<>();
        addDefaultBuff(d, type, battleType, profession, level);
        d.passiveDescription = buffText(d);
        c.titles.add(d);
    }

    private static void addDefaultBuff(TitleDef d, String type, String battleType, String profession, int level) {
        BuffType buff = null;
        double amount = 0.001D;
        if ("catch".equalsIgnoreCase(type)) { buff = BuffType.CATCH_CHANCE; amount = 0.005D; }
        else if ("boss_win".equalsIgnoreCase(type)) { buff = BuffType.BATTLING_XP; amount = 0.02D; }
        else if ("battle_win".equalsIgnoreCase(type)) { buff = BuffType.BATTLING_XP; amount = "RANKED".equalsIgnoreCase(battleType) ? 0.015D : 0.01D; }
        else if (profession != null && !profession.isBlank()) { buff = BuffType.fromProfession(ProfessionType.valueOf(profession)); amount = level >= 50 ? 0.02D : 0.01D; }
        else { buff = BuffType.CATCH_CHANCE; amount = 0.0025D; }
        if (buff == null) return;
        TitleBuff tb = new TitleBuff();
        tb.type = buff.name();
        tb.amount = amount;
        d.buffs.add(tb);
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
        /** PROFILE or ACCOUNT. ACCOUNT titles are owned by the player account and persist through all profiles. */
        public String scope = "PROFILE";
        /** Backwards-friendly boolean alias for scope = ACCOUNT. */
        public boolean accountBound = false;
        public String passiveDescription;
        public PassiveBonus passive;
        public List<TitleBuff> buffs = new ArrayList<>();
        public UnlockCondition unlock;
    }
    public static final class TitleBuff {
        public String type;
        /** Decimal amount. Example: 0.05 = +5%. */
        public double amount;
    }
    public static final class PassiveBonus {
        public String profession;
        public double professionXpBonus;
    }
    public static final class UnlockCondition {
        /** battle_win, catch, boss_win, profession_level, manual */
        public String type = "manual";
        public String battleType;
        public String profession;
        public int level;
    }
}
