package com.champutils.guild;

import com.champutils.buff.BuffContext;
import com.champutils.buff.BuffDefinition;
import com.champutils.buff.BuffProvider;
import com.champutils.buff.BuffType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guild implementation of the shared BuffProvider contract.
 *
 * This is intentionally provider-specific. The gameplay hooks use BuffManager,
 * so server-wide boosters and future systems can stack beside guild buffs
 * without rewriting catch and profession reward logic.
 */
public final class GuildBuffProvider implements BuffProvider {
    public static final String ID = "guild";
    public static final GuildBuffProvider INSTANCE = new GuildBuffProvider();

    private static final Map<UUID, CachedGuildBuffs> CACHE = new ConcurrentHashMap<>();
    private static final Map<BuffType, BuffDefinition> DEFINITIONS = new EnumMap<>(BuffType.class);

    private GuildBuffProvider() {}

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public double getBuff(BuffContext context, BuffType type) {
        if (context == null || context.player == null || type == null || !context.allows(type)) return 0.0D;
        return cached(context.player.getUUID()).get(type);
    }

    public static synchronized void rebuildFromConfig() {
        DEFINITIONS.clear();
        register(BuffType.SHINY_CHANCE, GuildBuffConfig.CONFIG.buffs.shinyChance);
        register(BuffType.PERFECT_IV_CHANCE, GuildBuffConfig.CONFIG.buffs.perfectIvChance);
        register(BuffType.MINING_XP, GuildBuffConfig.CONFIG.buffs.miningProfessionXp);
        register(BuffType.FORESTRY_XP, GuildBuffConfig.CONFIG.buffs.forestryProfessionXp);
        register(BuffType.FARMING_XP, GuildBuffConfig.CONFIG.buffs.farmingProfessionXp);
        register(BuffType.BATTLING_XP, GuildBuffConfig.CONFIG.buffs.battlingProfessionXp);
        clearCache();
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static List<BuffDefinition> definitions() {
        List<BuffDefinition> list = new ArrayList<>();
        for (BuffType type : BuffType.values()) {
            BuffDefinition definition = DEFINITIONS.get(type);
            if (definition != null) list.add(definition);
        }
        return Collections.unmodifiableList(list);
    }

    public static List<Component> activeBuffLines(UUID playerUuid) {
        rebuildFromConfigIfEmpty();
        CachedGuildBuffs buffs = cached(playerUuid);
        List<Component> lines = new ArrayList<>();
        for (BuffDefinition definition : definitions()) {
            double value = buffs.get(definition.type);
            if (value <= 0.0D) continue;
            lines.add(Component.literal("✦ +" + com.champutils.buff.BuffManager.percent(value) + " " + definition.type.displayName)
                    .withStyle(definition.type.color));
        }
        return lines;
    }

    public static List<Component> nextBuffLines(UUID playerUuid) {
        rebuildFromConfigIfEmpty();
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(playerUuid);
        if (guild == null) return Collections.emptyList();

        List<Component> lines = new ArrayList<>();
        for (BuffDefinition definition : definitions()) {
            if (!definition.enabled) continue;
            if (guild.level < definition.unlockLevel) {
                lines.add(Component.literal("Next: " + definition.type.displayName + " unlocks at guild level " + definition.unlockLevel)
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    public static List<Component> unlockedBuffMessages(int oldLevel, int newLevel) {
        rebuildFromConfigIfEmpty();
        List<Component> lines = new ArrayList<>();
        for (BuffDefinition definition : definitions()) {
            if (!definition.enabled) continue;
            if (oldLevel < definition.unlockLevel && newLevel >= definition.unlockLevel) {
                lines.add(Component.literal("[Guild] Buff Unlocked: +" + com.champutils.buff.BuffManager.percent(definition.unlockDisplayValue()) + " " + definition.type.displayName)
                        .withStyle(definition.type.color));
            }
        }
        return lines;
    }

    private static void register(BuffType type, GuildBuffConfig.BuffEntry entry) {
        if (type == null || entry == null) return;
        DEFINITIONS.put(type, BuffDefinition.perLevel(type, entry.enabled, entry.unlockLevel, entry.chancePerLevel, entry.maxBonus));
    }

    private static void register(BuffType type, GuildBuffConfig.ProfessionXpBuffEntry entry) {
        if (type == null || entry == null) return;
        DEFINITIONS.put(type, BuffDefinition.scaled(type, entry.enabled, entry.unlockLevel, entry.maxLevel, entry.startingBonus, entry.maxBonus));
    }

    private static CachedGuildBuffs cached(UUID playerUuid) {
        rebuildFromConfigIfEmpty();
        if (playerUuid == null) return emptyBuffs();
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(playerUuid);
        if (guild == null) return emptyBuffs();

        CachedGuildBuffs existing = CACHE.get(playerUuid);
        long now = System.currentTimeMillis();
        if (existing != null && existing.guildLevel == guild.level && now - existing.createdAt < 30_000L) {
            return existing;
        }

        EnumMap<BuffType, Double> values = new EnumMap<>(BuffType.class);
        for (BuffDefinition definition : definitions()) {
            values.put(definition.type, definition.valueAt(guild.level));
        }

        CachedGuildBuffs rebuilt = new CachedGuildBuffs(guild.level, values);
        CACHE.put(playerUuid, rebuilt);
        return rebuilt;
    }

    private static CachedGuildBuffs emptyBuffs() {
        return new CachedGuildBuffs(1, new EnumMap<>(BuffType.class));
    }

    private static synchronized void rebuildFromConfigIfEmpty() {
        if (DEFINITIONS.isEmpty()) rebuildFromConfig();
    }

    private static final class CachedGuildBuffs {
        final int guildLevel;
        final EnumMap<BuffType, Double> values;
        final long createdAt;

        CachedGuildBuffs(int guildLevel, EnumMap<BuffType, Double> values) {
            this.guildLevel = guildLevel;
            this.values = values;
            this.createdAt = System.currentTimeMillis();
        }

        double get(BuffType type) {
            return values.getOrDefault(type, 0.0D);
        }
    }
}
