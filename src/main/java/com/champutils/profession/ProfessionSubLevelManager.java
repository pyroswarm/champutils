package com.champutils.profession;

import com.champutils.profile.PlayerProfileManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ProfessionSubLevelManager {

    public static final double CHUNK_FIND_BONUS_PER_SUBLEVEL = 0.00030D;
    public static final double CHUNK_FIND_BONUS_CAP = 0.03D;
    public static final double RARITY_BONUS_PER_TEN_LEVELS = 0.00300D;
    public static final double RARITY_BONUS_CAP = 0.03D;
    public static final double MASTERED_SUBLEVEL_XP_BONUS = 0.10D;

    private ProfessionSubLevelManager() {}

    public static void addBlockXp(ServerPlayer player, ProfessionType profession, String blockId, int amount) {
        if (player == null || profession == null || blockId == null || blockId.isBlank() || amount <= 0) return;
        String category = switch (profession) {
            case FARMING -> "CROP";
            case FORESTRY -> "WOOD";
            case MINING -> isOreLike(blockId) ? "ORE" : "BLOCK";
            default -> "ACTION";
        };
        addXp(player, profession, category, blockId, amount);
    }

    public static void addPokemonTypeXp(ServerPlayer player, List<String> types, int amount) {
        if (player == null || types == null || types.isEmpty() || amount <= 0) return;
        int split = Math.max(1, (int) Math.ceil(amount / (double) Math.max(1, types.size())));
        for (String type : types) {
            if (type == null || type.isBlank()) continue;
            addXp(player, ProfessionType.BATTLING, "TYPE", type, split);
        }
    }

    public static void addXp(ServerPlayer player, ProfessionType profession, String category, String rawId, int amount) {
        if (player == null || profession == null || rawId == null || rawId.isBlank() || amount <= 0) return;
        amount = ProfessionXpBoostManager.applyBoosts(player, profession, amount);
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        ProfessionDataManager.ensureProfessionDefaults(data);
        String key = key(profession, category, rawId);
        ProfessionDataManager.ProfessionData.SubLevelData sublevel = data.sublevels.computeIfAbsent(key, ignored -> new ProfessionDataManager.ProfessionData.SubLevelData());
        sublevel.level = Math.max(1, Math.min(100, sublevel.level));
        if (sublevel.level >= 100) {
            sublevel.xp = 0;
            sublevel.actions++;
            ProfessionManager.markDirtyProfile(PlayerProfileManager.activeProfileId(player));
            return;
        }

        int masteredOtherSublevels = countMasteredSublevels(data, profession, key);
        int boostedAmount = Math.max(1, (int) Math.round(amount * (1.0D + (masteredOtherSublevels * MASTERED_SUBLEVEL_XP_BONUS))));
        sublevel.xp += boostedAmount;
        sublevel.actions++;

        while (sublevel.level < 100 && sublevel.xp >= xpRequired(sublevel.level)) {
            sublevel.xp -= xpRequired(sublevel.level);
            sublevel.level++;
            ProfessionSpecialCelebration.celebrateSublevelUp(player, displayName(key), sublevel.level);
            if (sublevel.level >= 100) {
                sublevel.level = 100;
                sublevel.xp = 0;
                break;
            }
        }
        ProfessionManager.markDirtyProfile(PlayerProfileManager.activeProfileId(player));
    }

    public static int xpRequired(int level) {
        int safeLevel = Math.max(1, Math.min(100, level));
        if (safeLevel >= 100) return Integer.MAX_VALUE / 4;

        ProfessionConfig.ProfessionSettings settings = ProfessionConfig.SETTINGS;
        int base = Math.max(1, settings == null ? 90 : settings.sublevelXpBase);
        int perLevel = Math.max(1, settings == null ? 25 : settings.sublevelXpPerLevel);
        double growthAfter50 = Math.max(1.0D, settings == null ? 1.09D : settings.sublevelXpGrowthAfter50);

        if (safeLevel < 50) return base + (safeLevel * perLevel);
        double baseAtFifty = base + (50.0D * perLevel);
        double scaled = baseAtFifty * Math.pow(growthAfter50, safeLevel - 49);
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE / 4, Math.round(scaled)));
    }

    public static double chunkFindChanceBonus(ServerPlayer player, ProfessionType profession) {
        return player == null ? 0.0D : chunkFindChanceBonus(ProfessionManager.getData(player), profession);
    }

    public static double chunkFindChanceBonus(ProfessionDataManager.ProfessionData data, ProfessionType profession) {
        if (data == null || profession == null || data.sublevels == null) return 0.0D;
        int totalLevels = 0;
        String prefix = profession.name() + ":";
        for (var entry : data.sublevels.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().startsWith(prefix) || entry.getValue() == null) continue;
            totalLevels += Math.max(1, Math.min(100, entry.getValue().level));
        }
        return Math.min(CHUNK_FIND_BONUS_CAP, totalLevels * CHUNK_FIND_BONUS_PER_SUBLEVEL);
    }

    public static double chunkRarityChanceBonus(ServerPlayer player, ProfessionType profession) {
        return player == null ? 0.0D : chunkRarityChanceBonus(ProfessionManager.getData(player), profession);
    }

    public static double chunkRarityChanceBonus(ProfessionDataManager.ProfessionData data, ProfessionType profession) {
        if (data == null || profession == null || data.sublevels == null) return 0.0D;
        int milestones = 0;
        String prefix = profession.name() + ":";
        for (var entry : data.sublevels.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().startsWith(prefix) || entry.getValue() == null) continue;
            milestones += Math.max(0, Math.min(100, entry.getValue().level) / 10);
        }
        return Math.min(RARITY_BONUS_CAP, milestones * RARITY_BONUS_PER_TEN_LEVELS);
    }

    public static int countMasteredSublevels(ProfessionDataManager.ProfessionData data, ProfessionType profession) {
        return countMasteredSublevels(data, profession, null);
    }

    private static int countMasteredSublevels(ProfessionDataManager.ProfessionData data, ProfessionType profession, String excludedKey) {
        if (data == null || profession == null || data.sublevels == null) return 0;
        String prefix = profession.name() + ":";
        int count = 0;
        for (var entry : data.sublevels.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().startsWith(prefix) || entry.getValue() == null) continue;
            if (excludedKey != null && excludedKey.equals(entry.getKey())) continue;
            if (entry.getValue().level >= 100) count++;
        }
        return count;
    }

    public static List<Map.Entry<String, ProfessionDataManager.ProfessionData.SubLevelData>> sublevels(ServerPlayer player, ProfessionType profession) {
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        ProfessionDataManager.ensureProfessionDefaults(data);
        String prefix = profession.name() + ":";
        List<Map.Entry<String, ProfessionDataManager.ProfessionData.SubLevelData>> entries = new ArrayList<>();
        for (var entry : data.sublevels.entrySet()) {
            if (entry.getKey() != null && entry.getKey().startsWith(prefix) && entry.getValue() != null) entries.add(entry);
        }
        entries.sort(Comparator.<Map.Entry<String, ProfessionDataManager.ProfessionData.SubLevelData>>comparingInt(e -> -e.getValue().level).thenComparing(Map.Entry::getKey));
        return entries;
    }

    public static Map<String, Integer> sublevelCountsByCategory(ServerPlayer player, ProfessionType profession) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var entry : sublevels(player, profession)) {
            String[] parts = entry.getKey().split(":", 3);
            String category = parts.length >= 2 ? parts[1] : "OTHER";
            result.merge(category, 1, Integer::sum);
        }
        return result;
    }

    public static String key(ProfessionType profession, String category, String rawId) {
        String safeCategory = category == null || category.isBlank() ? "MISC" : category.trim().toUpperCase(Locale.ROOT);
        String safeId = rawId == null ? "unknown" : rawId.trim().toLowerCase(Locale.ROOT);
        return profession.name() + ":" + safeCategory + ":" + safeId;
    }

    public static String displayName(String key) {
        if (key == null || key.isBlank()) return "Unknown";
        String[] parts = key.split(":", 3);
        String raw = parts.length == 3 ? parts[2] : key;
        int namespace = raw.indexOf(':');
        if (namespace >= 0 && namespace + 1 < raw.length()) raw = raw.substring(namespace + 1);
        raw = raw.replace('_', ' ').replace('-', ' ');
        StringBuilder builder = new StringBuilder();
        for (String part : raw.split(" ")) {
            if (part.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.length() > 1 ? part.substring(1) : "");
        }
        return builder.length() == 0 ? key : builder.toString();
    }

    public static String categoryName(String key) {
        if (key == null || key.isBlank()) return "Sublevel";
        String[] parts = key.split(":", 3);
        if (parts.length < 2) return "Sublevel";
        return switch (parts[1]) {
            case "CROP" -> "Crop";
            case "WOOD" -> "Wood";
            case "ORE" -> "Ore";
            case "TYPE" -> "Type Slayer";
            case "ACTIVE" -> "Active Skill";
            case "BLOCK" -> "Block";
            default -> parts[1];
        };
    }

    private static boolean isOreLike(String blockId) {
        String id = blockId.toLowerCase(Locale.ROOT);
        return id.contains("ore") || id.contains("ancient_debris") || id.contains("tumblestone") || id.contains("dawn_stone") || id.contains("dusk_stone") || id.contains("moon_stone") || id.contains("sun_stone") || id.contains("thunder_stone") || id.contains("water_stone") || id.contains("fire_stone") || id.contains("ice_stone") || id.contains("leaf_stone") || id.contains("shiny_stone");
    }
}
