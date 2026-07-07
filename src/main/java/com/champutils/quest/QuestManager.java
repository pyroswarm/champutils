package com.champutils.quest;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.adventurer.AdventurerGuildManager;
import com.champutils.battle.BattleContextManager;
import com.champutils.economy.EconomyManager;
import com.champutils.crate.CrateCreditManager;
import com.champutils.guild.GuildRepository;
import com.champutils.network.NetworkEventManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.profession.ProfessionChunkManager;
import com.champutils.profession.ProfessionType;
import com.champutils.profile.PlayerProfileManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class QuestManager {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final Random RANDOM = new Random();
    private static final Map<UUID, QuestDataManager.QuestData> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, QuestDataManager.GuildQuestData> GUILD_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> DIRTY_GUILDS = ConcurrentHashMap.newKeySet();
    private static int tickCounter = 0;

    public static void load() {
        QuestConfig.load();
    }

    public static void handleJoin(ServerPlayer player) {
        QuestDataManager.QuestData data = getData(player);
        refreshIfNeeded(player, data, true);
        cleanupExpiredContracts(player, data, false);
        savePlayer(player);
    }

    public static void preload(UUID profileId, String playerName) {
        if (profileId == null) return;
        CACHE.computeIfAbsent(profileId, id -> QuestDataManager.load(id, playerName == null || playerName.isBlank() ? id.toString() : playerName));
    }

    public static void tick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < 1200) return;
        tickCounter = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuestDataManager.QuestData data = getData(player);
            refreshIfNeeded(player, data, false);
            cleanupExpiredContracts(player, data, false);
        }
    }

    public static QuestDataManager.QuestData getData(ServerPlayer player) {
        UUID uuid = PlayerProfileManager.activeProfileId(player);
        QuestDataManager.QuestData data = CACHE.get(uuid);
        if (data != null) return data;
        data = QuestDataManager.load(uuid, player.getName().getString());
        CACHE.put(uuid, data);
        return data;
    }

    public static void refreshIfNeeded(ServerPlayer player, QuestDataManager.QuestData data, boolean quiet) {
        String dailyKey = dailyPeriodKey();
        String weeklyKey = weeklyPeriodKey();
        boolean changed = false;
        if (data.daily == null || data.daily.periodKey == null || !data.daily.periodKey.equals(dailyKey)) {
            data.daily = generateSet(player, dailyKey, true);
            changed = true;
            if (!quiet) player.sendSystemMessage(Component.literal("Daily quests have refreshed. Use /quest menu.").withStyle(ChatFormatting.AQUA));
        }
        if (data.weekly == null || data.weekly.periodKey == null || !data.weekly.periodKey.equals(weeklyKey)) {
            data.weekly = generateSet(player, weeklyKey, false);
            changed = true;
            if (!quiet) player.sendSystemMessage(Component.literal("Weekly quests have refreshed. Use /quest menu.").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (changed) markDirty(player);
    }

    private static QuestDataManager.QuestSet generateSet(ServerPlayer player, String periodKey, boolean daily) {
        QuestDataManager.QuestSet set = new QuestDataManager.QuestSet();
        set.periodKey = periodKey;
        set.completed = false;
        set.objectives = new ArrayList<>();
        List<QuestConfig.Template> source = daily ? QuestConfig.SETTINGS.dailyTemplates : QuestConfig.SETTINGS.weeklyTemplates;
        int count = Math.max(1, daily ? QuestConfig.SETTINGS.dailyObjectiveCount : QuestConfig.SETTINGS.weeklyObjectiveCount);
        List<QuestConfig.Template> pool = eligibleTemplates(player, source);
        Set<String> usedProfessions = new HashSet<>();
        Set<String> usedIds = new HashSet<>();
        for (int i = 0; i < count; i++) {
            QuestConfig.Template picked = pickWeighted(pool, usedProfessions);
            if (picked == null) picked = pickWeighted(pool, null);
            if (picked == null) break;
            final QuestConfig.Template selected = picked;
            final String selectedId = safe(selected.id);
            pool.removeIf(template -> template == selected || safe(template.id).equalsIgnoreCase(selectedId));
            usedIds.add(selectedId);
            usedProfessions.add(safe(selected.profession));
            set.objectives.add(fromTemplate(selected));
        }
        return set;
    }

    private static List<QuestConfig.Template> eligibleTemplates(ServerPlayer player, List<QuestConfig.Template> source) {
        List<QuestConfig.Template> out = new ArrayList<>();
        if (source == null) return out;
        Set<String> seen = new HashSet<>();
        for (QuestConfig.Template t : source) {
            if (t == null || t.id == null || t.objectiveType == null) continue;
            String id = safe(t.id);
            if (!seen.add(id.toLowerCase(Locale.ROOT))) continue;
            ProfessionType profession = parseProfession(t.profession);
            int level = profession == null ? 1 : ProfessionManager.getBenefitLevel(player, profession);
            if (level >= Math.max(1, t.minLevel) && Math.max(1, t.weight) > 0) out.add(t);
        }
        return out;
    }

    private static QuestConfig.Template pickWeighted(List<QuestConfig.Template> pool, Set<String> preferNotIn) {
        if (pool == null || pool.isEmpty()) return null;
        List<QuestConfig.Template> filtered = new ArrayList<>();
        for (QuestConfig.Template t : pool) {
            if (preferNotIn == null || !preferNotIn.contains(safe(t.profession))) filtered.add(t);
        }
        if (filtered.isEmpty()) filtered = pool;
        int total = 0;
        for (QuestConfig.Template t : filtered) total += Math.max(1, t.weight);
        int roll = RANDOM.nextInt(Math.max(1, total));
        int current = 0;
        for (QuestConfig.Template t : filtered) {
            current += Math.max(1, t.weight);
            if (roll < current) return t;
        }
        return filtered.get(0);
    }

    private static QuestDataManager.Objective fromTemplate(QuestConfig.Template t) {
        QuestDataManager.Objective o = new QuestDataManager.Objective();
        o.id = t.id;
        o.description = t.description;
        o.objectiveType = t.objectiveType;
        o.profession = t.profession;
        o.target = t.target;
        o.required = Math.max(1, t.amount);
        o.progress = 0;
        return o;
    }

    private static QuestDataManager.Objective fromGuildTemplate(QuestConfig.Template t) {
        QuestDataManager.Objective o = new QuestDataManager.Objective();
        o.id = t.id;
        o.description = t.description;
        o.objectiveType = t.objectiveType;
        o.profession = t.profession;
        o.target = t.target;
        o.required = Math.max(1, t.amount);
        o.requiredPlayers = Math.max(1, QuestConfig.SETTINGS.guildWeeklyRequiredPlayers);
        o.progress = 0;
        return o;
    }

    public static QuestDataManager.GuildQuestData getGuildData(ServerPlayer player) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        if (guild == null || guild.id == null) return null;
        QuestDataManager.GuildQuestData data = GUILD_CACHE.get(guild.id);
        if (data != null) {
            refreshGuildIfNeeded(guild, data);
            return data;
        }
        data = QuestDataManager.loadGuild(guild.id, guild.name);
        GUILD_CACHE.put(guild.id, data);
        refreshGuildIfNeeded(guild, data);
        return data;
    }

    private static void refreshGuildIfNeeded(GuildRepository.GuildSnapshot guild, QuestDataManager.GuildQuestData data) {
        if (guild == null || data == null) return;
        String weeklyKey = weeklyPeriodKey();
        if (data.weekly == null || data.weekly.periodKey == null || !data.weekly.periodKey.equals(weeklyKey)) {
            data.weekly = generateGuildWeeklySet(weeklyKey);
            data.claimedWeekly = new HashSet<>();
            markGuildDirty(guild.id);
        }
        data.guildId = guild.id.toString();
        data.guildName = guild.name;
    }

    private static QuestDataManager.QuestSet generateGuildWeeklySet(String periodKey) {
        QuestDataManager.QuestSet set = new QuestDataManager.QuestSet();
        set.periodKey = periodKey;
        set.completed = false;
        set.objectives = new ArrayList<>();
        List<QuestConfig.Template> pool = new ArrayList<>();
        if (QuestConfig.SETTINGS.guildWeeklyTemplates != null) pool.addAll(QuestConfig.SETTINGS.guildWeeklyTemplates);
        int count = Math.max(1, QuestConfig.SETTINGS.guildWeeklyObjectiveCount);
        Set<String> usedProfessions = new HashSet<>();
        for (int i = 0; i < count; i++) {
            QuestConfig.Template picked = pickWeighted(pool, usedProfessions);
            if (picked == null) picked = pickWeighted(pool, null);
            if (picked == null) break;
            pool.remove(picked);
            usedProfessions.add(safe(picked.profession));
            set.objectives.add(fromGuildTemplate(picked));
        }
        return set;
    }

    public static void recordBlock(ServerPlayer player, ProfessionType profession, String blockId) {
        if (player == null || profession == null || blockId == null) return;
        String type = switch (profession) {
            case MINING -> "MINE_BLOCK";
            case FORESTRY -> "CHOP_BLOCK";
            case FARMING -> "HARVEST_CROP";
            default -> "";
        };
        increment(player, objective -> matchesBlockObjective(objective, type, blockId), 1);
        AdventureGuideManager.increment(player, "profession_action", 1);
    }

    public static void recordProfessionAbility(ServerPlayer player, String abilityId) {
        if (player == null || abilityId == null || abilityId.isBlank()) return;
        String normalized = abilityId.toLowerCase(Locale.ROOT);
        increment(player, objective -> "USE_PROFESSION_ABILITY".equalsIgnoreCase(objective.objectiveType) && targetMatches(objective.target, normalized), 1);
    }

    public static void recordProfessionFragment(ServerPlayer player, ProfessionType profession, String rarity) {
        if (player == null) return;
        String normalizedRarity = rarity == null ? "any" : rarity.toLowerCase(Locale.ROOT);
        increment(player, objective -> {
            String type = safe(objective.objectiveType).toUpperCase(Locale.ROOT);
            String target = safe(objective.target);
            return ("EARN_PROFESSION_FRAGMENT".equals(type) || "PROFESSION_FRAGMENT".equals(type))
                    && (target.equalsIgnoreCase("any")
                    || targetMatches(target, normalizedRarity)
                    || (profession != null && targetMatches(target, profession.name())));
        }, 1);
    }

    public static void recordBattleWin(ServerPlayer player, BattleContextManager.BattleType battleType) {
        if (player == null) return;
        String target = battleType == null ? "UNKNOWN" : battleType.name();
        increment(player, objective -> "WIN_BATTLE".equalsIgnoreCase(objective.objectiveType) && targetMatches(objective.target, target), 1);
        if (battleType == BattleContextManager.BattleType.RANKED || battleType == BattleContextManager.BattleType.CASUAL) {
            AdventureGuideManager.increment(player, "pvp_play", 1);
        }
        if (battleType == BattleContextManager.BattleType.ADVENTURE_ROAMING) {
            AdventureGuideManager.increment(player, "adventurer_request", 1);
        }
    }

    public static void recordDefeatedPokemonType(ServerPlayer player, String type) {
        if (player == null || type == null || type.isBlank()) return;
        String normalized = type.toLowerCase(Locale.ROOT);
        increment(player, objective -> "DEFEAT_TYPE".equalsIgnoreCase(objective.objectiveType) && targetMatches(objective.target, normalized), 1);
    }

    private static boolean matchesBlockObjective(QuestDataManager.Objective objective, String directType, String blockId) {
        if (objective == null || blockId == null) return false;
        String objectiveType = safe(objective.objectiveType).toUpperCase(Locale.ROOT);
        String target = safe(objective.target);
        if ("HARVEST_CROP".equals(objectiveType)) return "HARVEST_CROP".equals(directType) && (target.equalsIgnoreCase("any") || blockId.equals(target));
        if ("HARVEST_CROP_CONTAINS".equals(objectiveType)) return "HARVEST_CROP".equals(directType) && blockId.contains(target);
        if (objectiveType.equals(directType) && (target.equalsIgnoreCase("any") || blockMatchesTarget(blockId, target))) return true;
        if (objectiveType.equals(directType + "_CONTAINS")) return blockId.contains(target) || blockMatchesTarget(blockId, target);
        if (objectiveType.equals(directType + "_TAG") && "logs".equalsIgnoreCase(target)) return blockId.endsWith("_log") || blockId.endsWith("_stem") || blockId.contains("log");
        return false;
    }

    private static boolean blockMatchesTarget(String blockId, String target) {
        if (blockId == null || target == null) return false;
        String b = blockId.toLowerCase(Locale.ROOT);
        String t = target.toLowerCase(Locale.ROOT);
        if (b.equals(t)) return true;
        if (!t.contains(":")) t = "minecraft:" + t;
        if (b.equals(t)) return true;
        if (t.equals("minecraft:diamond_ore") || t.equals("diamond_ore") || t.equals("diamond")) {
            return b.equals("minecraft:diamond_ore") || b.equals("minecraft:deepslate_diamond_ore");
        }
        if (t.startsWith("minecraft:") && t.endsWith("_ore")) {
            String deepslate = "minecraft:deepslate_" + t.substring("minecraft:".length());
            return b.equals(t) || b.equals(deepslate);
        }
        if (!target.contains(":") && !target.endsWith("_ore")) {
            String ore = "minecraft:" + target.toLowerCase(Locale.ROOT) + "_ore";
            String deepOre = "minecraft:deepslate_" + target.toLowerCase(Locale.ROOT) + "_ore";
            if (b.equals(ore) || b.equals(deepOre)) return true;
        }
        return false;
    }

    private interface ObjectiveMatcher { boolean matches(QuestDataManager.Objective objective); }

    private static void increment(ServerPlayer player, ObjectiveMatcher matcher, int amount) {
        QuestDataManager.QuestData data = getData(player);
        refreshIfNeeded(player, data, true);
        boolean changedDaily = incrementSet(data.daily, matcher, amount);
        boolean changedWeekly = incrementSet(data.weekly, matcher, amount);
        boolean changedContracts = incrementContracts(data, matcher, amount);
        boolean changedGuildWeekly = incrementGuildWeekly(player, matcher, amount);
        if (changedDaily || changedWeekly || changedContracts || changedGuildWeekly) {
            markDirty(player);
            if (changedDaily && isReady(data.daily)) notifyReady(player, "Daily");
            if (changedWeekly && isReady(data.weekly)) notifyReady(player, "Weekly");
            if (changedContracts) notifyReadyContracts(player, data);
            if (changedGuildWeekly) notifyReadyGuildWeekly(player);
        }
    }

    private static boolean incrementSet(QuestDataManager.QuestSet set, ObjectiveMatcher matcher, int amount) {
        if (set == null || set.completed || set.objectives == null) return false;
        boolean changed = false;
        for (QuestDataManager.Objective o : set.objectives) {
            if (o == null || o.progress >= o.required || !matcher.matches(o)) continue;
            o.progress = Math.min(o.required, o.progress + Math.max(1, amount));
            changed = true;
        }
        return changed;
    }

    private static boolean incrementGuildWeekly(ServerPlayer player, ObjectiveMatcher matcher, int amount) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        if (guild == null || guild.id == null) return false;
        QuestDataManager.GuildQuestData data = getGuildData(player);
        if (data == null || data.weekly == null || data.weekly.objectives == null) return false;
        boolean changed = false;
        String playerKey = PlayerProfileManager.activeProfileId(player).toString();
        for (QuestDataManager.Objective o : data.weekly.objectives) {
            if (o == null) continue;
            if (o.requiredPlayers <= 0) o.requiredPlayers = Math.max(1, QuestConfig.SETTINGS.guildWeeklyRequiredPlayers);
            if (o.completedPlayers == null) o.completedPlayers = new HashSet<>();
            if (o.playerProgress == null) o.playerProgress = new HashMap<>();
            if (o.completedPlayers.contains(playerKey) || !matcher.matches(o)) continue;
            int next = Math.min(o.required, o.playerProgress.getOrDefault(playerKey, 0) + Math.max(1, amount));
            o.playerProgress.put(playerKey, next);
            if (next >= o.required) {
                o.completedPlayers.add(playerKey);
            }
            o.progress = Math.min(o.requiredPlayers, o.completedPlayers.size());
            changed = true;
        }
        if (changed) {
            markGuildDirty(guild.id);
            saveGuild(guild.id);
        }
        return changed;
    }

    private static void notifyReadyGuildWeekly(ServerPlayer player) {
        QuestDataManager.GuildQuestData data = getGuildData(player);
        if (data != null && isReady(data.weekly) && !hasClaimedGuildWeekly(player)) {
            player.sendSystemMessage(Component.literal("Guild weekly quests complete! Open /quest menu to claim your guild rewards.").withStyle(ChatFormatting.GOLD));
        }
    }

    public static boolean hasClaimedGuildWeekly(ServerPlayer player) {
        QuestDataManager.GuildQuestData data = getGuildData(player);
        return data != null && data.claimedWeekly != null && data.claimedWeekly.contains(PlayerProfileManager.activeProfileId(player).toString());
    }

    public static boolean completeGuildWeekly(ServerPlayer player) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        if (guild == null || guild.id == null) {
            player.sendSystemMessage(Component.literal("You must be in a guild to claim guild weekly rewards.").withStyle(ChatFormatting.RED));
            return false;
        }
        QuestDataManager.GuildQuestData data = getGuildData(player);
        if (data == null || data.weekly == null || !isReady(data.weekly)) {
            player.sendSystemMessage(Component.literal("Your guild has not completed its weekly quests yet.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (data.claimedWeekly == null) data.claimedWeekly = new HashSet<>();
        String playerKey = PlayerProfileManager.activeProfileId(player).toString();
        if (data.claimedWeekly.contains(playerKey)) {
            player.sendSystemMessage(Component.literal("You already claimed this guild weekly reward.").withStyle(ChatFormatting.RED));
            return false;
        }
        data.claimedWeekly.add(playerKey);
        int credits = Math.max(0, QuestConfig.SETTINGS.guildWeeklyCompletionCredits);
        if (credits > 0) EconomyManager.deposit(player, EconomyManager.wholeCreditsToCents(credits), "guild_weekly_quest");
        awardQuestChunks(player, "GUILD");
        AdventurerGuildManager.awardGuildActivity(player, 400, 10, "guild weekly quests");
        runRewardCommands(player, QuestConfig.SETTINGS.guildWeeklyRewardCommands);
        markGuildDirty(guild.id);
        saveGuild(guild.id);
        player.sendSystemMessage(Component.literal("Guild weekly rewards claimed!").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static List<Component> rewardLore(boolean daily) {
        List<Component> lore = new ArrayList<>();
        int credits = daily ? QuestConfig.SETTINGS.dailyCompletionCredits : QuestConfig.SETTINGS.weeklyCompletionCredits;
        int xp = daily ? QuestConfig.SETTINGS.dailyProfessionXpPerObjective : QuestConfig.SETTINGS.weeklyProfessionXpPerObjective;
        if (credits > 0) lore.add(Component.literal("§7• §6" + EconomyManager.formatWholeCredits(credits)));
        if (xp > 0) lore.add(Component.literal("§7• §a" + xp + " Profession XP per objective"));
        lore.add(Component.literal("§7• §e" + (daily ? 75 : 300) + " Adventurer XP"));
        lore.add(Component.literal("§7• §b" + (daily ? 2 : 8) + " Adventurer's Marks"));
        lore.add(Component.literal("§7• §6" + (daily ? "8 Cobblestone + 2 Copper chunks" : "24 Cobblestone + 8 Copper + 3 Iron chunks")));
        addCommandRewardLore(lore, daily ? QuestConfig.SETTINGS.dailyRewardCommands : QuestConfig.SETTINGS.weeklyRewardCommands);
        String crateId = daily ? QuestConfig.SETTINGS.dailyCrateCreditId : QuestConfig.SETTINGS.weeklyCrateCreditId;
        lore.add(Component.literal("§7• §eGuaranteed 1 " + displayCrateId(crateId) + " Crate Credit"));
        return lore;
    }

    public static List<Component> guildRewardLore() {
        List<Component> lore = new ArrayList<>();
        if (QuestConfig.SETTINGS.guildWeeklyCompletionCredits > 0) lore.add(Component.literal("§7• §6" + EconomyManager.formatWholeCredits(QuestConfig.SETTINGS.guildWeeklyCompletionCredits)));
        lore.add(Component.literal("§7• §e400 Adventurer XP"));
        lore.add(Component.literal("§7• §b10 Adventurer's Marks"));
        lore.add(Component.literal("§7• §6Player Guild chunk bundle"));
        addCommandRewardLore(lore, QuestConfig.SETTINGS.guildWeeklyRewardCommands);
        if (lore.stream().noneMatch(c -> c.getString().toLowerCase(Locale.ROOT).contains("guild crate credit"))) {
            lore.add(Component.literal("§7• §fPlayer Guild Crate Credit ×1"));
        }
        return lore;
    }

    public static List<Component> contractRewardLore(List<String> commands) {
        return contractRewardLore(commands, 0, "f");
    }

    public static List<Component> contractRewardLore(List<String> commands, int rewardCredits, String difficulty) {
        List<Component> lore = new ArrayList<>();
        if (rewardCredits > 0) lore.add(Component.literal("§7• §6" + EconomyManager.formatWholeCredits(rewardCredits)));
        lore.add(Component.literal("§7• §e" + guildXpForDifficulty(difficulty) + " Adventurer XP"));
        lore.add(Component.literal("§7• §b" + guildMarksForDifficulty(difficulty) + " Adventurer's Marks"));
        lore.add(Component.literal("§7• §6" + contractChunkSummary(difficulty)));
        addCommandRewardLore(lore, commands);
        lore.add(Component.literal("§7• §eGuaranteed 1 " + displayCrateId(crateIdForDifficulty(difficulty)) + " Crate Credit"));
        return lore;
    }

    private static void addCommandRewardLore(List<Component> lore, List<String> commands) {
        if (commands == null || commands.isEmpty()) return;
        int shown = 0;
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;
            String friendly = friendlyReward(raw);
            if (friendly.isBlank()) continue;
            lore.add(Component.literal("§7• §f" + friendly));
            if (++shown >= 8) break;
        }
    }

    private static String friendlyReward(String raw) {
        String original = raw == null ? "" : raw.trim();
        if (original.isBlank()) return "";

        String normalized = original.replaceAll("\\s+", " ");
        String[] rawParts = normalized.split(" ");

        if (rawParts.length >= 3 && rawParts[0].equalsIgnoreCase("give")) {
            String itemId = rawParts[2];
            String amount = rawParts.length >= 4 ? rawParts[3] : "1";
            return prettyItemId(itemId) + " ×" + safeAmount(amount);
        }

        if (rawParts.length >= 4
                && rawParts[0].equalsIgnoreCase("opencrates")
                && rawParts[1].equalsIgnoreCase("givekey")) {
            String crateId = rawParts[3];
            String amount = rawParts.length >= 5 ? rawParts[4] : "1";
            return displayCrateId(crateId) + " Crate Credit ×" + safeAmount(amount);
        }

        String value = normalized.replace("%player%", "you").replace("%uuid%", "your UUID").trim();
        return value;
    }

    private static void announceContractCreated(ServerPlayer player, QuestConfig.ContractTemplate template) {
        if (player == null || player.server == null || template == null) return;
        String rewards = contractAnnouncementRewards(template);
        String line = "§6§l[Contracts] §e" + player.getGameProfile().getName()
                + " §fcreated a new contract: §b" + safe(template.description)
                + " §8| §7Rank: §f" + QuestConfig.rankForDifficulty(template.difficulty)
                + " §8| §7Cost: §6" + EconomyManager.formatWholeCredits(template.creditCost)
                + " §8| §7Time: §f" + template.durationHours + "h"
                + (rewards.isBlank() ? "" : " §8| §7Rewards: §f" + rewards);
        Component message = Component.literal(line);
        player.server.getPlayerList().broadcastSystemMessage(message, false);
        NetworkEventManager.publishBroadcastText(line);
    }

    private static String contractAnnouncementRewards(QuestConfig.ContractTemplate template) {
        List<String> rewards = new ArrayList<>();
        if (template.rewardCredits > 0) rewards.add(EconomyManager.formatWholeCredits(template.rewardCredits));
        if (template.rewardCommands != null) {
            for (String command : template.rewardCommands) {
                String friendly = friendlyReward(command);
                if (!friendly.isBlank()) rewards.add(friendly);
                if (rewards.size() >= 3) break;
            }
        }
        rewards.add(displayCrateId(crateIdForDifficulty(template.difficulty)) + " Crate Credit");
        return String.join(", ", rewards);
    }

    private static String safeAmount(String value) {
        if (value == null || value.isBlank()) return "1";
        String cleaned = value.trim();
        try {
            return String.valueOf(Math.max(1, Integer.parseInt(cleaned)));
        } catch (Exception ignored) {
            return cleaned;
        }
    }

    private static int crateChance() {
        return 100;
    }

    private static void maybeAwardCrateCredit(ServerPlayer player, String crateId) {
        if (player == null) return;
        CrateCreditManager.addCredits(player, crateIdForDifficulty(crateId), 1);
    }

    private static void awardRarityFragments(ServerPlayer player, String rarity) {
        if (player == null) return;
        String normalized = crateIdForDifficulty(rarity).toUpperCase(Locale.ROOT);
        int amount = 1 + RANDOM.nextInt(3);
        ProfessionFragmentManager.giveFragments(player, normalized, amount);
        player.sendSystemMessage(Component.literal("+" + amount + " " + normalized + " Essence" + (amount == 1 ? "" : "s")).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static String crateIdForDifficulty(String difficulty) {
        String value = safe(difficulty).toLowerCase(Locale.ROOT).replace(' ', '_');
        return switch (value) {
            case "e" -> "e";
            case "d" -> "d";
            case "c" -> "c";
            case "a" -> "a";
            case "s" -> "s";
            case "guild" -> "guild";
            default -> "f";
        };
    }

    private static String displayCrateId(String crateId) {
        return titleWords(crateIdForDifficulty(crateId).replace('_', ' '));
    }

    private static String prettyItemId(String itemId) {
        String value = safe(itemId);
        int idx = value.indexOf(':');
        if (idx >= 0 && idx + 1 < value.length()) value = value.substring(idx + 1);
        return titleWords(value.replace('_', ' ').replace('-', ' '));
    }

    private static String titleWords(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String[] parts = value.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) out.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static void notifyReady(ServerPlayer player, String label) {
        player.sendSystemMessage(Component.literal(label + " quests complete! Open the Adventurer's Guild to claim your rewards.").withStyle(ChatFormatting.GOLD));
    }

    public static boolean complete(ServerPlayer player, boolean daily) {
        QuestDataManager.QuestData data = getData(player);
        refreshIfNeeded(player, data, true);
        QuestDataManager.QuestSet set = daily ? data.daily : data.weekly;
        if (set == null || set.completed) {
            player.sendSystemMessage(Component.literal("You have already claimed this quest reward.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!isReady(set)) {
            player.sendSystemMessage(Component.literal("You have not completed all objectives yet.").withStyle(ChatFormatting.RED));
            return false;
        }
        set.completed = true;
        int credits = daily ? QuestConfig.SETTINGS.dailyCompletionCredits : QuestConfig.SETTINGS.weeklyCompletionCredits;
        if (credits > 0) EconomyManager.deposit(player, EconomyManager.wholeCreditsToCents(credits), daily ? "daily_quest" : "weekly_quest");
        int xpEach = daily ? QuestConfig.SETTINGS.dailyProfessionXpPerObjective : QuestConfig.SETTINGS.weeklyProfessionXpPerObjective;
        if (xpEach > 0) {
            for (QuestDataManager.Objective o : set.objectives) {
                ProfessionType profession = parseProfession(o.profession);
                if (profession != null) ProfessionManager.addXp(player, profession, xpEach);
            }
        }
        runRewardCommands(player, daily ? QuestConfig.SETTINGS.dailyRewardCommands : QuestConfig.SETTINGS.weeklyRewardCommands);
        String questRarity = daily ? QuestConfig.SETTINGS.dailyCrateCreditId : QuestConfig.SETTINGS.weeklyCrateCreditId;
        maybeAwardCrateCredit(player, questRarity);
        awardQuestChunks(player, daily ? "DAILY" : "WEEKLY");
        awardRarityFragments(player, questRarity);
        AdventurerGuildManager.awardGuildActivity(player, daily ? 75 : 300, daily ? 2 : 8, daily ? "daily guild board" : "weekly guild board");
        if (daily) com.champutils.cosmetic.TitleManager.unlock(player, "questing_soul");
        markDirty(player);
        savePlayer(player);
        player.sendSystemMessage(Component.literal((daily ? "Daily" : "Weekly") + " quest rewards claimed!").withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static void awardQuestChunks(ServerPlayer player, String tier) {
        String key = tier == null ? "F" : tier.trim().toUpperCase(Locale.ROOT);
        switch (key) {
            case "DAILY" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 8, false); ProfessionChunkManager.addChunk(player, "COPPER", 2, false); }
            case "WEEKLY" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 24, false); ProfessionChunkManager.addChunk(player, "COPPER", 8, false); ProfessionChunkManager.addChunk(player, "IRON", 3, false); }
            case "GUILD" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 32, false); ProfessionChunkManager.addChunk(player, "COPPER", 12, false); ProfessionChunkManager.addChunk(player, "IRON", 5, false); ProfessionChunkManager.addChunk(player, "GOLD", 1, false); }
            case "E" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 8, false); ProfessionChunkManager.addChunk(player, "COPPER", 3, false); }
            case "D" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 12, false); ProfessionChunkManager.addChunk(player, "COPPER", 5, false); ProfessionChunkManager.addChunk(player, "IRON", 2, false); }
            case "C" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 18, false); ProfessionChunkManager.addChunk(player, "COPPER", 7, false); ProfessionChunkManager.addChunk(player, "IRON", 3, false); }
            case "B" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 24, false); ProfessionChunkManager.addChunk(player, "COPPER", 10, false); ProfessionChunkManager.addChunk(player, "IRON", 4, false); ProfessionChunkManager.addChunk(player, "GOLD", 1, false); }
            case "A" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 30, false); ProfessionChunkManager.addChunk(player, "COPPER", 13, false); ProfessionChunkManager.addChunk(player, "IRON", 6, false); ProfessionChunkManager.addChunk(player, "GOLD", 2, false); }
            case "S" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 40, false); ProfessionChunkManager.addChunk(player, "COPPER", 18, false); ProfessionChunkManager.addChunk(player, "IRON", 9, false); ProfessionChunkManager.addChunk(player, "GOLD", 3, false); ProfessionChunkManager.addChunk(player, "DIAMOND", 1, false); }
            default -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 6, false); ProfessionChunkManager.addChunk(player, "COPPER", 2, false); }
        }
    }


    private static int guildXpForDifficulty(String difficulty) {
        return switch ((difficulty == null ? "F" : difficulty.trim().toUpperCase(Locale.ROOT))) {
            case "E" -> 60;
            case "D" -> 90;
            case "C" -> 140;
            case "B" -> 220;
            case "A" -> 350;
            case "S" -> 550;
            default -> 40;
        };
    }

    private static int guildMarksForDifficulty(String difficulty) {
        return switch ((difficulty == null ? "F" : difficulty.trim().toUpperCase(Locale.ROOT))) {
            case "E" -> 2;
            case "D" -> 3;
            case "C" -> 4;
            case "B" -> 6;
            case "A" -> 9;
            case "S" -> 14;
            default -> 1;
        };
    }

    private static String contractChunkSummary(String difficulty) {
        return switch ((difficulty == null ? "F" : difficulty.trim().toUpperCase(Locale.ROOT))) {
            case "E" -> "8 Cobblestone + 3 Copper chunks";
            case "D" -> "12 Cobblestone + 5 Copper + 2 Iron chunks";
            case "C" -> "18 Cobblestone + 7 Copper + 3 Iron chunks";
            case "B" -> "24 Cobblestone + 10 Copper + 4 Iron + 1 Gold chunk";
            case "A" -> "30 Cobblestone + 13 Copper + 6 Iron + 2 Gold chunks";
            case "S" -> "40 Cobblestone + 18 Copper + 9 Iron + 3 Gold + 1 Diamond chunk";
            default -> "6 Cobblestone + 2 Copper chunks";
        };
    }

    private static void runRewardCommands(ServerPlayer player, List<String> commands) {
        MinecraftServer server = player.getServer();
        if (server == null || commands == null) return;
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;
            String cmd = raw.replace("%player%", player.getName().getString()).replace("%uuid%", PlayerProfileManager.activeProfileId(player).toString());
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
        }
    }


    private static boolean incrementContracts(QuestDataManager.QuestData data, ObjectiveMatcher matcher, int amount) {
        if (data == null || data.contracts == null) return false;
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (QuestDataManager.Contract c : data.contracts) {
            if (c == null || c.completed || now >= c.expiresAtMillis || c.progress >= c.required || !matcher.matches(c)) continue;
            c.progress = Math.min(c.required, c.progress + Math.max(1, amount));
            changed = true;
        }
        return changed;
    }

    private static void notifyReadyContracts(ServerPlayer player, QuestDataManager.QuestData data) {
        if (data == null || data.contracts == null) return;
        for (QuestDataManager.Contract c : data.contracts) {
            if (c != null && !c.completed && c.progress >= c.required && System.currentTimeMillis() < c.expiresAtMillis) {
                player.sendSystemMessage(Component.literal("Contract complete! Open the Adventurer's Guild to claim your reward.").withStyle(ChatFormatting.GOLD));
                return;
            }
        }
    }

    public static List<QuestConfig.ContractTemplate> eligibleContracts(ServerPlayer player) {
        List<QuestConfig.ContractTemplate> out = new ArrayList<>();
        if (QuestConfig.SETTINGS.contractTemplates == null) return out;
        for (QuestConfig.ContractTemplate t : QuestConfig.SETTINGS.contractTemplates) {
            if (t == null || t.id == null || t.objectiveType == null) continue;
            ProfessionType profession = parseProfession(t.profession);
            int level = profession == null ? 1 : ProfessionManager.getBenefitLevel(player, profession);
            if (level >= Math.max(1, t.minLevel)
                    && AdventurerGuildManager.hasRank(player, t.minAdventurerRank)
                    && Math.max(1, t.weight) > 0) out.add(t);
        }
        return out;
    }

    public static QuestConfig.ContractTemplate findContractTemplate(String id) {
        if (id == null || QuestConfig.SETTINGS.contractTemplates == null) return null;
        for (QuestConfig.ContractTemplate t : QuestConfig.SETTINGS.contractTemplates) {
            if (t != null && id.equalsIgnoreCase(t.id)) return t;
        }
        return null;
    }

    public static boolean buyContract(ServerPlayer player, String contractId) {
        QuestDataManager.QuestData data = getData(player);
        cleanupExpiredContracts(player, data, true);
        int active = 0;
        if (data.contracts != null) {
            long now = System.currentTimeMillis();
            for (QuestDataManager.Contract c : data.contracts) {
                if (c != null && !c.completed && now < c.expiresAtMillis) active++;
            }
        }
        if (active >= Math.max(1, QuestConfig.SETTINGS.maxActiveContracts)) {
            player.sendSystemMessage(Component.literal("You already have the maximum active contracts.").withStyle(ChatFormatting.RED));
            return false;
        }
        QuestConfig.ContractTemplate t = findContractTemplate(contractId);
        if (t == null) {
            player.sendSystemMessage(Component.literal("Unknown contract: " + contractId).withStyle(ChatFormatting.RED));
            return false;
        }
        ProfessionType profession = parseProfession(t.profession);
        int level = profession == null ? 1 : ProfessionManager.getBenefitLevel(player, profession);
        if (level < Math.max(1, t.minLevel)) {
            player.sendSystemMessage(Component.literal("You need " + t.profession + " level " + t.minLevel + " for that contract.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!AdventurerGuildManager.hasRank(player, t.minAdventurerRank)) {
            player.sendSystemMessage(Component.literal("You need Adventurer Rank " + t.minAdventurerRank + " for that contract.").withStyle(ChatFormatting.RED));
            return false;
        }
        long cost = EconomyManager.wholeCreditsToCents(Math.max(0, t.creditCost));
        if (cost > 0) {
            EconomyManager.TransactionResult result = EconomyManager.withdraw(player, cost, "quest_contract_buy:" + t.id);
            if (!result.success) {
                player.sendSystemMessage(Component.literal(result.error == null ? "Not enough Credits." : result.error).withStyle(ChatFormatting.RED));
                return false;
            }
        }
        if (data.contracts == null) data.contracts = new ArrayList<>();
        QuestDataManager.Contract c = new QuestDataManager.Contract();
        c.id = t.id;
        c.description = t.description;
        c.objectiveType = t.objectiveType;
        c.profession = t.profession;
        c.target = t.target;
        c.required = Math.max(1, t.amount);
        c.progress = 0;
        c.creditCost = Math.max(0, t.creditCost);
        c.rewardCredits = Math.max(0, t.rewardCredits);
        c.difficulty = t.difficulty == null ? "F" : t.difficulty;
        c.purchasedAtMillis = System.currentTimeMillis();
        c.expiresAtMillis = c.purchasedAtMillis + Math.max(1, t.durationHours) * 60L * 60L * 1000L;
        c.rewardCommands = new ArrayList<>();
        if (t.rewardCommands != null) c.rewardCommands.addAll(t.rewardCommands);
        data.contracts.add(c);
        markDirty(player);
        savePlayer(player);
        AdventureGuideManager.increment(player, "contract_buy", 1);
        player.sendSystemMessage(Component.literal("Contract purchased: " + c.description + " (expires in " + t.durationHours + "h)").withStyle(ChatFormatting.GREEN));
        announceContractCreated(player, t);
        return true;
    }

    public static boolean completeContract(ServerPlayer player) {
        QuestDataManager.QuestData data = getData(player);
        cleanupExpiredContracts(player, data, true);
        if (data.contracts == null || data.contracts.isEmpty()) {
            player.sendSystemMessage(Component.literal("You have no active contracts.").withStyle(ChatFormatting.RED));
            return false;
        }
        long now = System.currentTimeMillis();
        for (QuestDataManager.Contract c : data.contracts) {
            if (c == null || c.completed || now >= c.expiresAtMillis) continue;
            if (c.progress < c.required) continue;
            c.completed = true;
            if (c.rewardCredits > 0) EconomyManager.deposit(player, EconomyManager.wholeCreditsToCents(c.rewardCredits), "quest_contract_complete:" + c.id);
            runRewardCommands(player, c.rewardCommands);
            maybeAwardCrateCredit(player, crateIdForDifficulty(c.difficulty));
            awardQuestChunks(player, c.difficulty);
            awardRarityFragments(player, c.difficulty);
            AdventurerGuildManager.awardGuildActivity(player, guildXpForDifficulty(c.difficulty), guildMarksForDifficulty(c.difficulty), "contract");
            com.champutils.cosmetic.TitleManager.unlock(player, "contractor");
            ProfessionType profession = parseProfession(c.profession);
            if (profession != null) ProfessionManager.addXp(player, profession, Math.max(100, c.required / 2));
            markDirty(player);
            savePlayer(player);
            AdventureGuideManager.increment(player, "contract_complete", 1);
            player.sendSystemMessage(Component.literal("Contract reward claimed: " + c.description).withStyle(ChatFormatting.GREEN));
            return true;
        }
        player.sendSystemMessage(Component.literal("No completed contract is ready to claim.").withStyle(ChatFormatting.RED));
        return false;
    }

    public static boolean abandonContract(ServerPlayer player) {
        QuestDataManager.QuestData data = getData(player);
        if (data.contracts == null || data.contracts.isEmpty()) {
            player.sendSystemMessage(Component.literal("You have no contracts to abandon.").withStyle(ChatFormatting.RED));
            return false;
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < data.contracts.size(); i++) {
            QuestDataManager.Contract c = data.contracts.get(i);
            if (c == null || c.completed || now >= c.expiresAtMillis) continue;
            data.contracts.remove(i);
            markDirty(player);
            savePlayer(player);
            player.sendSystemMessage(Component.literal("Contract abandoned. Credits are not refunded.").withStyle(ChatFormatting.YELLOW));
            return true;
        }
        player.sendSystemMessage(Component.literal("You have no active contract to abandon.").withStyle(ChatFormatting.RED));
        return false;
    }

    private static void cleanupExpiredContracts(ServerPlayer player, QuestDataManager.QuestData data, boolean notify) {
        if (data == null || data.contracts == null || data.contracts.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = data.contracts.size() - 1; i >= 0; i--) {
            QuestDataManager.Contract c = data.contracts.get(i);
            if (c == null || c.completed || now >= c.expiresAtMillis) {
                if (c != null && !c.completed && now >= c.expiresAtMillis && notify) {
                    player.sendSystemMessage(Component.literal("Contract expired: " + c.description).withStyle(ChatFormatting.RED));
                }
                data.contracts.remove(i);
                changed = true;
            }
        }
        if (changed) markDirty(player);
    }

    public static String timeLeftText(QuestDataManager.Contract c) {
        if (c == null) return "0m";
        long left = Math.max(0, c.expiresAtMillis - System.currentTimeMillis());
        long minutes = left / 60000L;
        long hours = minutes / 60L;
        long mins = minutes % 60L;
        return hours > 0 ? hours + "h " + mins + "m" : mins + "m";
    }

    public static boolean isReady(QuestDataManager.QuestSet set) {
        if (set == null || set.objectives == null || set.objectives.isEmpty()) return false;
        for (QuestDataManager.Objective o : set.objectives) {
            if (o == null) return false;
            int required = o.requiredPlayers > 0 ? o.requiredPlayers : o.required;
            if (o.progress < required) return false;
        }
        return true;
    }

    public static String dailyPeriodKey() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate date = now.toLocalDate();
        LocalDateTime reset = date.atTime(Math.max(0, Math.min(23, QuestConfig.SETTINGS.dailyResetHour)), Math.max(0, Math.min(59, QuestConfig.SETTINGS.dailyResetMinute)));
        if (now.isBefore(reset)) date = date.minusDays(1);
        return date.toString();
    }

    public static String weeklyPeriodKey() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        DayOfWeek resetDay = parseDay(QuestConfig.SETTINGS.weeklyResetDay);
        LocalDate date = now.toLocalDate();
        while (date.getDayOfWeek() != resetDay) date = date.minusDays(1);
        LocalDateTime reset = date.atTime(Math.max(0, Math.min(23, QuestConfig.SETTINGS.weeklyResetHour)), Math.max(0, Math.min(59, QuestConfig.SETTINGS.weeklyResetMinute)));
        if (now.isBefore(reset)) date = date.minusWeeks(1);
        WeekFields wf = WeekFields.ISO;
        return date.getYear() + "-W" + String.format("%02d", date.get(wf.weekOfWeekBasedYear()));
    }

    private static DayOfWeek parseDay(String value) {
        try { return DayOfWeek.valueOf(safe(value).toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return DayOfWeek.MONDAY; }
    }

    private static boolean targetMatches(String objectiveTarget, String actual) {
        String target = safe(objectiveTarget);
        String a = safe(actual);
        return target.equalsIgnoreCase("any") || target.equalsIgnoreCase(a);
    }

    private static ProfessionType parseProfession(String text) {
        try { return ProfessionType.valueOf(safe(text).toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return null; }
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static void markDirty(ServerPlayer player) { DIRTY.add(PlayerProfileManager.activeProfileId(player)); }

    private static void markGuildDirty(UUID guildId) { if (guildId != null) DIRTY_GUILDS.add(guildId); }

    private static void saveGuild(UUID guildId) {
        if (guildId == null || !DIRTY_GUILDS.contains(guildId)) return;
        QuestDataManager.GuildQuestData data = GUILD_CACHE.get(guildId);
        if (data != null) QuestDataManager.saveGuild(guildId, data);
        DIRTY_GUILDS.remove(guildId);
    }

    public static void savePlayer(ServerPlayer player) {
        UUID uuid = PlayerProfileManager.activeProfileId(player);
        if (!DIRTY.contains(uuid)) return;
        QuestDataManager.QuestData data = CACHE.get(uuid);
        if (data != null) QuestDataManager.save(uuid, data);
        DIRTY.remove(uuid);
    }

    public static void unloadPlayer(ServerPlayer player) {
        savePlayer(player);
        CACHE.remove(PlayerProfileManager.activeProfileId(player));
    }

    public static void saveAll() {
        for (UUID uuid : new HashSet<>(DIRTY)) {
            QuestDataManager.QuestData data = CACHE.get(uuid);
            if (data != null) QuestDataManager.save(uuid, data);
        }
        DIRTY.clear();
        for (UUID guildId : new HashSet<>(DIRTY_GUILDS)) {
            QuestDataManager.GuildQuestData data = GUILD_CACHE.get(guildId);
            if (data != null) QuestDataManager.saveGuild(guildId, data);
        }
        DIRTY_GUILDS.clear();
    }
}
