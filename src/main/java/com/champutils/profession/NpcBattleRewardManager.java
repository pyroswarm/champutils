package com.champutils.profession;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class NpcBattleRewardManager {

    private static final Random RANDOM = new Random();

    public static void rollReward(ServerPlayer player) {
        if (player == null || !BattleProfessionLootConfig.enabled) {
            return;
        }

        int battlingLevel = Math.max(1, ProfessionManager.getLevel(player, ProfessionType.BATTLING));
        int rolls = getRollCount(battlingLevel);
        double chance = getRollChance(battlingLevel);
        boolean playedSuperRareSound = false;

        for (int i = 0; i < rolls; i++) {
            if (RANDOM.nextDouble() < chance) {
                playedSuperRareSound = rollItemReward(player, battlingLevel, playedSuperRareSound);
            }
        }

        playedSuperRareSound = rollFragmentJackpot(player, battlingLevel, playedSuperRareSound);
    }

    private static int getRollCount(int battlingLevel) {
        int rolls = Math.max(1, BattleProfessionLootConfig.baseRolls);

        if (BattleProfessionLootConfig.bonusRollEveryLevels > 0) {
            rolls += Math.max(0, battlingLevel / BattleProfessionLootConfig.bonusRollEveryLevels);
        }

        return Math.max(1, Math.min(Math.max(1, BattleProfessionLootConfig.maxRolls), rolls));
    }

    private static double getRollChance(int battlingLevel) {
        double chance = BattleProfessionLootConfig.baseRollChance +
                (BattleProfessionLootConfig.rollChancePerBattlingLevel * Math.max(0, battlingLevel - 1));

        if (BattleProfessionLootConfig.maxRollChance > 0.0D) {
            chance = Math.min(chance, BattleProfessionLootConfig.maxRollChance);
        }

        return Math.max(0.0D, chance);
    }

    private static boolean rollItemReward(ServerPlayer player, int battlingLevel, boolean soundAlreadyPlayed) {
        BattleProfessionLootConfig.LootEntry reward = getWeightedReward(battlingLevel);

        if (reward == null) {
            return soundAlreadyPlayed;
        }

        int min = Math.max(1, reward.minAmount);
        int max = Math.max(min, reward.maxAmount);
        int amount = min + RANDOM.nextInt(max - min + 1);

        return giveItemReward(player, reward.itemId, amount, soundAlreadyPlayed);
    }

    private static BattleProfessionLootConfig.LootEntry getWeightedReward(int battlingLevel) {
        List<BattleProfessionLootConfig.LootEntry> valid = new ArrayList<>();
        int totalWeight = 0;

        for (BattleProfessionLootConfig.LootEntry entry : BattleProfessionLootConfig.rewards) {
            if (entry == null || !entry.enabled || entry.itemId == null || entry.itemId.isBlank()) {
                continue;
            }

            if (battlingLevel < Math.max(1, entry.minBattlingLevel)) {
                continue;
            }

            int levelBonus = Math.max(0, battlingLevel - Math.max(1, entry.minBattlingLevel));
            int effectiveWeight = entry.weight + (entry.weightPerLevelAboveUnlock * levelBonus);

            if (effectiveWeight <= 0) {
                continue;
            }

            valid.add(entry);
            totalWeight += effectiveWeight;
        }

        if (valid.isEmpty() || totalWeight <= 0) {
            return null;
        }

        int roll = RANDOM.nextInt(totalWeight);
        int current = 0;

        for (BattleProfessionLootConfig.LootEntry entry : valid) {
            int levelBonus = Math.max(0, battlingLevel - Math.max(1, entry.minBattlingLevel));
            int effectiveWeight = entry.weight + (entry.weightPerLevelAboveUnlock * levelBonus);
            current += Math.max(0, effectiveWeight);

            if (roll < current) {
                return entry;
            }
        }

        return null;
    }

    private static boolean rollFragmentJackpot(ServerPlayer player, int battlingLevel, boolean soundAlreadyPlayed) {
        BattleProfessionLootConfig.FragmentJackpotSettings settings = BattleProfessionLootConfig.fragmentJackpots;

        if (settings == null || !settings.enabled || battlingLevel < Math.max(1, settings.minBattlingLevel)) {
            return soundAlreadyPlayed;
        }

        double chance = settings.baseChance + (settings.chancePerBattlingLevel * Math.max(0, battlingLevel - 1));

        if (settings.maxChance > 0.0D) {
            chance = Math.min(chance, settings.maxChance);
        }

        if (RANDOM.nextDouble() >= Math.max(0.0D, chance)) {
            return soundAlreadyPlayed;
        }

        String rarity = rollFragmentRarity(settings);

        if (rarity == null || rarity.isBlank()) {
            return soundAlreadyPlayed;
        }

        if (!settings.allowMythic && "MYTHIC".equalsIgnoreCase(rarity)) {
            return soundAlreadyPlayed;
        }

        if (!ProfessionWeaponFragmentManager.giveFragments(player, rarity, 1)) {
            return soundAlreadyPlayed;
        }

        sendFragmentMessage(player, rarity);

        if (!soundAlreadyPlayed && isSuperRareFragment(rarity)) {
            ProfessionActionBarManager.playBattleSuperRareSound(player);
            return true;
        }

        return soundAlreadyPlayed;
    }

    private static String rollFragmentRarity(BattleProfessionLootConfig.FragmentJackpotSettings settings) {
        if (settings.rarityWeights == null || settings.rarityWeights.isEmpty()) {
            return null;
        }

        int totalWeight = 0;

        for (Map.Entry<String, Integer> entry : settings.rarityWeights.entrySet()) {
            String rarity = ProfessionWeaponFragmentConfig.normalizeRarity(entry.getKey());

            if (!settings.allowMythic && "MYTHIC".equals(rarity)) {
                continue;
            }

            if (entry.getValue() != null && entry.getValue() > 0) {
                totalWeight += entry.getValue();
            }
        }

        if (totalWeight <= 0) {
            return null;
        }

        int roll = RANDOM.nextInt(totalWeight);
        int current = 0;

        for (Map.Entry<String, Integer> entry : settings.rarityWeights.entrySet()) {
            String rarity = ProfessionWeaponFragmentConfig.normalizeRarity(entry.getKey());

            if (!settings.allowMythic && "MYTHIC".equals(rarity)) {
                continue;
            }

            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }

            current += entry.getValue();

            if (roll < current) {
                return rarity;
            }
        }

        return null;
    }

    private static boolean giveItemReward(ServerPlayer player, String itemId, int amount, boolean soundAlreadyPlayed) {
        MinecraftServer server = player.getServer();

        if (server == null || itemId == null || itemId.isBlank() || amount <= 0) {
            return soundAlreadyPlayed;
        }

        String command = "give " + player.getName().getString() + " " + itemId + " " + amount;

        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);

        if (BattleProfessionLootConfig.announceRewards && ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            if (BattleProfessionLootConfig.isSuperRareItem(itemId)) {
                ProfessionActionBarManager.sendRareDropMessage(player, itemId, amount, false);

                if (!soundAlreadyPlayed) {
                    ProfessionActionBarManager.playBattleSuperRareSound(player);
                    return true;
                }
            } else {
                ProfessionActionBarManager.sendBattleLootMessage(player, itemId, amount);
            }
        }

        return soundAlreadyPlayed;
    }

    private static void sendFragmentMessage(ServerPlayer player, String rarity) {
        if (!ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            return;
        }

        ChatFormatting color = switch (ProfessionWeaponFragmentConfig.normalizeRarity(rarity)) {
            case "UNCOMMON" -> ChatFormatting.GREEN;
            case "RARE" -> ChatFormatting.BLUE;
            case "EPIC" -> ChatFormatting.LIGHT_PURPLE;
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "MYTHIC" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };

        player.displayClientMessage(
                Component.literal("Battle Jackpot! ")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal(formatWords(rarity) + " Weapon Fragment x1").withStyle(color)),
                true
        );
    }

    private static boolean isSuperRareFragment(String rarity) {
        String normalized = ProfessionWeaponFragmentConfig.normalizeRarity(rarity);
        return "LEGENDARY".equals(normalized) || "MYTHIC".equals(normalized);
    }

    private static String formatWords(String input) {
        if (input == null || input.isBlank()) {
            return "Common";
        }

        String[] parts = input.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(part.charAt(0)));

            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        return builder.toString();
    }
}
