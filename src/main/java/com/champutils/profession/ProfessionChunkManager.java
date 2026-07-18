package com.champutils.profession;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.economy.EconomyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class ProfessionChunkManager {
    private static final Random RANDOM = new Random();
    private ProfessionChunkManager() {}

    public static void rollActivity(ServerPlayer player, ProfessionType profession) {
        rollActivity(player, profession, 1.0D);
    }

    public static void rollActivity(ServerPlayer player, ProfessionType profession, double activityMultiplier) {
        if (player == null || profession == null || !ProfessionChunkConfig.CONFIG.enabled) return;
        String key = profession.name();
        ProfessionChunkConfig.ActivityData activity = ProfessionChunkConfig.CONFIG.activities.get(key);
        if (activity == null || activity.rolls == null || activity.rolls.isEmpty()) return;

        int level = Math.max(1, ProfessionManager.getBenefitLevel(player, profession));
        double multiplier = Math.max(0.0D, activity.activityMultiplier) * Math.max(0.0D, activityMultiplier);
        int found = 0;

        for (Map.Entry<String, ProfessionChunkConfig.RollData> entry : activity.rolls.entrySet()) {
            String chunk = normalizeChunk(entry.getKey());
            ProfessionChunkConfig.RollData roll = entry.getValue();
            if (roll == null || !ProfessionChunkConfig.CONFIG.chunks.containsKey(chunk)) continue;
            int minLevel = Math.max(1, roll.minProfessionLevel);
            if (level < minLevel) continue;
            int scalingStart = Math.max(1, roll.levelScalingStart);
            int scaledLevels = Math.max(0, level - scalingStart);
            double chance = Math.min(roll.maxChancePercent, roll.baseChancePercent + (roll.chancePerLevelPercent * scaledLevels));
            // Overall profession level should matter more than any single sublevel.
            // This boosts every chunk roll by up to +50% at level 100 before sublevel/trinket bonuses.
            double overallLevelBonus = Math.min(0.50D, Math.max(0, level - 1) * 0.005D);
            chance *= multiplier * (1.0D + overallLevelBonus);
            double sublevelFindBonus = ProfessionSubLevelManager.chunkFindChanceBonus(player, profession);
            double sublevelRarityBonus = ProfessionSubLevelManager.chunkRarityChanceBonus(player, profession) * rarityWeight(chunk);
            chance *= (1.0D + sublevelFindBonus + sublevelRarityBonus);
            double preTrinketChance = Math.min(100.0D, chance);
            double trinketBonus = ProfessionTrinketManager.chunkChanceBonus(player);
            chance *= (1.0D + trinketBonus);
            chance = Math.min(100.0D, chance);
            double rolled = RANDOM.nextDouble() * 100.0D;
            if (chance > 0.0D && rolled < chance) {
                addChunk(player, chunk, 1, true);
                found++;
                if (trinketBonus > 0.0D && rolled >= preTrinketChance) {
                    if (ProfessionNotificationSettings.areTrinketMessagesEnabled(player)) player.sendSystemMessage(Component.literal("[Trinket] Chunky Brick boosted your odds and found a " + formatChunk(chunk) + "!").withStyle(ChatFormatting.GOLD));
                }
            }
        }

        if (found > 0 && ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            ProfessionNotificationSettings.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.6F);
        }
    }

    public static void addChunk(ServerPlayer player, String chunk, int amount, boolean announce) {
        if (player == null || amount <= 0) return;
        String key = normalizeChunk(chunk);
        ProfessionManager.addChunks(player, key, amount);
        ProfessionManager.savePlayer(player);
        if ("COPPER".equals(key)) {
            AdventureGuideManager.increment(player, "chunk_copper", amount);
        }

        if (announce && ProfessionChunkConfig.CONFIG.announceFinds && ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            ProfessionChunkConfig.ChunkData chunkData = ProfessionChunkConfig.CONFIG.chunks.get(key);
            String name = chunkData == null ? formatChunk(key) : chunkData.displayName;
            if (isCRankOrBetter(key)) {
                ProfessionSpecialCelebration.celebrateHighRankChunk(player, name + (amount > 1 ? " x" + amount : ""));
            } else {
                player.displayClientMessage(Component.literal("§6Chunk Found! §e" + name + (amount > 1 ? " x" + amount : "")), true);
            }
        }
    }

    public static int count(ServerPlayer player, String chunk) {
        if (player == null) return 0;
        return ProfessionManager.getChunks(player, normalizeChunk(chunk));
    }

    public static int remove(ServerPlayer player, String chunk, int amount) {
        if (player == null || amount <= 0) return 0;
        int removed = ProfessionManager.removeChunks(player, normalizeChunk(chunk), amount);
        if (removed > 0) ProfessionManager.savePlayer(player);
        return removed;
    }

    public static long valueCents(String chunk) {
        ProfessionChunkConfig.ChunkData config = ProfessionChunkConfig.CONFIG.chunks.get(normalizeChunk(chunk));
        if (config == null || config.sellCredits <= 0.0D) return 0L;
        return EconomyManager.creditsToCents(config.sellCredits);
    }


    public static SellResult sell(ServerPlayer player, String chunk, int amount) {
        if (player == null) return new SellResult(false, "Player missing.", 0, 0L, normalizeChunk(chunk));
        String key = normalizeChunk(chunk);
        ProfessionChunkConfig.ChunkData config = ProfessionChunkConfig.CONFIG.chunks.get(key);
        if (config == null || config.sellCredits <= 0.0D) {
            return new SellResult(false, "This chunk tier is not sellable.", 0, 0L, key);
        }
        int available = Math.max(0, count(player, key));
        int requested = Math.max(1, Math.min(64, amount));
        int toSell = Math.min(requested, available);
        if (toSell <= 0) {
            return new SellResult(false, "You do not have any " + formatChunk(key) + "s to sell.", 0, 0L, key);
        }
        int removed = remove(player, key, toSell);
        if (removed <= 0) {
            return new SellResult(false, "Could not remove chunks safely.", 0, 0L, key);
        }
        long cents = EconomyManager.creditsToCents(config.sellCredits) * removed;
        if (cents > 0L) {
            EconomyManager.depositAsync(player, cents, "profession_chunk_sale:" + key.toLowerCase(Locale.ROOT));
        }
        return new SellResult(true, "", removed, cents, key);
    }

    public static long sellAllValueCents(ServerPlayer player) {
        if (player == null) return 0L;
        Map<String, Integer> chunks = ProfessionManager.getChunkBalances(player);
        if (chunks.isEmpty()) return 0L;
        long totalCents = 0L;
        for (Map.Entry<String, Integer> entry : chunks.entrySet()) {
            String chunk = normalizeChunk(entry.getKey());
            int amount = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
            ProfessionChunkConfig.ChunkData config = ProfessionChunkConfig.CONFIG.chunks.get(chunk);
            if (amount <= 0 || config == null || config.sellCredits <= 0.0D) continue;
            totalCents += EconomyManager.creditsToCents(config.sellCredits) * amount;
        }
        return Math.max(0L, totalCents);
    }

    public static long sellAll(ServerPlayer player) {
        if (player == null) return 0L;
        Map<String, Integer> chunks = ProfessionManager.getChunkBalances(player);
        if (chunks.isEmpty()) return 0L;
        long totalCents = 0L;
        for (Map.Entry<String, Integer> entry : new java.util.LinkedHashMap<>(chunks).entrySet()) {
            String chunk = normalizeChunk(entry.getKey());
            int amount = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
            ProfessionChunkConfig.ChunkData config = ProfessionChunkConfig.CONFIG.chunks.get(chunk);
            if (amount <= 0 || config == null || config.sellCredits <= 0.0D) continue;
            totalCents += EconomyManager.creditsToCents(config.sellCredits) * amount;
            ProfessionManager.removeChunks(player, chunk, amount);
        }
        if (totalCents <= 0L) return 0L;
        ProfessionManager.savePlayer(player);
        EconomyManager.depositAsync(player, totalCents, "profession_chunk_sale");
        return totalCents;
    }

    public static TradeResult tradeChunkForFragments(ServerPlayer player, String chunk, int trades) {
        if (player == null) return new TradeResult(false, "Player missing.", 0, "", "");
        String key = normalizeChunk(chunk);
        ProfessionChunkConfig.ChunkData config = ProfessionChunkConfig.CONFIG.chunks.get(key);
        if (config == null) return new TradeResult(false, "Unknown chunk tier.", 0, key, "");
        int chunksPer = Math.max(1, config.chunksPerFragment);
        int fragmentsPer = Math.max(1, config.fragmentsPerTrade);
        int requestedTrades = Math.max(1, Math.min(64, trades));
        int available = Math.max(0, count(player, key));
        int maxTrades = available / chunksPer;
        int safeTrades = Math.min(requestedTrades, maxTrades);
        if (safeTrades <= 0) {
            return new TradeResult(false, "You need " + chunksPer + " " + formatChunk(key) + "s. You have " + available + ".", 0, key, config.fragmentRarity);
        }
        long neededLong = (long) chunksPer * safeTrades;
        long fragmentsLong = (long) fragmentsPer * safeTrades;
        if (neededLong > Integer.MAX_VALUE || fragmentsLong > Integer.MAX_VALUE) {
            return new TradeResult(false, "Trade amount is too large.", 0, key, config.fragmentRarity);
        }
        int needed = (int) neededLong;
        int fragments = (int) fragmentsLong;
        int removed = remove(player, key, needed);
        if (removed < needed) return new TradeResult(false, "Could not remove chunks safely. No Essence was created.", 0, key, config.fragmentRarity);
        ProfessionManager.addFragments(player, ProfessionFragmentConfig.normalizeRarity(config.fragmentRarity), fragments);
        ProfessionManager.savePlayer(player);
        return new TradeResult(true, "", fragments, key, ProfessionFragmentConfig.normalizeRarity(config.fragmentRarity));
    }

    private static boolean isCRankOrBetter(String chunk) {
        return switch (normalizeChunk(chunk)) {
            case "GOLD", "DIAMOND", "NETHERITE" -> true;
            default -> false;
        };
    }

    private static double rarityWeight(String chunk) {
        return switch (normalizeChunk(chunk)) {
            case "COPPER" -> 0.25D;
            case "IRON" -> 0.50D;
            case "GOLD" -> 0.75D;
            case "DIAMOND" -> 1.00D;
            case "NETHERITE" -> 1.25D;
            default -> 0.10D;
        };
    }

    public static String normalizeChunk(String chunk) {
        if (chunk == null || chunk.isBlank()) return "COBBLESTONE";
        return chunk.trim().toUpperCase(Locale.ROOT);
    }

    public static String formatChunk(String chunk) {
        String key = normalizeChunk(chunk).toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = key.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder + " Chunk";
    }

    public static ChatFormatting color(String chunk) {
        return switch (normalizeChunk(chunk)) {
            case "COPPER" -> ChatFormatting.GOLD;
            case "IRON" -> ChatFormatting.GRAY;
            case "GOLD" -> ChatFormatting.YELLOW;
            case "DIAMOND" -> ChatFormatting.AQUA;
            case "NETHERITE" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    public record TradeResult(boolean success, String error, int fragments, String chunk, String rarity) {}
    public record SellResult(boolean success, String error, int sold, long cents, String chunk) {}
}

