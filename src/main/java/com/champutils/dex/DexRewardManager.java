package com.champutils.dex;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.shop.NpcShopService;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolManager;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class DexRewardManager {

    private static final Random RANDOM = new Random();

    private DexRewardManager() {
    }

    public static boolean claim(ServerPlayer player, int percent) {
        if (player == null) return false;

        int unlocked = DexProgressManager.getUnlockedPercent(player);
        if (unlocked < percent) {
            int required = DexProgressManager.requiredCaughtForPercent(percent);
            player.sendSystemMessage(Component.literal("You have not reached " + percent + "% Pokédex completion yet. Catch " + required + " unique Pokémon to unlock this reward.").withStyle(ChatFormatting.RED));
            return false;
        }

        if (DexRewardClaimData.hasClaimed(player.getUUID(), percent)) {
            player.sendSystemMessage(Component.literal("You already claimed the " + percent + "% Pokédex reward.").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        java.util.UUID profileId = com.champutils.profile.PlayerProfileManager.activeProfileId(player);
        DexRewardClaimData.markClaimedAsync(profileId, percent).whenComplete((reserved, error) -> player.server.execute(() -> {
            if (!com.champutils.teleport.SafeTeleportManager.isLive(player)
                    || !profileId.equals(com.champutils.profile.PlayerProfileManager.activeProfileId(player))) return;
            if (error != null) {
                player.sendSystemMessage(Component.literal("Could not claim that Pokédex reward right now.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }
            if (!Boolean.TRUE.equals(reserved)) {
                player.sendSystemMessage(Component.literal("You already claimed the " + percent + "% Pokédex reward.").withStyle(ChatFormatting.YELLOW));
                DexRewardsMenu.open(player);
                return;
            }
            com.champutils.network.NetworkEventManager.publishCacheInvalidation("DEX_REWARD_CLAIMS", profileId);

            DexRewardConfig.DexRewardTier tier = DexRewardConfig.getTier(percent);
            for (String rawCommand : tier.commands) runRewardCommand(player, rawCommand, percent);
            AdventureGuideManager.increment(player, "dex_reward", 1);
            ProfessionNotificationSettings.playSound(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
            player.sendSystemMessage(Component.literal("Claimed " + percent + "% Pokédex reward!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            DexRewardsMenu.open(player);
        }));
        return true;
    }

    private static void runRewardCommand(ServerPlayer player, String rawCommand, int percent) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        String command = rawCommand
                .replace("%player%", player.getGameProfile().getName())
                .replace("%uuid%", player.getUUID().toString())
                .replace("%percent%", String.valueOf(percent))
                .replace("%caught%", String.valueOf(DexProgressManager.getCaughtCount(player)))
                .replace("%total%", String.valueOf(DexProgressManager.getTotalPokemon()));

        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        if (handleInternalDexRewardCommand(player, command)) {
            return;
        }

        CommandSourceStack source = server.createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();

        server.getCommands().performPrefixedCommand(source, command);
    }

    private static boolean handleInternalDexRewardCommand(ServerPlayer player, String command) {
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isBlank()) {
            return false;
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) {
            return false;
        }

        if (parts[0].equalsIgnoreCase("dexrandompokemon")) {
            return handleRandomPokemonReward(player, parts);
        }

        if (parts[0].equalsIgnoreCase("dexrandomtool")) {
            return handleRandomToolReward(player, parts);
        }

        return false;
    }

    private static boolean handleRandomPokemonReward(ServerPlayer player, String[] parts) {
        if (parts.length < 5) {
            player.sendSystemMessage(Component.literal("Invalid dexrandompokemon reward command. Expected: dexrandompokemon <player> <pool> <shiny> <level>").withStyle(ChatFormatting.RED));
            return true;
        }

        try {
            NpcShopService.PokemonCratePool pool = NpcShopService.PokemonCratePool.valueOf(parts[2].trim().toUpperCase().replace('-', '_'));
            boolean shiny = Boolean.parseBoolean(parts[3]);
            int level = Integer.parseInt(parts[4]);
            if (!NpcShopService.grantDexPokemonReward(player, pool, shiny, level)) {
                player.sendSystemMessage(Component.literal("Could not grant the random " + parts[2] + " Pokédex reward Pokémon.").withStyle(ChatFormatting.RED));
            }
        } catch (Exception exception) {
            player.sendSystemMessage(Component.literal("Invalid dexrandompokemon reward command: " + exception.getMessage()).withStyle(ChatFormatting.RED));
        }

        return true;
    }

    private static boolean handleRandomToolReward(ServerPlayer player, String[] parts) {
        if (parts.length < 5) {
            player.sendSystemMessage(Component.literal("Invalid dexrandomtool reward command. Expected: dexrandomtool <player> <rarity> <toolType|any> <amount>").withStyle(ChatFormatting.RED));
            return true;
        }

        String rarity = parts[2].trim().toLowerCase(Locale.ROOT);
        String toolType = parts[3].trim().toLowerCase(Locale.ROOT);
        int amount;

        try {
            amount = Math.max(1, Math.min(64, Integer.parseInt(parts[4])));
        } catch (Exception exception) {
            amount = 1;
        }

        int given = 0;
        for (int i = 0; i < amount; i++) {
            String selectedToolId = selectRandomToolId(rarity, toolType);
            if (selectedToolId == null) {
                player.sendSystemMessage(Component.literal("No " + rarity + " " + toolType + " profession tools exist in profession_tools.json.").withStyle(ChatFormatting.RED));
                return true;
            }

            ItemStack stack = ProfessionToolManager.createTool(selectedToolId, false);
            if (stack.isEmpty()) {
                player.sendSystemMessage(Component.literal("Could not create random profession tool: " + selectedToolId).withStyle(ChatFormatting.RED));
                return true;
            }

            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            given++;
        }

        player.sendSystemMessage(Component.literal("Received " + given + " random " + rarity + " profession tool reward.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static String selectRandomToolId(String rarity, String toolType) {
        List<String> candidates = new ArrayList<>();

        for (Map.Entry<String, ProfessionToolConfig.ToolData> entry : ProfessionToolConfig.TOOLS.entrySet()) {
            ProfessionToolConfig.ToolData data = entry.getValue();
            if (data == null) {
                continue;
            }

            String configuredRarity = data.rarity == null ? "" : data.rarity.trim().toLowerCase(Locale.ROOT);
            if (!configuredRarity.equals(rarity)) {
                continue;
            }

            if (!matchesToolType(entry.getKey(), data, toolType)) {
                continue;
            }

            candidates.add(entry.getKey());
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(RANDOM.nextInt(candidates.size()));
    }

    private static boolean matchesToolType(String toolId, ProfessionToolConfig.ToolData data, String toolType) {
        String normalized = toolType == null ? "any" : toolType.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("any") || normalized.equals("tool") || normalized.equals("random")) {
            return true;
        }

        String haystack = ((toolId == null ? "" : toolId) + " " + (data.baseItem == null ? "" : data.baseItem)).toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "pick", "pickaxe", "pickaxes" -> haystack.contains("pickaxe");
            case "axe", "axes" -> !haystack.contains("pickaxe") && haystack.contains("axe");
            case "hoe", "hoes" -> haystack.contains("hoe");
            case "sword", "swords" -> haystack.contains("sword");
            default -> haystack.contains(normalized);
        };
    }
}
