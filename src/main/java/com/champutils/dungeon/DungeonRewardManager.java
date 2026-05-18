package com.champutils.dungeon;

import com.champutils.profession.ProfessionNotificationSettings;

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

    @FunctionalInterface
    private interface PlannedGrant {
        void grant(ServerPlayer player);
    }

    public static final class PlannedCrateReward {
        private final DungeonRarity rarity;
        private final DungeonNativeCrateRegistry.CrateType type;
        private final DungeonCrateRewardSummary summary;
        private final List<PlannedGrant> grants = new ArrayList<>();
        private final List<DungeonCrateRewardSummary.RewardLine> spinPool = new ArrayList<>();
        private DungeonCrateRewardSummary.RewardLine bonusReward;

        private PlannedCrateReward(DungeonRarity rarity, DungeonNativeCrateRegistry.CrateType type, Component title) {
            this.rarity = rarity == null ? DungeonRarity.COMMON : rarity;
            this.type = type == null ? DungeonNativeCrateRegistry.CrateType.NORMAL : type;
            this.summary = new DungeonCrateRewardSummary(title, this.rarity, this.type == DungeonNativeCrateRegistry.CrateType.POKEMON);
        }

        public DungeonRarity rarity() {
            return rarity;
        }

        public DungeonNativeCrateRegistry.CrateType type() {
            return type;
        }

        public DungeonCrateRewardSummary summary() {
            return summary;
        }

        public List<DungeonCrateRewardSummary.RewardLine> rewards() {
            return summary.rewards();
        }

        public List<DungeonCrateRewardSummary.RewardLine> spinRewards() {
            return java.util.Collections.unmodifiableList(spinPool);
        }

        public DungeonCrateRewardSummary.RewardLine finalReward() {
            if (bonusReward != null) {
                return bonusReward;
            }
            if (!spinPool.isEmpty()) {
                return spinPool.get(0);
            }
            return new DungeonCrateRewardSummary.RewardLine(
                    new ItemStack(type == DungeonNativeCrateRegistry.CrateType.POKEMON ? Items.EGG : Items.CHEST),
                    Component.literal("Crate Reward").withStyle(ChatFormatting.GOLD),
                    Component.literal("Opening...").withStyle(ChatFormatting.GRAY)
            );
        }

        private void addGuaranteed(ItemStack icon, Component title, Component detail, PlannedGrant grant) {
            summary.add(icon, title, detail);
            if (grant != null) {
                grants.add(grant);
            }
        }

        private void addPossibleBonus(ItemStack icon, Component title, Component detail) {
            spinPool.add(new DungeonCrateRewardSummary.RewardLine(icon, title, detail));
        }

        private void addBonus(ItemStack icon, Component title, Component detail, PlannedGrant grant) {
            DungeonCrateRewardSummary.RewardLine line = new DungeonCrateRewardSummary.RewardLine(icon, title, detail);
            bonusReward = line;
            summary.add(line.icon(), line.title(), line.detail());
            if (grant != null) {
                grants.add(grant);
            }
            if (spinPool.isEmpty()) {
                spinPool.add(line);
            }
        }

        // Kept for older helper methods in this file. New crate planning uses addGuaranteed/addBonus.
        private void add(ItemStack icon, Component title, Component detail, PlannedGrant grant) {
            addBonus(icon, title, detail, grant);
        }

        private void grantAll(ServerPlayer player) {
            for (PlannedGrant grant : grants) {
                try {
                    grant.grant(player);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
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

    public static PlannedCrateReward planCrateReward(DungeonRarity requestedRarity, DungeonNativeCrateRegistry.CrateType requestedType) {
        DungeonRarity rarity = requestedRarity == null ? DungeonRarity.COMMON : requestedRarity;
        DungeonNativeCrateRegistry.CrateType type = requestedType == null ? DungeonNativeCrateRegistry.CrateType.NORMAL : requestedType;
        DungeonRewardConfig.RewardTable table = DungeonRewardConfig.getTable(rarity);
        String displayName = nice(rarity.name()) + (type == DungeonNativeCrateRegistry.CrateType.POKEMON ? " Pokemon Crate" : " Reward Crate");

        if (type == DungeonNativeCrateRegistry.CrateType.POKEMON) {
            return planPokemonChest(rarity, displayName, table);
        }
        return planNormalChest(rarity, displayName, table);
    }

    public static boolean grantPlannedCrateReward(ServerPlayer player, PlannedCrateReward plan) {
        if (player == null || plan == null) {
            return false;
        }

        DungeonRarity rarity = plan.rarity();
        boolean pokemon = plan.type() == DungeonNativeCrateRegistry.CrateType.POKEMON;

        if (pokemon) {
            if (!DungeonCrateCreditManager.consumePokemonCredit(player.getUUID(), rarity)) {
                player.sendSystemMessage(Component.literal("You have no " + nice(rarity.name()) + " Pokemon Crate credits.").withStyle(ChatFormatting.RED));
                return false;
            }
            player.sendSystemMessage(Component.literal("Pokemon Crate opened!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        } else {
            if (!DungeonCrateCreditManager.consumeNormalCredit(player.getUUID(), rarity)) {
                player.sendSystemMessage(Component.literal("You have no " + nice(rarity.name()) + " Reward Crate credits.").withStyle(ChatFormatting.RED));
                return false;
            }
            player.sendSystemMessage(Component.literal("Loot Crate opened!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        }

        plan.grantAll(player);
        sendSummaryToChat(player, plan.summary());
        sendCreditRemaining(player, rarity);
        DungeonCrateOpeningGui.openRewardSummary(player, plan.summary());
        return true;
    }

    private interface BonusCandidate {
        ItemStack icon();
        Component title();
        Component detail();
        void grant(ServerPlayer player);
    }

    private static final class WeightedBonusCandidate {
        private final double weight;
        private final BonusCandidate candidate;

        private WeightedBonusCandidate(double weight, BonusCandidate candidate) {
            this.weight = Math.max(0.0D, weight);
            this.candidate = candidate;
        }
    }

    private static PlannedCrateReward planNormalChest(DungeonRarity rarity, String displayName, DungeonRewardConfig.RewardTable table) {
        PlannedCrateReward plan = new PlannedCrateReward(
                rarity,
                DungeonNativeCrateRegistry.CrateType.NORMAL,
                Component.literal(displayName + " Rewards")
        );

        addGuaranteedFragments(plan, rarity, table);

        List<WeightedBonusCandidate> candidates = buildNormalBonusCandidates(rarity, displayName, table);
        for (WeightedBonusCandidate weighted : candidates) {
            BonusCandidate candidate = weighted.candidate;
            plan.addPossibleBonus(candidate.icon(), candidate.title(), candidate.detail());
        }

        WeightedBonusCandidate selected = selectWeighted(candidates);
        if (selected != null && selected.candidate != null) {
            BonusCandidate candidate = selected.candidate;
            plan.addBonus(candidate.icon(), candidate.title(), candidate.detail(), candidate::grant);
        } else {
            Component line = Component.literal("No bonus reward configured").withStyle(ChatFormatting.GRAY);
            plan.addBonus(new ItemStack(Items.CHEST), line, Component.literal("Add crate bonus rewards in dungeon_rewards.json.").withStyle(ChatFormatting.DARK_GRAY), null);
        }

        return plan;
    }

    private static PlannedCrateReward planPokemonChest(DungeonRarity rarity, String displayName, DungeonRewardConfig.RewardTable table) {
        PlannedCrateReward plan = new PlannedCrateReward(
                rarity,
                DungeonNativeCrateRegistry.CrateType.POKEMON,
                Component.literal(displayName + " Rewards")
        );

        addGuaranteedFragments(plan, rarity, table);

        List<WeightedBonusCandidate> candidates = buildPokemonBonusCandidates(rarity, displayName, table);
        for (WeightedBonusCandidate weighted : candidates) {
            BonusCandidate candidate = weighted.candidate;
            plan.addPossibleBonus(candidate.icon(), candidate.title(), candidate.detail());
        }

        WeightedBonusCandidate selected = selectWeighted(candidates);
        if (selected != null && selected.candidate != null) {
            BonusCandidate candidate = selected.candidate;
            plan.addBonus(candidate.icon(), candidate.title(), candidate.detail(), candidate::grant);
        } else {
            Component line = Component.literal("No Pokemon rewards configured").withStyle(ChatFormatting.GRAY);
            plan.addBonus(new ItemStack(Items.BARRIER), line, Component.literal("Check dungeon_rewards.json").withStyle(ChatFormatting.RED), null);
        }

        return plan;
    }

    private static void addGuaranteedFragments(PlannedCrateReward plan, DungeonRarity rarity, DungeonRewardConfig.RewardTable table) {
        int fragments = randomBetween(table.guaranteedFragmentsMin, table.guaranteedFragmentsMax);
        if (fragments > 0) {
            ItemStack stack = ProfessionFragmentManager.createFragmentStack(rarity.name(), fragments);
            Component line = Component.literal(fragments + "x " + nice(rarity.name()) + " Tool Fragment").withStyle(rarity.getColor());
            plan.addGuaranteed(stack, line, Component.literal("Guaranteed crate fragments").withStyle(ChatFormatting.GRAY), player -> giveStack(player, stack.copy()));
        }

        DungeonRarity higher = nextRarity(rarity);
        if (higher != null && roll(table.higherTierFragmentChance)) {
            int higherFragments = randomBetween(table.higherTierFragmentsMin, table.higherTierFragmentsMax);
            if (higherFragments > 0) {
                ItemStack stack = ProfessionFragmentManager.createFragmentStack(higher.name(), higherFragments);
                Component line = Component.literal(higherFragments + "x " + nice(higher.name()) + " Tool Fragment").withStyle(higher.getColor(), ChatFormatting.BOLD);
                plan.addGuaranteed(stack, line, Component.literal("Bonus higher-tier fragments").withStyle(ChatFormatting.GRAY), player -> giveStack(player, stack.copy()));
            }
        }
    }

    private static List<WeightedBonusCandidate> buildNormalBonusCandidates(DungeonRarity rarity, String displayName, DungeonRewardConfig.RewardTable table) {
        List<WeightedBonusCandidate> candidates = new ArrayList<>();

        if (table.itemRewards != null) {
            for (DungeonRewardConfig.ItemReward reward : table.itemRewards) {
                WeightedBonusCandidate candidate = itemBonusCandidate(reward);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }

        if (table.commandRewards != null) {
            for (DungeonRewardConfig.CommandReward reward : table.commandRewards) {
                WeightedBonusCandidate candidate = commandBonusCandidate(reward);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }

        if (table.unidentifiedToolChance > 0.0D) {
            boolean ascended = roll(table.ascendedToolChance);
            ItemStack rolledTool = createRandomDungeonTool(rarity, ascended);
            if (!rolledTool.isEmpty()) {
                candidates.add(new WeightedBonusCandidate(table.unidentifiedToolChance, new BonusCandidate() {
                    @Override
                    public ItemStack icon() {
                        return rolledTool.copy();
                    }

                    @Override
                    public Component title() {
                        return Component.literal((ascended ? "Ascended " : "") + "Unidentified " + nice(rarity.name()) + " Profession Tool").withStyle(rarity.getColor(), ChatFormatting.BOLD);
                    }

                    @Override
                    public Component detail() {
                        return Component.literal("Rare full tool drop").withStyle(ChatFormatting.GOLD);
                    }

                    @Override
                    public void grant(ServerPlayer player) {
                        giveStack(player, rolledTool.copy());
                        if (rarity == DungeonRarity.MYTHIC) {
                            playGlobalSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);
                        } else {
                            ProfessionNotificationSettings.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);
                        }
                        if (DungeonRewardConfig.CONFIG.announceMythicTools && rarity == DungeonRarity.MYTHIC) {
                            broadcast(player, Component.literal("✦ " + player.getName().getString() + " found a FULL MYTHIC crate tool from " + displayName + "!")
                                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
                        }
                    }
                }));
            }
        }

        return candidates;
    }

    private static List<WeightedBonusCandidate> buildPokemonBonusCandidates(DungeonRarity rarity, String displayName, DungeonRewardConfig.RewardTable table) {
        List<WeightedBonusCandidate> candidates = new ArrayList<>();
        if (table.pokemonRewards == null || table.pokemonRewards.isEmpty()) {
            return candidates;
        }

        for (DungeonRewardConfig.PokemonReward reward : table.pokemonRewards) {
            if (reward == null || reward.pokemon == null || reward.pokemon.isBlank() || reward.chance <= 0.0D) {
                continue;
            }

            String label = reward.display == null || reward.display.isBlank() ? reward.pokemon : reward.display;
            boolean shiny = roll(reward.shinyChance);
            ItemStack icon = pokemonRewardIcon(reward, shiny);
            candidates.add(new WeightedBonusCandidate(reward.chance, new BonusCandidate() {
                @Override
                public ItemStack icon() {
                    return icon.copy();
                }

                @Override
                public Component title() {
                    return Component.literal(label + " Lv. " + reward.level + (shiny ? " ✨" : "")).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
                }

                @Override
                public Component detail() {
                    return Component.literal("Sent to your party or PC").withStyle(ChatFormatting.GRAY);
                }

                @Override
                public void grant(ServerPlayer player) {
                    runPokemonRewardCommands(player, reward, shiny);
                    ProfessionNotificationSettings.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.1F);
                    if (reward.announce && DungeonRewardConfig.CONFIG.announceLegendaryPokemon) {
                        broadcast(player, Component.literal("✦ " + player.getName().getString() + " found " + label + " from a " + nice(rarity.name()) + " Pokemon crate!")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    }
                }
            }));
        }

        return candidates;
    }

    private static WeightedBonusCandidate itemBonusCandidate(DungeonRewardConfig.ItemReward reward) {
        if (reward == null || reward.id == null || reward.id.isBlank() || reward.chance <= 0.0D) {
            return null;
        }

        Item item;
        try {
            item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.id));
        } catch (Exception ignored) {
            item = Items.AIR;
        }

        if (item == null || item == Items.AIR) {
            return null;
        }

        int rolledAmount = Math.max(1, randomBetween(reward.min, reward.max));
        ItemStack preview = new ItemStack(item, Math.min(64, rolledAmount));
        String label = reward.display == null || reward.display.isBlank() ? reward.id : reward.display;

        return new WeightedBonusCandidate(reward.chance, new BonusCandidate() {
            @Override
            public ItemStack icon() {
                return preview.copy();
            }

            @Override
            public Component title() {
                int shown = preview.getCount();
                return Component.literal(shown + "x " + label).withStyle(ChatFormatting.GREEN);
            }

            @Override
            public Component detail() {
                return Component.literal("Bonus item reward").withStyle(ChatFormatting.GRAY);
            }

            @Override
            public void grant(ServerPlayer player) {
                giveStack(player, preview.copy());
            }
        });
    }

    private static WeightedBonusCandidate commandBonusCandidate(DungeonRewardConfig.CommandReward reward) {
        if (reward == null || reward.commands == null || reward.commands.isEmpty() || reward.chance <= 0.0D) {
            return null;
        }

        String label = reward.display == null || reward.display.isBlank() ? "Command Reward" : reward.display;
        List<String> commands = new ArrayList<>(reward.commands);
        return new WeightedBonusCandidate(reward.chance, new BonusCandidate() {
            @Override
            public ItemStack icon() {
                return new ItemStack(Items.COMMAND_BLOCK);
            }

            @Override
            public Component title() {
                return Component.literal(label).withStyle(ChatFormatting.AQUA);
            }

            @Override
            public Component detail() {
                return Component.literal("Console reward command").withStyle(ChatFormatting.GRAY);
            }

            @Override
            public void grant(ServerPlayer player) {
                runCommands(player, commands, null, 0, false);
            }
        });
    }

    private static WeightedBonusCandidate selectWeighted(List<WeightedBonusCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        double total = 0.0D;
        for (WeightedBonusCandidate candidate : candidates) {
            if (candidate != null && candidate.candidate != null && candidate.weight > 0.0D) {
                total += candidate.weight;
            }
        }

        if (total <= 0.0D) {
            return null;
        }

        double roll = RANDOM.nextDouble() * total;
        double cursor = 0.0D;
        for (WeightedBonusCandidate candidate : candidates) {
            if (candidate == null || candidate.candidate == null || candidate.weight <= 0.0D) {
                continue;
            }
            cursor += candidate.weight;
            if (roll <= cursor) {
                return candidate;
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    private static ItemStack pokemonRewardIcon(DungeonRewardConfig.PokemonReward reward, boolean shiny) {
        if (reward == null) {
            return new ItemStack(Items.EGG);
        }

        return PokemonIconUtil.createPokemonIcon(reward.pokemon, shiny, reward.iconItem, reward.announce);
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

        PlannedCrateReward plan = planCrateReward(rarity, DungeonNativeCrateRegistry.CrateType.NORMAL);
        return grantPlannedCrateReward(player, plan);
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

        PlannedCrateReward plan = planCrateReward(rarity, DungeonNativeCrateRegistry.CrateType.POKEMON);
        return grantPlannedCrateReward(player, plan);
    }

    public static int getPendingChestCount(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return DungeonCrateCreditManager.getTotalCredits(player.getUUID());
    }

    private static void sendSummaryToChat(ServerPlayer player, DungeonCrateRewardSummary summary) {
        if (player == null || summary == null) return;
        for (DungeonCrateRewardSummary.RewardLine line : summary.rewards()) {
            player.sendSystemMessage(Component.literal("  + ").append(line.title()));
        }
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

    private static void planItemReward(DungeonRewardConfig.ItemReward reward, PlannedCrateReward plan) {
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
            return;
        }

        int amount = randomBetween(reward.min, reward.max);
        if (amount <= 0) {
            return;
        }

        ItemStack stack = new ItemStack(item, Math.min(64, amount));
        String label = reward.display == null || reward.display.isBlank() ? reward.id : reward.display;
        Component line = Component.literal(amount + "x " + label).withStyle(ChatFormatting.GREEN);
        plan.add(stack, line, Component.literal("Item reward").withStyle(ChatFormatting.GRAY), player -> giveStack(player, stack.copy()));
    }

    private static void planCommandReward(DungeonRewardConfig.CommandReward reward, PlannedCrateReward plan) {
        if (reward == null || reward.commands == null || reward.commands.isEmpty() || !roll(reward.chance)) {
            return;
        }

        String label = reward.display == null || reward.display.isBlank() ? "Command Reward" : reward.display;
        Component line = Component.literal(label).withStyle(ChatFormatting.AQUA);
        List<String> commands = new ArrayList<>(reward.commands);
        plan.add(new ItemStack(Items.COMMAND_BLOCK), line, Component.literal("Console reward command").withStyle(ChatFormatting.GRAY), player -> runCommands(player, commands, null, 0, false));
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
        if (rarity == DungeonRarity.MYTHIC) {
            playGlobalSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);
        } else {
            ProfessionNotificationSettings.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);
        }

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

    private static void playGlobalSound(ServerPlayer source, net.minecraft.sounds.SoundEvent sound, SoundSource sourceType, float volume, float pitch) {
        MinecraftServer server = source == null ? null : source.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            ProfessionNotificationSettings.playSound(target, sound, sourceType, volume, pitch);
        }
    }

    private static void broadcast(ServerPlayer player, Component message) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            ProfessionNotificationSettings.sendBroadcast(server, message);
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
