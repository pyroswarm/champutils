package com.champutils.profession;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Map;
import java.util.Random;

public final class ProfessionWeaponFragmentDropManager {

    private static final Random RANDOM = new Random();

    private ProfessionWeaponFragmentDropManager() {
    }

    public static void rollReward(ServerPlayer player, ProfessionType profession) {
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

        double chance = getChance(player, profession, settings);

        if (chance <= 0.0D) {
            return;
        }

        if (RANDOM.nextDouble() >= chance) {
            return;
        }

        String rarity = rollRarity();

        if (rarity == null || rarity.isBlank()) {
            return;
        }

        if (!ProfessionWeaponFragmentManager.giveFragments(player, rarity, 1)) {
            return;
        }

        sendMessage(player, rarity, profession, settings);
    }

    private static double getChance(
            ServerPlayer player,
            ProfessionType profession,
            ProfessionWeaponFragmentConfig.DropSettings settings
    ) {
        int level = Math.max(1, ProfessionManager.getLevel(player, profession));

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

    private static String rollRarity() {
        int totalWeight = 0;

        for (Integer weight : ProfessionWeaponFragmentConfig.RARITY_WEIGHTS.values()) {
            if (weight != null && weight > 0) {
                totalWeight += weight;
            }
        }

        if (totalWeight <= 0) {
            return null;
        }

        int roll = RANDOM.nextInt(totalWeight);
        int current = 0;

        for (Map.Entry<String, Integer> entry : ProfessionWeaponFragmentConfig.RARITY_WEIGHTS.entrySet()) {
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
                    Component.literal("Weapon Fragment! ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(prettyRarity + " x1").withStyle(color))
                            .append(Component.literal(" from " + professionName).withStyle(ChatFormatting.YELLOW)),
                    true
            );
        } else {
            player.sendSystemMessage(
                    Component.literal("Weapon Fragment! ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(prettyRarity + " x1").withStyle(color))
                            .append(Component.literal(" from " + professionName).withStyle(ChatFormatting.YELLOW))
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
                        .append(Component.literal(formatWords(rarity) + " Weapon Fragment").withStyle(color))
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
