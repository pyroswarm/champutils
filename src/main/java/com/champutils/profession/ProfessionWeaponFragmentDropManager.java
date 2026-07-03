package com.champutils.profession;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfessionWeaponFragmentDropManager {

    private static final Random RANDOM = new Random();
    private static final Map<String, Integer> PITY_COUNTERS = new ConcurrentHashMap<>();

    private ProfessionWeaponFragmentDropManager() {
    }

    public static void rollReward(ServerPlayer player, ProfessionType profession) {
        rollReward(player, profession, 1.0D);
    }

    public static void rollReward(ServerPlayer player, ProfessionType profession, double chanceMultiplier) {
        if (player == null || profession == null) {
            return;
        }

        if (!ProfessionWeaponFragmentConfig.ENABLED) {
            return;
        }

        ProfessionWeaponFragmentConfig.DropSettings settings = ProfessionWeaponFragmentConfig.DROP_SETTINGS;

        if (settings == null) {
            return;
        }

        ItemStack tool = findProfessionTool(player, profession);
        String toolRarity = toolRarity(tool);
        if (toolRarity == null || toolRarity.isBlank()) {
            return;
        }

        double chance = getChance(player, profession, settings) * Math.max(0.0D, chanceMultiplier);
        int level = Math.max(0, ProfessionManager.getBenefitLevel(player, profession));

        if (chance <= 0.0D) {
            return;
        }

        String pityKey = pityKey(player, profession);
        int previousMisses = PITY_COUNTERS.getOrDefault(pityKey, 0);
        int pityActions = Math.max(0, settings.pityActions == null ? 0 : settings.pityActions);
        boolean pityTriggered = pityActions > 0 && previousMisses + 1 >= pityActions;

        double roll = RANDOM.nextDouble();
        if (!pityTriggered && roll >= chance) {
            PITY_COUNTERS.merge(pityKey, 1, Integer::sum);
            return;
        }

        String rarity = rollRarityForLevel(level);

        if (rarity == null || rarity.isBlank()) {
            // A bad/old config can leave the eligible pool empty. Never consume a successful roll without a reward.
            rarity = fallbackRarityForLevel(level);
        }

        if (rarity == null || rarity.isBlank()) {
            // Absolute last-resort safety. COMMON is always supported by the default config and the storage layer
            // only needs a normalized text key, so a successful roll should never become a silent miss.
            rarity = "COMMON";
        }

        rarity = ProfessionWeaponFragmentConfig.normalizeRarity(rarity);
        PITY_COUNTERS.remove(pityKey);
        ProfessionManager.addFragments(player, rarity, 1);
        com.champutils.quest.QuestManager.recordProfessionFragment(player, profession, rarity);
        sendMessage(player, rarity, profession, settings);
    }

    private static double getChance(
            ServerPlayer player,
            ProfessionType profession,
            ProfessionWeaponFragmentConfig.DropSettings settings
    ) {
        int level = Math.max(1, ProfessionManager.getBenefitLevel(player, profession));

        double chance = settings.baseDropChance + (settings.chancePerLevel * Math.max(0, level - 1));

        if (settings.maxDropChance > 0.0D) {
            chance = Math.min(chance, settings.maxDropChance);
        }

        double multiplier = 1.0D;

        if (settings.professionMultipliers != null) {
            multiplier = settings.professionMultipliers.getOrDefault(profession.name(), 1.0D);
        }

        return Math.max(0.0D, chance * multiplier);
    }


    private static String rollRarityForLevel(int level) {
        return rollRarity(maxRarityForLevel(level));
    }

    private static String maxRarityForLevel(int level) {
        if (level >= 50) return "LEGENDARY";
        if (level >= 30) return "EPIC";
        if (level >= 20) return "RARE";
        if (level >= 10) return "UNCOMMON";
        return "COMMON";
    }

    private static String fallbackRarityForLevel(int level) {
        int maxTier = Math.min(tierIndex(maxRarityForLevel(level)), TIER_ORDER.length - 1);
        for (int i = maxTier; i >= 0; i--) {
            String rarity = TIER_ORDER[i];
            if (ProfessionWeaponFragmentConfig.FRAGMENTS.containsKey(rarity)) {
                return rarity;
            }
        }
        return "COMMON";
    }

    private static String rollRarity(String toolRarity) {
        Map<String, Integer> eligibleWeights = eligibleWeights(toolRarity);
        int totalWeight = 0;

        for (Integer weight : eligibleWeights.values()) {
            if (weight != null && weight > 0) {
                totalWeight += weight;
            }
        }

        if (totalWeight <= 0) {
            return null;
        }

        int roll = RANDOM.nextInt(totalWeight);
        int current = 0;

        for (Map.Entry<String, Integer> entry : eligibleWeights.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }

            current += entry.getValue();

            if (roll < current) {
                return ProfessionWeaponFragmentConfig.normalizeRarity(entry.getKey());
            }
        }

        return null;
    }


    private static String fallbackRarity(String toolRarity) {
        int maxTier = Math.min(tierIndex(toolRarity) + 1, TIER_ORDER.length - 1);
        for (int i = maxTier; i >= 0; i--) {
            String rarity = TIER_ORDER[i];
            if (ProfessionWeaponFragmentConfig.FRAGMENTS.containsKey(rarity)) {
                return rarity;
            }
        }
        return "COMMON";
    }

    private static String pityKey(ServerPlayer player, ProfessionType profession) {
        UUID uuid = player.getUUID();
        return uuid + ":" + profession.name();
    }

    private static Map<String, Integer> eligibleWeights(String toolRarity) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        int maxTier = Math.min(tierIndex(toolRarity) + 1, TIER_ORDER.length - 1);
        for (int i = 0; i <= maxTier; i++) {
            String rarity = TIER_ORDER[i];
            Integer weight = ProfessionWeaponFragmentConfig.RARITY_WEIGHTS.get(rarity);
            if (weight != null && weight > 0) {
                result.put(rarity, weight);
            }
        }

        if (!result.isEmpty()) {
            return result;
        }

        // Config repair fallback: if rarityWeights is missing/zeroed for the eligible tool pool,
        // recreate a sane local pool instead of making every successful roll fail as "no eligible rarity".
        int[] defaults = new int[]{800000, 150000, 40000, 9000, 950, 50};
        for (int i = 0; i <= maxTier; i++) {
            result.put(TIER_ORDER[i], defaults[i]);
        }
        return result;
    }

    private static final String[] TIER_ORDER = new String[]{"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};

    private static int tierIndex(String rarity) {
        String normalized = ProfessionWeaponFragmentConfig.normalizeRarity(rarity);
        for (int i = 0; i < TIER_ORDER.length; i++) {
            if (TIER_ORDER[i].equals(normalized)) return i;
        }
        return 0;
    }

    private static ItemStack findProfessionTool(ServerPlayer player, ProfessionType profession) {
        ItemStack main = player.getMainHandItem();
        if (ProfessionToolUtil.isUsableProfessionTool(player, main, profession)) return main;
        ItemStack off = player.getOffhandItem();
        if (ProfessionToolUtil.isUsableProfessionTool(player, off, profession)) return off;
        return ItemStack.EMPTY;
    }

    private static String toolRarity(ItemStack tool) {
        ProfessionToolConfig.ToolData data = ProfessionToolUtil.getToolData(tool);
        if (data == null || data.rarity == null || data.rarity.isBlank()) return null;
        return ProfessionWeaponFragmentConfig.normalizeRarity(data.rarity);
    }

    private static void sendMessage(
            ServerPlayer player,
            String rarity,
            ProfessionType profession,
            ProfessionWeaponFragmentConfig.DropSettings settings
    ) {
        if (!ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            return;
        }

        ChatFormatting color = colorFor(rarity);
        String prettyRarity = formatWords(rarity);
        String professionName = formatWords(profession.name());

        if (settings.actionBarMessage) {
            player.displayClientMessage(
                    Component.literal("Profession Fragment! ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(prettyRarity + " x1").withStyle(color))
                            .append(Component.literal(" stored from " + professionName).withStyle(ChatFormatting.YELLOW)),
                    true
            );
        } else {
            player.sendSystemMessage(
                    Component.literal("Profession Fragment! ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(prettyRarity + " x1").withStyle(color))
                            .append(Component.literal(" stored from " + professionName).withStyle(ChatFormatting.YELLOW))
            );
        }

        if ("MYTHIC".equals(rarity)) {
            playGlobalSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.6F);
            playGlobalSound(player, SoundEvents.ENDER_DRAGON_GROWL, 0.45F, 1.7F);
            broadcast(player, rarity, profession, settings);
            return;
        }

        if ("LEGENDARY".equals(rarity)) {
            ProfessionNotificationSettings.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);
            broadcast(player, rarity, profession, settings);
            return;
        }

        ProfessionNotificationSettings.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    private static void playGlobalSound(ServerPlayer source, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (source == null || source.getServer() == null) return;
        for (ServerPlayer target : source.getServer().getPlayerList().getPlayers()) {
            ProfessionNotificationSettings.playSound(target, sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    private static void broadcast(
            ServerPlayer player,
            String rarity,
            ProfessionType profession,
            ProfessionWeaponFragmentConfig.DropSettings settings
    ) {
        if (!settings.announceLegendaryAndMythicToServer) {
            return;
        }

        MinecraftServer server = player.getServer();

        if (server == null) {
            return;
        }

        if (!ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            return;
        }

        ChatFormatting color = colorFor(rarity);

        ProfessionNotificationSettings.sendBroadcast(
                server,
                Component.literal(player.getName().getString()).withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(" found a ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(formatWords(rarity) + " Profession Fragment").withStyle(color))
                        .append(Component.literal(" while training " + formatWords(profession.name()) + "!").withStyle(ChatFormatting.GRAY))
        );
    }

    private static ChatFormatting colorFor(String rarity) {
        return switch (ProfessionWeaponFragmentConfig.normalizeRarity(rarity)) {
            case "UNCOMMON" -> ChatFormatting.GREEN;
            case "RARE" -> ChatFormatting.BLUE;
            case "EPIC" -> ChatFormatting.LIGHT_PURPLE;
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "MYTHIC" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
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
