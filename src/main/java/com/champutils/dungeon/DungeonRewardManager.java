package com.champutils.dungeon;

import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolManager;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class DungeonRewardManager {

    private static final Random RANDOM = new Random();

    private DungeonRewardManager() {
    }

    public static void grantCompletionRewards(ServerPlayer player, DungeonSession session) {
        if (player == null || session == null) {
            return;
        }

        int normalChestCount = Math.max(0, DungeonRewardConfig.CONFIG.normalChestCount);
        int pokemonChestCount = Math.max(0, DungeonRewardConfig.CONFIG.pokemonChestCount);

        // Backward compatibility for older generated configs.
        if (normalChestCount <= 0 && DungeonRewardConfig.CONFIG.chestCount > 0) {
            normalChestCount = DungeonRewardConfig.CONFIG.chestCount;
        }

        DungeonCrateCreditManager.grantCredits(player.getUUID(), session.rarity, normalChestCount, pokemonChestCount);

        player.sendSystemMessage(Component.literal("Dungeon crate credits earned!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("+ " + normalChestCount + "x " + nice(session.rarity.name()) + " Reward Crate credit(s)").withStyle(session.rarity.getColor()));
        player.sendSystemMessage(Component.literal("+ " + pokemonChestCount + "x " + nice(session.rarity.name()) + " Pokemon Crate credit(s)").withStyle(ChatFormatting.LIGHT_PURPLE));
        player.sendSystemMessage(Component.literal("Go to spawn and open the matching crate. Credits are bound to you and cannot be traded.").withStyle(ChatFormatting.GRAY));
    }

    private static DungeonCrateRewardSummary openVirtualChest(ServerPlayer player, DungeonRarity rarity, String displayName, DungeonRewardConfig.RewardTable table, int chest, int chestCount) {
        DungeonCrateRewardSummary summary = new DungeonCrateRewardSummary(
                Component.literal(displayName + " Rewards"),
                rarity,
                false
        );

        player.sendSystemMessage(Component.literal("Loot Crate " + chest + "/" + chestCount).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.8F, 1.0F + (chest * 0.05F));

        int fragments = randomBetween(table.guaranteedFragmentsMin, table.guaranteedFragmentsMax);
        if (fragments > 0) {
            ItemStack stack = ProfessionFragmentManager.createFragmentStack(rarity.name(), fragments);
            giveStack(player, stack.copy());
            Component line = Component.literal(fragments + "x " + nice(rarity.name()) + " Tool Fragment").withStyle(rarity.getColor());
            player.sendSystemMessage(Component.literal("  + ").append(line));
            summary.add(stack, line, Component.literal("Guaranteed crate fragments").withStyle(ChatFormatting.GRAY));
        }

        DungeonRarity higher = nextRarity(rarity);
        if (higher != null && roll(table.higherTierFragmentChance)) {
            int higherFragments = randomBetween(table.higherTierFragmentsMin, table.higherTierFragmentsMax);
            if (higherFragments > 0) {
                ItemStack stack = ProfessionFragmentManager.createFragmentStack(higher.name(), higherFragments);
                giveStack(player, stack.copy());
                Component line = Component.literal(higherFragments + "x " + nice(higher.name()) + " Tool Fragment").withStyle(higher.getColor(), ChatFormatting.BOLD);
                player.sendSystemMessage(Component.literal("  + ").append(line));
                summary.add(stack, line, Component.literal("Bonus higher-tier fragments").withStyle(ChatFormatting.GRAY));
            }
        }

        if (table.itemRewards != null) {
            for (DungeonRewardConfig.ItemReward reward : table.itemRewards) {
                rollItemReward(player, reward, summary);
            }
        }

        if (table.commandRewards != null) {
            for (DungeonRewardConfig.CommandReward reward : table.commandRewards) {
                rollCommandReward(player, reward, summary);
            }
        }

        if (roll(table.unidentifiedToolChance)) {
            ItemStack tool = createRandomDungeonTool(rarity, roll(table.ascendedToolChance));
            if (!tool.isEmpty()) {
                giveStack(player, tool.copy());
                Component line = Component.literal("Unidentified " + nice(rarity.name()) + " Profession Tool").withStyle(rarity.getColor(), ChatFormatting.BOLD);
                player.sendSystemMessage(Component.literal("  ★ ").append(line));
                summary.add(tool, line, Component.literal("Rare full tool drop").withStyle(ChatFormatting.GOLD));
                player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);

                if (DungeonRewardConfig.CONFIG.announceMythicTools && rarity == DungeonRarity.MYTHIC) {
                    broadcast(player, Component.literal("✦ " + player.getName().getString() + " found a FULL MYTHIC dungeon tool from " + displayName + "!")
                            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
                }
            }
        }

        if (summary.isEmpty()) {
            summary.add(new ItemStack(Items.CHEST), Component.literal("No bonus rewards rolled").withStyle(ChatFormatting.GRAY), Component.literal("You still consumed a crate credit.").withStyle(ChatFormatting.DARK_GRAY));
        }

        return summary;
    }

    private static DungeonCrateRewardSummary openPokemonChest(ServerPlayer player, DungeonRarity rarity, String displayName, DungeonRewardConfig.RewardTable table, int chest, int chestCount) {
        DungeonCrateRewardSummary summary = new DungeonCrateRewardSummary(
                Component.literal(displayName + " Rewards"),
                rarity,
                true
        );

        player.sendSystemMessage(Component.literal("Pokemon Crate " + chest + "/" + chestCount).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_IN, SoundSource.PLAYERS, 0.8F, 1.0F);

        if (table.pokemonRewards == null || table.pokemonRewards.isEmpty()) {
            Component line = Component.literal("No Pokemon rewards configured").withStyle(ChatFormatting.GRAY);
            player.sendSystemMessage(Component.literal("  ").append(line));
            summary.add(new ItemStack(Items.BARRIER), line, Component.literal("Check dungeon_rewards.json").withStyle(ChatFormatting.RED));
            return summary;
        }

        DungeonRewardConfig.PokemonReward selected = selectPokemonReward(table);
        if (selected == null) {
            Component line = Component.literal("No Pokemon reward is available").withStyle(ChatFormatting.GRAY);
            player.sendSystemMessage(Component.literal("  ").append(line));
            summary.add(new ItemStack(Items.BARRIER), line, Component.literal("Check dungeon_rewards.json").withStyle(ChatFormatting.RED));
            return summary;
        }

        boolean shiny = roll(selected.shinyChance);
        runPokemonRewardCommands(player, selected, shiny);

        String label = selected.display == null || selected.display.isBlank() ? selected.pokemon : selected.display;
        Component line = Component.literal(label + " Lv. " + selected.level + (shiny ? " ✨" : "")).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        player.sendSystemMessage(Component.literal("  ★ Pokemon Reward: ").append(line));
        summary.add(new ItemStack(Items.EGG), line, Component.literal("Sent to your party or PC").withStyle(ChatFormatting.GRAY));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.1F);

        if (selected.announce && DungeonRewardConfig.CONFIG.announceLegendaryPokemon) {
            broadcast(player, Component.literal("✦ " + player.getName().getString() + " found " + label + " from a " + nice(rarity.name()) + " Pokemon crate!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }

        return summary;
    }

    private static DungeonRewardConfig.PokemonReward selectPokemonReward(DungeonRewardConfig.RewardTable table) {
        if (table == null || table.pokemonRewards == null || table.pokemonRewards.isEmpty()) {
            return null;
        }

        double totalWeight = 0.0D;
        List<DungeonRewardConfig.PokemonReward> candidates = new ArrayList<>();
        for (DungeonRewardConfig.PokemonReward reward : table.pokemonRewards) {
            if (reward == null || reward.pokemon == null || reward.pokemon.isBlank() || reward.chance <= 0.0D) {
                continue;
            }
            candidates.add(reward);
            totalWeight += reward.chance;
        }

        if (candidates.isEmpty() || totalWeight <= 0.0D) {
            return null;
        }

        // Pokemon crates are meant to feel special, so they guarantee one Pokemon by default.
        // The chance field is treated as a relative weight for choosing WHICH Pokemon is won.
        if (!table.guaranteePokemonReward && !roll(Math.min(100.0D, totalWeight))) {
            return null;
        }

        double roll = RANDOM.nextDouble() * totalWeight;
        double cursor = 0.0D;
        for (DungeonRewardConfig.PokemonReward reward : candidates) {
            cursor += reward.chance;
            if (roll <= cursor) {
                return reward;
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    public static boolean openPendingDungeonChest(ServerPlayer player, DungeonRarity requestedRarity) {
        if (player == null) {
            return false;
        }

        DungeonRarity rarity = requestedRarity == null ? DungeonRarity.COMMON : requestedRarity;
        int before = DungeonCrateCreditManager.getNormalCredits(player.getUUID(), rarity);
        if (before <= 0) {
            player.sendSystemMessage(Component.literal("You have no " + nice(rarity.name()) + " Reward Crate credits.").withStyle(ChatFormatting.RED));
            return false;
        }

        if (!DungeonCrateCreditManager.consumeNormalCredit(player.getUUID(), rarity)) {
            player.sendSystemMessage(Component.literal("Could not consume your crate credit. Try again.").withStyle(ChatFormatting.RED));
            return false;
        }

        DungeonRewardConfig.RewardTable table = DungeonRewardConfig.getTable(rarity);
        DungeonCrateRewardSummary summary = openVirtualChest(player, rarity, nice(rarity.name()) + " Reward Crate", table, 1, 1);
        sendCreditRemaining(player, rarity);
        DungeonCrateOpeningGui.openRewardSummary(player, summary);
        return true;
    }

    public static boolean openPendingPokemonChest(ServerPlayer player, DungeonRarity requestedRarity) {
        if (player == null) {
            return false;
        }

        DungeonRarity rarity = requestedRarity == null ? DungeonRarity.COMMON : requestedRarity;
        int before = DungeonCrateCreditManager.getPokemonCredits(player.getUUID(), rarity);
        if (before <= 0) {
            player.sendSystemMessage(Component.literal("You have no " + nice(rarity.name()) + " Pokemon Crate credits.").withStyle(ChatFormatting.RED));
            return false;
        }

        if (!DungeonCrateCreditManager.consumePokemonCredit(player.getUUID(), rarity)) {
            player.sendSystemMessage(Component.literal("Could not consume your Pokemon crate credit. Try again.").withStyle(ChatFormatting.RED));
            return false;
        }

        DungeonRewardConfig.RewardTable table = DungeonRewardConfig.getTable(rarity);
        DungeonCrateRewardSummary summary = openPokemonChest(player, rarity, nice(rarity.name()) + " Pokemon Crate", table, 1, 1);
        sendCreditRemaining(player, rarity);
        DungeonCrateOpeningGui.openRewardSummary(player, summary);
        return true;
    }

    public static int getPendingChestCount(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return DungeonCrateCreditManager.getTotalCredits(player.getUUID());
    }

    private static void sendCreditRemaining(ServerPlayer player, DungeonRarity rarity) {
        int normal = DungeonCrateCreditManager.getNormalCredits(player.getUUID(), rarity);
        int pokemon = DungeonCrateCreditManager.getPokemonCredits(player.getUUID(), rarity);
        if (normal > 0 || pokemon > 0) {
            player.sendSystemMessage(Component.literal("Spawn crate credits remaining for " + nice(rarity.name()) + ": " + normal + " normal, " + pokemon + " Pokemon.").withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.literal("All " + nice(rarity.name()) + " spawn crate credits claimed.").withStyle(ChatFormatting.GREEN));
        }
    }

    private static void rollItemReward(ServerPlayer player, DungeonRewardConfig.ItemReward reward, DungeonCrateRewardSummary summary) {
        if (reward == null || reward.id == null || reward.id.isBlank() || !roll(reward.chance)) {
            return;
        }

        Item item;
        try {
            item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.id));
        } catch (Exception ignored) {
            item = Items.AIR;
        }

        if (item == null || item == Items.AIR) {
            player.sendSystemMessage(Component.literal("  ! Invalid reward item in dungeon_rewards.json: " + reward.id).withStyle(ChatFormatting.RED));
            return;
        }

        int amount = randomBetween(reward.min, reward.max);
        if (amount <= 0) {
            return;
        }

        ItemStack stack = new ItemStack(item, Math.min(64, amount));
        giveStack(player, stack.copy());
        String label = reward.display == null || reward.display.isBlank() ? reward.id : reward.display;
        Component line = Component.literal(amount + "x " + label).withStyle(ChatFormatting.GREEN);
        player.sendSystemMessage(Component.literal("  + ").append(line));
        if (summary != null) {
            summary.add(stack, line, Component.literal("Item reward").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void rollCommandReward(ServerPlayer player, DungeonRewardConfig.CommandReward reward, DungeonCrateRewardSummary summary) {
        if (reward == null || reward.commands == null || reward.commands.isEmpty() || !roll(reward.chance)) {
            return;
        }

        runCommands(player, reward.commands, null, 0, false);
        String label = reward.display == null || reward.display.isBlank() ? "Command Reward" : reward.display;
        Component line = Component.literal(label).withStyle(ChatFormatting.AQUA);
        player.sendSystemMessage(Component.literal("  + ").append(line));
        if (summary != null) {
            summary.add(new ItemStack(Items.COMMAND_BLOCK), line, Component.literal("Console reward command").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void runPokemonRewardCommands(ServerPlayer player, DungeonRewardConfig.PokemonReward reward, boolean shiny) {
        if (reward.commands == null || reward.commands.isEmpty()) {
            return;
        }
        runCommands(player, reward.commands, reward.pokemon, reward.level, shiny);
    }

    private static void runCommands(ServerPlayer player, List<String> commands, String pokemon, int level, boolean shiny) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        String playerName = player.getGameProfile().getName();
        String shinyFlag = shiny ? "shiny" : "";

        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }

            String parsed = command
                    .replace("%player%", playerName)
                    .replace("{player}", playerName)
                    .replace("%pokemon%", pokemon == null ? "" : pokemon)
                    .replace("{pokemon}", pokemon == null ? "" : pokemon)
                    .replace("%level%", String.valueOf(level))
                    .replace("{level}", String.valueOf(level))
                    .replace("%shiny%", String.valueOf(shiny))
                    .replace("{shiny}", String.valueOf(shiny))
                    .replace("%shiny_flag%", shinyFlag)
                    .replace("{shiny_flag}", shinyFlag)
                    .trim();

            if (parsed.startsWith("/")) {
                parsed = parsed.substring(1);
            }

            // Backward compatibility: old generated configs used /pokegive with a target player.
            // Cobblemon's self give command is /givepokemon or /pokegive, while giving to another
            // player from console should use /givepokemonother or /pokegiveother.
            // This converts: pokegive Player pikachu level=20 -> givepokemonother Player pikachu level=20
            String lowerParsed = parsed.toLowerCase(Locale.ROOT);
            if (lowerParsed.startsWith("pokegive ") || lowerParsed.startsWith("givepokemon ")) {
                String[] parts = parsed.split("\\s+", 3);
                if (parts.length >= 3 && parts[1].equalsIgnoreCase(playerName)) {
                    parsed = "givepokemonother " + playerName + " " + parts[2];
                }
            }

            server.getCommands().performPrefixedCommand(source, parsed);
        }
    }

    public static void grantFragments(ServerPlayer player, DungeonRarity rarity, int min, int max) {
        if (player == null || rarity == null) {
            return;
        }

        int amount = randomBetween(min, max);
        if (amount <= 0) {
            return;
        }

        giveStack(player, ProfessionFragmentManager.createFragmentStack(rarity.name(), amount));
        player.sendSystemMessage(Component.literal("+ " + amount + "x " + nice(rarity.name()) + " Tool Fragment")
                .withStyle(rarity.getColor()));
    }

    public static void grantRandomTool(ServerPlayer player, DungeonRarity rarity, double ascendedChancePercent) {
        if (player == null || rarity == null) {
            return;
        }

        boolean ascended = roll(ascendedChancePercent);
        ItemStack tool = createRandomDungeonTool(rarity, ascended);
        if (tool.isEmpty()) {
            player.sendSystemMessage(Component.literal("No profession tools exist for rarity: " + rarity.name()).withStyle(ChatFormatting.RED));
            return;
        }

        giveStack(player, tool);
        player.sendSystemMessage(Component.literal("★ Unidentified " + nice(rarity.name()) + " profession tool!")
                .withStyle(rarity.getColor(), ChatFormatting.BOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);

        if (DungeonRewardConfig.CONFIG.announceMythicTools && rarity == DungeonRarity.MYTHIC) {
            broadcast(player, Component.literal("✦ " + player.getName().getString() + " found a FULL MYTHIC dungeon tool!")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        }
    }

    public static void grantItem(ServerPlayer player, String itemId, int min, int max) {
        if (player == null || itemId == null || itemId.isBlank()) {
            return;
        }

        Item item;
        try {
            item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        } catch (Exception ignored) {
            item = Items.AIR;
        }

        if (item == null || item == Items.AIR) {
            player.sendSystemMessage(Component.literal("Invalid dungeon reward item: " + itemId).withStyle(ChatFormatting.RED));
            return;
        }

        int amount = randomBetween(min, max);
        if (amount <= 0) {
            return;
        }

        giveStack(player, new ItemStack(item, Math.min(64, amount)));
        player.sendSystemMessage(Component.literal("+ " + amount + "x " + itemId).withStyle(ChatFormatting.GREEN));
    }

    public static ItemStack createRandomDungeonTool(DungeonRarity rarity, boolean ascended) {
        List<String> candidates = new ArrayList<>();
        String wanted = rarity.name();

        for (Map.Entry<String, ProfessionToolConfig.ToolData> entry : ProfessionToolConfig.TOOLS.entrySet()) {
            ProfessionToolConfig.ToolData data = entry.getValue();
            if (data == null || data.rarity == null) {
                continue;
            }

            if (!wanted.equalsIgnoreCase(data.rarity)) {
                continue;
            }

            if (ascended && !data.hasAscendedVariant) {
                continue;
            }

            candidates.add(entry.getKey());
        }

        if (candidates.isEmpty() && ascended) {
            return createRandomDungeonTool(rarity, false);
        }

        if (candidates.isEmpty()) {
            return ItemStack.EMPTY;
        }

        String toolId = candidates.get(RANDOM.nextInt(candidates.size()));
        return ProfessionToolManager.createLootTool(toolId, ascended);
    }

    private static void giveStack(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }

        boolean added = player.getInventory().add(stack);
        if (!added) {
            player.drop(stack, false);
        }
    }

    private static void broadcast(ServerPlayer player, Component message) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    private static int randomBetween(int min, int max) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        if (high <= 0) {
            return 0;
        }
        if (low < 0) {
            low = 0;
        }
        return low + RANDOM.nextInt((high - low) + 1);
    }

    private static boolean roll(double chancePercent) {
        return chancePercent > 0.0D && RANDOM.nextDouble() * 100.0D < chancePercent;
    }

    private static DungeonRarity nextRarity(DungeonRarity rarity) {
        if (rarity == null) return null;
        int next = rarity.ordinal() + 1;
        DungeonRarity[] values = DungeonRarity.values();
        return next >= values.length ? null : values[next];
    }

    private static String nice(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }

        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] words = lower.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

}
